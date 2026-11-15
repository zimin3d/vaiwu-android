package su.neit.vaiwu

data class ShopItemSnapshot(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val price: Int,
    val owned: Boolean,
    val effect: String,
    val kind: Int,
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
    val equipment: Array<EquipmentSnapshot>,
)

data class RpsResult(
    val petChoice: Int,
    val outcome: Int,
    val reward: Int,
)

enum class AppScreen {
    Home,
    Shop,
    Inventory,
    Rps,
}

val shopCategoryNames = listOf("Еда", "Аптека", "Подарки", "Оружие", "Броня", "Амулеты")
val equipmentSlotNames = listOf("Оружие", "Броня", "Амулет")
val rpsChoiceNames = listOf("Камень", "Бумага", "Ножницы")
