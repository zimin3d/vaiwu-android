package su.neit.vaiwu.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import su.neit.vaiwu.Sfx
import su.neit.vaiwu.sfxForCombatEvent

class BattlePlaybackTest {

    private fun baseState(): CombatState {
        val hero = EncounterBuilder.buildHero(
            HeroBuild(
                characterId = 0,
                customName = "Aoi",
                level = 2,
                hp = 30,
                maxHp = 40,
                mp = 14,
                maxMp = 20,
                strength = 4,
                vitality = 4,
                agility = 3,
                magic = 5,
                luck = 2,
                weaponId = WeaponId.WoodenSword,
                armorBonus = 0,
                hpPotions = 1,
                mpPotions = 1,
            ),
            Pos(4, 2),
        )
        val slime = EncounterBuilder.buildMonster("e1", MonsterKind.Slime, 1, Pos(6, 2)).copy(hp = 0)
        return TacticalCombat.createCombat(listOf(hero, slime), TacticalGrid(), seed = 3L)
            .copy(combatants = listOf(hero, slime))
    }

    @Test
    fun rewindStateRestoresHpPositionsAndKeepsFallingUnitVisible() {
        val finalState = baseState()
        val pending = listOf(
            CombatEvent.Move(1, PLAYER_ID, Pos(1, 2), Pos(4, 2), listOf(Pos(2, 2), Pos(3, 2), Pos(4, 2))),
            CombatEvent.Damage(2, "e1", amount = 12, kind = DamageKind.Hit, crit = false),
            CombatEvent.Down(3, "e1"),
            CombatEvent.Heal(4, PLAYER_ID, PLAYER_ID, amount = 10),
        )

        val rewound = rewindState(finalState, pending)
        assertEquals(Pos(1, 2), rewound.player.pos)
        assertEquals(20, rewound.player.hp)
        val rewoundEnemy = rewound.combatants.first { it.id == "e1" }
        assertTrue("Falling enemy should remain visible before Down plays", rewoundEnemy.hp >= 12)
        assertTrue(rewoundEnemy.isAlive)
    }

    @Test
    fun buildPlaybackScheduleAlignsConsequencesToImpactAndFlagsShake() {
        val finalState = baseState()
        val events = listOf(
            CombatEvent.Move(1, PLAYER_ID, Pos(1, 2), Pos(4, 2), listOf(Pos(4, 2))),
            CombatEvent.SkillCast(2, PLAYER_ID, "e1", SkillId.AoiFlash, Element.Light, SkillEffect.Attack, 14),
            CombatEvent.Damage(3, "e1", amount = 12, kind = DamageKind.Hit, crit = true),
            CombatEvent.Down(4, "e1"),
        )

        val (beats, totalMs) = buildPlaybackSchedule(finalState, events)
        assertEquals(4, beats.size)
        assertEquals(0L, beats[0].atMs)

        val castBeat = beats[1]
        val dmgBeat = beats[2]
        val downBeat = beats[3]
        assertEquals(castBeat.atMs + beatTiming(events[1]).impactMs, dmgBeat.atMs)
        assertTrue("Heavy crit hit should trigger screen shake", dmgBeat.shake)
        assertTrue("Down event should trigger screen shake", downBeat.shake)
        assertTrue("Total schedule duration must exceed last beat", totalMs > downBeat.atMs)
        assertEquals(Pos(4, 2), castBeat.fxItem.from)
    }

    @Test
    fun sfxMappingCoversEveryCombatEvent() {
        assertEquals(Sfx.Tap, sfxForCombatEvent(CombatEvent.Move(1, PLAYER_ID, Pos(0, 0), Pos(1, 0), listOf(Pos(1, 0)))))
        assertEquals(Sfx.Magic, sfxForCombatEvent(CombatEvent.Move(2, PLAYER_ID, Pos(0, 0), Pos(3, 0), listOf(Pos(3, 0)), teleport = true)))
        assertEquals(Sfx.Crit, sfxForCombatEvent(CombatEvent.Damage(3, "e1", 10, DamageKind.Hit, crit = true)))
        assertEquals(Sfx.Hurt, sfxForCombatEvent(CombatEvent.Damage(4, PLAYER_ID, 6, DamageKind.Hit, crit = false)))
        assertEquals(Sfx.Win, sfxForCombatEvent(CombatEvent.Down(5, "e1")))
        assertEquals(Sfx.Lose, sfxForCombatEvent(CombatEvent.Down(6, PLAYER_ID)))
        assertNotNull(sfxForCombatEvent(CombatEvent.ShieldUp(7, PLAYER_ID, Element.Light, 15, SkillId.AoiMirrorShield)))
    }
}
