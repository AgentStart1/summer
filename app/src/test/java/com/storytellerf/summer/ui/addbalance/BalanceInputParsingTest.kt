package com.storytellerf.summer.ui.addbalance

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceInputParsingTest {
    @Test
    fun imageResultFormatting_isLocaleIndependent() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1234.56", formatBalanceForInput(1234.56))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun parser_acceptsGermanDecimalAndGroupingSeparators() {
        assertEquals(1234.56, parseBalanceInput("1.234,56", Locale.GERMANY)!!, 0.0)
    }

    @Test
    fun parser_acceptsLocaleNeutralImportedValueInGermanLocale() {
        assertEquals(-245.70, parseBalanceInput("-245.70", Locale.GERMANY)!!, 0.0)
    }

    @Test
    fun parser_rejectsTrailingText() {
        assertNull(parseBalanceInput("123.45 yuan", Locale.US))
    }
}
