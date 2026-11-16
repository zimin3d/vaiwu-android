package su.neit.vaiwu

/**
 * Sound-effect ids from `vaiwu/game/SoundSynth.hpp`.
 *
 * The numbering is part of the JNI contract with [SoundSynth], so keep it in
 * sync with the native enum. The firmware owns the list; Android only maps
 * game events to these ids.
 */
object Sfx {
    const val Tap = 0
    const val Feed = 1
    const val PlayStart = 2
    const val Win = 3
    const val Lose = 4
    const val Wash = 5
    const val Toilet = 6
    const val Talk = 7
    const val Sick = 8
    const val Death = 9
    const val Note1 = 10
    const val Note2 = 11
    const val Note3 = 12
    const val Note4 = 13
    const val Alarm = 14
    const val Hit = 15
    const val Crit = 16
    const val Magic = 17
    const val Hurt = 18
    const val Encounter = 19
    const val Count = 20

    /** Melody-game pads in the order the UI shows them. */
    val Note = intArrayOf(Note1, Note2, Note3, Note4)
}
