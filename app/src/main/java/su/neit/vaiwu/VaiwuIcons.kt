package su.neit.vaiwu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons of the home screen that the Material Symbols set shipped with
 * `material-icons-core` does not cover, drawn as simple vectors.
 *
 * The fill colour is irrelevant: the [androidx.compose.material3.Icon] tint
 * overrides it with the colour of the theme.
 */
internal object VaiwuIcons {
    /** A bowl with rising steam: feeding. */
    val bowl: ImageVector = ImageVector.Builder(
        name = "VaiwuBowl",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 11f)
            lineTo(21f, 11f)
            curveTo(21f, 15.5f, 16.9f, 19f, 12f, 19f)
            curveTo(7.1f, 19f, 3f, 15.5f, 3f, 11f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(9f, 3f)
            curveTo(10.6f, 4.6f, 7.8f, 5.6f, 9.4f, 7f)
            moveTo(15f, 3f)
            curveTo(16.6f, 4.6f, 13.8f, 5.6f, 15.4f, 7f)
        }
    }.build()

    /** A droplet of water: washing. */
    val drop: ImageVector = ImageVector.Builder(
        name = "VaiwuDrop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2.5f)
            curveTo(12f, 2.5f, 19f, 10f, 19f, 14.2f)
            curveTo(19f, 18f, 15.8f, 21.5f, 12f, 21.5f)
            curveTo(8.2f, 21.5f, 5f, 18f, 5f, 14.2f)
            curveTo(5f, 10f, 12f, 2.5f, 12f, 2.5f)
            close()
        }
    }.build()

    /** Four point sparkles: cleaning. */
    val sparkles: ImageVector = ImageVector.Builder(
        name = "VaiwuSparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 3f)
            lineTo(11.7f, 8.3f)
            lineTo(17f, 10f)
            lineTo(11.7f, 11.7f)
            lineTo(10f, 17f)
            lineTo(8.3f, 11.7f)
            lineTo(3f, 10f)
            lineTo(8.3f, 8.3f)
            close()
            moveTo(17f, 14f)
            lineTo(17.9f, 16.1f)
            lineTo(20f, 17f)
            lineTo(17.9f, 17.9f)
            lineTo(17f, 20f)
            lineTo(16.1f, 17.9f)
            lineTo(14f, 17f)
            lineTo(16.1f, 16.1f)
            close()
        }
    }.build()

    /** A filled heart: the mini-game target. */
    val heart: ImageVector = ImageVector.Builder(
        name = "VaiwuHeart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 21f)
            curveTo(12f, 21f, 3f, 14.5f, 3f, 9f)
            curveTo(3f, 6f, 5.5f, 3.5f, 8.5f, 3.5f)
            curveTo(10.5f, 3.5f, 12f, 5f, 12f, 5f)
            curveTo(12f, 5f, 13.5f, 3.5f, 15.5f, 3.5f)
            curveTo(18.5f, 3.5f, 21f, 6f, 21f, 9f)
            curveTo(21f, 14.5f, 12f, 21f, 12f, 21f)
            close()
        }
    }.build()

    /** A speech bubble: talking. */
    val speech: ImageVector = ImageVector.Builder(
        name = "VaiwuSpeech",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 15f)
            lineTo(12f, 15f)
            lineTo(7.5f, 19.5f)
            lineTo(7.5f, 15f)
            lineTo(4f, 15f)
            close()
        }
    }.build()
}
