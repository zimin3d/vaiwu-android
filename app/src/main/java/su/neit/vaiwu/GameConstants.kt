package su.neit.vaiwu

/**
 * Action ids shared with `native-lib.cpp` and the native game firmware.
 */
object PetAction {
    const val Feed = 0
    const val Play = 1
    const val Wash = 2
    const val Clean = 3
    const val Talk = 4
    const val Wake = 5
    const val Medicine = 6
    const val Hug = 7
}

/**
 * `ActionResult` values from `vaiwu/game/VaiwuPet.hpp`.
 */
object ActionResult {
    const val Ok = 0
    const val RefusedDead = 1
    const val RefusedSleeping = 2
    const val RefusedCaprice = 3
    const val NotHungry = 4
    const val TooTired = 5
}

/**
 * `LifeState` values from `vaiwu/game/VaiwuPet.hpp`.
 */
object LifeState {
    const val Awake = 0
    const val Sleeping = 1
    const val Dead = 2
}

/**
 * `RpsResult.outcome` values returned by the native side.
 */
object RpsOutcome {
    const val Win = 1
    const val Draw = 0
    const val Loss = -1
}
