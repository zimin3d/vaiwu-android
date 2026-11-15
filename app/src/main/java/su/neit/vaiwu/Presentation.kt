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
