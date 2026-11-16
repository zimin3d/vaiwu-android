package su.neit.vaiwu

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dark "cozy room" palette from `docs/ui-spec.md`. Values live in [Palette]
 * so that both the theme and the contrast unit tests read one source.
 */
private val VaiwuColorScheme = darkColorScheme(
    primary = Color(Palette.Primary),
    onPrimary = Color(Palette.OnPrimary),
    primaryContainer = Color(Palette.PrimaryContainer),
    onPrimaryContainer = Color(Palette.OnPrimaryContainer),
    secondary = Color(Palette.Secondary),
    onSecondary = Color(Palette.OnSecondary),
    secondaryContainer = Color(Palette.SurfaceVariant),
    onSecondaryContainer = Color(Palette.TextPrimary),
    tertiary = Color(Palette.Accent),
    onTertiary = Color(Palette.OnAccent),
    tertiaryContainer = Color(Palette.SurfaceElevated),
    onTertiaryContainer = Color(Palette.TextPrimary),
    background = Color(Palette.Background),
    onBackground = Color(Palette.TextPrimary),
    surface = Color(Palette.Surface),
    onSurface = Color(Palette.TextPrimary),
    surfaceVariant = Color(Palette.SurfaceVariant),
    onSurfaceVariant = Color(Palette.TextSecondary),
    surfaceTint = Color(Palette.Primary),
    inverseSurface = Color(Palette.TextPrimary),
    inverseOnSurface = Color(Palette.Background),
    error = Color(Palette.Danger),
    onError = Color(Palette.OnDanger),
    outline = Color(Palette.Outline),
    outlineVariant = Color(Palette.OutlineMuted),
    surfaceDim = Color(Palette.Background),
    surfaceBright = Color(Palette.SurfaceVariant),
    surfaceContainerLowest = Color(Palette.Background),
    surfaceContainerLow = Color(Palette.Surface),
    surfaceContainer = Color(Palette.Surface),
    surfaceContainerHigh = Color(Palette.SurfaceElevated),
    surfaceContainerHighest = Color(Palette.SurfaceVariant),
)

private val VaiwuTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val VaiwuShapes = Shapes(
    extraSmall = RoundedCornerShape(VaiwuRadius.chip),
    small = RoundedCornerShape(VaiwuRadius.field),
    medium = RoundedCornerShape(VaiwuRadius.card),
    large = RoundedCornerShape(VaiwuRadius.sheet),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VaiwuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaiwuColorScheme,
        typography = VaiwuTypography,
        shapes = VaiwuShapes,
        content = content,
    )
}
