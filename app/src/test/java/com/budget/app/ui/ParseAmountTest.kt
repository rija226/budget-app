package com.budget.app.ui

import com.budget.app.ui.format.parseAmountMinor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseAmountTest {
    @Test
    fun accepted() {
        assertEquals(1250L, parseAmountMinor("12,50"))
        assertEquals(1250L, parseAmountMinor("12.50"))
        assertEquals(1250L, parseAmountMinor(" 12,5 € "))
        assertEquals(1200L, parseAmountMinor("12"))
        assertEquals(150000L, parseAmountMinor("1.500"))
        assertEquals(150000L, parseAmountMinor("1.500,00"))
        assertEquals(150000L, parseAmountMinor("1,500.00"))
        assertEquals(123456789L, parseAmountMinor("1.234.567,89"))
    }

    @Test
    fun rejected() {
        assertNull(parseAmountMinor(""))
        assertNull(parseAmountMinor("0"))
        assertNull(parseAmountMinor("-5"))
        assertNull(parseAmountMinor("12,505"))
        assertNull(parseAmountMinor("abc"))
        assertNull(parseAmountMinor("1.2.3"))
    }
}
