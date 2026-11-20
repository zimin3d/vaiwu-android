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
 * `ShopItemSnapshot.kind`: what a purchase turns into. Consumables go into
 * the bag (use them later), equipment is owned once, potions live in the RPG
 * bag and are spent in battle.
 */
object ShopItemKind {
    const val Consumable = 0
    const val Equipment = 1
    const val Potion = 2
    const val MpPotion = 3
}

/**
 * `vaiwu::game::UseResult` values from `Shop.hpp`, plus the free-rice results
 * of `nativeFeedWith` (offset by 10 to keep the two sets apart).
 */
object UseResult {
    const val Ok = 0
    const val NotOwned = 1
    const val NotApplicable = 2
    const val NoCoins = 3
    const val RefusedDead = 4

    const val FreeRiceRefusedDead = 10
    const val FreeRiceRefusedSleeping = 11
    const val FreeRiceRefusedCaprice = 12
    const val FreeRiceNotHungry = 13
    const val Unknown = 19
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
 * `Emotion` values from `vaiwu/game/VaiwuPet.hpp`. Mirrors the visual mood
 * used to pick `sprite_<character>_<emotion>.png`.
 */
object Emotion {
    const val Idle = 0
    const val Happy = 1
    const val Sad = 2
    const val Angry = 3
    const val Sleepy = 4
    const val Sick = 5
    const val Dead = 6
}

/**
 * `RpsResult.outcome` values returned by the native side.
 */
object RpsOutcome {
    const val Win = 1
    const val Draw = 0
    const val Loss = -1
}
