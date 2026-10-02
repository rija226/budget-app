package com.budget.app.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.budget.app.budget.Notify
import com.budget.app.budget.PeriodRange
import com.budget.app.data.AnalysisState
import com.budget.app.data.AppDatabase
import com.budget.app.data.Settings
import com.budget.app.data.counts
import com.budget.app.data.day
import com.budget.app.ui.format.formatEur
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class AnalysisSection(val heading: String, val paragraphs: List<String>)
data class SavingTip(val title: String, val body: String, val monthlyEurMinor: Long)
data class AiAnalysis(val teaser: String, val sections: List<AnalysisSection>, val tips: List<SavingTip>)

/** The analysis as stored in `analyses.json` (the AI answer, already validated). */
fun parseAnalysis(json: String): AiAnalysis {
    val o = JSONObject(json)
    fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun JSONArray.strings() = (0 until length()).map { getString(it) }
    return AiAnalysis(
        o.getString("teaser"),
        o.getJSONArray("sections").objects().map { AnalysisSection(it.getString("heading"), it.getJSONArray("paragraphs").strings()) },
        o.getJSONArray("tips").objects().map {
            SavingTip(
                it.getString("title"), it.getString("body"),
                BigDecimal(it.get("monthlySavingEur").toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong(),
            )
        },
    )
}

private class AiFailure(message: String, val keyProblem: Boolean = false, val retry: Boolean = false) : Exception(message)

/** Claude API calls (CLAUDE.md "Claude API"): only summaries leave the phone, never raw notifications, balances or card numbers. */
object Ai {
    private const val MODEL = "claude-opus-5-5"

    private fun schema(vararg props: Pair<String, Any>) = JsonOutputFormat.builder().schema(
        JsonOutputFormat.Schema.builder().apply { props.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build(),
    ).build()

    private fun obj(properties: Map<String, Any>) =
        mapOf("type" to "object", "properties" to properties, "required" to properties.keys.toList(), "additionalProperties" to false)

    private fun ask(key: String, system: String, user: String, effort: OutputConfig.Effort, format: JsonOutputFormat, maxTokens: Long): String {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(maxTokens)
            .system(system)
            .addUserMessage(user)
            .outputConfig(OutputConfig.builder().effort(effort).format(format).build())
            // ako model odbije zahtjev, server ga sam proslijedi drugom modelu
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        val msg = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw AiFailure("Server nije prihvatio API ključ (greška 401).", keyProblem = true)
        } catch (e: PermissionDeniedException) {
            throw AiFailure("API ključ nema dozvolu za ovaj model (greška 403).", keyProblem = true)
        } catch (e: AnthropicIoException) {
            throw AiFailure("Nema internet veze. Podaci su sačuvani.", retry = true)
        } catch (e: AnthropicServiceException) {
            throw AiFailure("Server trenutno ne odgovara (greška ${e.statusCode()}).", retry = e.statusCode() >= 429)
        } finally {
            client.close()
        }
        when (msg.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiFailure("Model je odbio zahtjev.")
            StopReason.MAX_TOKENS -> throw AiFailure("Odgovor je bio predug.")
            else -> {}
        }
        return msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    }

    // ---- Kategorija nepoznatog trgovca ----

    fun categorizeLater(ctx: Context, txId: Long) {
        if (Settings(ctx).apiKey == null) return
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "categorize-$txId", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CategorizeWorker>().setInputData(workDataOf("tx" to txId)).setConstraints(online).build(),
        )
    }

    internal fun categorize(key: String, merchant: String, amount: String, categories: List<String>): String {
        val text = ask(
            key,
            "Razvrstavaš plaćanja karticom u kategorije troškova lične aplikacije za budžet (Srbija, Crna Gora, BiH). " +
                "Na osnovu naziva trgovca izaberi tačno jednu kategoriju sa datog spiska. Ako nisi siguran, izaberi \"Ostalo\".",
            "Trgovac: $merchant\nIznos: $amount\nKategorije: ${categories.joinToString(", ")}",
            OutputConfig.Effort.LOW,
            schema(*obj(mapOf("category" to mapOf("type" to "string", "enum" to categories))).toList().toTypedArray()),
            4000,
        )
        return JSONObject(text).getString("category")
    }

    // ---- Analiza perioda ----

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun analyzeLater(ctx: Context, analysisId: Long) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "analysis-$analysisId", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AnalysisWorker>().setInputData(workDataOf("id" to analysisId)).setConstraints(online).build(),
        )
    }

    private val ANALYSIS_SYSTEM = """
        Ti si savjetnik za lične finansije u aplikaciji za budžet. Dobijaš sažetak jednog završenog perioda (od plate do plate)
        i sažetak prethodnog perioda za poređenje. Piši na srpskom, ijekavica, latinica, u drugom licu jednine.
        Ton je miran i činjeničan: bez uzvičnika, bez "pažnja", bez moralisanja. Koristi samo brojeve iz sažetka; iznose piši kao 12,50 €.
        Vrati:
        - teaser: jedna rečenica za karticu u listi, npr. "Potrošeno 742,30 € od 780,00 €. Najveća promjena: Dostava, +45%."
        - sections: tačno tri odjeljka sa naslovima "Ukratko", "Gdje je otišao novac" i "Šta se promijenilo", svaki sa 1-2 kratka pasusa.
          Navedi gdje je potrošeno više nego inače i šta se ponavlja (pretplate, česte sitne kupovine).
        - tips: tačno tri konkretna savjeta za uštedu, svaki sa kratkim naslovom, objašnjenjem od 1-2 rečenice vezanim za stvarne
          trgovce i kategorije iz sažetka, i realnom procjenom mjesečne uštede u eurima (monthlySavingEur).
    """.trimIndent()

    private val analysisFormat by lazy {
        val str = mapOf("type" to "string")
        val section = obj(mapOf("heading" to str, "paragraphs" to mapOf("type" to "array", "items" to str)))
        val tip = obj(mapOf("title" to str, "body" to str, "monthlySavingEur" to mapOf("type" to "number")))
        schema(
            *obj(
                mapOf(
                    "teaser" to str,
                    "sections" to mapOf("type" to "array", "items" to section),
                    "tips" to mapOf("type" to "array", "items" to tip),
                ),
            ).toList().toTypedArray(),
        )
    }

    /** Only totals per category, the top 15 merchants and the previous period: no raw texts, balances or card numbers. */
    internal suspend fun summary(ctx: Context, period: PeriodRange, budgetMinor: Long): String {
        val db = AppDatabase.get(ctx)
        val cats = db.categories().allNow().associateBy { it.id }
        val txs = db.transactions().allNow()
        fun inPeriod(p: PeriodRange) = txs.filter { it.counts() && it.day() in p }
        val cur = inPeriod(period)
        val prev = inPeriod(PeriodRange(period.start.minusDays(period.days.toLong()), period.start))
        fun byCategory(l: List<com.budget.app.data.Transaction>) = l.groupBy { cats[it.categoryId]?.name ?: "Bez kategorije" }
            .mapValues { (_, v) -> v.sumOf { it.amountEurMinor!! }.formatEur() }
        val limits = cats.values.filter { it.limitEurMinor != null }.associate { it.name to it.limitEurMinor!!.formatEur() }
        val merchants = cur.groupBy { it.merchant }.map { (m, l) -> Triple(m, l, l.sumOf { it.amountEurMinor!! }) }
            .sortedByDescending { it.third }.take(15)
            .map { (m, l, sum) ->
                JSONObject().put("trgovac", m).put("kategorija", cats[l.first().categoryId]?.name ?: "Bez kategorije")
                    .put("broj", l.size).put("ukupno", sum.formatEur())
                    .put("radnimDanima", l.count { it.day().dayOfWeek.value <= 5 })
            }
        val salary = txs.filter { it.isSalary && it.day() == period.start }.sumOf { it.amountEurMinor ?: 0 }
        return JSONObject()
            .put("period", period.label)
            .put("budzet", budgetMinor.formatEur())
            .put("potroseno", cur.sumOf { it.amountEurMinor!! }.formatEur())
            .put("plata", salary.formatEur())
            .put("poKategorijama", JSONObject(byCategory(cur)))
            .put("limiti", JSONObject(limits))
            .put("najveciTrgovci", JSONArray(merchants))
            .put("prethodniPeriodPoKategorijama", JSONObject(byCategory(prev)))
            .put("podizanjaGotovine", cur.count { cats[it.categoryId]?.name == "Gotovina" })
            .toString(2)
    }

    internal fun analyze(key: String, summary: String): String {
        val text = ask(key, ANALYSIS_SYSTEM, "Sažetak perioda:\n$summary", OutputConfig.Effort.HIGH, analysisFormat, 16000)
        parseAnalysis(text) // validates before it is stored
        return text
    }

    // ---- Workers ----

    class CategorizeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val key = Settings(applicationContext).apiKey ?: return Result.success()
            val db = AppDatabase.get(applicationContext)
            val tx = db.transactions().byId(inputData.getLong("tx", 0)) ?: return Result.success()
            if (tx.categoryId != null) return Result.success()
            val cats = db.categories().allNow().filter { !it.isIncome }
            return try {
                val name = categorize(key, tx.merchant, tx.amountMinor.formatEur(), cats.map { it.name })
                val fresh = db.transactions().byId(tx.id) ?: return Result.success()
                if (fresh.categoryId == null) db.transactions().update(fresh.copy(categoryId = cats.find { it.name == name }?.id, categoryConfirmed = false))
                Result.success()
            } catch (e: AiFailure) {
                if (e.retry && runAttemptCount < 5) Result.retry() else Result.success()
            }
        }
    }

    class AnalysisWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val db = AppDatabase.get(applicationContext)
            val a = db.analyses().byId(inputData.getLong("id", 0)) ?: return Result.success()
            val key = Settings(applicationContext).apiKey
            if (key == null) {
                db.analyses().update(a.copy(state = AnalysisState.FAILED, error = "API ključ nije unesen.", keyProblem = true))
                return Result.success()
            }
            val period = PeriodRange(LocalDate.ofEpochDay(a.periodStart), LocalDate.ofEpochDay(a.periodEnd))
            return try {
                val json = analyze(key, summary(applicationContext, period, a.budgetMinor))
                db.analyses().update(a.copy(state = AnalysisState.READY, json = json, error = null, createdAt = System.currentTimeMillis()))
                val parsed = parseAnalysis(json)
                Notify.analysisReady(applicationContext, a.id, period.label, parsed.tips.size, parsed.tips.sumOf { it.monthlyEurMinor })
                Result.success()
            } catch (e: AiFailure) {
                if (e.retry && runAttemptCount < 3) return Result.retry()
                db.analyses().update(a.copy(state = AnalysisState.FAILED, error = e.message, keyProblem = e.keyProblem))
                Result.success()
            } catch (e: Exception) {
                db.analyses().update(a.copy(state = AnalysisState.FAILED, error = "Odgovor nije u očekivanom obliku.", keyProblem = false))
                Result.success()
            }
        }
    }
}
