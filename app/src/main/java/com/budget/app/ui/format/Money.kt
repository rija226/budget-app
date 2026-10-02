package com.budget.app.ui.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val symbols = DecimalFormatSymbols().apply {
    decimalSeparator = ','
    groupingSeparator = '.'
}
private val amount = DecimalFormat("#,##0.00", symbols)

private const val NBSP = ' ' // keeps "12,50 €" on one line

/** 150000 -> "1.500,00 €" */
fun Long.formatEur(): String = amount.format(this / 100.0) + NBSP + "€"

/** Currencies are stored as ISO codes; BAM is shown the way people write it. */
fun displayCurrency(code: String) = if (code == "BAM") "KM" else code

/** 2500, "BAM" -> "25,00 KM" */
fun Long.formatCurrency(code: String): String = amount.format(this / 100.0) + NBSP + displayCurrency(code)

/** Foreign row secondary line: "≈ 12,78 €" */
fun Long.formatApproxEur(): String = "≈ " + formatEur()

/** 1250 -> "12,50" (amount field, no currency) */
fun Long.formatPlain(): String = amount.format(this / 100.0)

/**
 * Typed amount -> cents, or null if it is not a positive amount with at most 2 decimals.
 * "12,50", "12.50", "1.500", "1.500,00", "1,500.00" are all accepted; a dot followed by exactly
 * three digits is a thousands separator.
 */
fun parseAmountMinor(text: String): Long? {
    var s = text.replace(" ", "").replace(NBSP.toString(), "").removeSuffix("€")
    if (s.isEmpty() || !s.all { it.isDigit() || it == ',' || it == '.' }) return null
    val lastComma = s.lastIndexOf(','); val lastDot = s.lastIndexOf('.')
    s = when {
        lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        lastComma >= 0 -> s.replace(',', '.')
        lastDot >= 0 && s.length - lastDot - 1 == 3 -> s.replace(".", "")
        else -> s
    }
    if (s.count { it == '.' } > 1) return null
    val cents = s.toBigDecimalOrNull()?.movePointRight(2) ?: return null
    if (cents.signum() <= 0 || cents.stripTrailingZeros().scale() > 0) return null
    return cents.longValueExact()
}

/** Day group header: "Danas", "Juče", then "30.09." */
fun LocalDate.dayLabel(today: LocalDate): String = when (this) {
    today -> "Danas"
    today.minusDays(1) -> "Juče"
    else -> format(DayFormat)
}

val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")   // "30.09."
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")   // "14:32"
