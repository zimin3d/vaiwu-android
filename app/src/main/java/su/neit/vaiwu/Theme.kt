package su.neit.vaiwu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaiwuColors = darkColorScheme(
    primary = Color(0xFFD5B8FF),
    onPrimary = Color(0xFF38235B),
    secondary = Color(0xFF37E08D),
    background = Color(0xFF18122B),
    surface = Color(0xFF241B3D),
    onBackground = Color(0xFFF2E6FF),
    onSurface = Color(0xFFF2E6FF),
    surfaceVariant = Color(0xFF322659),
    onSurfaceVariant = Color(0xFF9C8FC7),
    primaryContainer = Color(0xFF3A2F5C),
    onPrimaryContainer = Color(0xFFF2E6FF),
    error = Color(0xFFE04452),
    outline = Color(0xFF3A2F5C),
)

@Composable
fun VaiwuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VaiwuColors, content = content)
}
