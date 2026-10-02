package com.budget.app.ui

import com.budget.app.ai.AnalysisSection
import com.budget.app.ai.SavingTip
import com.budget.app.budget.PeriodRange
import com.budget.app.ui.components.TxKind
import com.budget.app.ui.components.categoryIcon
import java.time.LocalDate
import java.time.LocalDateTime

// ---- Sample data shared by the screens until real data is wired (DESIGN.md step 5). Delete then. ----

internal object Sample {
    val today: LocalDate = LocalDate.of(2026, 10, 11)
    val period = PeriodRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25))
    val periods = listOf(period, PeriodRange(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 25)))

    private fun at(day: Int, month: Int, h: Int, m: Int) = LocalDateTime.of(2026, month, day, h, m)
    private fun cat(name: String, spent: Long, limit: Long?) = CategoryUi(name, categoryIcon(name), spent, limit)

    val categories = listOf(
        cat("Market", 18000, 25000),
        cat("Restorani i kafići", 9600, 10000),
        cat("Dostava", 6200, 5000),
        cat("Gorivo i prevoz", 7000, 12000),
        cat("Zabava", 1800, 6000),
        cat("Pretplate", 2400, 3000),
        cat("Gotovina", 10000, 10000),
        cat("Režije", 8450, null),
        cat("Ostalo", 1420, null),
    )
    val categoryNames = categories.map { it.name }

    private fun tx(
        id: Long, merchant: String, category: String, at: LocalDateTime, amount: Long,
        currency: String = "EUR", eur: Long? = if (currency == "EUR") amount else null,
        kind: TxKind = TxKind.Expense, ai: Boolean = false,
    ) = TransactionUi(id, merchant, category, categoryIcon(category), at, amount, currency, eur, kind, ai)

    val transactions = listOf(
        tx(1, "Maxi", "Market", at(11, 10, 14, 32), 1250),
        tx(2, "Pekara Zlatni klas", "Restorani i kafići", at(11, 10, 8, 15), 2500, "KM", 1278, ai = true),
        tx(3, "Wolt", "Dostava", at(10, 10, 20, 41), 1840),
        tx(4, "Shell", "Gorivo i prevoz", at(10, 10, 17, 5), 4500),
        tx(5, "Steam", "Zabava", at(9, 10, 21, 30), 1499, "USD"),
        tx(6, "Lidl", "Market", at(9, 10, 18, 10), 3420),
        tx(7, "Kafeterija", "Restorani i kafići", at(5, 10, 9, 12), 450, ai = true),
        tx(8, "Netflix", "Pretplate", at(5, 10, 6, 0), 1299),
        tx(9, "Bankomat", "Gotovina", at(28, 9, 11, 20), 10000),
        tx(10, "Uplata plate", "Plata", at(25, 9, 9, 2), 150000, kind = TxKind.Salary),
        tx(11, "Prenos na štednju", "Prenos", at(25, 9, 9, 40), 30000, kind = TxKind.Transfer),
        tx(12, "Hotel Adria", "Putovanja", at(20, 9, 15, 0), 12000),
    )

    private val sources = mapOf(
        1L to ("Google Wallet" to "com.google.android.apps.walletnfcrel\n11.10. 14:32:07\nMaxi\nEUR 12,50"),
        2L to ("Google Wallet" to "com.google.android.apps.walletnfcrel\n11.10. 08:15:42\nPekara Zlatni klas\nKM 25.00"),
        5L to ("Moja banka" to "rs.mojabanka.mobile\n09.10. 21:30:11\nPlaćanje karticom 14,99 USD\nSTEAM PURCHASE"),
        9L to ("Moja banka" to "rs.mojabanka.mobile\n28.09. 11:20:55\nIsplata na bankomatu 100,00 EUR"),
    )

    val budget = 78000L

    val zaProvjeru get() = ZaProvjeruState(
        today,
        transactions.filter { it.aiSuggested }.map { it to (detail(it.id)?.source ?: "Google Wallet") },
        listOf(UnknownNotification(100, "Moja banka", at(29, 9, 21, 14), "Rezervacija sredstava 30,00 EUR\nHAC ENC LUCKO")),
        transactions.filter { it.amountEurMinor == null },
    )

    val budzet get() = BudzetState(budget, categories.map { k -> BudzetCategory(k.name, k.limit, transactions.count { it.category == k.name }) })

    val izvjestaji get() = IzvjestajiState(
        periods.mapIndexed { i, p -> buildReport(p, periods.getOrNull(i + 1), transactions, today, budget) },
        analyses,
    )

    private fun range(m: Int) = PeriodRange(LocalDate.of(2026, m, 25), LocalDate.of(2026, m + 1, 25))

    val analyses = listOf(
        AnalysisUi(
            1, range(8), 74230, 78000, 150000,
            AnalysisStatus.Ready(
                "Potrošeno 742,30 € od 780,00 €. Najveća promjena: Dostava, +45%.",
                listOf(
                    AnalysisSection(
                        "Ukratko",
                        listOf("Period si završio sa 37,70 € ispod budžeta. Market i Gorivo i prevoz su bili ispod limita, a Dostava i Restorani i kafići su prešli limit, prvi put u posljednja tri perioda."),
                    ),
                    AnalysisSection(
                        "Gdje je otišao novac",
                        listOf(
                            "Najviše je otišlo na Market (231,40 €) i Gotovinu (160,00 €). Dostava je porasla na 72,50 €, uglavnom kroz 6 narudžbi preko Wolta, sve radnim danima poslije 20 h.",
                            "Pretplate su stabilne (24,00 €). Od toga Netflix i jedna usluga za muziku čine 20,99 €.",
                        ),
                    ),
                    AnalysisSection(
                        "Šta se promijenilo",
                        listOf("U odnosu na prethodni period, Dostava je veća za 45%, a Gorivo i prevoz manji za 18%. Podizanje gotovine je češće (4 puta umjesto 2), pa je teže vidjeti gdje je taj novac potrošen."),
                    ),
                ),
                listOf(
                    SavingTip("Dostava samo vikendom", "Radnim danima si naručivao hranu u prosjeku jednom u pet dana, uvijek poslije 20 h. Ako te večeri spremiš nešto jednostavno, dostava ostaje za vikend.", 2500),
                    SavingTip("Jedno podizanje gotovine sedmično", "Četiri podizanja po 40,00 € teško je pratiti. Jedan fiksni iznos sedmično daje jasniju sliku i obično manje sitnih troškova.", 2000),
                    SavingTip("Kafa van kuće dva puta sedmično", "Restorani i kafići su prešli limit za 8,40 €, većinom zbog kafe radnim danima. Dva dana manje nedjeljno vraća kategoriju ispod limita.", 1300),
                ),
                LocalDateTime.of(2026, 9, 25, 7, 12),
            ),
        ),
        AnalysisUi(
            2, range(7), 80115, 78000, 150000,
            AnalysisStatus.Ready(
                "Potrošeno 801,15 € od 780,00 €, 21,15 € preko budžeta.",
                listOf(AnalysisSection("Ukratko", listOf("Period je završen 21,15 € preko budžeta, najviše zbog Putovanja."))),
                listOf(
                    SavingTip("Putovanja planiraj unaprijed", "Smještaj rezervisan dvije sedmice ranije bio je 20% jeftiniji.", 3000),
                    SavingTip("Manje dostave radnim danima", "Pet narudžbi radnim danima, sve poslije 20 h.", 2200),
                    SavingTip("Provjeri pretplate", "Dvije pretplate za muziku plaćaju se istovremeno.", 1200),
                ),
                LocalDateTime.of(2026, 8, 25, 7, 5),
            ),
        ),
        AnalysisUi(3, range(6), 69900, 78000, 150000, AnalysisStatus.Loading),
        AnalysisUi(4, range(5), 71240, 78000, 150000, AnalysisStatus.Failed("Nema internet veze. Podaci su sačuvani.", keyProblem = false)),
    )

    fun detail(id: Long): TransakcijaUi? = transactions.find { it.id == id }?.let { t ->
        val (source, raw) = sources[id] ?: ("Moja banka" to null)
        TransakcijaUi(t, source, raw, rate = if (t.currency == "KM") "1 € = 1,95583 KM" else null)
    }
}
