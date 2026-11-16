package su.neit.vaiwu

/**
 * Pure mapping from game events to synthesizer effects.
 *
 * Keeping this free of Android types makes the "what should be heard" rules
 * unit-testable on the JVM, independently from the AudioTrack plumbing.
 */

/**
 * Effect for a care action. Returns null when the pet is dead: the death
 * transition is played once by the snapshot watcher instead.
 */
internal fun sfxForCareAction(
    action: Int,
    result: Int,
    wasSick: Boolean,
    lifeState: Int,
    isSick: Boolean,
): Int? {
    if (lifeState == LifeState.Dead) return null
    // A refused action still needs audible feedback so the button never feels
    // dead, but it must not sound like success.
    if (result != ActionResult.Ok) return Sfx.Tap
    return when (action) {
        PetAction.Feed -> Sfx.Feed
        PetAction.Play -> Sfx.PlayStart
        PetAction.Wash -> Sfx.Wash
        PetAction.Clean -> Sfx.Toilet
        PetAction.Talk -> Sfx.Talk
        PetAction.Hug -> Sfx.Talk
        // Waking fully is a "talk" chirp; refusing to wake is a soft tap.
        PetAction.Wake -> if (lifeState == LifeState.Awake) Sfx.Talk else Sfx.Tap
        // The native cure() has no result code, so the state transition is the
        // only reliable signal that the medicine actually worked.
        PetAction.Medicine -> if (wasSick && !isSick) Sfx.Feed else Sfx.Tap
        else -> Sfx.Tap
    }
}

/** Shared mapping for mini-games and rock-paper-scissors rounds. */
internal fun sfxForOutcome(outcome: Int): Int = when {
    outcome > 0 -> Sfx.Win
    outcome < 0 -> Sfx.Lose
    else -> Sfx.Tap
}

/**
 * Picks the battle cue from the state change the native side reported.
 * The native snapshot has no per-turn crit flag, so a plain hit is used for
 * damage and a skill or potion gets the magic cue, mirroring the firmware.
 */
internal fun sfxForBattleAction(
    action: Int,
    previousFoeHp: Int,
    foeHp: Int,
    previousHp: Int,
    hp: Int,
    previousMp: Int,
    mp: Int,
    outcome: Int,
    awaitingRest: Boolean,
    cleared: Boolean,
): Int = when {
    cleared -> Sfx.Win
    outcome == BattleOutcome.Victory -> Sfx.Win
    outcome == BattleOutcome.Defeat -> Sfx.Lose
    outcome == BattleOutcome.Escaped -> Sfx.Tap
    awaitingRest -> Sfx.Win
    mp > previousMp || hp > previousHp -> Sfx.Magic
    hp < previousHp -> Sfx.Hurt
    action == RpgAction.Skill -> Sfx.Magic
    foeHp < previousFoeHp -> Sfx.Hit
    else -> Sfx.Tap
}

/** Maps a melody pad (0..3) to its note effect. */
internal fun sfxForNote(note: Int): Int = Sfx.Note.getOrElse(note) { Sfx.Tap }
