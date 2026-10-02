package com.budget.app.ui

import com.budget.app.ui.components.limitInfo
import com.budget.app.ui.format.formatApproxEur
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.theme.LimitStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitInfoTest {
    private val nb = ' '

    @Test
    fun money() {
        assertEquals("1.500,00${nb}€", 150000L.formatEur())
        assertEquals("12,50${nb}€", 1250L.formatEur())
        assertEquals("≈ 12,78${nb}€", 1278L.formatApproxEur())
    }

    @Test
    fun statesFromDesign() {
        // spent, limit in cents -> status, pct, note (DESIGN.md rule 3)
        limitInfo(18000, 25000).let {
            assertEquals(LimitStatus.Ok, it.status)
            assertEquals(72, it.pct)
            assertEquals("U redu · ostalo 70,00${nb}€", it.note)
        }
        limitInfo(9600, 10000).let {
            assertEquals(LimitStatus.Warning, it.status)
            assertEquals("Blizu limita · ostalo 4,00${nb}€", it.note)
        }
        limitInfo(10000, 10000).let {
            assertEquals(LimitStatus.Over, it.status)
            assertEquals("Limit dostignut", it.note)
        }
        limitInfo(6200, 5000).let {
            assertEquals(LimitStatus.Over, it.status)
            assertEquals(124, it.pct)
            assertEquals(1f, it.fraction)
            assertEquals("Preko limita za 12,00${nb}€", it.note)
        }
        limitInfo(1420, null).let {
            assertEquals(LimitStatus.NoLimit, it.status)
            assertEquals("Bez limita", it.note)
            assertEquals(0f, it.fraction)
        }
    }
}
