package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every image the code can ask for must be in the shipped asset folder. A
 * missing file does not crash the app: the sprite just stays an empty box.
 * This test makes that failure visible in CI instead.
 */
class AssetCatalogTest {

    private val assets = File("src/main/assets")

    private fun missing(names: Collection<String>): List<String> =
        names.distinct().filterNot { File(assets, it).isFile }

    @Test
    fun everyCharacterEmotionSpriteExists() {
        val names = characterIds.flatMap { character ->
            emotionIds.map { emotion -> "sprite_${character}_$emotion.png" }
        }
        assertEquals(emptyList<String>(), missing(names))
    }

    @Test
    fun everyMonsterSpriteExists() {
        val names = (0 until 6).map { monsterAsset(it) }
        assertEquals(emptyList<String>(), missing(names))
    }

    @Test
    fun everyHomeAndBattleBackgroundExists() {
        val names = (0 until 24).flatMap { hour ->
            listOf(homeBackgroundForHour(hour), battleBackgroundForHour(hour))
        }
        assertEquals(emptyList<String>(), missing(names))
    }

    @Test
    fun everyTacticalCoverAndArenaAssetExists() {
        val coverNames = su.neit.vaiwu.combat.CoverKind.entries.map {
            su.neit.vaiwu.combat.coverAssetName(it)
        }
        val arenaNames = su.neit.vaiwu.combat.ArenaTheme.entries.mapNotNull {
            su.neit.vaiwu.combat.arenaBackdropAssetName(it)
        }
        assertEquals(emptyList<String>(), missing(coverNames + arenaNames))
    }

    @Test
    fun animatedSpriteSequencesExistForAkaneAndAoi() {
        val available = assets.list()?.toSet().orEmpty()
        assertEquals(4, resolveSpriteFrames("sprite_akane_idle", available).size)
        assertEquals(3, resolveSpriteFrames("sprite_akane_happy", available).size)
        assertEquals(3, resolveSpriteFrames("sprite_akane_sleepy", available).size)
        assertEquals(3, resolveSpriteFrames("sprite_aoi_idle", available).size)
    }
}
