package com.budget.app

import com.budget.app.ingest.Kind
import com.budget.app.ingest.Parsed
import com.budget.app.ingest.parseGeneric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserTest {
    @Test
    fun walletStyle() {
        assertEquals(Parsed(1250, "EUR", "Maxi", Kind.EXPENSE), parseGeneric("Maxi", "EUR 12,50"))
        assertEquals(Parsed(2500, "BAM", "Pekara Zlatni klas", Kind.EXPENSE), parseGeneric("Pekara Zlatni klas", "KM 25.00"))
        assertEquals(Parsed(1840, "EUR", "Wolt", Kind.EXPENSE), parseGeneric("Wolt", "€18.40 with Visa ••1234"))
    }

    @Test
    fun bankStyle() {
        assertEquals(Parsed(4500, "EUR", "SHELL BEOGRAD", Kind.EXPENSE), parseGeneric("Plaćanje karticom 45,00 EUR", "SHELL BEOGRAD"))
        assertEquals(
            Parsed(1250, "EUR", "MAXI 123 BEOGRAD", Kind.EXPENSE),
            parseGeneric("Plaćanje karticom", "Potrošeno 12,50 EUR kod MAXI 123 BEOGRAD. Stanje: 1.234,56 EUR"),
        )
        assertEquals(Parsed(10000, "EUR", "Bankomat", Kind.WITHDRAWAL), parseGeneric(null, "Isplata na bankomatu 100,00 EUR"))
        assertEquals(
            Parsed(15000000, "RSD", "Uplata", Kind.INCOME),
            parseGeneric("Priliv", "Uplata 150.000,00 RSD na račun"),
        )
    }

    @Test
    fun notAPayment() {
        assertNull(parseGeneric("Vaš izvod za septembar je spreman.", null))
        assertNull(parseGeneric("Novi kod za prijavu", "Kod: 482913"))
    }
}
