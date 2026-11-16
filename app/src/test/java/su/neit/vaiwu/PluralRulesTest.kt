package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the Russian plural rules used by the counted texts
 * (UI-CONTENT-02).
 */
class PluralRulesTest {

    @Test
    fun russianPluralFormFollowsCldrRules() {
        assertEquals(PluralForm.One, russianPluralForm(1))
        assertEquals(PluralForm.One, russianPluralForm(21))
        assertEquals(PluralForm.One, russianPluralForm(101))
        assertEquals(PluralForm.Few, russianPluralForm(2))
        assertEquals(PluralForm.Few, russianPluralForm(4))
        assertEquals(PluralForm.Few, russianPluralForm(22))
        assertEquals(PluralForm.Many, russianPluralForm(0))
        assertEquals(PluralForm.Many, russianPluralForm(5))
        assertEquals(PluralForm.Many, russianPluralForm(11))
        assertEquals(PluralForm.Many, russianPluralForm(12))
        assertEquals(PluralForm.Many, russianPluralForm(100))
    }
}
