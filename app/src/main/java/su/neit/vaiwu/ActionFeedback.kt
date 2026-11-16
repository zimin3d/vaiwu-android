package su.neit.vaiwu

/**
 * Feedback for care actions whose native side does not report a detailed
 * result (Wake and Medicine). The message must reflect the state *after* the
 * action: claiming success while the pet is still asleep is what hid the
 * "medicine is unreachable" defect.
 *
 * Returns null when the action is an ordinary one and the caller should fall
 * back to the generic result handling.
 */
internal fun careFeedback(
    action: Int,
    wasSick: Boolean,
    lifeState: Int,
    isSick: Boolean,
): String? = when {
    lifeState == LifeState.Dead -> "Питомец ушёл..."
    action == PetAction.Wake && lifeState == LifeState.Awake -> "Пора вставать!"
    action == PetAction.Wake -> "Она ещё спит"
    action == PetAction.Medicine && wasSick && !isSick -> "Лекарство принято"
    action == PetAction.Medicine -> "Лекарство сейчас не нужно"
    else -> null
}
