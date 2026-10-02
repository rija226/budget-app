package com.budget.app.ui

import com.budget.app.budget.PeriodRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import com.budget.app.ui.components.TxKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReportTest {
    private fun tx(day: LocalDate, cat: String, eur: Long?, merchant: String = cat, kind: TxKind = TxKind.Expense, currency: String = "EUR") =
        TransactionUi(0, merchant, cat, Icons.Rounded.Category, LocalDateTime.of(day, java.time.LocalTime.NOON), eur ?: 100, currency, eur, kind)

    private val period = PeriodRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25))
    private val previous = PeriodRange(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 25))
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun onlyExpensesWithEuroAmountCount() {
        val r = buildReport(
            period, previous,
            listOf(
                tx(today, "Market", 1000),
                tx(today, "Market", 250, merchant = "Lidl"),
                tx(today.minusDays(1), "Dostava", 1840),
                tx(today, "Plata", 150000, kind = TxKind.Salary),
                tx(today, "Prenos", 30000, kind = TxKind.Transfer),
                tx(today, "Zabava", null, currency = "USD"), // no exchange rate yet
                tx(today.plusDays(1), "Market", 999),         // future, not shown yet
            ),
            today, 78000,
        )
        assertEquals(listOf("Dostava" to 1840L, "Market" to 1250L), r.byCategory)
        assertEquals(1250L, r.days.last().eurMinor)
        assertEquals("Danas", r.days.last().label)
        assertEquals(6, r.days.size) // 25.–30.
        assertEquals(30, r.periodDays)
        assertEquals(2600L, r.dailyBudgetMinor)
        assertEquals("Dostava", r.merchants.first().name)
    }

    @Test
    fun comparesSameNumberOfDaysOfPreviousPeriod() {
        val r = buildReport(
            period, previous,
            listOf(
                tx(today, "Dostava", 6200),
                tx(LocalDate.of(2026, 8, 27), "Dostava", 3800),  // inside 25.08.–30.08.
                tx(LocalDate.of(2026, 9, 10), "Dostava", 9999),  // later in the previous period, not compared
                tx(LocalDate.of(2026, 8, 26), "Zabava", 3500),
            ),
            today, null,
        )
        assertEquals("25.08.–30.08.", r.compareRange)
        assertEquals(listOf(CompareRow("Dostava", 6200, 3800), CompareRow("Zabava", 0, 3500)), r.compare)
    }

    @Test
    fun plural() {
        assertEquals("transakcija", transakcija(1))
        assertEquals("transakcije", transakcija(3))
        assertEquals("transakcija", transakcija(5))
        assertEquals("transakcija", transakcija(12))
        assertEquals("transakcije", transakcija(22))
    }
}
