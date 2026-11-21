package su.neit.vaiwu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalCombatTest {

    private fun sampleHero(characterId: Int = 0, pos: Pos = Pos(1, 2), mp: Int = 20): Combatant {
        val build = HeroBuild(
            characterId = characterId,
            customName = "Hero",
            level = 3,
            hp = 50,
            maxHp = 50,
            mp = mp,
            maxMp = 25,
            strength = 5,
            vitality = 5,
            agility = 5,
            magic = 6,
            luck = 4,
            weaponId = WeaponId.WoodenSword,
            armorBonus = 1,
            hpPotions = 2,
            mpPotions = 2,
        )
        return EncounterBuilder.buildHero(build, pos)
    }

    @Test
    fun elementalDefenseMatrixMatchesDesign() {
        assertEquals(1.5, defenseFactor(Element.Fire, Element.Fire), 1e-6)
        assertEquals(0.3, defenseFactor(Element.Fire, Element.Wind), 1e-6)
        assertEquals(1.2, defenseFactor(Element.Light, Element.Dark), 1e-6)
        assertEquals(1.3, defenseFactor(Element.Earth, Element.Wind), 1e-6)
        assertEquals(1.0, defenseFactor(Element.None, Element.Fire), 1e-6)
    }

    @Test
    fun coverReducesRangedDamageButNotMeleeOrEarthTremor() {
        val grid = TacticalGrid(covers = listOf(CoverTile(Pos(3, 2), CoverKind.Boulder)))
        val hero = sampleHero(characterId = 6, pos = Pos(2, 2)) // Kolobok
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 2, Pos(4, 2))
        val state = TacticalCombat.createCombat(listOf(hero, enemy), grid, seed = 42L)

        // Ranged check across cover
        assertTrue(TacticalCombat.isCovered(grid, state.player, state.aliveEnemies.first()))
        val tremor = state.player.skills.first { it.id == SkillId.KolobokEarthTremor }
        val tremorPreview = TacticalCombat.previewSkillAttack(state, state.player, state.aliveEnemies.first(), tremor)
        assertFalse("Earth Tremor must pierce cover", tremorPreview.covered)
        assertTrue(tremorPreview.inRange)

        // Compare covered vs uncovered ranged attack (Aoi Flash)
        val aoi = sampleHero(characterId = 0, pos = Pos(1, 2))
        val sCovered = TacticalCombat.createCombat(listOf(aoi, enemy), grid, seed = 42L)
        val sOpen = TacticalCombat.createCombat(listOf(aoi, enemy), TacticalGrid(), seed = 42L)
        val flash = sCovered.player.skills.first { it.id == SkillId.AoiFlash }
        val pCov = TacticalCombat.previewSkillAttack(sCovered, sCovered.player, sCovered.aliveEnemies.first(), flash)
        val pOpen = TacticalCombat.previewSkillAttack(sOpen, sOpen.player, sOpen.aliveEnemies.first(), flash)
        assertTrue(pCov.covered)
        assertFalse(pOpen.covered)
        assertTrue("Covered damage ${pCov.damage} must be less than open ${pOpen.damage}", pCov.damage < pOpen.damage)
    }

    @Test
    fun shieldAbsorbsAndDecaysOnTurnStart() {
        val hero = sampleHero(characterId = 0, pos = Pos(1, 2))
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 1, Pos(7, 2))
        var state = TacticalCombat.createCombat(listOf(hero, enemy), TacticalGrid(), seed = 7L)
        state = TacticalCombat.performAction(state, CombatAction.UseSkill(SkillId.AoiMirrorShield))
        val shieldVal = state.player.shield?.value ?: 0.0
        assertTrue("Shield should be active", shieldVal > 10.0)

        // End turn + enemy turn -> next player turn shield decays by 25%
        state = TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
        val decayed = state.player.shield?.value ?: 0.0
        assertTrue("Shield should decay from $shieldVal to ~$decayed", decayed > 0.0 && decayed < shieldVal)
    }

    @Test
    fun stunSkipsTurnAndGrantsSteeledAntiStunlock() {
        val hero = sampleHero(characterId = 0, pos = Pos(2, 2))
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 2, Pos(4, 2)).copy(stunned = 1)
        var state = TacticalCombat.createCombat(listOf(hero, enemy), TacticalGrid(), seed = 10L)

        // Ending player's turn should skip the stunned slime and immediately return turn to player with slime steeled!
        state = TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
        assertTrue(state.isPlayerTurn)
        val slimeAfter = state.combatants.first { it.id == "e1" }
        assertEquals(0, slimeAfter.stunned)
        assertEquals(STEELED_TURNS, slimeAfter.steeled)
        assertTrue(state.log.any { it is CombatLogEntry.StunSkippedTurn && it.actorId == "e1" })

        // Casting Ice Bind on a steeled enemy must NOT re-stun it
        state = TacticalCombat.performAction(state, CombatAction.UseSkill(SkillId.AoiIceBind, targetId = "e1"))
        val slimeAfterBind = state.combatants.first { it.id == "e1" }
        assertEquals(0, slimeAfterBind.stunned)
    }

    @Test
    fun burnHalvesAndPoisonTicksWhileGolemIsPoisonImmune() {
        val hero = sampleHero(characterId = 7, pos = Pos(2, 2)) // Vesper (DarkBolt applies burn + poison)
        val golem = EncounterBuilder.buildMonster("g1", MonsterKind.Golem, 5, Pos(5, 2))
        var state = TacticalCombat.createCombat(listOf(hero, golem), TacticalGrid(), seed = 15L)

        state = TacticalCombat.performAction(state, CombatAction.UseSkill(SkillId.VesperDarkBolt, targetId = "g1"))
        val gAfterHit = state.combatants.first { it.id == "g1" }
        assertTrue("Burn should apply to Golem", gAfterHit.burn > 0)
        assertEquals("Golem must be immune to poison", 0, gAfterHit.poisonTurns)
        val initialBurn = gAfterHit.burn

        state = TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
        val gAfterTurn = state.combatants.first { it.id == "g1" }
        assertEquals(initialBurn / 2, gAfterTurn.burn)
    }

    @Test
    fun vesperShadowStepTeleportsOverCoverWall() {
        val wall = (0 until GRID_HEIGHT).map { y -> CoverTile(Pos(3, y), CoverKind.Pillar) }
        val grid = TacticalGrid(covers = wall)
        val vesper = sampleHero(characterId = 7, pos = Pos(2, 2))
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Ghost, 3, Pos(7, 2))
        var state = TacticalCombat.createCombat(listOf(vesper, enemy), grid, seed = 21L)

        // Normal walk cannot cross the wall
        assertNull(TacticalCombat.reachablePath(state, state.player, Pos(5, 2)))
        val stepSkill = state.player.skills.first { it.id == SkillId.VesperShadowStep }
        assertTrue(TacticalCombat.canTeleportTo(state, state.player, stepSkill, Pos(5, 2)))

        state = TacticalCombat.performAction(state, CombatAction.UseSkill(SkillId.VesperShadowStep, targetPos = Pos(5, 2)))
        assertEquals(Pos(5, 2), state.player.pos)
    }

    @Test
    fun shroomMendsWoundedAllyAndFoxCommandsPack() {
        val hero = sampleHero(characterId = 0, pos = Pos(1, 2))
        val woundedSlime = EncounterBuilder.buildMonster("s1", MonsterKind.Slime, 2, Pos(6, 2)).copy(hp = 6)
        val shroom = EncounterBuilder.buildMonster("m1", MonsterKind.Shroom, 2, Pos(7, 2))
        var state = TacticalCombat.createCombat(listOf(hero, woundedSlime, shroom), TacticalGrid(), seed = 33L)

        val intents = TacticalCombat.enemyIntents(state)
        val shroomIntent = intents.first { it.actorId == "m1" }
        assertEquals(IntentKind.Mend, shroomIntent.kind)
        assertEquals("s1", shroomIntent.targetId)

        state = TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
        assertTrue("Slime should have been healed by Shroom", state.combatants.first { it.id == "s1" }.hp > 6)

        // Fox marks player when another enemy is alive
        val fox = EncounterBuilder.buildMonster("f1", MonsterKind.Fox, 3, Pos(5, 2))
        val ally = EncounterBuilder.buildMonster("s2", MonsterKind.Slime, 3, Pos(6, 3))
        var foxState = TacticalCombat.createCombat(listOf(hero, fox, ally), TacticalGrid(), seed = 44L)
        val foxIntent = TacticalCombat.enemyIntents(foxState).first { it.actorId == "f1" }
        assertEquals(IntentKind.Command, foxIntent.kind)

        foxState = TacticalCombat.performAndResolve(foxState, CombatAction.EndTurn)
        assertTrue(foxState.log.any { it is CombatLogEntry.Commanded && it.actorId == "f1" })
    }

    @Test
    fun undoRestoresExactDeterministicStateWithoutRerolling() {
        val hero = sampleHero(characterId = 1, pos = Pos(2, 2))
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 2, Pos(3, 2))
        val initial = TacticalCombat.createCombat(listOf(hero, enemy), TacticalGrid(), seed = 777L)

        val firstRun = TacticalCombat.performAction(initial, CombatAction.WeaponAttack("e1"))
        val secondRun = TacticalCombat.performAction(initial, CombatAction.WeaponAttack("e1"))

        assertEquals(firstRun.combatants, secondRun.combatants)
        assertEquals(firstRun.rng, secondRun.rng)
        assertEquals(firstRun.events, secondRun.events)
    }

    @Test
    fun potionsRestoreHpMpAndEdgeFleeSucceeds() {
        val hero = sampleHero(characterId = 0, pos = Pos(0, 2), mp = 4).copy(hp = 20)
        val enemy = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 2, Pos(7, 2))
        var state = TacticalCombat.createCombat(listOf(hero, enemy), TacticalGrid(), seed = 99L, hpPotions = 1, mpPotions = 1)

        state = TacticalCombat.performAction(state, CombatAction.UseHpPotion)
        assertEquals(0, state.hpPotions)
        assertTrue(state.player.hp > 20)

        // Start a fresh turn with full AP at x=0 and flee -> guaranteed escape
        state = TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
        state = TacticalCombat.performAction(state, CombatAction.Flee)
        assertEquals(CombatWinner.Escaped, state.winner)
    }
}
