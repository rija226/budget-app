package com.budget.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.math.BigDecimal
import java.time.LocalDate

/** Owner settings in SharedPreferences "budzet" (Auto Backup takes them along). */
class Settings(context: Context) {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences("budzet", Context.MODE_PRIVATE)

    var uvodDone: Boolean
        get() = p.getBoolean("uvod_done", false)
        set(v) = p.edit().putBoolean("uvod_done", v).apply()

    /** First day the app ran; the provisional period starts here until the first salary. */
    val firstDay: LocalDate
        get() = p.getLong("first_day", -1).takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
            ?: LocalDate.now().also { p.edit().putLong("first_day", it.toEpochDay()).apply() }

    /** Budžet po periodu, euro cents; null until the owner sets it. */
    var budgetMinor: Long?
        get() = p.getLong("budget_minor", -1).takeIf { it >= 0 }
        set(v) = p.edit().apply { if (v == null) remove("budget_minor") else putLong("budget_minor", v) }.apply()

    /** Pravilo za platu: source id ("bankA", "bankB", or "any") and the minimum amount in euro cents. */
    var salarySource: String?
        get() = p.getString("salary_source", null)
        set(v) = p.edit().putString("salary_source", v).apply()

    var salaryMinMinor: Long?
        get() = p.getLong("salary_min", -1).takeIf { it >= 0 }
        set(v) = p.edit().apply { if (v == null) remove("salary_min") else putLong("salary_min", v) }.apply()

    var apiKey: String?
        get() = p.getString("api_key", null)?.takeIf { it.isNotBlank() }
        set(v) = p.edit().putString("api_key", v).apply()

    /** Units of the currency for 1 EUR, entered by hand. */
    val rates: Map<String, BigDecimal>
        get() = p.all.filterKeys { it.startsWith("rate_") }
            .mapNotNull { (k, v) -> (v as? String)?.toBigDecimalOrNull()?.let { k.removePrefix("rate_") to it } }
            .toMap()

    fun setRate(currency: String, perEur: BigDecimal) = p.edit().putString("rate_$currency", perEur.toPlainString()).apply()

    /** Each limit warning is sent once per period, category and threshold. */
    fun alertSent(key: String) = p.getBoolean("alert_$key", false)
    fun markAlert(key: String) = p.edit().putBoolean("alert_$key", true).apply()

    /** Emits on every change, so screens can recompute. */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        p.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(l) }
    }
}
