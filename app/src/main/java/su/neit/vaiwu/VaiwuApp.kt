package su.neit.vaiwu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Root composable of the redone UI: it owns the navigation state machine,
 * the snackbar host and the global top bar / bottom bar frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaiwuApp(viewModel: PetViewModel, voice: VoiceController? = null) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val systemMessage by viewModel.systemMessage.collectAsStateWithLifecycle()
    val speech by viewModel.speech.collectAsStateWithLifecycle()
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
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val shopCategory by viewModel.shopCategory.collectAsStateWithLifecycle()

    var confirmingReset by remember { mutableStateOf(false) }
    var confirmingExit by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hour = remember { currentHour() }
    val pet = snapshot
    val currentTab = screen.tab
    val roundActive = isRoundActive(screen, rpsResult, miniGame, battle)

    LaunchedEffect(systemMessage?.id) {
        val message = systemMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        viewModel.consumeSystemMessage()
    }

    BackHandler(enabled = screen != AppScreen.Home) {
        if (roundActive) {
            confirmingExit = true
        } else {
            viewModel.goBack()
        }
    }

    if (pet == null) {
        OnboardingScreen(
            profile = viewModel::characterProfile,
            onSettle = { characterId, name -> viewModel.createPet(characterId, name) },
        )
    } else {
        VaiwuScaffold(
            topBar = {
                VaiwuTopBar(
                    title = { AppTopBarTitle(screen = screen, pet = pet, battle = battle) },
                    onBack = if (screen.isFocusScreen) {
                        {
                            // Same guard as BackHandler: leaving a live round
                            // forfeits the reward, so ask first (UI-NAV-04).
                            if (roundActive) {
                                confirmingExit = true
                            } else {
                                viewModel.goBack()
                            }
                        }
                    } else null,
                    actions = {
                        if (screen == AppScreen.Home) {
                            CoinChip(coins = pet.coins)
                        }
                        IconButton(onClick = viewModel::openSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                currentTab?.let { tab ->
                    VaiwuBottomBar(
                        selected = tab,
                        onSelect = viewModel::openTab,
                    )
                }
            },
            snackbarHostState = snackbarHostState,
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (screen) {
                    AppScreen.Home -> HomeScreen(
                        pet = pet,
                        speech = speech?.text,
                        hour = hour,
                        onAction = viewModel::performAction,
                        onGames = { viewModel.openTab(AppTab.Games) },
                        onShop = { viewModel.openFocus(AppScreen.Shop) },
                        onPet = viewModel::openPet,
                        onRestart = { confirmingReset = true },
                    )
                    AppScreen.Games -> GamesScreen(
                        pet = pet,
                        onRps = viewModel::startRps,
                        onMiniGame = { kind, melodyLength -> viewModel.startMiniGame(kind, melodyLength) },
                        onBattle = viewModel::startBattle,
                    )
                    AppScreen.Pet -> PetScreen(
                        pet = pet,
                        inventory = inventory,
                        onAllocate = viewModel::allocateStat,
                        onEquip = { slot, itemId -> viewModel.equip(slot, itemId) },
                        onSell = { itemId -> viewModel.sell(itemId) },
                    )
                    AppScreen.Chat -> ChatScreen(
                        messages = chatMessages,
                        busy = chatBusy,
                        speechEnabled = assistantSettings.speechEnabled,
                        voice = voice,
                        onSend = viewModel::submitChat,
                    )
                    AppScreen.Shop -> ShopScreen(
                        items = shopItems,
                        category = shopCategory,
                        coins = pet.coins,
                        onBuy = viewModel::buyItem,
                        onInventory = viewModel::openPet,
                        onCategoryChange = viewModel::selectShopCategory,
                    )
                    AppScreen.Rps -> RpsScreen(
                        pet = pet,
                        result = rpsResult,
                        onChoice = viewModel::playRps,
                        onAgain = { viewModel.startRps() },
                        onExit = {
                            if (isRoundActive(AppScreen.Rps, rpsResult, miniGame, battle)) {
                                confirmingExit = true
                            } else {
                                viewModel.openHome()
                            }
                        },
                    )
                    AppScreen.MiniGame -> miniGame?.let { game ->
                        MiniGameScreen(
                            pet = pet,
                            state = game,
                            onChoice = viewModel::chooseMiniGame,
                            onNote = viewModel::playNote,
                            onAgain = { viewModel.startMiniGame(game.kind, game.sequence.size) },
                            onExit = {
                                if (isRoundActive(AppScreen.MiniGame, rpsResult, miniGame, battle)) {
                                    confirmingExit = true
                                } else {
                                    viewModel.openHome()
                                }
                            },
                        )
                    }
                    AppScreen.Battle -> battle?.let { currentBattle ->
                        BattleScreen(
                            battle = currentBattle,
                            onAction = viewModel::battleAction,
                            onNextRoom = { viewModel.nextDungeonRoom() },
                            onExit = {
                                if (isRoundActive(AppScreen.Battle, rpsResult, miniGame, battle)) {
                                    confirmingExit = true
                                } else {
                                    viewModel.openHome()
                                }
                            },
                        )
                    }
                    AppScreen.Settings -> SettingsScreen(
                        settings = assistantSettings,
                        soundEnabled = soundEnabled,
                        voice = voice,
                        onSoundEnabled = viewModel::setSoundEnabled,
                        onSave = { apiKey, speech, lat, lon -> viewModel.saveAssistantSettings(apiKey, speech, lat, lon) },
                        onUpdate = viewModel::checkForUpdates,
                        onHelp = viewModel::openHelp,
                        onReset = { confirmingReset = true },                    )
                    AppScreen.Help -> HelpScreen()
                }
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onDownload = viewModel::downloadUpdate,
        onDismiss = viewModel::dismissUpdate,
    )
    if (confirmingReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_title),
            message = stringResource(R.string.reset_text),
            confirmLabel = stringResource(R.string.start_over),
            onConfirm = {
                confirmingReset = false
                viewModel.resetPet()
            },
            onDismiss = { confirmingReset = false },
        )
    }
    if (confirmingExit) {
        ConfirmDialog(
            title = stringResource(R.string.exit_title),
            message = stringResource(R.string.exit_message),
            confirmLabel = stringResource(R.string.exit_confirm),
            onConfirm = {
                confirmingExit = false
                viewModel.openHome()
            },
            onDismiss = { confirmingExit = false },
        )
    }
}

@Composable
private fun AppTopBarTitle(
    screen: AppScreen,
    pet: PetSnapshot,
    battle: BattleSnapshot?,
) {
    if (screen == AppScreen.Home) {
        Column {
            Text(
                text = pet.petName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val day = pet.ageSeconds / 86_400 + 1
            Text(
                text = pluralStringResource(R.plurals.pet_day, day, day),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        val title = when (screen) {
            AppScreen.Rps -> stringResource(R.string.rps_title)
            AppScreen.MiniGame -> stringResource(R.string.mini_title)
            AppScreen.Battle -> battle?.let { b ->
                if (b.dungeon) {
                    stringResource(R.string.battle_dungeon, b.room, b.rooms)
                } else {
                    stringResource(R.string.battle_adventure)
                }
            } ?: stringResource(R.string.battle_adventure)
            AppScreen.Shop -> stringResource(R.string.shop_title)
            AppScreen.Settings -> stringResource(R.string.settings_title)
            AppScreen.Help -> stringResource(R.string.help_title)
            else -> stringResource(R.string.nav_home)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
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
                        Text(
                            state.release.notes,
                            modifier = Modifier.heightIn(max = 240.dp),
                        )
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
        dismissButton = {},
    )
}
