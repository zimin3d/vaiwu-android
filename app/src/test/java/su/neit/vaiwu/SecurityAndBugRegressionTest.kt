package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class SecurityAndBugRegressionTest {

    @Test
    fun spriteFrameResolverDiscoversOneBasedAndZeroBasedSequences() {
        val oneBasedAssets = setOf(
            "sprite_aoi_idle.png",
            "sprite_aoi_idle_01.png",
            "sprite_aoi_idle_02.png",
        )
        assertEquals(
            listOf("sprite_aoi_idle.png", "sprite_aoi_idle_01.png", "sprite_aoi_idle_02.png"),
            resolveSpriteFrames("sprite_aoi_idle", oneBasedAssets),
        )

        val zeroBasedAssets = setOf(
            "sprite_rin_idle_00.png",
            "sprite_rin_idle_01.png",
        )
        assertEquals(
            listOf("sprite_rin_idle_00.png", "sprite_rin_idle_01.png"),
            resolveSpriteFrames("sprite_rin_idle", zeroBasedAssets),
        )

        assertEquals(
            listOf("sprite_hina_idle.png"),
            resolveSpriteFrames("sprite_hina_idle", setOf("sprite_hina_idle.png")),
        )
    }

    @Test
    fun shippedAoiIdleAssetsResolveToMultiFrameBreathingAnimation() {
        val assets = File("src/main/assets").list()?.toSet().orEmpty()
        val frames = resolveSpriteFrames("sprite_aoi_idle", assets)
        assertEquals(
            listOf("sprite_aoi_idle.png", "sprite_aoi_idle_01.png", "sprite_aoi_idle_02.png"),
            frames,
        )
    }

    @Test
    fun apiKeySanitizationStripsControlCharsAndPreservesExistingOnBlankSave() {
        assertEquals("sk-or-v1-abc123", sanitizeApiKey("  sk-or-v1-abc123\r\nX-Injected: bad "))
        assertEquals("existing-key", resolveApiKeyUpdate("", clearRequested = false, existing = "existing-key"))
        assertEquals("existing-key", resolveApiKeyUpdate("   ", clearRequested = false, existing = "existing-key"))
        assertEquals("new-key", resolveApiKeyUpdate(" new-key ", clearRequested = false, existing = "existing-key"))
        assertEquals("", resolveApiKeyUpdate("new-key", clearRequested = true, existing = "existing-key"))
    }

    @Test
    fun coordinatesParseBothDecimalSeparatorsAndRejectInvalidInput() {
        assertEquals(55.75, parseCoordinate("55,75")!!, 0.0001)
        assertEquals(-33.86, parseCoordinate(" -33.86 ")!!, 0.0001)
        assertNull(parseCoordinate(""))
        assertNull(parseCoordinate("abc"))
        assertNull(parseCoordinate("NaN"))
        assertNull(parseCoordinate("Infinity"))

        assertTrue(isValidCoordinates(55.75, 37.61))
        assertFalse(isValidCoordinates(91.0, 0.0))
        assertFalse(isValidCoordinates(0.0, -181.0))
        assertFalse(isValidCoordinates(Double.NaN, 0.0))
    }

    @Test
    fun weatherCoordinatesAreRoundedForPrivacy() {
        assertEquals(
            "latitude=55.76&longitude=37.62",
            formatWeatherCoordinates(55.75583, 37.61730),
        )
    }

    @Test
    fun boundedUtf8ReaderRejectsOversizedPayloads() {
        val ok = ByteArrayInputStream("hello".toByteArray(Charsets.UTF_8)).readBoundedUtf8(10)
        assertEquals("hello", ok)

        var threw = false
        try {
            ByteArrayInputStream(ByteArray(1024) { 'a'.code.toByte() }).readBoundedUtf8(100)
        } catch (_: IOException) {
            threw = true
        }
        assertTrue("Expected IOException for payload exceeding maxBytes", threw)
    }

    @Test
    fun heartMiniGameOffsetStaysInsidePlayfieldBounds() {
        val width = 1080
        val height = 1600
        val icon = 216
        for (hx in 10..85 step 5) {
            for (hy in 15..75 step 5) {
                val (x, y) = heartOffsetPx(width, height, icon, hx, hy)
                assertTrue("x=$x out of bounds for heartX=$hx", x in 0..(width - icon))
                assertTrue("y=$y out of bounds for heartY=$hy", y in 0..(height - icon))
            }
        }
    }

    @Test
    fun gamesAvailabilityMatchesNativeEnergyAndLifeRules() {
        fun samplePet(energy: Float, lifeState: Int = LifeState.Awake) = PetSnapshot(
            characterId = 0,
            characterName = "Aoi",
            petName = "Aoi",
            hunger = 80f,
            happiness = 80f,
            energy = energy,
            discipline = 60f,
            weightKg = 15f,
            ageSeconds = 100,
            coins = 0,
            emotionId = 0,
            lifeState = lifeState,
            isSick = false,
        )

        assertTrue(canPlayGames(samplePet(24f, LifeState.Awake)))
        assertFalse(canPlayGames(samplePet(23f, LifeState.Awake)))
        assertFalse(canPlayGames(samplePet(100f, LifeState.Sleeping)))
        assertFalse(canPlayGames(samplePet(100f, LifeState.Dead)))
    }

    @Test
    fun topBarTitlesMapEveryTabToItsOwnLabel() {
        assertEquals(R.string.nav_home, screenTitleRes(AppScreen.Home))
        assertEquals(R.string.nav_games, screenTitleRes(AppScreen.Games))
        assertEquals(R.string.nav_pet, screenTitleRes(AppScreen.Pet))
        assertEquals(R.string.nav_chat, screenTitleRes(AppScreen.Chat))
        assertEquals(R.string.shop_title, screenTitleRes(AppScreen.Shop))
        assertEquals(R.string.settings_title, screenTitleRes(AppScreen.Settings))
        assertEquals(R.string.help_title, screenTitleRes(AppScreen.Help))
    }
}
