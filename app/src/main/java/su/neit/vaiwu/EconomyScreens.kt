package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ShopScreen(
    items: List<ShopItemSnapshot>,
    coins: Int,
    onBuy: (Int) -> Unit,
    onInventory: () -> Unit,
    onHome: () -> Unit,
) {
    val shopCategoryNames = stringArrayResource(R.array.shop_categories)
    var category by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.shop_title), onHome)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.coins, coins),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onInventory) { Text(stringResource(R.string.equipment)) }
        }
        ScrollableTabRow(selectedTabIndex = category, edgePadding = 0.dp) {
            shopCategoryNames.forEachIndexed { index, name ->
                Tab(
                    selected = category == index,
                    onClick = { category = index },
                    text = { Text(name) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items.filter { it.categoryId == category }, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        if (item.owned) {
                            Text(
                                stringResource(R.string.bought),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Button(onClick = { onBuy(item.id) }) {
                                Text(stringResource(R.string.buy_with_price, item.price))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun InventoryScreen(
    inventory: InventorySnapshot?,
    onEquip: (Int, Int) -> Unit,
    onSell: (Int) -> Unit,
    onHome: () -> Unit,
) {
    val equipmentSlotNames = stringArrayResource(R.array.equipment_slots)
    var pendingSale by remember { mutableStateOf<EquipmentSnapshot?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader(stringResource(R.string.equipment), onHome)
        if (inventory == null) {
            Text(stringResource(R.string.no_data))
            return@Column
        }
        Text(
            stringResource(R.string.inventory_level, inventory.level, inventory.xp, inventory.xpNext),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(
                R.string.inventory_hp_mp,
                inventory.hp,
                inventory.hpMax,
                inventory.mp,
                inventory.mpMax,
            ),
        )
        Text(
            stringResource(
                R.string.inventory_potions,
                inventory.potions,
                inventory.mpPotions,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.inventory_stats,
                inventory.strength,
                inventory.vitality,
                inventory.agility,
                inventory.magic,
                inventory.luck,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        equipmentSlotNames.forEachIndexed { slot, slotName ->
            Text(slotName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val owned = inventory.equipment.filter { it.slot == slot && it.owned }
            if (owned.isEmpty()) {
                Text(
                    stringResource(R.string.slot_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            owned.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(item.bonuses, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(8.dp))
        }
    }
    pendingSale?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingSale = null },
            title = { Text(stringResource(R.string.sell_title, item.name)) },
            text = { Text(stringResource(R.string.sell_text, item.sellPrice)) },
            confirmButton = {
                TextButton(onClick = {
                    onSell(item.id)
                    pendingSale = null
                }) { Text(stringResource(R.string.sell)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSale = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun RpsScreen(
    pet: PetSnapshot,
    result: RpsResult?,
    onChoice: (Int) -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val rpsChoiceNames = stringArrayResource(R.array.rps_choices)
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(stringResource(R.string.rps_title), onHome)
        PetSprite(
            characterId = pet.characterId,
            emotionId = pet.emotionId,
            description = pet.petName,
            modifier = Modifier.size(144.dp, 192.dp),
        )
        if (result == null) {
            Text(
                stringResource(R.string.rps_win_hint),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rpsChoiceNames.forEachIndexed { choice, name ->
                    Button(onClick = { onChoice(choice) }, modifier = Modifier.weight(1f)) {
                        Text(name, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Text(
                stringResource(
                    R.string.rps_pet_choice,
                    pet.petName,
                    rpsChoiceNames[result.petChoice],
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                when (result.outcome) {
                    RpsOutcome.Win -> stringResource(R.string.rps_win, result.reward)
                    RpsOutcome.Draw -> stringResource(R.string.rps_draw)
                    else -> stringResource(R.string.rps_loss)
                },
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAgain) { Text(stringResource(R.string.rps_again)) }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onHome) { Text(stringResource(R.string.home)) }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(72.dp))
    }
}
