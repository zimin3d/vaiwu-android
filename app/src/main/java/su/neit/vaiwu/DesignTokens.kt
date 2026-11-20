package su.neit.vaiwu

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp

/**
 * Spacing, radius and size tokens from `docs/ui-spec.md` (section 3).
 * Screens must use these values instead of ad-hoc literals.
 */
internal object VaiwuSpacing {
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val screenPadding = large
    val cardPadding = large
    val cardGap = medium
    val sectionGap = xxl
    val itemGap = small
}

internal object VaiwuRadius {
    val chip = 8.dp
    val field = 12.dp
    val card = 16.dp
    val sheet = 24.dp
    val pill = 999.dp
}

internal object VaiwuSizes {
    val primaryButtonHeight = 52.dp
    val secondaryButtonHeight = 44.dp
    val minTouchTarget = 48.dp
    val careActionHeight = 64.dp
    val bottomBarHeight = 64.dp
    val statBarHeight = 8.dp
    val icon = 24.dp
    val pixelIconSmall = 16.dp
    val pixelIconLarge = 32.dp
    /** Fixed height of the battle log area so text changes never move controls. */
    val battleLogHeight = 120.dp
}

/**
 * Colour tone of a stat bar or status chip. The thresholds are defined by the
 * UI spec and must not be re-derived per screen.
 */
enum class StatTone {
    Danger,
    Warning,
    Healthy,
}

const val StatDangerThreshold = 20f
const val StatWarningThreshold = 40f

fun statTone(value: Float): StatTone = when {
    value < StatDangerThreshold -> StatTone.Danger
    value < StatWarningThreshold -> StatTone.Warning
    else -> StatTone.Healthy
}

/**
 * Heroine sprite height on the home stage. The sprite scales with the stage
 * box, so a tablet shows a proportionally larger character instead of a tiny
 * one while phones keep the familiar size (UI-A11Y-05).
 */
internal fun petSpriteHeightForStage(stageHeight: Dp): Dp =
    (stageHeight.value * 0.62f).toDp().coerceIn(180.dp, 340.dp)
