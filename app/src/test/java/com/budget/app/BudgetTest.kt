package com.budget.app

import com.budget.app.budget.PeriodRange
import com.budget.app.budget.allowance
import com.budget.app.budget.reachedThresholds
import com.budget.app.budget.merchantKey
import com.budget.app.budget.periods
import com.budget.app.budget.toEurMinor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test
    fun merchantKeys() {
        assertEquals("MAXI", merchantKey("MAXI 123 BEOGRAD"))
        assertEquals("MAXI", merchantKey("Maxi 456"))
        assertEquals("PEKARA ZLATNI KLAS", merchantKey("Pekara Zlatni klas"))
        assertEquals("ŠTARK", merchantKey("Štark d.o.o."))
        assertEquals("BEOGRAD", merchantKey("Beograd")) // only noise: keep it rather than an empty key
    }

    @Test
    fun euroConversion() {
        val rates = mapOf("RSD" to BigDecimal("117.20"), "USD" to BigDecimal("1.08"))
        assertEquals(1250L, toEurMinor(1250, "EUR", rates))
        assertEquals(1278L, toEurMinor(2500, "BAM", rates))   // 25,00 KM
        assertEquals(1024L, toEurMinor(120000, "RSD", rates)) // 1.200,00 RSD
        assertEquals(1388L, toEurMinor(1499, "USD", rates))
        assertNull(toEurMinor(1000, "GBP", rates))
    }

    @Test
    fun periodsFromSalaries() {
        assertEquals(listOf(PeriodRange(d(10, 2), d(11, 2))), periods(emptyList(), d(10, 2)))
        assertEquals(listOf(PeriodRange(d(10, 10), d(11, 10))), periods(listOf(d(10, 10)), d(10, 2)))
        assertEquals(
            listOf(PeriodRange(d(10, 9), d(11, 7)), PeriodRange(d(9, 10), d(10, 9))),
            periods(listOf(d(10, 9), d(9, 10)), d(9, 1)), // 29-day previous period predicts the next salary
        )
    }

    @Test
    fun dailyAllowance() {
        // 780 € budget, 412,60 € spent before today, 14 days left including today, 0 spent today
        val a = allowance(78000, 41260, 0, d(10, 11), d(10, 25))
        assertEquals(14, a.daysLeft)
        assertEquals(2624L, a.remainingTodayMinor) // 367,40 € / 14 = 26,24 €
        // spent 35 € today: 8,76 € over
        assertEquals(-876L, allowance(78000, 41260, 3500, d(10, 11), d(10, 25)).remainingTodayMinor)
        // salary day already passed: at least one day
        assertEquals(1, allowance(1000, 0, 0, d(10, 26), d(10, 25)).daysLeft)
    }

    @Test
    fun thresholds() {
        assertEquals(emptyList<Int>(), reachedThresholds(7999, 10000))
        assertEquals(listOf(80), reachedThresholds(8000, 10000))
        assertEquals(listOf(80, 100), reachedThresholds(10000, 10000))
        assertEquals(emptyList<Int>(), reachedThresholds(500, 0))
    }
}
