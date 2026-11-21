package su.neit.vaiwu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterBalanceTest {

    private fun starterBuild(characterId: Int, level: Int): HeroBuild {
        // Mirrors Rpg.cpp base stats + level stat growth
        val base = listOf(
            intArrayOf(2, 3, 2, 4, 2), // Aoi
            intArrayOf(3, 2, 4, 1, 3), // Hina
            intArrayOf(4, 3, 3, 1, 2), // Rin
            intArrayOf(1, 2, 2, 5, 3), // Yuki
            intArrayOf(2, 2, 4, 2, 3), // Akane
            intArrayOf(1, 4, 1, 4, 3), // Uni
            intArrayOf(2, 4, 2, 1, 4), // Kolobok
            intArrayOf(2, 2, 3, 4, 2), // Vesper
        )[characterId]
        val extra = (level - 1) * 3
        val str = base[0] + extra / 3
        val vit = base[1] + extra / 3
        val agi = base[2] + (extra - 2 * (extra / 3)) / 2
        val mag = base[3] + extra / 3
        val luck = base[4]
        val hpMax = 20 + vit * 6 + (level - 1) * 4
        val mpMax = 8 + mag * 4 + (level - 1) * 2
        val weapon = when {
            level >= 5 && mag >= str -> WeaponId.MagicStaff
            level >= 5 -> WeaponId.SteelSword
            level >= 3 -> WeaponId.WoodenSword
            else -> WeaponId.Fists
        }
        return HeroBuild(
            characterId = characterId,
            customName = "Hero$characterId",
            level = level,
            hp = hpMax,
            maxHp = hpMax,
            mp = mpMax,
            maxMp = mpMax,
            strength = str,
            vitality = vit,
            agility = agi,
            magic = mag,
            luck = luck,
            weaponId = weapon,
            armorBonus = if (level >= 3) 2 else 0,
            hpPotions = 1,
            mpPotions = 1,
        )
    }

    @Test
    fun allEightHeroinesHaveThreeUniqueSkillsAndAllSixMonstersBuildCleanly() {
        for (characterId in 0..7) {
            val hero = EncounterBuilder.buildHero(starterBuild(characterId, 1))
            assertEquals(3, hero.skills.size)
            assertEquals(3, hero.skills.map { it.id }.distinct().size)
        }
        for (kind in MonsterKind.entries) {
            val m = EncounterBuilder.buildMonster("m", kind, level = 5, pos = Pos(7, 2))
            assertTrue(m.maxHp > 0)
            assertTrue(m.xpReward > 0)
            assertTrue(m.goldReward > 0)
            assertTrue(m.skills.isNotEmpty())
        }
    }

    @Test
    fun dungeonRunHasThreeToFiveRoomsAndPositiveChestGold() {
        val hero = starterBuild(0, 3)
        for (seed in 1L..20L) {
            val session = EncounterBuilder.startSession(hero, dungeon = true, seed = seed * 31L)
            assertTrue(session.dungeon)
            assertTrue(session.rooms in EncounterBuilder.DUNGEON_MIN_ROOMS..EncounterBuilder.DUNGEON_MAX_ROOMS)
            assertTrue(EncounterBuilder.computeDungeonChestGold(session.rooms, seed) > 0)
        }
    }

    @Test
    fun autoSimulationAllHeroinesAcrossLevelsCompletesAndWinsMajority() {
        val levels = listOf(1, 3, 5, 7)
        for (characterId in 0..7) {
            var wins = 0
            var total = 0
            for (level in levels) {
                for (trial in 1L..3L) {
                    total++
                    val build = starterBuild(characterId, level)
                    var state = EncounterBuilder.buildRoomCombat(
                        hero = build,
                        dungeon = false,
                        room = 1,
                        rooms = 1,
                        seed = (characterId + 1) * 1000L + level * 100L + trial,
                    )
                    var steps = 0
                    while (state.winner == null && steps++ < 120) {
                        if (!state.isPlayerTurn) {
                            state = TacticalCombat.runEnemyTurns(state)
                            continue
                        }
                        val target = state.aliveEnemies.firstOrNull() ?: break
                        val hint = TacticalCoach.advise(state, target.id)
                        val focusId = hint?.focusEnemyId ?: target.id
                        val action: CombatAction = when (val pick = hint?.pick) {
                            is CoachPick.Skill -> {
                                val sk = state.player.skills.first { it.id == pick.skillId }
                                when (sk.effect) {
                                    SkillEffect.Defense, SkillEffect.Heal, SkillEffect.Veil -> CombatAction.UseSkill(sk.id)
                                    SkillEffect.Teleport -> CombatAction.EndTurn
                                    SkillEffect.Attack, SkillEffect.Control -> CombatAction.UseSkill(sk.id, targetId = focusId)
                                }
                            }
                            CoachPick.Weapon -> CombatAction.WeaponAttack(focusId)
                            CoachPick.HpPotion -> CombatAction.UseHpPotion
                            CoachPick.MpPotion -> CombatAction.UseMpPotion
                            is CoachPick.Field -> if (pick.to != null) CombatAction.Move(pick.to) else CombatAction.EndTurn
                            CoachPick.EndTurn, null -> CombatAction.EndTurn
                        }
                        state = try {
                            TacticalCombat.performAndResolve(state, action)
                        } catch (_: CombatActionException) {
                            TacticalCombat.performAndResolve(state, CombatAction.EndTurn)
                        }
                    }
                    assertNotNull("Battle must terminate for char=$characterId lvl=$level", state.winner)
                    if (state.winner == CombatWinner.Player) wins++
                }
            }
            assertTrue(
                "Heroine $characterId won only $wins/$total simulated battles",
                wins >= total / 2,
            )
        }
    }
}
