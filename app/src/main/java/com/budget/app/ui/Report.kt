package com.budget.app.ui

import com.budget.app.budget.PeriodRange
import com.budget.app.ui.components.TxKind
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.formatCurrency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MerchantRow(val name: String, val meta: String, val eurMinor: Long)
data class CompareRow(val name: String, val nowMinor: Long, val beforeMinor: Long)
data class DayBar(val label: String, val eurMinor: Long, val today: Boolean)

/** Everything on the Pregled tab for one period. Amounts in euro cents. */
data class ReportUi(
    val period: PeriodRange,
    val byCategory: List<Pair<String, Long>>,
    val days: List<DayBar>,
    val budgetMinor: Long?,
    val periodDays: Int,
    val merchants: List<MerchantRow>,
    val compare: List<CompareRow>,
    /** Same number of days at the start of the previous period, e.g. "25.08.–30.08."; null without a previous period. */
    val compareRange: String?,
) {
    val dailyBudgetMinor get() = budgetMinor?.let { it / periodDays }
}

internal fun transakcija(n: Int) = if (n % 10 in 2..4 && n % 100 !in 12..14) "transakcije" else "transakcija"

/**
 * Only expenses with a known euro amount count: salary, other income, transfers and rows still waiting
 * for an exchange rate are left out, the same rule as the budget (DESIGN.md "Behavior notes").
 */
fun buildReport(
    period: PeriodRange,
    previous: PeriodRange?,
    transactions: List<TransactionUi>,
    today: LocalDate,
    budgetMinor: Long?,
): ReportUi {
    val spend = transactions.filter { it.kind == TxKind.Expense && it.amountEurMinor != null }
    val last = minOf(today, period.endExclusive.minusDays(1))
    val cur = spend.filter { it.at.toLocalDate().let { d -> d in period && !d.isAfter(last) } }

    val byCategory = cur.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountEurMinor!! } }
        .toList().sortedByDescending { it.second }

    val firstBar = maxOf(period.start, last.minusDays(5))
    val days = generateSequence(firstBar) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { d ->
        DayBar(if (d == today) "Danas" else "${d.dayOfMonth}.", cur.filter { it.at.toLocalDate() == d }.sumOf { it.amountEurMinor!! }, d == today)
    }.toList()

    val merchants = cur.groupBy { it.merchant }.map { (name, l) ->
        val one = l.singleOrNull()
        val meta = if (one != null && one.currency != "EUR") one.amountMinor.formatCurrency(one.currency) else "${l.size} ${transakcija(l.size)}"
        MerchantRow(name, "${l.first().category} · $meta", l.sumOf { it.amountEurMinor!! })
    }.sortedByDescending { it.eurMinor }.take(5)

    var compare = emptyList<CompareRow>()
    var compareRange: String? = null
    if (previous != null) {
        val elapsed = ChronoUnit.DAYS.between(period.start, last)
        val prevLast = minOf(previous.start.plusDays(elapsed), previous.endExclusive.minusDays(1))
        compareRange = "${previous.start.format(DayFormat)}–${prevLast.format(DayFormat)}"
        val before = spend.filter { it.at.toLocalDate().let { d -> !d.isBefore(previous.start) && !d.isAfter(prevLast) } }
            .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountEurMinor!! } }
        val now = byCategory.toMap()
        compare = (now.keys + before.keys).map { CompareRow(it, now[it] ?: 0, before[it] ?: 0) }.sortedByDescending { it.nowMinor }
    }

    return ReportUi(
        period, byCategory, days, budgetMinor,
        ChronoUnit.DAYS.between(period.start, period.endExclusive).toInt(),
        merchants, compare, compareRange,
    )
}
