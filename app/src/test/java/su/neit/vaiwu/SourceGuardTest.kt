package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Source-level guards. Android resources and Compose are not available to
 * JVM tests, so these read the Kotlin sources directly and fail on the
 * patterns that caused past regressions (`docs/handoff.md`, section 13).
 */
class SourceGuardTest {

    private val sources: List<File> = File("src/main/java")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun codeLines(file: File): List<Pair<Int, String>> =
        file.readLines().withIndex()
            .map { (index, line) -> index + 1 to line }
            .filterNot { (_, line) ->
                val trimmed = line.trim()
                trimmed.startsWith("//") || trimmed.startsWith("*") ||
                    trimmed.startsWith("/*") || trimmed.startsWith("import ") ||
                    trimmed.startsWith("package ")
            }

    /**
     * Russian text in code must come from `strings.xml`, otherwise it cannot be
     * translated and is easy to miss in review (UI-CONTENT-01). Literals below
     * are `@Preview` sample data, not shipped text.
     */
    @Test
    fun russianTextIsNotHardcodedInKotlin() {
        val russianLiteral = Regex("\"([^\"\\\\]*[\u0410-\u044F\u0401\u0451][^\"\\\\]*)\"")
        val previewSamples = setOf(
            "Длинная строка для проверки переноса текста на экране при большом масштабе.",
            "Очень длинная фраза питомца, которая должна оставаться читаемой при увеличенном масштабе интерфейса без обрезки кнопок и заголовков.",
        )
        val offenders = sources.flatMap { file ->
            codeLines(file).mapNotNull { (number, line) ->
                val literals = russianLiteral.findAll(line).map { it.groupValues[1] }.toList()
                if (literals.any { it !in previewSamples }) "${file.name}:$number" else null
            }
        }
        assertEquals("Russian text outside strings.xml: $offenders", emptyList<String>(), offenders)
    }

    /**
     * Regression: `BattleScreen` read `Calendar` inside the composable body, so
     * the background was not tied to the composition state. The hour is now read
     * once in `VaiwuApp` and passed down as a parameter.
     */
    @Test
    fun composablesDoNotReadTheClock() {
        val offenders = sources
            .filter { file -> file.readText().contains("@Composable") }
            .flatMap { file ->
                codeLines(file).mapNotNull { (number, line) ->
                    if (line.contains("Calendar.getInstance") || line.contains("java.util.Calendar")) {
                        "${file.name}:$number"
                    } else {
                        null
                    }
                }
            }
        assertEquals("Clock read inside composable files: $offenders", emptyList<String>(), offenders)
    }
}
