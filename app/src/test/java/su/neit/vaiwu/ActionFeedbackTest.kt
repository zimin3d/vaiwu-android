package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionFeedbackTest {
    // careFeedback(action, wasSick, lifeState, isSick)

    @Test
    fun wakeReportsTheActualState() {
        assertEquals("Пора вставать!", careFeedback(PetAction.Wake, false, LifeState.Awake, false))
        // A low-energy pet can fall back asleep on the next tick; the message
        // must not claim success in that case.
        assertEquals("Она ещё спит", careFeedback(PetAction.Wake, false, LifeState.Sleeping, false))
    }

    @Test
    fun medicineReportsWhetherThePetWasCured() {
        assertEquals("Лекарство принято", careFeedback(PetAction.Medicine, true, LifeState.Sleeping, false))
        assertEquals("Лекарство сейчас не нужно", careFeedback(PetAction.Medicine, true, LifeState.Awake, true))
        assertEquals("Лекарство сейчас не нужно", careFeedback(PetAction.Medicine, false, LifeState.Awake, false))
    }

    @Test
    fun deadPetAlwaysGetsTheSameMessage() {
        assertEquals("Питомец ушёл...", careFeedback(PetAction.Medicine, true, LifeState.Dead, false))
        assertEquals("Питомец ушёл...", careFeedback(PetAction.Feed, false, LifeState.Dead, false))
    }

    @Test
    fun ordinaryActionsFallBackToGenericHandling() {
        assertNull(careFeedback(PetAction.Feed, false, LifeState.Awake, false))
        assertNull(careFeedback(PetAction.Hug, true, LifeState.Awake, true))
    }
}
