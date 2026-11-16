package su.neit.vaiwu

fun homeBackgroundForHour(hour: Int): String = when (hour) {
    in 6..10 -> "home_morning.png"
    in 11..17 -> "home_day.png"
    in 18..22 -> "home_evening.png"
    else -> "home_night.png"
}

fun battleBackgroundForHour(hour: Int): String = when (hour) {
    in 6..10 -> "bg_morning.png"
    in 11..17 -> "bg_day.png"
    in 18..22 -> "bg_evening.png"
    else -> "bg_night.png"
}

fun monsterAsset(kind: Int): String {
    val name = listOf("slime", "bat", "shroom", "fox", "ghost", "golem").getOrElse(kind) { "slime" }
    return "monster_$name.png"
}

fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
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
