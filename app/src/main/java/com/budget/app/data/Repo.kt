package com.budget.app.data

import android.content.Context
import com.budget.app.ai.Ai
import com.budget.app.budget.Notify
import com.budget.app.budget.PeriodRange
import com.budget.app.budget.merchantKey
import com.budget.app.budget.periods
import com.budget.app.budget.reachedThresholds
import com.budget.app.budget.toEurMinor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun Transaction.day(): LocalDate = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** The budget rule: expenses with a known euro amount. Transfers, salary, other income and rows without a rate don't count. */
fun Transaction.counts() = type == TxType.EXPENSE && amountEurMinor != null

fun periodsOf(txs: List<Transaction>, firstDay: LocalDate): List<PeriodRange> =
    periods(txs.filter { it.isSalary }.map { it.day() }, firstDay)

/** Every change to transactions, categories and settings goes through here, so the follow-ups (rules, AI, alerts, analysis) always run. */
object Repo {
    private fun db(ctx: Context) = AppDatabase.get(ctx)

    /** Rules, AI category, limit alerts and, for a salary, the analysis of the period that just ended. */
    suspend fun afterChange(ctx: Context, tx: Transaction) {
        if (tx.type == TxType.EXPENSE && tx.categoryId == null) Ai.categorizeLater(ctx, tx.id)
        if (tx.isSalary) startAnalysis(ctx)
        checkAlerts(ctx)
    }

    // ---- Transakcije ----

    /** Manual entry; [salary] starts a new period, [fromRawId] links the unrecognized notification it replaces. */
    suspend fun addManual(
        ctx: Context, amountMinor: Long, currency: String, merchant: String, categoryId: Long?,
        transfer: Boolean, salary: Boolean, fromRawId: Long? = null,
    ) {
        val s = Settings(ctx)
        val d = db(ctx)
        val raw = fromRawId?.let { d.rawNotifications().byId(it) }
        val tx = Transaction(
            amountMinor = amountMinor, currency = currency,
            amountEurMinor = toEurMinor(amountMinor, currency, s.rates),
            type = when { transfer -> TxType.TRANSFER; salary -> TxType.INCOME; else -> TxType.EXPENSE },
            merchant = merchant, categoryId = categoryId, categoryConfirmed = categoryId != null,
            isSalary = salary && !transfer,
            occurredAt = raw?.postedAt ?: System.currentTimeMillis(), source = "manual",
        )
        val id = d.transactions().insert(tx)
        raw?.let { d.rawNotifications().setStatus(it.id, RawStatus.PARSED, id) }
        rememberCategory(ctx, merchant, categoryId, tx.type)
        afterChange(ctx, tx.copy(id = id))
    }

    suspend fun ignoreRaw(ctx: Context, rawId: Long) = db(ctx).rawNotifications().setStatus(rawId, RawStatus.IGNORED)

    suspend fun edit(ctx: Context, id: Long, merchant: String, categoryId: Long?, confirmed: Boolean, transfer: Boolean, salary: Boolean) {
        val d = db(ctx)
        val old = d.transactions().byId(id) ?: return
        val incomeCategory = categoryId != null && d.categories().allNow().find { it.id == categoryId }?.isIncome == true
        val type = when {
            transfer -> TxType.TRANSFER
            salary || incomeCategory || old.type == TxType.INCOME -> TxType.INCOME
            else -> TxType.EXPENSE
        }
        val new = old.copy(merchant = merchant, categoryId = categoryId, categoryConfirmed = confirmed, type = type, isSalary = salary && !transfer)
        d.transactions().update(new)
        if (confirmed && categoryId != old.categoryId || confirmed && !old.categoryConfirmed) rememberCategory(ctx, merchant, categoryId, type)
        if (new.isSalary && !old.isSalary) afterChange(ctx, new) else checkAlerts(ctx)
    }

    suspend fun confirmCategory(ctx: Context, id: Long) {
        val d = db(ctx)
        val t = d.transactions().byId(id) ?: return
        d.transactions().update(t.copy(categoryConfirmed = true))
        rememberCategory(ctx, t.merchant, t.categoryId, t.type)
    }

    /** The original notification stays in the raw log, marked as ignored. */
    suspend fun delete(ctx: Context, id: Long) {
        db(ctx).rawNotifications().unlink(id)
        db(ctx).transactions().delete(id)
    }

    private suspend fun rememberCategory(ctx: Context, merchant: String, categoryId: Long?, type: TxType) {
        if (categoryId != null && type == TxType.EXPENSE) db(ctx).rules().upsert(MerchantRule(merchantKey(merchant), categoryId))
    }

    // ---- Kurs ----

    suspend fun setRate(ctx: Context, currency: String, perEur: BigDecimal) {
        val s = Settings(ctx)
        s.setRate(currency, perEur)
        val d = db(ctx)
        d.transactions().withoutRate(currency).forEach { d.transactions().update(it.copy(amountEurMinor = toEurMinor(it.amountMinor, it.currency, s.rates))) }
        checkAlerts(ctx)
    }

    suspend fun setEurAmount(ctx: Context, id: Long, eurMinor: Long) {
        val d = db(ctx)
        d.transactions().byId(id)?.let { d.transactions().update(it.copy(amountEurMinor = eurMinor)) }
        checkAlerts(ctx)
    }

    // ---- Kategorije ----

    suspend fun addCategory(ctx: Context, name: String): Long = db(ctx).categories().insert(Category(name = name))

    suspend fun renameCategory(ctx: Context, id: Long, name: String) {
        val d = db(ctx)
        d.categories().allNow().find { it.id == id }?.let { d.categories().update(it.copy(name = name)) }
    }

    suspend fun setLimit(ctx: Context, id: Long, limitMinor: Long?) {
        val d = db(ctx)
        d.categories().allNow().find { it.id == id }?.let { d.categories().update(it.copy(limitEurMinor = limitMinor)) }
        checkAlerts(ctx)
    }

    /** Transactions and merchant rules move to "Ostalo". */
    suspend fun deleteCategory(ctx: Context, id: Long) {
        val d = db(ctx)
        val cats = d.categories().allNow()
        val cat = cats.find { it.id == id } ?: return
        val fallback = cats.find { it.name == FALLBACK && !it.isIncome } ?: return
        if (cat.id == fallback.id) return
        d.transactions().moveCategory(cat.id, fallback.id)
        d.rules().moveCategory(cat.id, fallback.id)
        d.categories().delete(cat)
    }

    const val FALLBACK = "Ostalo"

    // ---- Upozorenja i analiza ----

    /** Sends each 80 % / 100 % warning once per period and category (only the highest new one). */
    suspend fun checkAlerts(ctx: Context) {
        val d = db(ctx)
        val s = Settings(ctx)
        val txs = d.transactions().allNow()
        val period = periodsOf(txs, s.firstDay).first()
        val today = LocalDate.now()
        val daysLeft = ChronoUnit.DAYS.between(today, period.endExclusive).toInt().coerceAtLeast(1)
        val spent = txs.filter { it.counts() && it.day() in period }.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountEurMinor!! } }
        d.categories().allNow().filter { it.limitEurMinor != null && !it.isIncome }.forEach { c ->
            val sum = spent[c.id] ?: 0
            val fresh = reachedThresholds(sum, c.limitEurMinor!!).filter { !s.alertSent("${period.start}_${c.id}_$it") }
            if (fresh.isEmpty()) return@forEach
            fresh.forEach { s.markAlert("${period.start}_${c.id}_$it") }
            Notify.limit(ctx, c.id, c.name, fresh.max(), sum, c.limitEurMinor, daysLeft)
        }
    }

    /** After a salary: analysis of the period that just ended (none before the first full period). */
    suspend fun startAnalysis(ctx: Context) {
        val d = db(ctx)
        val ps = periodsOf(d.transactions().allNow(), Settings(ctx).firstDay)
        val ended = ps.getOrNull(1) ?: return
        if (d.analyses().forPeriod(ended.start.toEpochDay()) != null) return
        val id = d.analyses().insert(
            Analysis(
                periodStart = ended.start.toEpochDay(), periodEnd = ended.endExclusive.toEpochDay(),
                budgetMinor = Settings(ctx).budgetMinor ?: 0, state = AnalysisState.LOADING, createdAt = System.currentTimeMillis(),
            ),
        )
        Ai.analyzeLater(ctx, id)
    }

    suspend fun retryAnalysis(ctx: Context, id: Long) {
        val d = db(ctx)
        d.analyses().byId(id)?.let { d.analyses().update(it.copy(state = AnalysisState.LOADING, error = null, keyProblem = false)) }
        Ai.analyzeLater(ctx, id)
    }
}
