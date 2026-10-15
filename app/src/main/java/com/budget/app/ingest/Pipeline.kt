package com.budget.app.ingest

import android.content.Context
import android.content.pm.ApplicationInfo
import com.budget.app.budget.merchantKey
import com.budget.app.budget.toEurMinor
import com.budget.app.data.AppDatabase
import com.budget.app.data.RawNotification
import com.budget.app.data.RawStatus
import com.budget.app.data.Repo
import com.budget.app.data.Settings
import com.budget.app.data.Transaction
import com.budget.app.data.TxType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

const val WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
private val TEST_SOURCES = setOf("wallet", "bankA", "bankB")

// ponytail: samo Erste; HYPO (com.comtrade.HYPOnetmBankarstvoBiH) vlasnik rijetko koristi, dodaje se kao bankB ako zatreba
private val BANK_PACKAGES = mapOf("hr.erstebank.george" to "bankA")

/** Packages the listener records; logs of everything else are deleted by "Obradi ponovo sve". */
val KNOWN_PACKAGES = BANK_PACKAGES.keys + WALLET_PACKAGE + "com.android.shell"

/** "wallet", "bankA", "bankB" or null for apps we don't read. */
fun sourceOf(pkg: String, tag: String?, debug: Boolean): String? = when {
    pkg == WALLET_PACKAGE -> "wallet"
    debug && pkg == "com.android.shell" && tag in TEST_SOURCES -> tag
    else -> BANK_PACKAGES[pkg]
}

fun sourceLabel(source: String): String = source.split('+').joinToString(" + ") {
    when (it) {
        "wallet" -> "Google Wallet"
        "bankA" -> "Erste"
        "bankB" -> "Banka B"
        "manual" -> "Ručni unos"
        else -> it
    }
}

fun Context.isDebug() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** Raw notification -> transaction: parse, merge Wallet + bank duplicates, categorize, then the follow-ups in [Repo.afterChange]. */
object Pipeline {
    private val lock = Mutex()
    private const val MERGE_WINDOW_MS = 10 * 60_000L
    // ponytail: Wallet istu notifikaciju objavi ponovo (odmah i kad stigne sljedeće plaćanje); isti tekst u 12 h = ponavljanje.
    // Dvije iste kafe u 12 h: druga dolazi samo preko Erste notifikacije.
    private const val REPEAT_WINDOW_MS = 12 * 3_600_000L

    suspend fun process(ctx: Context, raw: RawNotification) = lock.withLock { processLocked(ctx, raw) }

    /** "Obradi ponovo sve": drops logs of unknown apps, then new and unrecognized notifications; returns how many became transactions. */
    suspend fun reprocess(ctx: Context): Int = lock.withLock {
        val dao = AppDatabase.get(ctx).rawNotifications()
        dao.deleteExcept(KNOWN_PACKAGES.toList())
        dao.unprocessed().count { processLocked(ctx, it) }
    }

    private suspend fun processLocked(ctx: Context, raw: RawNotification): Boolean {
        val db = AppDatabase.get(ctx)
        val source = sourceOf(raw.packageName, raw.tag, ctx.isDebug()) ?: return false
        if (raw.title in NOISE_TITLES ||
            db.rawNotifications().repeats(raw.packageName, raw.title, raw.text, raw.id, raw.postedAt - REPEAT_WINDOW_MS) > 0
        ) {
            db.rawNotifications().setStatus(raw.id, RawStatus.IGNORED)
            return false
        }
        val p = parse(source, raw.title, raw.text)
        if (p == null) {
            db.rawNotifications().setStatus(raw.id, RawStatus.FAILED)
            return false
        }
        val at = p.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: raw.postedAt
        db.transactions().mergeCandidate(p.amountMinor, p.currency, at, at - MERGE_WINDOW_MS, at + MERGE_WINDOW_MS, source)?.let { t ->
            // CLAUDE.md: trgovac iz Walleta (čistiji naziv), sve ostalo iz banke
            val wallet = source == "wallet"
            db.transactions().update(
                t.copy(source = "${t.source}+$source", merchant = if (wallet) p.merchant else t.merchant, occurredAt = if (wallet) t.occurredAt else at),
            )
            db.rawNotifications().setStatus(raw.id, RawStatus.PARSED, t.id)
            return true
        }

        val s = Settings(ctx)
        val cats = db.categories().allNow()
        fun cat(name: String) = cats.find { it.name == name }?.id
        val eur = toEurMinor(p.amountMinor, p.currency, s.rates)
        val base = Transaction(
            amountMinor = p.amountMinor, currency = p.currency, amountEurMinor = eur, type = TxType.EXPENSE,
            merchant = p.merchant, categoryId = null, categoryConfirmed = false, occurredAt = at, source = source,
        )
        val tx = when (p.kind) {
            Kind.WITHDRAWAL -> base.copy(categoryId = cat("Gotovina"), categoryConfirmed = true)
            Kind.INCOME -> {
                val salary = s.salarySource.let { it == "any" || it == source } && eur != null && eur >= (s.salaryMinMinor ?: Long.MAX_VALUE)
                base.copy(type = TxType.INCOME, isSalary = salary, categoryId = cat(if (salary) "Plata" else "Ostali prihodi"), categoryConfirmed = true)
            }
            Kind.EXPENSE -> db.rules().get(merchantKey(p.merchant))
                ?.let { base.copy(categoryId = it.categoryId, categoryConfirmed = true) } ?: base
        }
        val id = db.transactions().insert(tx)
        db.rawNotifications().setStatus(raw.id, RawStatus.PARSED, id)
        Repo.afterChange(ctx, tx.copy(id = id))
        return true
    }
}
