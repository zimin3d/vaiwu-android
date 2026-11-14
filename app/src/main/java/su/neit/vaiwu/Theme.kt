package su.neit.vaiwu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaiwuColors = darkColorScheme(
    primary = Color(0xFFD5B8FF),
    onPrimary = Color(0xFF38235B),
    secondary = Color(0xFFFFC2D4),
    background = Color(0xFF171421),
    surface = Color(0xFF241D31),
    onBackground = Color(0xFFF4EDFF),
    onSurface = Color(0xFFF4EDFF),
    surfaceVariant = Color(0xFF352A45),
    onSurfaceVariant = Color(0xFFD8CBE6),
)

@Composable
fun VaiwuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VaiwuColors, content = content)
}
