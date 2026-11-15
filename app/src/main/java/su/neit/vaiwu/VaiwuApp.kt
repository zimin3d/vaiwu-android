package su.neit.vaiwu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaiwuApp(viewModel: PetViewModel, voice: VoiceController? = null) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val shopItems by viewModel.shopItems.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val rpsResult by viewModel.rpsResult.collectAsStateWithLifecycle()
    val miniGame by viewModel.miniGame.collectAsStateWithLifecycle()
    val battle by viewModel.battle.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatBusy by viewModel.chatBusy.collectAsStateWithLifecycle()
    val assistantSettings by viewModel.assistantSettings.collectAsStateWithLifecycle()
    var confirmingReset by remember { mutableStateOf(false) }

    BackHandler(enabled = screen != AppScreen.Home) {
        viewModel.openHome()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_version, BuildConfig.VERSION_NAME)) },
                actions = {
                    TextButton(onClick = viewModel::checkForUpdates) {
                        Text(stringResource(R.string.update))
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
                    CharacterSelection(
                        onSelect = { id, name -> viewModel.createPet(id, name) },
                        profile = viewModel::characterProfile,
                    )
                } else {
                    when (screen) {
                        AppScreen.Home -> PetHome(
                            pet = currentPet,
                            onAction = viewModel::performAction,
                            onReset = { confirmingReset = true },
                            onPlay = viewModel::openGames,
                            onShop = viewModel::openShop,
                            onChat = viewModel::openChat,
                            onStats = viewModel::openStats,
                            onSettings = viewModel::openSettings,
                            onHelp = viewModel::openHelp,
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
                        AppScreen.Games -> GamesScreen(
                            onRps = viewModel::startRps,
                            onMiniGame = viewModel::startMiniGame,
                            onBattle = viewModel::startBattle,
                            onHome = viewModel::openHome,
                        )
                        AppScreen.MiniGame -> miniGame?.let { game ->
                            MiniGameScreen(
                                pet = currentPet,
                                state = game,
                                onChoice = viewModel::chooseMiniGame,
                                onAgain = { viewModel.startMiniGame(game.kind, game.sequence.size) },
                                onHome = viewModel::openHome,
                            )
                        }
                        AppScreen.Battle -> battle?.let { currentBattle ->
                            BattleScreen(
                                battle = currentBattle,
                                onAction = viewModel::battleAction,
                                onNextRoom = viewModel::nextDungeonRoom,
                                onAllocate = viewModel::allocateStat,
                                onHome = viewModel::openHome,
                            )
                        }
                        AppScreen.Stats -> StatsScreen(
                            inventory = inventory,
                            onAllocate = viewModel::allocateStat,
                            onEquipment = viewModel::openInventory,
                            onHome = viewModel::openHome,
                        )
                        AppScreen.Chat -> ChatScreen(
                            messages = chatMessages,
                            busy = chatBusy,
                            speechEnabled = assistantSettings.speechEnabled,
                            voice = voice,
                            onSend = viewModel::submitChat,
                            onHome = viewModel::openHome,
                        )
                        AppScreen.Settings -> SettingsScreen(
                            settings = assistantSettings,
                            voice = voice,
                            onSave = viewModel::saveAssistantSettings,
                            onUpdate = viewModel::checkForUpdates,
                            onReset = { confirmingReset = true },
                            onHome = viewModel::openHome,
                        )
                        AppScreen.Help -> HelpScreen(onHome = viewModel::openHome)
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
    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingReset = false
                        viewModel.resetPet()
                    },
                ) {
                    Text(stringResource(R.string.start_over))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CharacterSelection(onSelect: (Int, String) -> Unit, profile: (Int) -> CharacterProfile?) {
    val characterNames = stringArrayResource(R.array.character_names)
    var petName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.choose_pet),
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = petName,
            onValueChange = { petName = it.take(24) },
            label = { Text("Имя питомца (необязательно)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(characterNames) { index, name ->
                val stats = remember(index) { profile(index) }
                Card(onClick = { onSelect(index, petName) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PetSprite(index, 0, name, Modifier.size(96.dp, 128.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        stats?.let {
                            Text(
                                "СИЛ ${it.strength} · ЖИВ ${it.vitality}\nЛОВ ${it.agility} · МАГ ${it.magic} · УДЧ ${it.luck}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            Text("Любимая еда: ${it.favoriteFood}", style = MaterialTheme.typography.bodySmall)
                        }
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
    onChat: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    val weight = stringResource(R.string.weight_kg, pet.weightKg)
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    Box(modifier = Modifier.fillMaxSize()) {
        AssetImage(homeBackgroundForHour(hour), "Дом", Modifier.fillMaxSize(), ContentScale.Crop)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB318122B))
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
            stringResource(R.string.pet_summary, pet.ageSeconds / 86_400 + 1, pet.coins, weight),
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
            Text(
                stringResource(R.string.pet_sick),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        StatBar(stringResource(R.string.stat_hunger), pet.hunger)
        StatBar(stringResource(R.string.stat_happiness), pet.happiness)
        StatBar(stringResource(R.string.stat_energy), pet.energy)
        StatBar(stringResource(R.string.stat_discipline), pet.discipline)
        Spacer(Modifier.height(12.dp))

        when (pet.lifeState) {
            LifeState.Dead -> Button(onClick = onReset) { Text(stringResource(R.string.start_over)) }
            LifeState.Sleeping -> Button(onClick = { onAction(PetAction.Wake) }) {
                Text(stringResource(R.string.wake_up))
            }
            else -> {
                Button(onClick = onShop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shop_with_coins, pet.coins))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onChat, modifier = Modifier.weight(1f)) { Text("Чат") }
                    TextButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Статы") }
                    TextButton(onClick = onHelp, modifier = Modifier.weight(1f)) { Text("Помощь") }
                    TextButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Настройки") }
                }
                ActionRow(
                    stringResource(R.string.action_feed),
                    PetAction.Feed,
                    stringResource(R.string.action_play),
                    PetAction.Play,
                ) { action ->
                    if (action == PetAction.Play) onPlay() else onAction(action)
                }
                ActionRow(
                    stringResource(R.string.action_wash),
                    PetAction.Wash,
                    stringResource(R.string.action_talk),
                    PetAction.Talk,
                    onAction,
                )
                ActionRow(
                    stringResource(R.string.action_clean),
                    PetAction.Clean,
                    stringResource(R.string.action_hug),
                    PetAction.Hug,
                    onAction,
                )
                if (pet.isSick) {
                    Button(
                        onClick = { onAction(PetAction.Medicine) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.give_medicine))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        }
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
            stringResource(R.string.percent, value.toInt()),
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
        UpdateUiState.Checking -> ProgressDialog(stringResource(R.string.update_checking))
        is UpdateUiState.Downloading -> {
            val percent = if (state.totalBytes > 0) {
                (state.receivedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
            val text = if (percent == null) {
                stringResource(R.string.update_downloading, state.release.version)
            } else {
                stringResource(R.string.update_downloading_progress, state.release.version, percent)
            }
            ProgressDialog(text, percent?.let { it / 100f })
        }
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(state.release.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.update_available, state.release.version))
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.release.notes, modifier = Modifier.heightIn(max = 240.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )
        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_close))
                }
            },
        )
    }
}

@Composable
private fun ProgressDialog(text: String, fraction: Float? = null) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(text)
                }
                if (fraction != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
    )
}
