package su.neit.vaiwu

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
                    EmptyState(
                        title = if (category == categories.lastIndex)
                            stringResource(R.string.shop_cosmetics_coming_soon)
                        else
                            stringResource(R.string.shop_empty),
                        actionLabel = null,
                        onAction = null,
                    )
                }
            }
        }
    }
}

/**
 * Иконка товара по `ShopItemSnapshot.id` (порядок enum `ShopItem` в
 * `external/vaiwu/.../Shop.hpp`). Неизвестный id — запасной значок.
 */
/**
 * Иконка снаряжения по `EquipmentSnapshot.id` (порядок `EquipItem` в
 * `external/vaiwu/.../Rpg.hpp`). Используются те же арты, что и в магазине.
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
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    painter = painterResource(shopIconFor(item.id)),
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
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
                if (item.count > 0) {
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
                    // action, buying another one is secondary.
                    Button(
                        onClick = { onUse(item.id) },
                        enabled = item.usable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.use_item))
                    }
                    OutlinedButton(
                        onClick = { onBuy(item.id) },
                        enabled = item.price <= coins,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.buy_with_price, item.price))
                    }
                } else {
                    Button(
                        onClick = { onBuy(item.id) },
                        enabled = item.price <= coins,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.buy_with_price, item.price))
                    }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    painter = painterResource(shopIconFor(item.id)),
                    contentDescription = null,
                    modifier = Modifier.padding(5.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                if (item.effect.isNotBlank()) {
                    Text(
                        item.effect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.bag_count, item.count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            OutlinedButton(onClick = onUse, enabled = item.usable) {
                Text(stringResource(R.string.use_item))
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
        inventory?.let { stats ->
            StatMeter(
                label = stringResource(R.string.stat_strength),
                value = stats.strength.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_vitality),
                value = stats.vitality.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_agility),
                value = stats.agility.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_magic),
                value = stats.magic.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_luck),
                value = stats.luck.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (stats.statPoints > 0) {
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Image(
                                painter = painterResource(equipIconFor(item.id)),
                                contentDescription = null,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.bonuses, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
                                if (item.equipped) {
                                    OutlinedButton(onClick = { onEquip(slot, -1) }) {
                                        Text(stringResource(R.string.unequip))
                                    }
                                } else {
                                    Button(onClick = { onEquip(slot, item.id) }) {
                                        Text(stringResource(R.string.equip))
                                    }
                                }
                                TextButton(onClick = { pendingSale = item }) {
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

@Composable
private fun StatAllocation(points: Int, onAllocate: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(VaiwuSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.stat_points_free, points),
                fontWeight = FontWeight.Bold,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(
                    stringResource(R.string.stat_strength) to 0,
                    stringResource(R.string.stat_vitality) to 1,
                    stringResource(R.string.stat_agility) to 2,
                    stringResource(R.string.stat_magic) to 3,
                    stringResource(R.string.stat_luck) to 4,
                ).forEach { (label, index) ->
                    OutlinedButton(onClick = { onAllocate(index) }) {
                        Text(stringResource(R.string.stat_allocate, label))
                    }
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
            modifier = Modifier.size(144.dp, 192.dp),
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(name, textAlign = TextAlign.Center)
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
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAgain) { Text(stringResource(R.string.rps_again)) }
        }
        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.home)) }
    }
}
