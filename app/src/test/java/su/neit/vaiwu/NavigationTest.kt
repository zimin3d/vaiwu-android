package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the navigation rules of `docs/ui-spec.md` (UI-NAV-01…07).
 */
class NavigationTest {

    @Test
    fun backOnTabLeadsHomeInsteadOfClosingTheApp() {
        assertEquals(AppScreen.Home, backTarget(AppScreen.Games, emptyList()))
        assertEquals(AppScreen.Home, backTarget(AppScreen.Chat, emptyList()))
        assertEquals(AppScreen.Home, backTarget(AppScreen.Pet, emptyList()))
        // Already on Home: nothing to do, the app may close itself (UI-NAV-03).
        assertNull(backTarget(AppScreen.Home, emptyList()))
    }

    @Test
    fun backOnFocusScreenReturnsToPreviousScreen() {
        assertEquals(AppScreen.Games, backTarget(AppScreen.Rps, listOf(AppScreen.Games)))
        assertEquals(
            AppScreen.Home,
            backTarget(AppScreen.Shop, listOf(AppScreen.Home)),
        )
        assertEquals(
            AppScreen.Settings,
            backTarget(AppScreen.Heroines, listOf(AppScreen.Settings)),
        )
        assertEquals(
            AppScreen.Settings,
            backTarget(AppScreen.Help, listOf(AppScreen.Settings)),
        )
    }

    @Test
    fun backOnSettingsGoesHome() {
        assertEquals(AppScreen.Home, backTarget(AppScreen.Settings, emptyList()))
    }

    @Test
    fun tabScreensAreMappedToTabs() {
        assertEquals(AppTab.Home, AppScreen.Home.tab)
        assertEquals(AppTab.Games, AppScreen.Games.tab)
        assertEquals(AppTab.Pet, AppScreen.Pet.tab)
        assertEquals(AppTab.Chat, AppScreen.Chat.tab)
        assertEquals(null, AppScreen.Shop.tab)
        assertEquals(null, AppScreen.Rps.tab)
        assertEquals(null, AppScreen.Settings.tab)
    }

    @Test
    fun gearScreensAreFocusScreens() {
        assertEquals(true, AppScreen.Settings.isFocusScreen)
        assertEquals(true, AppScreen.Help.isFocusScreen)
        assertEquals(false, AppScreen.Home.isFocusScreen)
    }

    @Test
    fun unfinishedRoundsRequireConfirmationToLeave() {
        // RPS is an active round while no result is shown yet.
        assertEquals(true, isRoundActive(AppScreen.Rps, rpsResult = null, miniGame = null, battle = null))
        assertEquals(false, isRoundActive(AppScreen.Rps, rpsResult = RpsResult(0, 1, 2), miniGame = null, battle = null))
        // The battle is active until the outcome is settled or the rest is over.
        assertEquals(true, isRoundActive(AppScreen.Battle, rpsResult = null, miniGame = null, battle = BattleSnapshot(
            active = true,
            over = false,
            dungeon = false,
            awaitingRest = false,
            cleared = false,
            boss = false,
            room = 1,
            rooms = 3,
            foeKind = 0,
            foeLevel = 2,
            foeHp = 20,
            foeHpMax = 30,
            level = 1,
            hp = 20,
            hpMax = 30,
            mp = 10,
            mpMax = 10,
            potions = 1,
            mpPotions = 0,
            xp = 0,
            xpNext = 10,
            statPoints = 0,
            outcome = BattleOutcome.Ongoing,
            chestGold = 0,
            foeName = "",
            log = "",
            skillName = "",
        )))
        assertEquals(false, isRoundActive(AppScreen.Battle, rpsResult = null, miniGame = null, battle = BattleSnapshot(
            active = true,
            over = true,
            dungeon = false,
            awaitingRest = false,
            cleared = true,
            boss = false,
            room = 1,
            rooms = 1,
            foeKind = 0,
            foeLevel = 2,
            foeHp = 0,
            foeHpMax = 30,
            level = 1,
            hp = 30,
            hpMax = 30,
            mp = 10,
            mpMax = 10,
            potions = 1,
            mpPotions = 0,
            xp = 10,
            xpNext = 20,
            statPoints = 0,
            outcome = BattleOutcome.Victory,
            chestGold = 5,
            foeName = "",
            log = "",
            skillName = "",
        )))
        // Finished mini-games are not active rounds anymore.
        assertEquals(true, isRoundActive(AppScreen.MiniGame, rpsResult = null, miniGame = MiniGameState(kind = MiniGameKind.Guess), battle = null))
        assertEquals(false, isRoundActive(AppScreen.MiniGame, rpsResult = null, miniGame = MiniGameState(kind = MiniGameKind.Guess, finished = true), battle = null))
    }

    @Test
    fun tacticalBattleRoundActiveMatchesSessionLifecycle() {
        val hero = su.neit.vaiwu.combat.HeroBuild(
            characterId = 0,
            customName = "Aoi",
            level = 1,
            hp = 30,
            maxHp = 30,
            mp = 12,
            maxMp = 12,
            strength = 2,
            vitality = 3,
            agility = 2,
            magic = 4,
            luck = 2,
            weaponId = su.neit.vaiwu.combat.WeaponId.Fists,
            armorBonus = 0,
            hpPotions = 1,
            mpPotions = 0,
        )
        val ongoing = su.neit.vaiwu.combat.EncounterBuilder.startSession(hero, dungeon = false, seed = 1L)
        assertEquals(true, isTacticalRoundActive(AppScreen.Battle, null, null, ongoing))

        val finished = ongoing.copy(state = ongoing.state.copy(winner = su.neit.vaiwu.combat.CombatWinner.Player))
        assertEquals(false, isTacticalRoundActive(AppScreen.Battle, null, null, finished))

        val resting = finished.copy(dungeon = true, awaitingRest = true)
        assertEquals(true, isTacticalRoundActive(AppScreen.Battle, null, null, resting))
    }
}
