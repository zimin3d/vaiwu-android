package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val characterNames = listOf("Aoi", "Hina", "Rin", "Yuki", "Akane", "Uni", "Kolobok")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaiwuApp(viewModel: PetViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val shopItems by viewModel.shopItems.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val rpsResult by viewModel.rpsResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vaiwu · ${BuildConfig.VERSION_NAME}") },
                actions = {
                    TextButton(onClick = viewModel::checkForUpdates) {
                        Text("Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val currentPet = snapshot
                if (currentPet == null) {
                    CharacterSelection(onSelect = viewModel::createPet)
                } else {
                    when (screen) {
                        AppScreen.Home -> PetHome(
                            pet = currentPet,
                            onAction = viewModel::performAction,
                            onReset = viewModel::resetPet,
                            onPlay = viewModel::startRps,
                            onShop = viewModel::openShop,
                        )
                        AppScreen.Shop -> ShopScreen(
                            items = shopItems,
                            coins = currentPet.coins,
                            onBuy = viewModel::buyItem,
                            onInventory = viewModel::openInventory,
                            onHome = viewModel::openHome,
                        )
                        AppScreen.Inventory -> InventoryScreen(
                            inventory = inventory,
                            onEquip = viewModel::equip,
                            onSell = viewModel::sell,
                            onHome = viewModel::openHome,
                        )
                        AppScreen.Rps -> RpsScreen(
                            pet = currentPet,
                            result = rpsResult,
                            onChoice = viewModel::playRps,
                            onAgain = viewModel::startRps,
                            onHome = viewModel::openHome,
                        )
                    }
                }
            }
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    UpdateDialog(
        state = updateState,
        onDownload = viewModel::downloadUpdate,
        onDismiss = viewModel::dismissUpdate,
    )
}

@Composable
private fun CharacterSelection(onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Кто поселится в твоём телефоне?",
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(characterNames) { index, name ->
                Card(onClick = { onSelect(index) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PetSprite(index, 0, name, Modifier.size(96.dp, 128.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PetHome(
    pet: PetSnapshot,
    onAction: (Int) -> Unit,
    onReset: () -> Unit,
    onPlay: () -> Unit,
    onShop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            pet.petName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "День ${pet.ageSeconds / 86_400 + 1} · ${pet.coins} монет · " +
                String.format(Locale.US, "%.1f кг", pet.weightKg),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PetSprite(
            characterId = pet.characterId,
            emotionId = pet.emotionId,
            description = pet.petName,
            modifier = Modifier.size(144.dp, 192.dp),
        )
        if (pet.isSick) {
            Text("Она заболела — нужно лекарство", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))
        StatBar("Сытость", pet.hunger)
        StatBar("Счастье", pet.happiness)
        StatBar("Энергия", pet.energy)
        StatBar("Дисциплина", pet.discipline)
        Spacer(Modifier.height(12.dp))

        when (pet.lifeState) {
            2 -> Button(onClick = onReset) { Text("Начать заново") }
            1 -> Button(onClick = { onAction(5) }) { Text("Разбудить") }
            else -> {
                Button(onClick = onShop, modifier = Modifier.fillMaxWidth()) {
                    Text("Магазин · ${pet.coins} монет")
                }
                ActionRow("Покормить", 0, "Поиграть", 1) { action ->
                    if (action == 1) onPlay() else onAction(action)
                }
                ActionRow("Помыть", 2, "Поговорить", 4, onAction)
                ActionRow("Убрать", 3, "Обнять", 7, onAction)
                if (pet.isSick) {
                    Button(onClick = { onAction(6) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Дать лекарство")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActionRow(
    firstLabel: String,
    firstAction: Int,
    secondLabel: String,
    secondAction: Int,
    onAction: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onAction(firstAction) }, modifier = Modifier.weight(1f)) {
            Text(firstLabel, textAlign = TextAlign.Center)
        }
        Button(onClick = { onAction(secondAction) }, modifier = Modifier.weight(1f)) {
            Text(secondLabel, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatBar(label: String, value: Float) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )
        Text(
            "${value.toInt()}%",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun UpdateDialog(state: UpdateUiState, onDownload: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> ProgressDialog("Проверяем GitHub Releases…")
        is UpdateUiState.Downloading -> ProgressDialog("Скачиваем ${state.release.version}…")
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(state.release.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Доступна версия ${state.release.version}")
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.release.notes, modifier = Modifier.heightIn(max = 240.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text("Скачать") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Позже") } },
        )
        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Обновление") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun ProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Обновление") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
        confirmButton = {},
    )
}
