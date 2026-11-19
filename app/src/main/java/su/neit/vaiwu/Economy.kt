package su.neit.vaiwu

data class ShopItemSnapshot(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val price: Int,
    val owned: Boolean,
    val effect: String,
    val kind: Int,
    /** How many of this item are carried in the bag (consumables only). */
    val count: Int,
    /** Using one now would change something (not wasted on a full stat). */
    val usable: Boolean,
)

data class EquipmentSnapshot(
    val id: Int,
    val slot: Int,
    val name: String,
    val bonuses: String,
    val owned: Boolean,
    val equipped: Boolean,
    val sellPrice: Int,
)

data class InventorySnapshot(
    val level: Int,
    val xp: Int,
    val xpNext: Int,
    val hp: Int,
    val hpMax: Int,
    val mp: Int,
    val mpMax: Int,
    val potions: Int,
    val mpPotions: Int,
    val strength: Int,
    val vitality: Int,
    val agility: Int,
    val magic: Int,
    val luck: Int,
    val statPoints: Int,
    val equipment: Array<EquipmentSnapshot>,
)

data class RpsResult(
    val petChoice: Int,
    val outcome: Int,
    val reward: Int,
)

/**
 * Every screen of the app. The first four are the bottom bar tabs, the next
 * four are focus screens reached from the tabs, the last two are the screens
 * of the gear. The navigation rules live in `Navigation.kt`.
 */
enum class AppScreen {
    Home,
    Games,
    Pet,
    Chat,
    Shop,
    Rps,
    MiniGame,
    Battle,
    Settings,
    Help,
}
