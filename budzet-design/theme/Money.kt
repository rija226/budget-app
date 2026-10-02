package rs.budzet.ui.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter

private val symbols = DecimalFormatSymbols().apply {
    decimalSeparator = ','
    groupingSeparator = '.'
}
private val amount = DecimalFormat("#,##0.00", symbols)

private const val NBSP = ' ' // keeps "12,50 €" on one line

/** 150000 -> "1.500,00 €" */
fun Long.formatEur(): String = amount.format(this / 100.0) + NBSP + "€"

/** 2500, "KM" -> "25,00 KM" */
fun Long.formatCurrency(code: String): String = amount.format(this / 100.0) + NBSP + code

/** Foreign row secondary line: "≈ 12,78 €" */
fun Long.formatApproxEur(): String = "≈ " + formatEur()

val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")   // "30.09."
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")   // "14:32"
