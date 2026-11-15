package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PresentationTest {
    @Test
    fun backgroundsFollowFirmwareDayParts() {
        assertEquals("home_night.png", homeBackgroundForHour(0))
        assertEquals("home_morning.png", homeBackgroundForHour(6))
        assertEquals("home_day.png", homeBackgroundForHour(11))
        assertEquals("home_evening.png", homeBackgroundForHour(18))
        assertEquals("home_evening.png", homeBackgroundForHour(22))
        assertEquals("home_night.png", homeBackgroundForHour(23))
        assertEquals("bg_morning.png", battleBackgroundForHour(10))
        assertEquals("bg_day.png", battleBackgroundForHour(17))
    }

    @Test
    fun unknownMonsterUsesSafeAsset() {
        assertEquals("monster_golem.png", monsterAsset(5))
        assertEquals("monster_slime.png", monsterAsset(-1))
        assertEquals("monster_slime.png", monsterAsset(99))
    }

    @Test
    fun clocksAndTimersAreBounded() {
        assertEquals("00:00", formatClock(-5))
        assertEquals("03:07", formatClock(187))
        assertEquals("100:00", formatClock(6_000))
    }

    @Test
    fun alarmRollsToTomorrowOnlyWhenTimePassed() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sameDay = Calendar.getInstance().apply {
            timeInMillis = nextAlarmAt(13, 0, now)
        }
        assertEquals(15, sameDay.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, sameDay.get(Calendar.HOUR_OF_DAY))
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = nextAlarmAt(12, 30, now)
        }
        assertEquals(16, tomorrow.get(Calendar.DAY_OF_MONTH))
    }
}
