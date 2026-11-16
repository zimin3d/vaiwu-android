package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    /**
     * Regression: selling an item called `text(R.plurals.sell, …)`, and
     * `getString` throws for a `<plurals>` id, so the sale crashed the app.
     * Plurals must go through `quantity(...)` or `pluralStringResource`.
     * Android resources are not available to JVM tests, so the guard reads
     * the sources directly.
     */
    @Test
    fun pluralResourcesAreNeverReadWithGetString() {
        val sources = File("src/main/java/su/neit/vaiwu").listFiles { file ->
            file.extension == "kt"
        }.orEmpty()
        assertTrue("sources not found", sources.isNotEmpty())
        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val usesPluralWithText = Regex("""\btext\(R\.plurals\.""").containsMatchIn(line)
                val usesPluralWithString = Regex("""stringResource\(R\.plurals\.""").containsMatchIn(line)
                if (usesPluralWithText || usesPluralWithString) "${file.name}:${index + 1}" else null
            }
        }
        assertEquals("plural resources read with getString: $offenders", emptyList<String>(), offenders)
    }
}
