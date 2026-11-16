package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the home care-action model of `docs/ui-spec.md`
 * (I-2, UI-HOME-02…04).
 */
class HomeActionsTest {

    private fun pet(
        lifeState: Int = LifeState.Awake,
        isSick: Boolean = false,
    ) = PetSnapshot(
        characterId = 0,
        characterName = "Aoi",
        petName = "Aoi",
        hunger = 50f,
        happiness = 50f,
        energy = 80f,
        discipline = 50f,
        weightKg = 12f,
        ageSeconds = 86_400,
        coins = 0,
        emotionId = 0,
        lifeState = lifeState,
        isSick = isSick,
    )

    @Test
    fun awakePetIsOfferedEveryCareAction() {
        val offers = homeOffers(pet(lifeState = LifeState.Awake))
        assertEquals(
            listOf(
                HomeOffer.Care(CareKind.Feed),
                HomeOffer.Care(CareKind.Wash),
                HomeOffer.Care(CareKind.Clean),
                HomeOffer.Care(CareKind.Talk),
                HomeOffer.Care(CareKind.Hug),
                HomeOffer.Care(CareKind.Games),
            ),
            offers,
        )
    }

    @Test
    fun sleepingPetKeepsWakeAndMedicineOnly() {
        assertEquals(
            listOf<HomeOffer>(HomeOffer.Wake),
            homeOffers(pet(lifeState = LifeState.Sleeping, isSick = false)),
        )
        assertEquals(
            listOf<HomeOffer>(HomeOffer.Wake, HomeOffer.Medicine),
            homeOffers(pet(lifeState = LifeState.Sleeping, isSick = true)),
        )
    }

    @Test
    fun deadPetKeepsOnlyTheRestartAction() {
        assertEquals(listOf<HomeOffer>(HomeOffer.Restart), homeOffers(pet(lifeState = LifeState.Dead)))
    }

    @Test
    fun careKindsMapToNativeActionIds() {
        assertEquals(PetAction.Feed, careAction(CareKind.Feed))
        assertEquals(PetAction.Wash, careAction(CareKind.Wash))
        assertEquals(PetAction.Clean, careAction(CareKind.Clean))
        assertEquals(PetAction.Talk, careAction(CareKind.Talk))
        assertEquals(PetAction.Hug, careAction(CareKind.Hug))
        assertEquals(PetAction.Play, careAction(CareKind.Games))
    }
}
