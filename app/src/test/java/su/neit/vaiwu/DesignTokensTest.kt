package su.neit.vaiwu

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Regression tests for the design tokens in `docs/ui-spec.md`.
 *
 * The palette is part of the contract: if someone changes a colour and breaks
 * the contrast table, this test fails instead of shipping unreadable text.
 */
class DesignTokensTest {

    private fun channel(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun luminance(argb: Long): Double {
        val r = channel(((argb shr 16) and 0xFF) / 255.0)
        val g = channel(((argb shr 8) and 0xFF) / 255.0)
        val b = channel((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(foreground: Long, background: Long): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    @Test
    fun `text pairs meet wcag aa`() {
        val pairs = mapOf(
            "textPrimary/background" to (Palette.TextPrimary to Palette.Background),
            "textPrimary/surface" to (Palette.TextPrimary to Palette.Surface),
            "textSecondary/surface" to (Palette.TextSecondary to Palette.Surface),
            "textSecondary/surfaceVariant" to (Palette.TextSecondary to Palette.SurfaceVariant),
            "primary/background" to (Palette.Primary to Palette.Background),
            "onPrimary/primary" to (Palette.OnPrimary to Palette.Primary),
            "secondary/surface" to (Palette.Secondary to Palette.Surface),
            "onSecondary/secondary" to (Palette.OnSecondary to Palette.Secondary),
            "accent/background" to (Palette.Accent to Palette.Background),
            "onAccent/accent" to (Palette.OnAccent to Palette.Accent),
            "danger/background" to (Palette.Danger to Palette.Background),
            "onDanger/danger" to (Palette.OnDanger to Palette.Danger),
            "warning/background" to (Palette.Warning to Palette.Background),
            "onWarning/warning" to (Palette.OnWarning to Palette.Warning),
            "info/background" to (Palette.Info to Palette.Background),
            "onPrimaryContainer/primaryContainer" to
                (Palette.OnPrimaryContainer to Palette.PrimaryContainer),
        )

        pairs.forEach { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue(
                "$name has contrast ${"%.2f".format(ratio)}:1, expected at least 4.5:1",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `control outlines meet non-text contrast`() {
        val ratio = contrast(Palette.Outline, Palette.Surface)
        assertTrue(
            "outline/surface has contrast ${"%.2f".format(ratio)}:1, expected at least 3:1",
            ratio >= 3.0,
        )
    }

    @Test
    fun `palette entries are opaque`() {
        val entries = mapOf(
            "background" to Palette.Background,
            "surface" to Palette.Surface,
            "surfaceElevated" to Palette.SurfaceElevated,
            "surfaceVariant" to Palette.SurfaceVariant,
            "primary" to Palette.Primary,
            "secondary" to Palette.Secondary,
            "accent" to Palette.Accent,
            "danger" to Palette.Danger,
            "warning" to Palette.Warning,
            "info" to Palette.Info,
            "textPrimary" to Palette.TextPrimary,
            "textSecondary" to Palette.TextSecondary,
            "outline" to Palette.Outline,
            "outlineMuted" to Palette.OutlineMuted,
        )

        entries.forEach { (name, argb) ->
            assertEquals("$name must be opaque", 0xFF, ((argb shr 24) and 0xFF).toInt())
        }
    }

    @Test
    fun `stat tone follows the documented thresholds`() {
        assertEquals(StatTone.Danger, statTone(0f))
        assertEquals(StatTone.Danger, statTone(19.9f))
        assertEquals(StatTone.Warning, statTone(20f))
        assertEquals(StatTone.Warning, statTone(39.9f))
        assertEquals(StatTone.Healthy, statTone(40f))
        assertEquals(StatTone.Healthy, statTone(100f))
    }

    @Test
    fun `layout tokens match the specification`() {
        assertEquals(16f, VaiwuSpacing.screenPadding.value, 0f)
        assertEquals(16f, VaiwuSpacing.cardPadding.value, 0f)
        assertEquals(12f, VaiwuSpacing.cardGap.value, 0f)
        assertEquals(52f, VaiwuSizes.primaryButtonHeight.value, 0f)
        assertEquals(44f, VaiwuSizes.secondaryButtonHeight.value, 0f)
        assertEquals(48f, VaiwuSizes.minTouchTarget.value, 0f)
        assertEquals(8f, VaiwuSizes.statBarHeight.value, 0f)
        assertEquals(24f, VaiwuRadius.sheet.value, 0f)
        assertEquals(120f, VaiwuSizes.battleLogHeight.value, 0f)
    }

    @Test
    fun `pet sprite height scales with the stage`() {
        assertEquals(180f, petSpriteHeightForStage(200.dp).value, 0f)
        assertEquals(186f, petSpriteHeightForStage(300.dp).value, 0f)
        assertEquals(340f, petSpriteHeightForStage(1200.dp).value, 0f)
    }
}
