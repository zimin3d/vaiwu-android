package su.neit.vaiwu

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Icon container dimensions for shop catalog cards and bag/equipment rows. */
private val SHOP_ICON_BOX_SIZE = 56.dp
private val SHOP_ICON_PADDING = 6.dp
private val BAG_ICON_BOX_SIZE = 48.dp
private val BAG_ICON_PADDING = 5.dp

/** Sentinel equipment id passed to `onEquip(slot, id)` to clear the slot. */
private const val UNEQUIP_ITEM_ID = -1

/** Native RPG stat allocation indices shared with `BattleSession::allocate()` in `Battle.hpp`. */
private const val STAT_INDEX_STRENGTH = 0
private const val STAT_INDEX_VITALITY = 1
private const val STAT_INDEX_AGILITY = 2
private const val STAT_INDEX_MAGIC = 3
private const val STAT_INDEX_LUCK = 4

/** Heroine sprite dimensions on the Rock-Paper-Scissors screen (3:4 portrait aspect). */
private val RPS_SPRITE_WIDTH = 144.dp
private val RPS_SPRITE_HEIGHT = 192.dp

/**
 * Shop focus screen (UI-ECO-01…03). Categories are horizontal chips, the balance
 * is visible without scrolling and the purchase button refuses coins that are
 * not enough.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShopScreen(
    items: List<ShopItemSnapshot>,
    category: Int,
    coins: Int,
    onBuy: (Int) -> Unit,
    onUse: (Int) -> Unit,
    onInventory: () -> Unit,
    onCategoryChange: (Int) -> Unit,
    onOpenWardrobeCatalog: (() -> Unit)? = null,
) {
    val categories = stringArrayResource(R.array.shop_categories)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(VaiwuSpacing.screenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            CoinChip(coins = coins)
            Text(
                text = stringResource(R.string.shop_balance_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onInventory) {
                Text(stringResource(R.string.bag_title))
            }
        }
        Spacer(Modifier.height(VaiwuSpacing.medium))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            categories.forEachIndexed { index, name ->
                FilterChip(
                    selected = category == index,
                    onClick = { onCategoryChange(index) },
                    label = { Text(name) },
                )
            }
        }
        Spacer(Modifier.height(VaiwuSpacing.medium))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            items(items.filter { it.categoryId == category }, key = { it.id }) { item ->
                ShopItemCard(
                    item = item,
                    coins = coins,
                    onBuy = onBuy,
                    onUse = onUse,
                )
            }
            if (items.none { it.categoryId == category }) {
                item {
                    val isCosmetics = category == categories.lastIndex
                    EmptyState(
                        title = if (isCosmetics)
                            stringResource(R.string.shop_cosmetics_hint)
                        else
                            stringResource(R.string.shop_empty),
                        actionLabel = if (isCosmetics && onOpenWardrobeCatalog != null)
                            stringResource(R.string.wardrobe_browse_catalog)
                        else
                            null,
                        onAction = if (isCosmetics) onOpenWardrobeCatalog else null,
                    )
                }
            }
        }
    }
}

/**
 * Maps an `EquipmentSnapshot.id` (matching native `EquipItem` order in
 * `external/vaiwu/.../Rpg.hpp`) to its drawable icon, with a fallback icon for unknown ids.
 */
internal fun equipIconFor(itemId: Int): Int = when (itemId) {
    0 -> R.drawable.shop_wooden_sword
    1 -> R.drawable.shop_steel_sword
    2 -> R.drawable.shop_magic_staff
    3 -> R.drawable.shop_wind_dagger
    4 -> R.drawable.shop_leather_vest
    5 -> R.drawable.shop_iron_armor
    6 -> R.drawable.shop_lucky_charm
    7 -> R.drawable.shop_star_amulet
    8 -> R.drawable.shop_moon_blade
    9 -> R.drawable.shop_scale_mail
    10 -> R.drawable.shop_silk_cloak
    11 -> R.drawable.shop_sage_ring
    else -> R.drawable.shop_icon_unknown
}

/**
 * Maps a `ShopItemSnapshot.id` (matching native `ShopItem` order in
 * `external/vaiwu/.../Shop.hpp`) to its drawable icon, with a fallback icon for unknown ids.
 */
internal fun shopIconFor(itemId: Int): Int = when (itemId) {
    0 -> R.drawable.shop_tasty_food
    1 -> R.drawable.shop_cake
    2 -> R.drawable.shop_toy
    3 -> R.drawable.shop_energy_drink
    4 -> R.drawable.shop_medicine
    5 -> R.drawable.shop_ribbon
    6 -> R.drawable.shop_sushi
    7 -> R.drawable.shop_vitamins
    8 -> R.drawable.shop_super_energy
    9 -> R.drawable.shop_hp_potion
    10 -> R.drawable.shop_wooden_sword
    11 -> R.drawable.shop_steel_sword
    12 -> R.drawable.shop_magic_staff
    13 -> R.drawable.shop_wind_dagger
    14 -> R.drawable.shop_leather_vest
    15 -> R.drawable.shop_iron_armor
    16 -> R.drawable.shop_lucky_charm
    17 -> R.drawable.shop_star_amulet
    18 -> R.drawable.shop_moon_blade
    19 -> R.drawable.shop_scale_mail
    20 -> R.drawable.shop_silk_cloak
    21 -> R.drawable.shop_sage_ring
    22 -> R.drawable.shop_mp_potion
    else -> R.drawable.shop_icon_unknown
}

/** Single catalog item card in [ShopScreen] with stable layout across owned/in-bag states. */
@Composable
private fun ShopItemCard(
    item: ShopItemSnapshot,
    coins: Int,
    onBuy: (Int) -> Unit,
    onUse: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(VaiwuSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(SHOP_ICON_BOX_SIZE),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    painter = painterResource(shopIconFor(item.id)),
                    contentDescription = null,
                    modifier = Modifier.padding(SHOP_ICON_PADDING),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (item.effect.isNotBlank()) {
                    Text(
                        item.effect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The bag line is rendered for every not-yet-owned item so the
                // card keeps a constant number of text lines across purchases.
                if (item.count > 0 || !item.owned) {
                    Text(
                        text = stringResource(R.string.shop_in_bag, item.count),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (item.owned) {
                    Text(
                        text = stringResource(R.string.bought),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else if (item.count > 0) {
                    // Something is already in the bag: using it is the main
                    // action, buying another one is secondary. Both actions sit
                    // in one row of equal-height buttons, so the card never
                    // grows taller after a purchase.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        VaiwuSecondaryButton(
                            text = stringResource(R.string.use_item),
                            onClick = { onUse(item.id) },
                            enabled = item.usable,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = VaiwuSizes.primaryButtonHeight),
                        )
                        VaiwuPrimaryButton(
                            text = stringResource(R.string.buy_with_price, item.price),
                            onClick = { onBuy(item.id) },
                            enabled = item.price <= coins,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    VaiwuPrimaryButton(
                        text = stringResource(R.string.buy_with_price, item.price),
                        onClick = { onBuy(item.id) },
                        enabled = item.price <= coins,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One carried consumable in the bag, with a "use now" action. */
@Composable
private fun BagItemCard(
    item: ShopItemSnapshot,
    onUse: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = VaiwuSpacing.xs),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(VaiwuSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(BAG_ICON_BOX_SIZE),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    painter = painterResource(shopIconFor(item.id)),
                    contentDescription = null,
                    modifier = Modifier.padding(BAG_ICON_PADDING),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.name,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.bag_count, item.count),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (item.effect.isNotBlank()) {
                    Text(
                        item.effect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VaiwuSecondaryButton(
                    text = stringResource(R.string.use_item),
                    onClick = onUse,
                    enabled = item.usable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Wardrobe card width for horizontal outfit carousel on [PetScreen]. */
private val WARDROBE_CARD_WIDTH = 124.dp
private val WARDROBE_PREVIEW_WIDTH = 76.dp
private val WARDROBE_PREVIEW_HEIGHT = 104.dp

@Composable
private fun WardrobeSection(
    pet: PetSnapshot,
    heroinesState: HeroinesUiState,
    onActivateOutfit: (String?) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val outfits = heroinesState.installed
        .filter { it.targetCharacterId == null || it.targetCharacterId == pet.characterId }
        .sortedByDescending { it.kind == "outfit" && it.targetCharacterId == pet.characterId }
    val activeId = heroinesState.activeHeroineId

    SectionCard(title = stringResource(R.string.wardrobe_title)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "default_outfit") {
                val isSelected = activeId == null
                WardrobeOutfitCard(
                    title = stringResource(R.string.wardrobe_default_outfit),
                    selected = isSelected,
                    onClick = { onActivateOutfit(null) },
                    preview = {
                        Box(
                            modifier = Modifier
                                .width(WARDROBE_PREVIEW_WIDTH)
                                .height(WARDROBE_PREVIEW_HEIGHT)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalActiveHeroineId provides null) {
                                PetSprite(
                                    characterId = pet.characterId,
                                    emotionId = 0,
                                    description = pet.petName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                )
                            }
                        }
                    },
                )
            }
            items(outfits, key = { it.id }) { item ->
                val isSelected = activeId == item.id
                WardrobeOutfitCard(
                    title = item.name,
                    selected = isSelected,
                    onClick = { onActivateOutfit(item.id) },
                    preview = {
                        HeroinePreviewThumb(
                            previewPath = heroinesState.previewPaths[item.id],
                            contentDescription = stringResource(R.string.heroines_preview_desc, item.name),
                        )
                    },
                )
            }
        }
        if (outfits.isEmpty()) {
            Text(
                text = stringResource(R.string.wardrobe_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VaiwuSecondaryButton(
            text = stringResource(R.string.wardrobe_browse_catalog),
            onClick = onOpenCatalog,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WardrobeOutfitCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.width(WARDROBE_CARD_WIDTH),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
        ) {
            preview()
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
            )
            if (selected) {
                OutlinedButton(
                    onClick = onClick,
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_wearing),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_wear),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Pet screen: stats, equipment and the bag live on one screen (I-5,
 * UI-ECO-05…07).
 */
@Composable
fun PetScreen(
    pet: PetSnapshot,
    inventory: InventorySnapshot?,
    shopItems: List<ShopItemSnapshot>,
    onAllocate: (Int) -> Unit,
    onEquip: (Int, Int) -> Unit,
    onSell: (Int) -> Unit,
    onUseItem: (Int) -> Unit,
    heroinesState: HeroinesUiState = HeroinesUiState(),
    onActivateOutfit: (String?) -> Unit = {},
    onOpenWardrobeCatalog: () -> Unit = {},
) {
    var pendingSale by remember { mutableStateOf<EquipmentSnapshot?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
    ) {
        Text(
            text = pet.petName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(VaiwuSpacing.small))
        WardrobeSection(
            pet = pet,
            heroinesState = heroinesState,
            onActivateOutfit = onActivateOutfit,
            onOpenCatalog = onOpenWardrobeCatalog,
        )
        inventory?.let { stats ->
            Spacer(Modifier.height(VaiwuSpacing.small))
            SectionCard(title = stringResource(R.string.inventory_level, stats.level, stats.xp, stats.xpNext)) {
                Text(
                    text = stringResource(R.string.inventory_hp_mp, stats.hp, stats.hpMax, stats.mp, stats.mpMax),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                RpgStatRow(
                    label = stringResource(R.string.stat_strength),
                    value = stats.strength,
                    modifier = Modifier.fillMaxWidth(),
                )
                RpgStatRow(
                    label = stringResource(R.string.stat_vitality),
                    value = stats.vitality,
                    modifier = Modifier.fillMaxWidth(),
                )
                RpgStatRow(
                    label = stringResource(R.string.stat_agility),
                    value = stats.agility,
                    modifier = Modifier.fillMaxWidth(),
                )
                RpgStatRow(
                    label = stringResource(R.string.stat_magic),
                    value = stats.magic,
                    modifier = Modifier.fillMaxWidth(),
                )
                RpgStatRow(
                    label = stringResource(R.string.stat_luck),
                    value = stats.luck,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (stats.statPoints > 0) {
                Spacer(Modifier.height(VaiwuSpacing.small))
                StatAllocation(points = stats.statPoints, onAllocate = onAllocate)
            }
        }
        Spacer(Modifier.height(VaiwuSpacing.sectionGap))
        Text(
            text = stringResource(R.string.bag_title),
            style = MaterialTheme.typography.titleLarge,
        )
        val carried = shopItems.filter { it.count > 0 }
        if (carried.isEmpty()) {
            Text(
                text = stringResource(R.string.bag_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            carried.forEach { item ->
                BagItemCard(item = item, onUse = { onUseItem(item.id) })
            }
        }
        inventory?.let { stats ->
            Text(
                text = stringResource(R.string.bag_potions, stats.potions, stats.mpPotions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = VaiwuSpacing.small),
            )
        }
        Spacer(Modifier.height(VaiwuSpacing.sectionGap))
        Text(
            text = stringResource(R.string.equipment),
            style = MaterialTheme.typography.titleLarge,
        )
        val slots = stringArrayResource(R.array.equipment_slots)
        slots.forEachIndexed { slot, slotName ->
            Text(slotName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val owned: List<EquipmentSnapshot> = inventory?.equipment?.filter { it.slot == slot && it.owned } ?: emptyList()
            if (owned.isEmpty()) {
                Text(
                    text = stringResource(R.string.slot_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            owned.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = VaiwuSpacing.xs),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.padding(VaiwuSpacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(BAG_ICON_BOX_SIZE),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Image(
                                painter = painterResource(equipIconFor(item.id)),
                                contentDescription = null,
                                modifier = Modifier.padding(BAG_ICON_PADDING),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.bonuses, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
                                if (item.equipped) {
                                    VaiwuSecondaryButton(
                                        text = stringResource(R.string.unequip),
                                        onClick = { onEquip(slot, UNEQUIP_ITEM_ID) },
                                    )
                                } else {
                                    VaiwuPrimaryButton(
                                        text = stringResource(R.string.equip),
                                        onClick = { onEquip(slot, item.id) },
                                    )
                                }
                                TextButton(
                                    onClick = { pendingSale = item },
                                    modifier = Modifier.heightIn(min = VaiwuSizes.minTouchTarget),
                                ) {
                                    Text(stringResource(R.string.sell_with_price, item.sellPrice))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(VaiwuSpacing.small))
        }
    }
    pendingSale?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.sell_title, item.name),
            message = stringResource(R.string.sell_text, item.sellPrice),
            confirmLabel = stringResource(R.string.sell),
            onConfirm = {
                onSell(item.id)
                pendingSale = null
            },
            onDismiss = { pendingSale = null },
        )
    }
}

/** Card for spending unallocated RPG level-up stat points across the 5 attributes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatAllocation(points: Int, onAllocate: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.stat_points_free, points),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(
                    stringResource(R.string.stat_strength) to STAT_INDEX_STRENGTH,
                    stringResource(R.string.stat_vitality) to STAT_INDEX_VITALITY,
                    stringResource(R.string.stat_agility) to STAT_INDEX_AGILITY,
                    stringResource(R.string.stat_magic) to STAT_INDEX_MAGIC,
                    stringResource(R.string.stat_luck) to STAT_INDEX_LUCK,
                ).forEach { (label, index) ->
                    VaiwuSecondaryButton(
                        text = stringResource(R.string.stat_allocate, label),
                        onClick = { onAllocate(index) },
                    )
                }
            }
        }
    }
}

/**
 * RPS focus screen (UI-GAME-04/05). The pet choice is visualised before the
 * result is shown; the exit from an unfinished round is confirmed by the app.
 */
@Composable
fun RpsScreen(
    pet: PetSnapshot,
    result: RpsResult?,
    onChoice: (Int) -> Unit,
    onAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val choices = stringArrayResource(R.array.rps_choices)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PetSprite(
            characterId = pet.characterId,
            emotionId = pet.emotionId,
            description = pet.petName,
            modifier = Modifier.size(RPS_SPRITE_WIDTH, RPS_SPRITE_HEIGHT),
        )
        if (result == null) {
            Text(
                text = stringResource(R.string.rps_win_hint),
                modifier = Modifier.padding(vertical = VaiwuSpacing.small),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                choices.forEachIndexed { index, name ->
                    Button(
                        onClick = { onChoice(index) },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.medium),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = VaiwuSizes.primaryButtonHeight),
                    ) {
                        Text(name, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.rps_pet_choice, pet.petName, choices[result.petChoice]),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when (result.outcome) {
                    RpsOutcome.Win -> stringResource(R.string.rps_win, result.reward)
                    RpsOutcome.Draw -> stringResource(R.string.rps_draw)
                    else -> stringResource(R.string.rps_loss)
                },
                modifier = Modifier.padding(VaiwuSpacing.small),
                color = when (result.outcome) {
                    RpsOutcome.Win -> MaterialTheme.colorScheme.secondary
                    RpsOutcome.Draw -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            VaiwuPrimaryButton(
                text = stringResource(R.string.rps_again),
                onClick = onAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(VaiwuSpacing.small))
        VaiwuSecondaryButton(
            text = stringResource(R.string.home),
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
