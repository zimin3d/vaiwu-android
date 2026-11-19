package su.neit.vaiwu

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object NoUpdate : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(
        val release: ReleaseInfo,
        val receivedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

/** One system message shown through the snackbar. */
data class SystemMessage(val id: Long, val text: String)

/** One phrase of the pet shown in the speech bubble. */
data class PetSpeech(val id: Long, val text: String)

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val pet = NativePet()
    private val saveStore = AtomicFile(File(application.filesDir, "pet.save"))
    private val updater = GitHubUpdateManager(application)
    private val assistantClient = AssistantClient(application)
    private val reminders = ReminderScheduler(application)
    private val settingsStore = application.getSharedPreferences("settings", 0)
    private val soundPlayer = SoundPlayer()
    private val saveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)
    private var lastSaveAt = 0L
    private var inForeground = true
    private var backgroundedAt = 0L
    private var backgroundSave: String? = null

    private val _snapshot = MutableStateFlow<PetSnapshot?>(null)
    val snapshot: StateFlow<PetSnapshot?> = _snapshot.asStateFlow()

    private val _systemMessage = MutableStateFlow<SystemMessage?>(null)
    val systemMessage: StateFlow<SystemMessage?> = _systemMessage.asStateFlow()

    private val _speech = MutableStateFlow<PetSpeech?>(null)
    val speech: StateFlow<PetSpeech?> = _speech.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.Home)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _shopCategory = MutableStateFlow(0)
    val shopCategory: StateFlow<Int> = _shopCategory.asStateFlow()

    private val history = mutableListOf<AppScreen>()
    private var messageCounter = 0L

    fun consumeSystemMessage() {
        _systemMessage.value = null
    }

    private fun postSystemMessage(text: String) {
        _systemMessage.value = SystemMessage(messageCounter++, text)
    }

    private fun postSpeech(text: String) {
        _speech.value = PetSpeech(messageCounter++, text)
    }

    private fun text(id: Int, vararg formatArgs: Any): String =
        getApplication<Application>().getString(id, *formatArgs)

    private val _shopItems = MutableStateFlow<List<ShopItemSnapshot>>(emptyList())
    val shopItems: StateFlow<List<ShopItemSnapshot>> = _shopItems.asStateFlow()

    private val _inventory = MutableStateFlow<InventorySnapshot?>(null)
    val inventory: StateFlow<InventorySnapshot?> = _inventory.asStateFlow()

    private val _rpsResult = MutableStateFlow<RpsResult?>(null)
    val rpsResult: StateFlow<RpsResult?> = _rpsResult.asStateFlow()

    private val miniGameEngine = MiniGameEngine()
    private val _miniGame = MutableStateFlow<MiniGameState?>(null)
    val miniGame: StateFlow<MiniGameState?> = _miniGame.asStateFlow()

    private val _battle = MutableStateFlow<BattleSnapshot?>(null)
    val battle: StateFlow<BattleSnapshot?> = _battle.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _assistantSettings = MutableStateFlow(readAssistantSettings())
    val assistantSettings: StateFlow<AssistantSettings> = _assistantSettings.asStateFlow()

    private val _soundEnabled = MutableStateFlow(settingsStore.getBoolean(SoundPreference, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    init {
        soundPlayer.enabled = _soundEnabled.value
        loadSavedPet()
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (!inForeground || _snapshot.value == null) continue
                val now = System.currentTimeMillis()
                pet.tick(now)
                refreshPet()
                if (now - lastSaveAt >= SaveIntervalMs) {
                    savePet()
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                val current = _miniGame.value ?: continue
                if (!inForeground || current.finished) continue
                val next = miniGameEngine.tick(current, 100)
                _miniGame.value = next
                settleMiniGame(current, next)
            }
        }
    }

    fun onForeground() {
        if (inForeground) return
        inForeground = true
        val core = backgroundSave
        val startedAt = backgroundedAt
        backgroundSave = null
        backgroundedAt = 0L
        val current = _snapshot.value
        if (core == null || current == null || startedAt <= 0L) return

        val now = System.currentTimeMillis()
        val effectiveStart = OfflineDecay.effectiveSaveTime(startedAt, now, current.hunger)
        if (pet.load(core, effectiveStart)) {
            pet.tick(now)
            refreshPet()
            // Loading resets the in-progress round, so the RPS screen would
            // otherwise accept a choice that the native side rejects.
            if (_screen.value == AppScreen.Rps || _screen.value == AppScreen.MiniGame ||
                _screen.value == AppScreen.Battle
            ) {
                _screen.value = AppScreen.Home
            }
            _rpsResult.value = null
            _miniGame.value = null
            _battle.value = null
        }
    }

    fun onBackground() {
        if (!inForeground) return
        inForeground = false
        backgroundedAt = System.currentTimeMillis()
        backgroundSave = if (_snapshot.value != null) pet.save() else null
        if (backgroundSave != null) {
            savePet()
        }
    }

    fun createPet(characterId: Int, name: String = "") {
        val profile = pet.characterProfile(characterId)
        val safeName = name.filter { it != '\n' && it != '\r' }.take(24).trim()
        val petName = safeName.ifBlank { profile?.name.orEmpty() }
        pet.startNew(characterId, petName, System.currentTimeMillis())
        refreshPet()
        postSpeech(pet.phrase(PhraseContext.Greeting) ?: text(R.string.pet_greeting))
        playSfx(Sfx.Win)
        savePet()
    }

    fun characterProfile(characterId: Int): CharacterProfile? = pet.characterProfile(characterId)

    fun resetPet() {
        backgroundSave = null
        backgroundedAt = 0L
        lastSaveAt = 0L
        _snapshot.value = null
        _screen.value = AppScreen.Home
        _shopItems.value = emptyList()
        _inventory.value = null
        _rpsResult.value = null
        _miniGame.value = null
        _battle.value = null
        _chatMessages.value = emptyList()
        _speech.value = null
        _systemMessage.value = null
        // Queued on the same single-thread dispatcher, so pending saves land
        // before the files are removed.
        saveScope.launch { saveStore.delete() }
    }

    fun openTab(tab: AppTab) {
        playSfx(Sfx.Tap)
        leaveRoundIfNeeded()
        history.clear()
        _screen.value = tab.screen
    }

    fun openHome() {
        leaveRoundIfNeeded()
        history.clear()
        _screen.value = AppScreen.Home
    }

    fun openFocus(screen: AppScreen) {
        if (_screen.value == screen) return
        history += _screen.value
        _screen.value = screen
    }

    fun openShop() {
        playSfx(Sfx.Tap)
        refreshEconomy()
        openFocus(AppScreen.Shop)
    }

    fun openSettings() {
        playSfx(Sfx.Tap)
        openFocus(AppScreen.Settings)
    }

    fun openHelp() {
        playSfx(Sfx.Tap)
        openFocus(AppScreen.Help)
    }

    fun goBack() {
        val current = _screen.value
        val target = backTarget(current, history.toList()) ?: return
        leaveRoundIfNeeded()
        if (target.tab != null) {
            history.clear()
        } else {
            val index = history.lastIndexOf(target)
            if (index >= 0) {
                history.subList(index, history.size).clear()
            }
        }
        _screen.value = target
    }

    fun openPet() {
        playSfx(Sfx.Tap)
        openTab(AppTab.Pet)
    }

    fun selectShopCategory(index: Int) {
        _shopCategory.value = index
    }

    private fun leaveRoundIfNeeded() {
        if (_screen.value == AppScreen.Rps || _screen.value == AppScreen.MiniGame) {
            pet.cancelPlay()
        }
        if (_screen.value == AppScreen.Battle) {
            pet.leaveBattle()
            _battle.value = null
            savePet()
        }
        _rpsResult.value = null
        _miniGame.value = null
    }

    fun startRps() {
        val result = pet.startPlay()
        refreshPet()
        val awake = _snapshot.value?.lifeState == LifeState.Awake
        if (result == ActionResult.Ok && awake) {
            _rpsResult.value = null
            openFocus(AppScreen.Rps)
            postSystemMessage(text(R.string.rps_prompt))
        } else {
            if (result == ActionResult.Ok) {
                pet.cancelPlay()
            }
            postSystemMessage(actionMessage(result))
        }
        playSfx(if (result == ActionResult.Ok && awake) Sfx.PlayStart else Sfx.Tap)
        savePet()
    }

    fun playRps(choice: Int) {
        if (_screen.value != AppScreen.Rps || _rpsResult.value != null) return
        val result = pet.playRps(choice)
        if (result == null) {
            openHome()
            postSystemMessage(text(R.string.rps_unavailable))
            return
        }
        _rpsResult.value = result
        refreshPet()
        refreshEconomy()
        postSystemMessage(
            if (result.outcome == RpsOutcome.Win) {
                quantity(R.plurals.rps_win, result.reward, result.reward)
            } else {
                text(if (result.outcome == RpsOutcome.Draw) R.string.rps_draw else R.string.rps_lose)
            },
        )
        playSfx(sfxForOutcome(result.outcome))
        savePet()
    }

    fun startMiniGame(kind: MiniGameKind, melodyLength: Int = 4) {
        val result = pet.startPlay()
        refreshPet()
        if (result != ActionResult.Ok || _snapshot.value?.lifeState != LifeState.Awake) {
            pet.cancelPlay()
            postSystemMessage(
                if (_snapshot.value?.lifeState == LifeState.Sleeping) {
                    text(R.string.not_enough_energy)
                } else {
                    actionMessage(result)
                },
            )
            playSfx(Sfx.Tap)
            savePet()
            return
        }
        _miniGame.value = miniGameEngine.start(kind, melodyLength)
        openFocus(AppScreen.MiniGame)
        postSystemMessage(text(R.string.minigame_started))
        playSfx(Sfx.PlayStart)
        savePet()
    }

    fun chooseMiniGame(choice: Int) {
        val current = _miniGame.value ?: return
        val next = miniGameEngine.choose(current, choice)
        _miniGame.value = next
        if (current.kind == MiniGameKind.Hearts) playSfx(Sfx.Tap)
        settleMiniGame(current, next)
    }

    private fun settleMiniGame(previous: MiniGameState, current: MiniGameState) {
        if (previous.finished || !current.finished) return
        if (!pet.finishGame(current.outcome, current.reward)) {
            postSystemMessage(text(R.string.round_finished))
            return
        }
        refreshPet()
        refreshEconomy()
        postSystemMessage(
            when {
                current.outcome > 0 -> quantity(R.plurals.rps_win, current.reward, current.reward)
                current.outcome < 0 -> text(R.string.rps_lose)
                else -> text(R.string.rps_draw)
            },
        )
        playSfx(sfxForOutcome(current.outcome))
        savePet()
    }

    fun startBattle(dungeon: Boolean) {
        val result = pet.startBattle(dungeon)
        refreshPet()
        if (result != ActionResult.Ok || _snapshot.value?.lifeState != LifeState.Awake) {
            pet.leaveBattle()
            postSystemMessage(
                if (_snapshot.value?.lifeState == LifeState.Sleeping) {
                    text(R.string.not_enough_energy_adventure)
                } else {
                    actionMessage(result)
                },
            )
            playSfx(Sfx.Tap)
        } else {
            _battle.value = pet.battleSnapshot()
            openFocus(AppScreen.Battle)
            postSystemMessage(
                text(if (dungeon) R.string.battle_started_dungeon else R.string.battle_started_adventure),
            )
            playSfx(Sfx.Encounter)
        }
        savePet()
    }

    fun battleAction(action: Int) {
        if (_screen.value != AppScreen.Battle) return
        val before = _battle.value
        val result = pet.battleAction(action) ?: return
        _battle.value = result
        refreshPet()
        _inventory.value = pet.inventory()
        playSfx(
            if (before == null) {
                Sfx.Encounter
            } else {
                sfxForBattleAction(
                    action = action,
                    previousFoeHp = before.foeHp,
                    foeHp = result.foeHp,
                    previousHp = before.hp,
                    hp = result.hp,
                    previousMp = before.mp,
                    mp = result.mp,
                    outcome = result.outcome,
                    awaitingRest = result.awaitingRest,
                    cleared = result.cleared,
                )
            },
        )
        if (result.over && !result.awaitingRest) {
            when (result.outcome) {
                BattleOutcome.Victory -> {
                    postSpeech(pet.phrase(PhraseContext.Victory) ?: text(R.string.battle_victory))
                }
                BattleOutcome.Defeat -> {
                    postSpeech(pet.phrase(PhraseContext.Fainted) ?: text(R.string.battle_fainted))
                }
                else -> postSystemMessage(text(R.string.battle_loot_saved))
            }
        }
        savePet()
    }

    fun nextDungeonRoom() {
        if (!pet.nextDungeonRoom()) return
        _battle.value = pet.battleSnapshot()
        refreshPet()
        playSfx(Sfx.Encounter)
        savePet()
    }

    fun allocateStat(stat: Int) {
        if (stat !in 0..4) return
        pet.allocateStat(stat)
        _inventory.value = pet.inventory()
        _battle.value = pet.battleSnapshot()
        refreshPet()
        savePet()
    }

    fun saveAssistantSettings(apiKey: String, speechEnabled: Boolean, latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            postSystemMessage(text(R.string.settings_invalid_coordinates))
            return false
        }
        settingsStore.edit()
            .putString("openrouter_key", apiKey.trim().take(300))
            .putBoolean("speech", speechEnabled)
            .putString("latitude", latitude.toString())
            .putString("longitude", longitude.toString())
            .putBoolean("location_configured", true)
            .apply()
        _assistantSettings.value = readAssistantSettings()
        postSystemMessage(text(R.string.settings_saved))
        playSfx(Sfx.Tap)
        return true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundPlayer.enabled = enabled
        _soundEnabled.value = enabled
        settingsStore.edit().putBoolean(SoundPreference, enabled).apply()
        // Confirm that turning sound back on actually works.
        if (enabled) playSfx(Sfx.Tap)
    }

    fun playNote(note: Int) {
        playSfx(sfxForNote(note))
    }

    fun submitChat(rawText: String) {
        val text = rawText.trim().take(2_000)
        if (text.isEmpty() || _chatBusy.value || _snapshot.value == null) return
        _chatMessages.value = (_chatMessages.value + ChatMessage(text, false)).takeLast(50)
        val intent = pet.parseIntent(text)
        if (intent == null) {
            answerChat(pet.phrase(PhraseContext.Talked) ?: text(R.string.pet_talked_fallback))
            return
        }
        when (intent.kind) {
            AssistantKind.SetAlarm -> {
                val at = nextAlarmAt(intent.hour, intent.minute, System.currentTimeMillis())
                reminders.schedule(at, text)
                answerChat(text(R.string.reminder_set, formatReminderTime(at)))
            }
            AssistantKind.Reminder -> {
                val at = System.currentTimeMillis() + intent.afterMinutes.coerceIn(1, 1_440) * 60_000L
                reminders.schedule(at, text)
                answerChat(text(R.string.reminder_heard, formatReminderTime(at)))
            }
            AssistantKind.Pomodoro -> {
                val at = System.currentTimeMillis() + 25 * 60_000L
                reminders.schedule(at, text(R.string.pomodoro_done))
                answerChat(text(R.string.pomodoro_started))
            }
            AssistantKind.Cancel -> {
                reminders.cancelAll()
                answerChat(text(R.string.reminders_cancelled))
            }
            AssistantKind.MissingTime -> answerChat(text(R.string.time_missing))
            AssistantKind.Joke -> answerChat(pet.joke())
            AssistantKind.Time -> answerChat(text(R.string.time_now, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())))
            AssistantKind.Convert -> answerChat(intent.conversion)
            AssistantKind.Weather -> loadWeather()
            else -> loadChatAnswer()
        }
    }

    private fun loadWeather() {
        val settings = _assistantSettings.value
        if (!settings.locationConfigured) {
            answerChat(text(R.string.weather_need_settings))
            return
        }
        _chatBusy.value = true
        viewModelScope.launch {
            val answer = try {
                withContext(Dispatchers.IO) {
                    assistantClient.weather(settings.latitude, settings.longitude)
                }
            } catch (_: Exception) {
                text(R.string.weather_failed)
            }
            _chatBusy.value = false
            answerChat(answer)
        }
    }

    private fun loadChatAnswer() {
        val settings = _assistantSettings.value
        val key = settingsStore.getString("openrouter_key", "").orEmpty()
        if (key.isBlank()) {
            answerChat(pet.phrase(PhraseContext.Talked) ?: text(R.string.chat_idle))
            return
        }
        val current = _snapshot.value ?: return
        _chatBusy.value = true
        viewModelScope.launch {
            val answer = try {
                withContext(Dispatchers.IO) {
                    assistantClient.chat(key, current.petName, current.characterName, _chatMessages.value)
                }
            } catch (_: Exception) {
                pet.phrase(PhraseContext.Talked)?.let { text(R.string.chat_offline, it) }
                    ?: text(R.string.chat_offline_fallback)
            }
            _chatBusy.value = false
            answerChat(answer)
        }
    }

    private fun answerChat(text: String) {
        _chatMessages.value = (_chatMessages.value + ChatMessage(text, true)).takeLast(50)
        playSfx(Sfx.Talk)
    }

    fun buyItem(itemId: Int) {
        val item = _shopItems.value.firstOrNull { it.id == itemId } ?: return
        val bought = pet.buy(itemId)
        _systemMessage.value = when {
            bought -> SystemMessage(messageCounter++, text(R.string.shop_bought, item.name))
            item.owned -> SystemMessage(messageCounter++, text(R.string.shop_already_bought))
            else -> SystemMessage(messageCounter++, text(R.string.shop_not_enough))
        }
        playSfx(if (bought) Sfx.Feed else Sfx.Tap)
        refreshPet()
        refreshEconomy()
        savePet()
    }

    fun equip(slot: Int, itemId: Int) {
        val changed = pet.equip(slot, itemId)
        _inventory.value = pet.inventory()
        _shopItems.value = pet.shop().toList()
        _systemMessage.value = when {
            !changed -> SystemMessage(messageCounter++, text(R.string.equip_failed))
            itemId >= 0 -> SystemMessage(messageCounter++, text(R.string.equip_done))
            else -> SystemMessage(messageCounter++, text(R.string.slot_freed))
        }
        playSfx(Sfx.Tap)
        savePet()
    }

    fun sell(itemId: Int) {
        val gold = pet.sell(itemId)
        _systemMessage.value = if (gold > 0) {
            SystemMessage(messageCounter++, quantity(R.plurals.sell, gold, gold))
        } else {
            SystemMessage(messageCounter++, text(R.string.sell_not_allowed))
        }
        playSfx(if (gold > 0) Sfx.Feed else Sfx.Tap)
        refreshPet()
        refreshEconomy()
        savePet()
    }

    fun performAction(action: Int) {
        val wasSick = _snapshot.value?.isSick == true
        val result = pet.action(action)
        refreshPet()
        val petNow = _snapshot.value
        // Wake and Medicine have no detailed native result, so their feedback
        // is derived from the actual state after the call.
        val feedback = careFeedback(
            action = action,
            wasSick = wasSick,
            lifeState = petNow?.lifeState ?: LifeState.Dead,
            isSick = petNow?.isSick == true,
        )
        val message: String? = when (feedback) {
            CareFeedback.Dead -> text(R.string.pet_left)
            CareFeedback.Woken -> text(R.string.pet_woken)
            CareFeedback.StillSleeping -> text(R.string.pet_still_sleeping)
            CareFeedback.MedicineTaken -> text(R.string.medicine_taken)
            CareFeedback.MedicineNotNeeded -> text(R.string.medicine_not_needed)
            null -> when (result) {
                ActionResult.Ok -> null
                ActionResult.RefusedDead -> text(R.string.pet_left)
                ActionResult.RefusedSleeping -> text(R.string.action_still_sleeping)
                ActionResult.RefusedCaprice -> text(R.string.action_caprice)
                else -> actionMessage(result)
            }
        }
        val speechText: String? = if (feedback == null && result == ActionResult.Ok) {
            val context = when (action) {
                PetAction.Feed -> PhraseContext.Fed
                PetAction.Wash -> PhraseContext.Washed
                PetAction.Clean -> PhraseContext.Toilet
                PetAction.Talk -> PhraseContext.Talked
                PetAction.Hug -> PhraseContext.Hugged
                else -> null
            }
            context?.let { pet.phrase(it) }
        } else {
            null
        }
        if (message != null) {
            _systemMessage.value = SystemMessage(messageCounter++, message)
        }
        if (speechText != null) {
            _speech.value = PetSpeech(messageCounter++, speechText)
        }
        playSfx(
            sfxForCareAction(
                action = action,
                result = result,
                wasSick = wasSick,
                lifeState = petNow?.lifeState ?: LifeState.Dead,
                isSick = petNow?.isSick == true,
            ),
        )
        savePet()
    }

    fun reportError(text: String) {
        _systemMessage.value = SystemMessage(messageCounter++, text)
    }

    fun savePet() = enqueueSave()

    fun flushSave() = enqueueSave()

    fun checkForUpdates() {
        if (_updateState.value !is UpdateUiState.Idle &&
            _updateState.value !is UpdateUiState.NoUpdate &&
            _updateState.value !is UpdateUiState.Error
        ) return

        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    updater.findLatestUpdate(BuildConfig.VERSION_NAME)
                }
                if (release == null) {
                    _updateState.value = UpdateUiState.NoUpdate
                } else {
                    _updateState.value = UpdateUiState.Available(release)
                }
            } catch (error: Exception) {
                _updateState.value = UpdateUiState.Error(
                    error.message ?: text(R.string.update_failed),
                )
            }
        }
    }

    fun downloadUpdate() {
        val available = _updateState.value as? UpdateUiState.Available ?: return
        _updateState.value = UpdateUiState.Downloading(available.release)
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    updater.download(available.release) { received, total ->
                        _updateState.value =
                            UpdateUiState.Downloading(available.release, received, total)
                    }
                }
                updater.install(apk)
                _updateState.value = UpdateUiState.Idle
                postSystemMessage(text(R.string.update_confirm_install))
            } catch (_: UnknownAppSourcesRequiredException) {
                _updateState.value = UpdateUiState.Error(
                    text(R.string.update_unknown_source),
                )
            } catch (error: Exception) {
                _updateState.value = UpdateUiState.Error(
                    error.message ?: text(R.string.update_failed),
                )
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    private fun enqueueSave() {
        val data = saveData() ?: return
        lastSaveAt = System.currentTimeMillis()
        saveScope.launch {
            if (!writeSave(data)) {
                withContext(Dispatchers.Main) {
                    postSystemMessage(text(R.string.save_failed))
                }
            }
        }
    }

    private fun saveData(): String? {
        if (_snapshot.value == null) return null
        val savedAt = System.currentTimeMillis()
        return "$SaveHeader $savedAt\n${pet.save()}"
    }

    private fun writeSave(data: String): Boolean {
        val output = try {
            saveStore.startWrite()
        } catch (_: Exception) {
            return false
        }
        return try {
            output.write(data.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            saveStore.finishWrite(output)
            true
        } catch (_: Exception) {
            saveStore.failWrite(output)
            false
        }
    }

    private fun loadSavedPet() {
        val data = try {
            saveStore.openRead().bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            // AtomicFile restores the .bak copy before throwing, if one exists.
            return
        } catch (_: Exception) {
            postSystemMessage(text(R.string.save_corrupted))
            return
        }
        val separator = data.indexOf('\n')
        val header = if (separator >= 0) data.substring(0, separator) else ""
        val savedAt = if (header.startsWith(SaveHeader)) {
            header.removePrefix(SaveHeader).trim().toLongOrNull() ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }
        val coreSave = if (separator >= 0) data.substring(separator + 1) else data
        if (!pet.load(coreSave, savedAt)) {
            postSystemMessage(text(R.string.save_corrupted))
            return
        }
        val now = System.currentTimeMillis()
        // Hunger is only known after parsing, so reload with a capped start
        // time: offline decay must not starve the pet to death.
        val effectiveStart = OfflineDecay.effectiveSaveTime(savedAt, now, pet.snapshot().hunger)
        if (effectiveStart != savedAt && !pet.load(coreSave, effectiveStart)) return
        pet.tick(now)
        refreshPet()
        postSystemMessage(text(R.string.pet_welcome_back))
    }

    private fun refreshPet() {
        val previous = _snapshot.value
        val next = pet.snapshot()
        _snapshot.value = next
        if (previous == null) return
        // Play state transitions once, not on every one-second tick.
        if (next.lifeState == LifeState.Dead && previous.lifeState != LifeState.Dead) {
            playSfx(Sfx.Death)
        } else if (next.isSick && !previous.isSick) {
            playSfx(Sfx.Sick)
        }
    }

    private fun playSfx(effect: Int?) {
        if (effect != null) soundPlayer.play(effect)
    }

    private fun refreshEconomy() {
        if (_snapshot.value == null) return
        _shopItems.value = pet.shop().toList()
        _inventory.value = pet.inventory()
    }

    private fun readAssistantSettings(): AssistantSettings = AssistantSettings(
        speechEnabled = settingsStore.getBoolean("speech", false),
        hasApiKey = !settingsStore.getString("openrouter_key", "").isNullOrBlank(),
        locationConfigured = settingsStore.getBoolean("location_configured", false),
        latitude = settingsStore.getString("latitude", "0")?.toDoubleOrNull() ?: 0.0,
        longitude = settingsStore.getString("longitude", "0")?.toDoubleOrNull() ?: 0.0,
    )

    private fun actionMessage(result: Int): String = when (result) {
        ActionResult.RefusedDead -> text(R.string.pet_left)
        ActionResult.RefusedSleeping -> text(R.string.action_still_sleeping)
        ActionResult.RefusedCaprice -> text(R.string.action_caprice)
        ActionResult.NotHungry -> text(R.string.action_already_full)
        ActionResult.TooTired -> text(R.string.action_low_energy)
        else -> text(R.string.action_unavailable)
    }

    private fun quantity(id: Int, quantity: Int, vararg formatArgs: Any): String =
        getApplication<Application>().resources.getQuantityString(id, quantity, *formatArgs)

    override fun onCleared() {
        // Enqueue the final save, then let the executor drain queued writes.
        // Cancelling the scope here would drop the save.
        flushSave()
        saveDispatcher.close()
        soundPlayer.close()
        pet.close()
        super.onCleared()
    }

    private companion object {
        const val SaveHeader = "VAIWU-ANDROID-1"
        const val SaveIntervalMs = 15_000L
        const val SoundPreference = "sound"
    }
}
