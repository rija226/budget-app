package com.budget.app.budget

import com.budget.app.ui.format.DayFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A budget period: from one salary (inclusive) to the next (exclusive). */
data class PeriodRange(val start: LocalDate, val endExclusive: LocalDate) {
    val label get() = "${start.format(DayFormat)}–${endExclusive.minusDays(1).format(DayFormat)}"
    operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(endExclusive)
    val days get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()
}

// ---- Trgovac ----

// ponytail: kratak spisak gradova i pravnih oznaka; dopuniti kad stvarne notifikacije pokažu druge
private val NOISE = setOf(
    "DOO", "AD", "SZR", "STR", "SUR", "PR", "DOOEL",
    "BEOGRAD", "NOVI", "SAD", "NIS", "KRAGUJEVAC", "SUBOTICA", "PODGORICA", "NIKSIC", "BUDVA", "BAR", "KOTOR", "HERCEG",
    "SARAJEVO", "BANJA", "LUKA", "MOSTAR", "TUZLA", "ZENICA", "ZAGREB", "RS", "ME", "BA", "SRB", "MNE", "BIH",
)

/**
 * Key under which a merchant's category is remembered: upper case, no digits or punctuation,
 * no legal suffixes or city names. "MAXI 123 BEOGRAD" and "Maxi 456" both become "MAXI".
 */
fun merchantKey(name: String): String {
    val words = name.uppercase()
        .replace(".", "") // "D.O.O." -> "DOO"
        .replace(Regex("[^\\p{L} ]"), " ")
        .split(' ')
        .filter { it.isNotBlank() }
    val kept = words.filter { it !in NOISE }
    return (kept.ifEmpty { words }).joinToString(" ")
}

// ---- Valute ----

/** KM is pegged to the euro. Other rates are entered by the owner. */
val BAM_PER_EUR = BigDecimal("1.95583")

/** [rates]: units of the currency for 1 EUR. Null when the rate is unknown. */
fun toEurMinor(amountMinor: Long, currency: String, rates: Map<String, BigDecimal>): Long? {
    val rate = when (currency) {
        "EUR" -> return amountMinor
        "BAM" -> BAM_PER_EUR
        else -> rates[currency] ?: return null
    }
    return BigDecimal(amountMinor).divide(rate, 0, RoundingMode.HALF_UP).longValueExact()
}

// ---- Periodi ----

/**
 * Periods newest first. Each salary starts a period; the running one ends at the expected next salary:
 * the last salary + the length of the previous period, or +1 month for the first one.
 * Before the first salary there is one provisional period from [firstDay].
 */
fun periods(salaryDays: List<LocalDate>, firstDay: LocalDate): List<PeriodRange> {
    val starts = salaryDays.distinct().sorted()
    if (starts.isEmpty()) return listOf(PeriodRange(firstDay, firstDay.plusMonths(1)))
    val closed = starts.zipWithNext { a, b -> PeriodRange(a, b) }
    val last = starts.last()
    val expected = closed.lastOrNull()?.let { last.plusDays(it.days.toLong()) } ?: last.plusMonths(1)
    return (closed + PeriodRange(last, expected)).reversed()
}

// ---- Dnevni limit ----

/** [remainingTodayMinor] < 0 means today is over by that much. [daysLeft] includes today. */
data class Allowance(val remainingTodayMinor: Long, val daysLeft: Int)

/**
 * The period budget minus everything spent before today, spread evenly over the days left
 * until the expected salary (today included), minus what was already spent today.
 */
fun allowance(budgetMinor: Long, spentBeforeTodayMinor: Long, spentTodayMinor: Long, today: LocalDate, salaryExpected: LocalDate): Allowance {
    val daysLeft = ChronoUnit.DAYS.between(today, salaryExpected).toInt().coerceAtLeast(1)
    val perDay = Math.floorDiv(budgetMinor - spentBeforeTodayMinor, daysLeft.toLong())
    return Allowance(perDay - spentTodayMinor, daysLeft)
}

// ---- Upozorenja ----

/** Limit thresholds (80 %, 100 %) reached by [spent]; the caller remembers which were already announced. */
fun reachedThresholds(spent: Long, limit: Long): List<Int> =
    if (limit <= 0) emptyList() else listOf(80, 100).filter { pct -> spent * 100 >= limit * pct }
