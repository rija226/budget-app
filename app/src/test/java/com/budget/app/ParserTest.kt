package com.budget.app

import com.budget.app.ingest.Kind
import com.budget.app.ingest.Parsed
import com.budget.app.ingest.parseErste
import com.budget.app.ingest.parseGeneric
import java.time.LocalDateTime
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
    fun erste() {
        fun card(t: String, rest: String) = "$t sati $rest karticom 400000XXXXXX0000. Ukupno dostupno u eurima: 1.000,00 EUR."
        assertEquals(
            Parsed(897, "EUR", "PLODINE SUPERMARKET POREC", Kind.EXPENSE, LocalDateTime.of(2026, 10, 14, 16, 50, 55)),
            parseErste(card("14.10.2026. u 16:50:55", "obrađena je transakcija na prodajnom mjestu PLODINE SUPERMARKET POREC u vrijednosti od 8,97 EUR")),
        )
        assertEquals(
            Parsed(14303, "BAM", "NESTRO PETROL PJ BRODBROD", Kind.EXPENSE, LocalDateTime.of(2026, 10, 12, 15, 16, 54)),
            parseErste(card("12.10.2026. u 15:16:54", "obrađena je transakcija na prodajnom mjestu NESTRO PETROL PJ BRODBROD u vrijednosti od 143,03 BAM")),
        )
        assertEquals(
            Parsed(3001, "EUR", "PETROL PM POREC, ZBANDPOREC (PARENZ", Kind.EXPENSE, LocalDateTime.of(2026, 10, 9, 15, 50, 13)),
            parseErste(card("09.10.2026. u 15:50:13", "obrađena je transakcija na prodajnom mjestu PETROL PM POREC, ZBANDPOREC (PARENZ u vrijednosti od 30,01 EUR")),
        )
        assertEquals(
            Parsed(40000, "EUR", "Bankomat", Kind.WITHDRAWAL, LocalDateTime.of(2026, 10, 9, 15, 43, 36)),
            parseErste(card("09.10.2026. u 15:43:36", "napravljena je transakcija na bankomatu u vrijednosti od 400,00 EUR")),
        )
        assertNull(parseErste("09.10.2026. u 13:13 sati >Racun: HR00xxxx >Stanje: 1000,00 EUR >Raspolozivo: 1000,00 EUR"))
    }

    @Test
    fun walletReal() {
        assertEquals(Parsed(14303, "BAM", "NESTRO PETROL PJ BROD", Kind.EXPENSE), parseGeneric("NESTRO PETROL  PJ BROD", "BAM143.03 with Visa Classic ••0000"))
        assertEquals(Parsed(4733, "EUR", "ReservedMax City Pula", Kind.EXPENSE), parseGeneric("ReservedMax City Pula", "€47.33 with Visa Classic ••0000"))
    }

    @Test
    fun notAPayment() {
        assertNull(parseGeneric("Vaš izvod za septembar je spreman.", null))
        assertNull(parseGeneric("Novi kod za prijavu", "Kod: 482913"))
    }
}
