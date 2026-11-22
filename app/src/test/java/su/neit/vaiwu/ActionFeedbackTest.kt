package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionFeedbackTest {
    // careFeedback(action, wasSick, lifeState, isSick)

    @Test
    fun wakeReportsTheActualState() {
        assertEquals(CareFeedback.Woken, careFeedback(PetAction.Wake, false, LifeState.Awake, false))
        // A low-energy pet can fall back asleep on the next tick; the message
        // must not claim success in that case.
        assertEquals(CareFeedback.StillSleeping, careFeedback(PetAction.Wake, false, LifeState.Sleeping, false))
    }

    @Test
    fun sleepReportsWhetherThePetFellAsleep() {
        assertEquals(CareFeedback.FellAsleep, careFeedback(PetAction.Sleep, false, LifeState.Sleeping, false))
        assertEquals(CareFeedback.NotTired, careFeedback(PetAction.Sleep, false, LifeState.Awake, false))
    }

    @Test
    fun medicineReportsWhetherThePetWasCured() {
        assertEquals(CareFeedback.MedicineTaken, careFeedback(PetAction.Medicine, true, LifeState.Sleeping, false))
        assertEquals(CareFeedback.MedicineNotNeeded, careFeedback(PetAction.Medicine, true, LifeState.Awake, true))
        assertEquals(CareFeedback.MedicineNotNeeded, careFeedback(PetAction.Medicine, false, LifeState.Awake, false))
    }

    @Test
    fun deadPetAlwaysGetsTheSameFeedback() {
        assertEquals(CareFeedback.Dead, careFeedback(PetAction.Medicine, true, LifeState.Dead, false))
        assertEquals(CareFeedback.Dead, careFeedback(PetAction.Feed, false, LifeState.Dead, false))
    }

    @Test
    fun ordinaryActionsFallBackToGenericHandling() {
        assertNull(careFeedback(PetAction.Feed, false, LifeState.Awake, false))
        assertNull(careFeedback(PetAction.Hug, true, LifeState.Awake, true))
    }
}
