package su.neit.vaiwu

/**
 * Care actions offered on the home screen. The set depends on the state of
 * the pet, so that every action the mechanics allow is reachable with a
 * single tap and the refused ones are not offered at all.
 */
enum class CareKind {
    Feed,
    Wash,
    Clean,
    Talk,
    Hug,
    Games,
}

/** What the home screen offers for the current pet state. */
sealed interface HomeOffer {
    /** A regular care action of the 2x3 grid. */
    data class Care(val kind: CareKind) : HomeOffer

    /** Waking a sleeping pet. */
    data object Wake : HomeOffer

    /** Medicine for a sick pet. */
    data object Medicine : HomeOffer

    /** Starting over after the pet died. */
    data object Restart : HomeOffer
}

/**
 * Offers of the home screen for the given pet state.
 *
 * A sleeping pet keeps the wake (and medicine when sick) actions instead of
 * the care grid, a dead pet keeps the restart action, and the navigation
 * itself is never hidden while a pet is asleep or dead (UI-HOME-02…04).
 */
fun homeOffers(pet: PetSnapshot): List<HomeOffer> = when (pet.lifeState) {
    LifeState.Dead -> listOf(HomeOffer.Restart)
    LifeState.Sleeping -> buildList {
        add(HomeOffer.Wake)
        if (pet.isSick) {
            add(HomeOffer.Medicine)
        }
    }
    LifeState.Awake -> CareKind.entries.map { HomeOffer.Care(it) }
    // Unknown native state: keep the care grid available rather than freezing the UI.
    else -> CareKind.entries.map { HomeOffer.Care(it) }
}

/** Native action ids behind the care kinds. */
fun careAction(kind: CareKind): Int = when (kind) {
    CareKind.Feed -> PetAction.Feed
    CareKind.Wash -> PetAction.Wash
    CareKind.Clean -> PetAction.Clean
    CareKind.Talk -> PetAction.Talk
    CareKind.Hug -> PetAction.Hug
    CareKind.Games -> PetAction.Play
}
