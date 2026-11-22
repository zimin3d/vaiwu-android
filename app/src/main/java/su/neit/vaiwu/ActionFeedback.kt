package su.neit.vaiwu

/**
 * Feedback kinds for care actions whose native side does not report a detailed
 * result (Wake and Medicine).
 *
 * The kinds describe what the user has to be told after the action: waking a
 * sleeping pet must not claim success while the pet is still asleep, medicine
 * must be reported as taken or simply unnecessary. Which text belongs to
 * which kind is a UI concern and lives in `strings.xml`.
 */
internal enum class CareFeedback {
    Dead,
    Woken,
    StillSleeping,
    FellAsleep,
    NotTired,
    MedicineTaken,
    MedicineNotNeeded,
}

/**
 * Returns the feedback kind for a care action, or `null` when the action has
 * no detailed native result and the caller should fall back to the generic
 * result handling.
 */
internal fun careFeedback(
    action: Int,
    wasSick: Boolean,
    lifeState: Int,
    isSick: Boolean,
): CareFeedback? = when {
    lifeState == LifeState.Dead -> CareFeedback.Dead
    action == PetAction.Wake && lifeState == LifeState.Awake -> CareFeedback.Woken
    action == PetAction.Wake -> CareFeedback.StillSleeping
    action == PetAction.Sleep && lifeState == LifeState.Sleeping -> CareFeedback.FellAsleep
    action == PetAction.Sleep -> CareFeedback.NotTired
    action == PetAction.Medicine && wasSick && !isSick -> CareFeedback.MedicineTaken
    action == PetAction.Medicine -> CareFeedback.MedicineNotNeeded
    else -> null
}
