package su.neit.vaiwu

/** Local wall-clock hour ranges controlling day/night background selection. */
private val MORNING_HOURS = 6..10
private val DAY_HOURS = 11..17
private val EVENING_HOURS = 18..22

private const val SECONDS_PER_MINUTE = 60

/** Ordered monster slugs matching native `MonsterKind` ids (0..5). */
private val MONSTER_ASSET_SLUGS = listOf("slime", "bat", "shroom", "fox", "ghost", "golem")

/** Returns the home-screen room backdrop asset filename for the given 24-hour [hour]. */
fun homeBackgroundForHour(hour: Int): String = when (hour) {
    in MORNING_HOURS -> "home_morning.png"
    in DAY_HOURS -> "home_day.png"
    in EVENING_HOURS -> "home_evening.png"
    else -> "home_night.png"
}

/** Returns the outdoor battle backdrop asset filename for the given 24-hour [hour]. */
fun battleBackgroundForHour(hour: Int): String = when (hour) {
    in MORNING_HOURS -> "bg_morning.png"
    in DAY_HOURS -> "bg_day.png"
    in EVENING_HOURS -> "bg_evening.png"
    else -> "bg_night.png"
}

/** Returns the sprite asset filename for native monster [kind], falling back to slime. */
fun monsterAsset(kind: Int): String {
    val name = MONSTER_ASSET_SLUGS.getOrElse(kind) { MONSTER_ASSET_SLUGS.first() }
    return "monster_$name.png"
}

/** Formats a non-negative duration in seconds as `MM:SS` (clamping negative inputs to `00:00`). */
fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / SECONDS_PER_MINUTE, safe % SECONDS_PER_MINUTE)
}

/**
 * Current hour of the day, read once per composition instead of touching
 * `java.util.Calendar` inside the composable body.
 */
fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/**
 * Which Russian plural form of a counted noun a value belongs to
 * (UI-CONTENT-02).
 */
internal enum class PluralForm {
    One,
    Few,
    Many,
}

/** The plural form of `count` in Russian according to CLDR rules. */
internal fun russianPluralForm(count: Int): PluralForm = when {
    count % 10 == 1 && count % 100 != 11 -> PluralForm.One
    count % 10 in 2..4 && count % 100 !in 12..14 -> PluralForm.Few
    else -> PluralForm.Many
}
