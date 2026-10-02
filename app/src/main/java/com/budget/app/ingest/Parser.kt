package com.budget.app.ingest

import com.budget.app.ui.format.parseAmountMinor

enum class Kind { EXPENSE, INCOME, WITHDRAWAL }

data class Parsed(val amountMinor: Long, val currency: String, val merchant: String, val kind: Kind)

private const val CUR = "EUR|RSD|BAM|KM|USD|CHF|GBP|DIN|€"
private val AMOUNT_THEN_CUR = Regex("""(\d[\d.,]*\d|\d)\s*($CUR)(?![\p{L}])""", RegexOption.IGNORE_CASE)
private val CUR_THEN_AMOUNT = Regex("""(?<![\p{L}])($CUR)\s*(\d[\d.,]*\d|\d)""", RegexOption.IGNORE_CASE)
private val AT_MERCHANT = Regex("""\b(?:kod|at)\s+([^,;\n]+?)(?:\.\s|\.$|[,;\n]|$)""", RegexOption.IGNORE_CASE)

private val WITHDRAWAL_WORDS = listOf("bankomat", "atm", "isplata gotovine", "podizanje gotovine", "gotovinsk")
private val INCOME_WORDS = listOf("uplata", "priliv", "primljen")
/** Lines that describe the transaction rather than name the merchant. */
private val DESCRIPTION_STARTS = listOf(
    "plaćanje", "placanje", "transakcija", "kupovina", "rezervacija", "isplata", "uplata", "priliv",
    "stanje", "raspoloživ", "raspoloziv", "kartica", "vaš", "vas ", "potrošeno", "potroseno",
)

fun normalizeCurrency(c: String): String = when (c.uppercase()) {
    "€" -> "EUR"
    "KM" -> "BAM"
    "DIN" -> "RSD"
    else -> c.uppercase()
}

/**
 * Best-effort reading of a payment notification: the first amount with a currency, a kind from
 * keywords, and the merchant from the line without an amount (Wallet: title = merchant) or "kod X".
 */
// ponytail: opšti čitač dok ne stignu stvarni primjeri banaka; u fazi 2 dobija se poseban čitač po banci
fun parseGeneric(title: String?, text: String?): Parsed? {
    val lines = listOfNotNull(title, text).flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val all = lines.joinToString("\n")
    val (num, cur) = AMOUNT_THEN_CUR.find(all)?.let { it.groupValues[1] to it.groupValues[2] }
        ?: CUR_THEN_AMOUNT.find(all)?.let { it.groupValues[2] to it.groupValues[1] }
        ?: return null
    val amount = parseAmountMinor(num) ?: return null
    val lower = all.lowercase()
    val kind = when {
        WITHDRAWAL_WORDS.any { it in lower } -> Kind.WITHDRAWAL
        INCOME_WORDS.any { it in lower } -> Kind.INCOME
        else -> Kind.EXPENSE
    }
    val merchant = lines.firstOrNull { l ->
        !AMOUNT_THEN_CUR.containsMatchIn(l) && !CUR_THEN_AMOUNT.containsMatchIn(l) &&
            DESCRIPTION_STARTS.none { l.lowercase().startsWith(it) }
    } ?: AT_MERCHANT.find(all)?.groupValues?.get(1)?.trim()
        ?: when (kind) {
            Kind.WITHDRAWAL -> "Bankomat"
            Kind.INCOME -> "Uplata"
            Kind.EXPENSE -> "Nepoznat trgovac"
        }
    return Parsed(amount, normalizeCurrency(cur), merchant.replace(Regex("\\s+"), " "), kind)
}
