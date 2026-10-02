package com.budget.app.ui

import com.budget.app.ai.parseAnalysis
import com.budget.app.budget.PeriodRange
import com.budget.app.budget.allowance
import com.budget.app.data.Analysis
import com.budget.app.data.AnalysisState
import com.budget.app.data.Category
import com.budget.app.data.RawNotification
import com.budget.app.data.RawStatus
import com.budget.app.data.Transaction
import com.budget.app.data.TxType
import com.budget.app.data.counts
import com.budget.app.data.day
import com.budget.app.data.periodsOf
import com.budget.app.ingest.sourceLabel
import com.budget.app.ingest.sourceOf
import com.budget.app.ui.components.HeroState
import com.budget.app.ui.components.TxKind
import com.budget.app.ui.components.categoryIcon
import com.budget.app.ui.format.displayCurrency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---- Real data -> screen states (DESIGN.md step 5). Pure functions over what Room and Settings hold. ----

/** What the screens need from Settings, read once per change. */
data class SettingsUi(
    val budgetMinor: Long?,
    val salarySource: String?,
    val salaryMinMinor: Long?,
    val firstDay: LocalDate,
    val rates: Map<String, BigDecimal>,
    val apiKey: String?,
)

data class Live(
    val txs: List<Transaction>,
    val cats: List<Category>,
    val raws: List<RawNotification>,
    val analyses: List<Analysis>,
    val s: SettingsUi,
    val today: LocalDate,
) {
    private val byId = cats.associateBy { it.id }
    val periods: List<PeriodRange> = periodsOf(txs, s.firstDay)
    val current get() = periods.first()
    val hasSalary = txs.any { it.isSalary }
    val expenseCategories = cats.filter { !it.isIncome }

    fun categoryName(id: Long?) = byId[id]?.name ?: "Bez kategorije"
    fun categoryId(name: String?) = cats.find { it.name == name }?.id

    fun ui(t: Transaction) = TransactionUi(
        t.id, t.merchant, categoryName(t.categoryId), categoryIcon(categoryName(t.categoryId)),
        LocalDateTime.ofInstant(Instant.ofEpochMilli(t.occurredAt), ZoneId.systemDefault()),
        t.amountMinor, t.currency, t.amountEurMinor,
        when {
            t.type == TxType.TRANSFER -> TxKind.Transfer
            t.isSalary -> TxKind.Salary
            t.type == TxType.INCOME -> TxKind.Income
            else -> TxKind.Expense
        },
        aiSuggested = t.categoryId != null && !t.categoryConfirmed,
    )

    private fun spentIn(p: PeriodRange, until: LocalDate = LocalDate.MAX) =
        txs.filter { it.counts() && it.day() in p && !it.day().isAfter(until) }

    private fun salarySourceLabel() = when (s.salarySource) {
        null -> null
        "any" -> "bilo koja banka"
        else -> sourceLabel(s.salarySource)
    }

    // ---- Početna ----

    fun pocetna(listenerOn: Boolean): PocetnaState {
        val p = current
        val inPeriod = spentIn(p, today)
        val spentToday = inPeriod.filter { it.day() == today }.sumOf { it.amountEurMinor!! }
        val spent = inPeriod.sumOf { it.amountEurMinor!! }
        val hero = when {
            !hasSalary -> HeroState.Empty(salarySourceLabel(), s.salaryMinMinor)
            s.budgetMinor == null -> HeroState.NoBudget
            else -> allowance(s.budgetMinor, spent - spentToday, spentToday, today, p.endExclusive).let {
                if (it.remainingTodayMinor >= 0) HeroState.Normal(it.remainingTodayMinor, it.daysLeft) else HeroState.Over(-it.remainingTodayMinor, it.daysLeft)
            }
        }
        val perCat = inPeriod.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountEurMinor!! } }
        return PocetnaState(
            today = today,
            hero = hero,
            pausedSince = if (listenerOn) null else raws.firstOrNull()?.postedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) } ?: LocalDateTime.now(),
            reviewCount = zaProvjeru().count,
            period = if (hasSalary && s.budgetMinor != null) PeriodUi(p.start, p.endExclusive, spent, s.budgetMinor) else null,
            categories = expenseCategories.filter { it.limitEurMinor != null || (perCat[it.id] ?: 0) > 0 }
                .map { CategoryUi(it.name, categoryIcon(it.name), perCat[it.id] ?: 0, it.limitEurMinor) },
            transactions = txs.take(5).map(::ui),
        )
    }

    // ---- Transakcije ----

    fun transakcije() = TransakcijeState(today, periods, cats.map { it.name }, txs.map(::ui))

    private val rawTime = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss")

    fun detail(id: Long): TransakcijaUi? = txs.find { it.id == id }?.let { t ->
        val raw = raws.filter { it.transactionId == id }.joinToString("\n\n") { r ->
            listOfNotNull(r.packageName, LocalDateTime.ofInstant(Instant.ofEpochMilli(r.postedAt), ZoneId.systemDefault()).format(rawTime), r.title, r.text).joinToString("\n")
        }.ifBlank { null }
        val rate = when (t.currency) {
            "EUR" -> null
            "BAM" -> "1 € = 1,95583 KM"
            else -> s.rates[t.currency]?.let { "1 € = ${it.toPlainString().replace('.', ',')} ${displayCurrency(t.currency)}" }
        }
        TransakcijaUi(ui(t), sourceLabel(t.source), raw, rate)
    }

    // ---- Izvještaji ----

    fun izvjestaji(): IzvjestajiState {
        val all = txs.map(::ui)
        val shown = periods.take(12)
        return IzvjestajiState(
            shown.mapIndexed { i, p -> buildReport(p, periods.getOrNull(i + 1), all, today, s.budgetMinor) },
            analyses.mapNotNull(::analysis),
        )
    }

    fun analysis(a: Analysis): AnalysisUi? {
        val p = PeriodRange(LocalDate.ofEpochDay(a.periodStart), LocalDate.ofEpochDay(a.periodEnd))
        val status = when (a.state) {
            AnalysisState.LOADING -> AnalysisStatus.Loading
            AnalysisState.FAILED -> AnalysisStatus.Failed(a.error ?: "Analiza nije uspjela.", a.keyProblem)
            AnalysisState.READY -> runCatching { parseAnalysis(a.json!!) }.getOrNull()?.let {
                AnalysisStatus.Ready(it.teaser, it.sections, it.tips, LocalDateTime.ofInstant(Instant.ofEpochMilli(a.createdAt), ZoneId.systemDefault()))
            } ?: AnalysisStatus.Failed("Sačuvana analiza je oštećena.", false)
        }
        val salary = txs.filter { it.isSalary && it.day() == p.start }.sumOf { it.amountEurMinor ?: 0 }.takeIf { it > 0 }
        return AnalysisUi(a.id, p, spentIn(p).sumOf { it.amountEurMinor!! }, a.budgetMinor, salary, status)
    }

    // ---- Budžet ----

    fun budzet() = BudzetState(
        s.budgetMinor ?: 0,
        expenseCategories.map { c -> BudzetCategory(c.name, c.limitEurMinor, txs.count { it.categoryId == c.id }) },
    )

    // ---- Za provjeru ----

    fun zaProvjeru() = ZaProvjeruState(
        today,
        txs.filter { it.type == TxType.EXPENSE && !it.categoryConfirmed }.map { ui(it) to sourceLabel(it.source) },
        raws.filter { it.status == RawStatus.FAILED }.map { r ->
            UnknownNotification(
                r.id, sourceOf(r.packageName, r.tag, true)?.let(::sourceLabel) ?: r.packageName,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(r.postedAt), ZoneId.systemDefault()),
                listOfNotNull(r.title, r.text).joinToString("\n"),
            )
        },
        txs.filter { it.amountEurMinor == null }.map(::ui),
    )
}
