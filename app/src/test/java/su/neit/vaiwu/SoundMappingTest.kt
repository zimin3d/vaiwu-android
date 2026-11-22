package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundMappingTest {
    // Effect ids are part of the JNI contract and must match SoundSynth.hpp.
    @Test
    fun effectIdsStayInSyncWithTheNativeEnum() {
        assertEquals(0, Sfx.Tap)
        assertEquals(19, Sfx.Encounter)
        assertEquals(20, Sfx.Count)
        assertEquals(
            listOf(Sfx.Note1, Sfx.Note2, Sfx.Note3, Sfx.Note4),
            Sfx.Note.toList(),
        )
    }

    @Test
    fun careActionsPickTheirOwnEffects() {
        assertEquals(Sfx.Feed, care(PetAction.Feed))
        assertEquals(Sfx.Wash, care(PetAction.Wash))
        assertEquals(Sfx.Toilet, care(PetAction.Clean))
        assertEquals(Sfx.Talk, care(PetAction.Talk))
        assertEquals(Sfx.Talk, care(PetAction.Hug))
    }

    @Test
    fun refusedCareStillGetsASoftTap() {
        assertEquals(Sfx.Tap, care(PetAction.Feed, result = ActionResult.RefusedSleeping))
        assertEquals(Sfx.Tap, care(PetAction.Wash, result = ActionResult.NotHungry))
        assertEquals(Sfx.Tap, care(PetAction.Talk, result = ActionResult.RefusedCaprice))
    }

    @Test
    fun deadPetDoesNotDoubleTheDeathCue() {
        assertNull(care(PetAction.Feed, lifeState = LifeState.Dead))
        assertNull(care(PetAction.Medicine, wasSick = true, lifeState = LifeState.Dead, isSick = true))
    }

    @Test
    fun wakeOnlyChirpsWhenItActuallyWakes() {
        assertEquals(Sfx.Talk, care(PetAction.Wake, lifeState = LifeState.Awake))
        assertEquals(Sfx.Tap, care(PetAction.Wake, lifeState = LifeState.Sleeping))
    }

    @Test
    fun sleepOnlyChirpsWhenItActuallySleeps() {
        assertEquals(Sfx.Talk, care(PetAction.Sleep, lifeState = LifeState.Sleeping))
        assertEquals(Sfx.Tap, care(PetAction.Sleep, lifeState = LifeState.Awake))
    }

    @Test
    fun medicineIsAudibleOnlyWhenItCures() {
        assertEquals(
            Sfx.Feed,
            care(PetAction.Medicine, wasSick = true, lifeState = LifeState.Sleeping, isSick = false),
        )
        assertEquals(
            Sfx.Tap,
            care(PetAction.Medicine, wasSick = true, lifeState = LifeState.Awake, isSick = true),
        )
        assertEquals(Sfx.Tap, care(PetAction.Medicine, wasSick = false, isSick = false))
    }

    @Test
    fun outcomesMapToWinLoseOrDraw() {
        assertEquals(Sfx.Win, sfxForOutcome(1))
        assertEquals(Sfx.Win, sfxForOutcome(3))
        assertEquals(Sfx.Lose, sfxForOutcome(-1))
        assertEquals(Sfx.Tap, sfxForOutcome(0))
    }

    @Test
    fun battlePrefersTheStrongestCue() {
        assertEquals(Sfx.Win, battle(action = RpgAction.Attack, outcome = BattleOutcome.Victory))
        assertEquals(Sfx.Win, battle(action = RpgAction.Attack, cleared = true))
        assertEquals(Sfx.Lose, battle(action = RpgAction.Attack, outcome = BattleOutcome.Defeat))
        assertEquals(Sfx.Tap, battle(action = RpgAction.Run, outcome = BattleOutcome.Escaped))
        assertEquals(Sfx.Win, battle(action = RpgAction.Attack, awaitingRest = true))
    }

    @Test
    fun battleDistinguishesSkillsPotionsHitsAndHurt() {
        // Drinking a potion heals: magic, not a hit.
        assertEquals(Sfx.Magic, battle(action = RpgAction.Potion, hp = 30, previousHp = 10))
        // Restoring MP is also a magic cue.
        assertEquals(Sfx.Magic, battle(action = RpgAction.MpPotion, mp = 20, previousMp = 5))
        // A skill that spends MP is magic even if the numbers are unclear.
        assertEquals(Sfx.Magic, battle(action = RpgAction.Skill, mp = 5, previousMp = 15))
        // Taking damage wins over the attack cue, like the firmware.
        assertEquals(Sfx.Hurt, battle(action = RpgAction.Attack, hp = 5, previousHp = 20))
        // A plain landed hit.
        assertEquals(Sfx.Hit, battle(action = RpgAction.Attack, foeHp = 10, previousFoeHp = 30))
        // Nothing changed (e.g. a refused action).
        assertEquals(Sfx.Tap, battle(action = RpgAction.Attack))
    }

    @Test
    fun melodyPadsMapToNotes() {
        assertEquals(Sfx.Note1, sfxForNote(0))
        assertEquals(Sfx.Note2, sfxForNote(1))
        assertEquals(Sfx.Note3, sfxForNote(2))
        assertEquals(Sfx.Note4, sfxForNote(3))
        assertEquals(Sfx.Tap, sfxForNote(4))
        assertEquals(Sfx.Tap, sfxForNote(-1))
    }

    private fun care(
        action: Int,
        result: Int = ActionResult.Ok,
        wasSick: Boolean = false,
        lifeState: Int = LifeState.Awake,
        isSick: Boolean = false,
    ): Int? = sfxForCareAction(action, result, wasSick, lifeState, isSick)

    private fun battle(
        action: Int,
        previousFoeHp: Int = 30,
        foeHp: Int = 30,
        previousHp: Int = 20,
        hp: Int = 20,
        previousMp: Int = 10,
        mp: Int = 10,
        outcome: Int = BattleOutcome.Ongoing,
        awaitingRest: Boolean = false,
        cleared: Boolean = false,
    ): Int = sfxForBattleAction(
        action = action,
        previousFoeHp = previousFoeHp,
        foeHp = foeHp,
        previousHp = previousHp,
        hp = hp,
        previousMp = previousMp,
        mp = mp,
        outcome = outcome,
        awaitingRest = awaitingRest,
        cleared = cleared,
    )
}
