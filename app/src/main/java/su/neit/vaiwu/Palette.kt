package su.neit.vaiwu

import androidx.compose.ui.graphics.Color

/**
 * Raw ARGB palette tokens. This is the single source of truth for colours:
 * [Theme.kt] turns them into a Material colour scheme and unit tests check
 * their contrast ratios. Do not hardcode colours in screens.
 */
internal object Palette {
    const val Background = 0xFF120E22
    const val Surface = 0xFF1E1833
    const val SurfaceElevated = 0xFF2A2245
    const val SurfaceVariant = 0xFF332A52

    const val Primary = 0xFFD5B8FF
    const val OnPrimary = 0xFF2A1845
    const val PrimaryContainer = 0xFF3D2F63
    const val OnPrimaryContainer = 0xFFF5EEFF

    const val Secondary = 0xFF4CE3A0
    const val OnSecondary = 0xFF05291A

    const val Accent = 0xFFFFD166
    const val OnAccent = 0xFF2B1D00

    const val Danger = 0xFFFF6B7A
    const val OnDanger = 0xFF2B0A0E
    const val Warning = 0xFFFFB86B
    const val OnWarning = 0xFF2B1D00
    const val Info = 0xFF72D9FF

    const val TextPrimary = 0xFFF5EEFF
    const val TextSecondary = 0xFFB9ACD9

    const val Outline = 0xFF7A68B0
    const val OutlineMuted = 0xFF4A3D6B

    const val MelodyRose = 0xFFFF7D9C
    const val MelodySky = 0xFF72D9FF
    const val MelodyGold = 0xFFFFD166
    const val MelodyViolet = 0xFF9F8CFF
    const val HeartTint = 0xFFFF8FA8
}

/**
 * Colours that are not part of the Material scheme but are still shared by
 * components (coins, warnings, hints, mini-games).
 */
internal object VaiwuColors {
    val accent = Color(Palette.Accent)
    val onAccent = Color(Palette.OnAccent)
    val warning = Color(Palette.Warning)
    val onWarning = Color(Palette.OnWarning)
    val info = Color(Palette.Info)
    val textSecondary = Color(Palette.TextSecondary)
    val outlineMuted = Color(Palette.OutlineMuted)
    val heartTint = Color(Palette.HeartTint)
    val melodyPads = listOf(
        Color(Palette.MelodyRose),
        Color(Palette.MelodySky),
        Color(Palette.MelodyGold),
        Color(Palette.MelodyViolet),
    )
}
