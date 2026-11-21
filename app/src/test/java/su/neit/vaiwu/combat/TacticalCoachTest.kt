package su.neit.vaiwu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalCoachTest {

    private fun heroAt(pos: Pos, hp: Int = 40, maxHp: Int = 40, mp: Int = 20): Combatant =
        EncounterBuilder.buildHero(
            HeroBuild(
                characterId = 0, // Aoi
                customName = "Aoi",
                level = 3,
                hp = hp,
                maxHp = maxHp,
                mp = mp,
                maxMp = 24,
                strength = 4,
                vitality = 4,
                agility = 4,
                magic = 6,
                luck = 3,
                weaponId = WeaponId.WoodenSword,
                armorBonus = 1,
                hpPotions = 1,
                mpPotions = 1,
            ),
            pos,
        )

    @Test
    fun coachPrioritizesFinishingKillableEnemy() {
        val hero = heroAt(Pos(2, 2))
        val lowSlime = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 1, Pos(4, 2)).copy(hp = 5)
        val state = TacticalCombat.createCombat(listOf(hero, lowSlime), TacticalGrid(), seed = 5L)

        val hint = TacticalCoach.advise(state, "e1")
        assertNotNull(hint)
        assertTrue(hint!!.message is CoachMessage.FinishTarget)
        assertEquals(CoachPick.Skill(SkillId.AoiFlash), hint.pick)
    }

    @Test
    fun coachRecommendsHpPotionWhenFacingLethalAttack() {
        val hero = heroAt(Pos(3, 2), hp = 8, maxHp = 40, mp = 0)
        val golem = EncounterBuilder.buildMonster("g1", MonsterKind.Golem, 5, Pos(4, 2))
        val state = TacticalCombat.createCombat(
            listOf(hero, golem),
            TacticalGrid(),
            seed = 8L,
            hpPotions = 1,
            mpPotions = 0,
        )

        val hint = TacticalCoach.advise(state, "g1")
        assertNotNull(hint)
        assertEquals(CoachPick.HpPotion, hint!!.pick)
        assertTrue(hint.message is CoachMessage.DrinkHpPotion)
    }

    @Test
    fun coachFocusesEnemyHealerWhenAboutToMend() {
        val hero = heroAt(Pos(3, 2))
        val hurtSlime = EncounterBuilder.buildMonster("s1", MonsterKind.Slime, 3, Pos(6, 2)).copy(hp = 12, maxHp = 40)
        val shroom = EncounterBuilder.buildMonster("m1", MonsterKind.Shroom, 3, Pos(6, 3)).copy(hp = 30, maxHp = 30)
        val state = TacticalCombat.createCombat(listOf(hero, hurtSlime, shroom), TacticalGrid(), seed = 12L)

        // Selected target is hurtSlime (not killable in 1 hit if hp=35, let's ensure not killable)
        val tougher = state.copy(
            combatants = state.combatants.map {
                if (it.id == "s1") it.copy(hp = 26, maxHp = 60) else it
            },
        )
        val hint = TacticalCoach.advise(tougher, "s1")
        assertNotNull(hint)
        assertEquals("m1", hint!!.focusEnemyId)
        assertTrue(hint.message is CoachMessage.FocusHealer)
    }

    @Test
    fun coachSuggestsApproachWhenTargetIsFar() {
        val hero = heroAt(Pos(0, 2), mp = 0)
        val slime = EncounterBuilder.buildMonster("s1", MonsterKind.Slime, 2, Pos(8, 2))
        val state = TacticalCombat.createCombat(listOf(hero, slime), TacticalGrid(), seed = 19L, mpPotions = 0)

        val hint = TacticalCoach.advise(state, "s1")
        assertNotNull(hint)
        assertTrue("Expected Field approach pick, got ${hint!!.pick}", hint.pick is CoachPick.Field)
        assertNotNull((hint.pick as CoachPick.Field).to)
    }
}
