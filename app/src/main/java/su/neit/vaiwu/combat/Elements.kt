package su.neit.vaiwu.combat

/**
 * Combat elements and shield-vs-attack effectiveness matrix.
 */
enum class Element {
    None,
    Fire,
    Water,
    Wind,
    Earth,
    Light,
    Dark,
}

/**
 * Multiplier for how strongly a shield of [shield] element resists an attack of [attack] element.
 * Higher values mean the shield absorbs more damage; lower values mean the attack punches through.
 */
fun defenseFactor(shield: Element, attack: Element): Double = when (shield) {
    Element.Fire -> when (attack) {
        Element.Fire -> 1.5
        Element.Water -> 1.2
        Element.Earth -> 0.6
        Element.Wind -> 0.3
        else -> 1.0
    }
    Element.Water -> when (attack) {
        Element.Fire -> 1.5
        Element.Water -> 1.2
        Element.Wind -> 0.8
        else -> 1.0
    }
    Element.Earth -> when (attack) {
        Element.Wind -> 1.3
        Element.Water -> 1.2
        Element.Fire -> 0.9
        else -> 1.0
    }
    Element.Wind -> when (attack) {
        Element.Earth -> 0.5
        Element.Wind -> 1.2
        else -> 1.0
    }
    Element.Light -> when (attack) {
        Element.Light -> 1.5
        Element.Dark -> 1.2
        else -> 1.0
    }
    Element.Dark -> when (attack) {
        Element.Light -> 0.8
        Element.Dark -> 1.3
        else -> 1.0
    }
    Element.None -> 1.0
}

/** ARGB colour representing an element for canvas FX, shields, and badges. */
fun elementColorArgb(element: Element): Long = when (element) {
    Element.None -> 0xFFD8D4E8
    Element.Fire -> 0xFFFF8A3D
    Element.Water -> 0xFF63B8FF
    Element.Wind -> 0xFF7BE8B4
    Element.Earth -> 0xFFC2955B
    Element.Light -> 0xFFFFE699
    Element.Dark -> 0xFFB975FF
}
