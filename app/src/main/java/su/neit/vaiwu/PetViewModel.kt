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
import su.neit.vaiwu.combat.CombatAction
import su.neit.vaiwu.combat.CombatActionException
import su.neit.vaiwu.combat.CombatEvent
import su.neit.vaiwu.combat.CombatState
import su.neit.vaiwu.combat.CombatWinner
import su.neit.vaiwu.combat.EncounterBuilder
import su.neit.vaiwu.combat.TacticalBattleSession
import su.neit.vaiwu.combat.TacticalCombat
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

/**
 * Main application ViewModel owning the native [NativePet] handle, periodic
 * simulation/mini-game tickers, atomic save persistence, tactical combat
 * sessions, assistant chat/reminders, and in-app updates.
 */
class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val pet = NativePet()
    private val saveStore = AtomicFile(File(application.filesDir, "pet.save"))
    private val updater = GitHubUpdateManager(application)
    private val assistantClient = AssistantClient(application)
    private val heroineStore = HeroineStore(application)
    private val heroineClient = HeroineClient()
    private val reminders = ReminderScheduler(application)
    private val settingsStore = application.getSharedPreferences("settings", 0)
    private val secretStore = SecretStore(settingsStore)
    private var heroinesCatalogEtag: String? = null
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

    private val _feedMenuOpen = MutableStateFlow(false)
    val feedMenuOpen: StateFlow<Boolean> = _feedMenuOpen.asStateFlow()

    private val _inventory = MutableStateFlow<InventorySnapshot?>(null)
    val inventory: StateFlow<InventorySnapshot?> = _inventory.asStateFlow()

    private val _rpsResult = MutableStateFlow<RpsResult?>(null)
    val rpsResult: StateFlow<RpsResult?> = _rpsResult.asStateFlow()

    private val miniGameEngine = MiniGameEngine()
    private val _miniGame = MutableStateFlow<MiniGameState?>(null)
    val miniGame: StateFlow<MiniGameState?> = _miniGame.asStateFlow()

    private val _battle = MutableStateFlow<BattleSnapshot?>(null)
    val battle: StateFlow<BattleSnapshot?> = _battle.asStateFlow()

    private val _tacticalBattle = MutableStateFlow<TacticalBattleSession?>(null)
    val tacticalBattle: StateFlow<TacticalBattleSession?> = _tacticalBattle.asStateFlow()

    private val _coachEnabled = MutableStateFlow(settingsStore.getBoolean(CoachPreference, true))
    val coachEnabled: StateFlow<Boolean> = _coachEnabled.asStateFlow()

    private val _battleFastMode = MutableStateFlow(settingsStore.getBoolean(FastModePreference, false))
    val battleFastMode: StateFlow<Boolean> = _battleFastMode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _assistantSettings = MutableStateFlow(readAssistantSettings())
    val assistantSettings: StateFlow<AssistantSettings> = _assistantSettings.asStateFlow()

    private val _heroinesState = MutableStateFlow(
        heroineStore.listInstalled().let { initialInstalled ->
            HeroinesUiState(
                hasAccessKey = secretStore.hasApiKey(),
                installed = initialInstalled,
                previewPaths = collectLocalPreviewPaths(emptyList(), initialInstalled),
                activeHeroineId = heroineStore.getActiveHeroineId(),
            )
        },
    )
    val heroinesState: StateFlow<HeroinesUiState> = _heroinesState.asStateFlow()

    private val _soundEnabled = MutableStateFlow(settingsStore.getBoolean(SoundPreference, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _hour = MutableStateFlow(currentHour())
    val hour: StateFlow<Int> = _hour.asStateFlow()

    init {
        soundPlayer.enabled = _soundEnabled.value
        loadSavedPet()
        viewModelScope.launch {
            while (isActive) {
                delay(PetTickIntervalMs)
                if (!inForeground || _snapshot.value == null) continue
                val now = System.currentTimeMillis()
                _hour.value = currentHour()
                pet.tick(now)
                refreshPet()
                if (now - lastSaveAt >= SaveIntervalMs) {
                    savePet()
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(MiniGameTickIntervalMs)
                val current = _miniGame.value ?: continue
                if (!inForeground || current.finished) continue
                val next = miniGameEngine.tick(current, MiniGameTickIntervalMs.toInt())
                _miniGame.value = next
                settleMiniGame(current, next)
            }
        }
    }

    /** Resumes simulation when the activity enters the foreground, applying capped offline decay. */
    fun onForeground() {
        _hour.value = currentHour()
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
            _tacticalBattle.value = null
        }
    }

    /** Snapshots and saves the pet when the activity leaves the foreground. */
    fun onBackground() {
        if (!inForeground) return
        inForeground = false
        backgroundedAt = System.currentTimeMillis()
        backgroundSave = if (_snapshot.value != null) pet.save() else null
        if (backgroundSave != null) {
            savePet()
        }
    }

    /** Creates a new pet for [characterId] with sanitized [name] (up to [MaxPetNameLength] chars). */
    fun createPet(characterId: Int, name: String = "") {
        val profile = pet.characterProfile(characterId)
        val safeName = name.filter { it != '\n' && it != '\r' }.take(MaxPetNameLength).trim()
        val petName = safeName.ifBlank { profile?.name.orEmpty() }
        pet.startNew(characterId, petName, System.currentTimeMillis())
        refreshPet()
        refreshEconomy()
        postSpeech(phraseFor(PhraseContext.Greeting) ?: text(R.string.pet_greeting))
        playSfx(Sfx.Win)
        savePet()
    }

    /** Fetches static character traits and starting RPG stats for onboarding cards. */
    fun characterProfile(characterId: Int): CharacterProfile? = pet.characterProfile(characterId)

    /** Clears all in-memory state and deletes the persisted save file so the user returns to onboarding. */
    fun resetPet() {
        backgroundSave = null
        backgroundedAt = 0L
        lastSaveAt = 0L
        _snapshot.value = null
        _screen.value = AppScreen.Home
        _shopItems.value = emptyList()
        _inventory.value = null
        _feedMenuOpen.value = false
        _rpsResult.value = null
        _miniGame.value = null
        _battle.value = null
        _tacticalBattle.value = null
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
        // The Pet tab shows the carried items, so make sure the bag snapshot
        // is fresh even if the shop was never opened in this session.
        if (tab.screen == AppScreen.Pet) {
            refreshEconomy()
        }
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

    /** Opens the feed menu (free rice + paid dishes, like the ESP firmware). */
    fun openFeedMenu() {
        if (_snapshot.value?.lifeState != LifeState.Awake) return
        playSfx(Sfx.Tap)
        refreshEconomy()
        _feedMenuOpen.value = true
    }

    fun closeFeedMenu() {
        _feedMenuOpen.value = false
    }

    fun openSettings() {
        playSfx(Sfx.Tap)
        openFocus(AppScreen.Settings)
    }

    fun openHeroines() {
        playSfx(Sfx.Tap)
        val installed = heroineStore.listInstalled()
        val catalog = _heroinesState.value.catalog
        _heroinesState.value = _heroinesState.value.copy(
            hasAccessKey = secretStore.hasApiKey(),
            installed = installed,
            previewPaths = collectLocalPreviewPaths(catalog, installed),
            activeHeroineId = heroineStore.getActiveHeroineId(),
            errorMessage = null,
        )
        openFocus(AppScreen.Heroines)
        if (secretStore.hasApiKey()) {
            if (catalog.isEmpty()) {
                refreshHeroinesCatalog()
            } else {
                prefetchCatalogPreviews(secretStore.readApiKey(), catalog)
            }
        }
    }

    fun refreshHeroinesCatalog() {
        if (_heroinesState.value.loading) return
        val key = secretStore.readApiKey()
        if (key.isBlank()) {
            _heroinesState.value = _heroinesState.value.copy(
                hasAccessKey = false,
                errorMessage = text(R.string.heroines_key_required),
            )
            return
        }
        _heroinesState.value = _heroinesState.value.copy(
            loading = true,
            hasAccessKey = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    heroineClient.fetchCatalog(key, heroinesCatalogEtag)
                }
            } catch (error: Exception) {
                CatalogFetchResult.Error(error.message ?: text(R.string.heroines_network_error))
            }
            when (result) {
                is CatalogFetchResult.Updated -> {
                    heroinesCatalogEtag = result.etag
                    val installed = heroineStore.listInstalled()
                    _heroinesState.value = _heroinesState.value.copy(
                        loading = false,
                        catalog = result.heroines,
                        installed = installed,
                        previewPaths = collectLocalPreviewPaths(result.heroines, installed),
                        activeHeroineId = heroineStore.getActiveHeroineId(),
                        errorMessage = null,
                    )
                    prefetchCatalogPreviews(key, result.heroines)
                }
                CatalogFetchResult.NotModified -> {
                    val current = _heroinesState.value
                    _heroinesState.value = current.copy(
                        loading = false,
                        previewPaths = collectLocalPreviewPaths(current.catalog, current.installed),
                        errorMessage = null,
                    )
                    prefetchCatalogPreviews(key, current.catalog)
                }
                is CatalogFetchResult.AuthError -> {
                    _heroinesState.value = _heroinesState.value.copy(
                        loading = false,
                        errorMessage = text(R.string.heroines_auth_error, result.httpCode),
                    )
                }
                is CatalogFetchResult.Error -> {
                    _heroinesState.value = _heroinesState.value.copy(
                        loading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    private fun collectLocalPreviewPaths(
        catalog: List<RemoteHeroine>,
        installed: List<InstalledHeroine>,
    ): Map<String, String> {
        val ids = buildSet {
            catalog.forEach { add(it.id) }
            installed.forEach { add(it.id) }
        }
        return buildMap {
            for (id in ids) {
                val installedPreview = heroineStore.installedPreviewFile(id)
                if (installedPreview != null) {
                    put(id, installedPreview.absolutePath)
                    continue
                }
                val cached = heroineStore.cachedPreviewFile(id)
                if (cached.isFile && cached.length() > 0L) {
                    put(id, cached.absolutePath)
                }
            }
        }
    }

    private fun prefetchCatalogPreviews(apiKey: String, catalog: List<RemoteHeroine>) {
        if (apiKey.isBlank() || catalog.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = mutableMapOf<String, String>()
            for (heroine in catalog) {
                if (heroine.previewUrl.isNullOrBlank()) continue
                if (heroineStore.installedPreviewFile(heroine.id) != null) continue
                val dest = heroineStore.cachedPreviewFile(heroine.id)
                if (dest.isFile && dest.length() > 0L) {
                    updated[heroine.id] = dest.absolutePath
                    continue
                }
                if (heroineClient.downloadPreview(apiKey, heroine, dest)) {
                    updated[heroine.id] = dest.absolutePath
                }
            }
            if (updated.isNotEmpty()) {
                val merged = _heroinesState.value.previewPaths + updated
                _heroinesState.value = _heroinesState.value.copy(previewPaths = merged)
            }
        }
    }

    fun downloadHeroine(heroine: RemoteHeroine) {
        if (_heroinesState.value.downloadingId != null) return
        val key = secretStore.readApiKey()
        if (key.isBlank()) {
            postSystemMessage(text(R.string.heroines_key_required))
            return
        }
        _heroinesState.value = _heroinesState.value.copy(
            downloadingId = heroine.id,
            downloadProgress = null,
            errorMessage = null,
        )
        viewModelScope.launch {
            val tmpZip = File(getApplication<Application>().cacheDir, "heroine_${heroine.id}.zip")
            try {
                val installed = withContext(Dispatchers.IO) {
                    val expectedSha = heroineClient.downloadPack(key, heroine, tmpZip) { received, total ->
                        val fraction = if (total > 0L) (received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                        _heroinesState.value = _heroinesState.value.copy(downloadProgress = fraction)
                    }
                    heroineStore.installPack(tmpZip, heroine.id, expectedSha)
                }
                clearSpriteBitmapCache()
                val installedList = heroineStore.listInstalled()
                _heroinesState.value = _heroinesState.value.copy(
                    downloadingId = null,
                    downloadProgress = null,
                    installed = installedList,
                    previewPaths = collectLocalPreviewPaths(_heroinesState.value.catalog, installedList),
                    activeHeroineId = heroineStore.getActiveHeroineId(),
                )
                postSystemMessage(text(R.string.heroines_installed_ok, installed.name))
                playSfx(Sfx.Win)
            } catch (error: Exception) {
                _heroinesState.value = _heroinesState.value.copy(
                    downloadingId = null,
                    downloadProgress = null,
                    errorMessage = error.message ?: text(R.string.heroines_download_failed),
                )
                postSystemMessage(text(R.string.heroines_download_failed))
            } finally {
                tmpZip.delete()
            }
        }
    }

    fun activateHeroine(heroineId: String?) {
        heroineStore.setActiveHeroineId(heroineId)
        clearSpriteBitmapCache()
        val active = heroineStore.getActiveHeroineId()
        _heroinesState.value = _heroinesState.value.copy(activeHeroineId = active)
        postSystemMessage(
            if (active == null) {
                text(R.string.heroines_switched_builtin)
            } else {
                val name = _heroinesState.value.installed.firstOrNull { it.id == active }?.name ?: active
                text(R.string.heroines_switched_custom, name)
            },
        )
        if (active != null) {
            heroineStore.loadDialogues(active)
                ?.phrases
                ?.get(phraseContextKey(PhraseContext.Greeting))
                ?.takeIf { it.isNotEmpty() }
                ?.random()
                ?.let { postSpeech(it) }
        }
        playSfx(Sfx.Tap)
    }

    private fun activePackDialogues(): HeroineDialogues? {
        val activeId = _heroinesState.value.activeHeroineId ?: heroineStore.getActiveHeroineId() ?: return null
        return heroineStore.loadDialogues(activeId)
    }

    private fun phraseFor(context: Int): String? {
        val key = phraseContextKey(context)
        val custom = if (key != null) {
            activePackDialogues()?.phrases?.get(key)?.takeIf { it.isNotEmpty() }?.random()
        } else {
            null
        }
        return custom ?: pet.phrase(context)
    }

    fun deleteHeroine(heroineId: String) {
        heroineStore.deleteHeroine(heroineId)
        clearSpriteBitmapCache()
        _heroinesState.value = _heroinesState.value.copy(
            installed = heroineStore.listInstalled(),
            activeHeroineId = heroineStore.getActiveHeroineId(),
        )
        postSystemMessage(text(R.string.heroines_deleted))
        playSfx(Sfx.Tap)
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
            _tacticalBattle.value = null
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

    /** Starts a new mini-game round of [kind] after paying the upfront energy cost. */
    fun startMiniGame(kind: MiniGameKind, melodyLength: Int = MELODY_DEFAULT_LENGTH) {
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

    /** Launches a new tactical Adventure (`dungeon = false`) or multi-room Dungeon (`dungeon = true`). */
    fun startBattle(dungeon: Boolean) {
        val result = pet.startPlay()
        refreshPet()
        val currentPet = _snapshot.value
        if (result != ActionResult.Ok || currentPet == null || currentPet.lifeState != LifeState.Awake) {
            if (result == ActionResult.Ok) {
                pet.cancelPlay()
            }
            postSystemMessage(
                if (currentPet?.lifeState == LifeState.Sleeping) {
                    text(R.string.not_enough_energy_adventure)
                } else {
                    actionMessage(result)
                },
            )
            playSfx(Sfx.Tap)
        } else {
            val inv = pet.inventory()
            _inventory.value = inv
            val hero = EncounterBuilder.heroBuildFromSnapshots(currentPet, inv)
            _tacticalBattle.value = EncounterBuilder.startSession(
                hero = hero,
                dungeon = dungeon,
                seed = System.nanoTime(),
            )
            _battle.value = null
            openFocus(AppScreen.Battle)
            postSystemMessage(
                text(if (dungeon) R.string.battle_started_dungeon else R.string.battle_started_adventure),
            )
            playSfx(Sfx.Encounter)
        }
        savePet()
    }

    /** Executes a player tactical combat action, resolves enemy AI turns, and settles the room if finished. */
    fun performTacticalAction(action: CombatAction) {
        if (_screen.value != AppScreen.Battle) return
        val session = _tacticalBattle.value ?: return
        if (session.isOver || !session.state.isPlayerTurn) return

        val nextState = try {
            TacticalCombat.performAndResolve(session.state, action)
        } catch (_: CombatActionException) {
            playSfx(Sfx.Tap)
            return
        }

        if (nextState.winner != null) {
            _tacticalBattle.value = settleTacticalRoom(session, nextState)
            refreshPet()
            refreshEconomy()
            savePet()
        } else {
            val nextUndo = if (action is CombatAction.EndTurn) {
                emptyList()
            } else {
                session.undoStack + session.state
            }
            _tacticalBattle.value = session.copy(state = nextState, undoStack = nextUndo)
        }
    }

    fun undoTacticalAction() {
        val session = _tacticalBattle.value ?: return
        if (session.isOver || session.undoStack.isEmpty()) return
        val previous = session.undoStack.last().copy(events = emptyList())
        _tacticalBattle.value = session.copy(
            state = previous,
            undoStack = session.undoStack.dropLast(1),
        )
        playSfx(Sfx.Tap)
    }

    fun setCoachEnabled(enabled: Boolean) {
        _coachEnabled.value = enabled
        settingsStore.edit().putBoolean(CoachPreference, enabled).apply()
        playSfx(Sfx.Tap)
    }

    fun setBattleFastMode(fast: Boolean) {
        _battleFastMode.value = fast
        settingsStore.edit().putBoolean(FastModePreference, fast).apply()
        playSfx(Sfx.Tap)
    }

    fun playCombatEventSfx(event: CombatEvent) {
        playSfx(sfxForCombatEvent(event))
    }

    /** Commits a finished tactical room's HP/MP, potions, XP, gold, and dungeon chest rewards to native state. */
    private fun settleTacticalRoom(
        session: TacticalBattleSession,
        finished: CombatState,
    ): TacticalBattleSession {
        val player = finished.player
        val winner = finished.winner ?: return session
        return when (winner) {
            CombatWinner.Player -> {
                val isFinalDungeonRoom = session.dungeon && session.room >= session.rooms
                val chest = if (isFinalDungeonRoom) {
                    EncounterBuilder.computeDungeonChestGold(
                        rooms = session.rooms,
                        seed = finished.rng.seed xor DungeonChestSeedSalt,
                    )
                } else {
                    0
                }
                val goldTotal = finished.goldDelta + chest
                val applyPlayBonus = !session.dungeon || isFinalDungeonRoom
                val levels = pet.settleTacticalBattle(
                    outcome = BattleOutcome.Victory,
                    hpLeft = player.hp,
                    mpLeft = player.mp,
                    potionsLeft = finished.hpPotions,
                    mpPotionsLeft = finished.mpPotions,
                    xpGained = finished.xpEarned,
                    goldDelta = goldTotal,
                    applyPlayOutcome = applyPlayBonus,
                ).coerceAtLeast(0)

                if (applyPlayBonus) {
                    postSpeech(phraseFor(PhraseContext.Victory) ?: text(R.string.battle_victory))
                }
                session.copy(
                    state = finished,
                    undoStack = emptyList(),
                    awaitingRest = session.dungeon && !isFinalDungeonRoom,
                    dungeonCleared = isFinalDungeonRoom,
                    chestGold = chest,
                    bankedGold = session.bankedGold + goldTotal,
                    bankedXp = session.bankedXp + finished.xpEarned,
                    totalXpThisRoom = finished.xpEarned,
                    totalGoldThisRoom = goldTotal,
                    levelsGainedThisRoom = levels,
                    settled = true,
                )
            }
            CombatWinner.Enemy -> {
                val defeatPenalty = -(DefeatGoldPenaltyBase + player.level * DefeatGoldPenaltyPerLevel)
                val netGold = (finished.goldDelta + defeatPenalty).coerceAtMost(0)
                pet.settleTacticalBattle(
                    outcome = BattleOutcome.Defeat,
                    hpLeft = 0,
                    mpLeft = player.mp,
                    potionsLeft = finished.hpPotions,
                    mpPotionsLeft = finished.mpPotions,
                    xpGained = 0,
                    goldDelta = netGold,
                    applyPlayOutcome = true,
                )
                postSpeech(phraseFor(PhraseContext.Fainted) ?: text(R.string.battle_fainted))
                session.copy(
                    state = finished,
                    undoStack = emptyList(),
                    awaitingRest = false,
                    dungeonCleared = false,
                    totalXpThisRoom = 0,
                    totalGoldThisRoom = netGold,
                    levelsGainedThisRoom = 0,
                    settled = true,
                )
            }
            CombatWinner.Escaped -> {
                pet.settleTacticalBattle(
                    outcome = BattleOutcome.Escaped,
                    hpLeft = player.hp.coerceAtLeast(1),
                    mpLeft = player.mp,
                    potionsLeft = finished.hpPotions,
                    mpPotionsLeft = finished.mpPotions,
                    xpGained = finished.xpEarned,
                    goldDelta = finished.goldDelta,
                    applyPlayOutcome = false,
                )
                postSystemMessage(text(R.string.battle_loot_saved))
                session.copy(
                    state = finished,
                    undoStack = emptyList(),
                    awaitingRest = false,
                    dungeonCleared = false,
                    totalXpThisRoom = finished.xpEarned,
                    totalGoldThisRoom = finished.goldDelta,
                    levelsGainedThisRoom = 0,
                    settled = true,
                )
            }
        }
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
                    postSpeech(phraseFor(PhraseContext.Victory) ?: text(R.string.battle_victory))
                }
                BattleOutcome.Defeat -> {
                    postSpeech(phraseFor(PhraseContext.Fainted) ?: text(R.string.battle_fainted))
                }
                else -> postSystemMessage(text(R.string.battle_loot_saved))
            }
        }
        savePet()
    }

    /** Rests between dungeon rooms (native HP/MP recovery) and builds the next room's combat encounter. */
    fun nextDungeonRoom() {
        val session = _tacticalBattle.value ?: return
        if (!session.dungeon || !session.awaitingRest || session.dungeonCleared) return
        if (!pet.restDungeonRoom()) return
        refreshPet()
        val currentPet = _snapshot.value ?: return
        val inv = pet.inventory()
        _inventory.value = inv
        val nextRoom = session.room + 1
        val hero = EncounterBuilder.heroBuildFromSnapshots(currentPet, inv)
        val nextState = EncounterBuilder.buildRoomCombat(
            hero = hero,
            dungeon = true,
            room = nextRoom,
            rooms = session.rooms,
            seed = System.nanoTime() xor (nextRoom.toLong() * DungeonRoomSeedSalt),
        )
        _tacticalBattle.value = session.copy(
            room = nextRoom,
            isBossRoom = nextRoom >= session.rooms,
            state = nextState,
            undoStack = emptyList(),
            awaitingRest = false,
            settled = false,
            totalXpThisRoom = 0,
            totalGoldThisRoom = 0,
            levelsGainedThisRoom = 0,
        )
        playSfx(Sfx.Encounter)
        savePet()
    }

    /** Spends one unallocated RPG level-up stat point on attribute [stat] (0=STR..4=LUCK). */
    fun allocateStat(stat: Int) {
        if (stat !in RpgStatRange) return
        pet.allocateStat(stat)
        _inventory.value = pet.inventory()
        _battle.value = pet.battleSnapshot()
        refreshPet()
        savePet()
    }

    fun setSpeechEnabled(enabled: Boolean) {
        settingsStore.edit().putBoolean("speech", enabled).apply()
        _assistantSettings.value = readAssistantSettings()
        playSfx(Sfx.Tap)
    }

    fun clearApiKey() {
        secretStore.writeApiKey("")
        heroinesCatalogEtag = null
        _assistantSettings.value = readAssistantSettings()
        _heroinesState.value = _heroinesState.value.copy(hasAccessKey = false)
        postSystemMessage(text(R.string.settings_api_cleared))
        playSfx(Sfx.Tap)
    }

    fun saveAssistantSettings(apiKey: String, latitudeRaw: String, longitudeRaw: String): Boolean {
        val latitude = parseCoordinate(latitudeRaw)
        val longitude = parseCoordinate(longitudeRaw)
        if (latitude == null || longitude == null || !isValidCoordinates(latitude, longitude)) {
            postSystemMessage(text(R.string.settings_invalid_coordinates))
            return false
        }
        val nextKey = resolveApiKeyUpdate(
            input = apiKey,
            clearRequested = false,
            existing = secretStore.readApiKey(),
        )
        if (apiKey.isNotBlank()) {
            if (!secretStore.writeApiKey(nextKey)) {
                postSystemMessage(text(R.string.settings_api_save_failed))
                return false
            }
        }
        settingsStore.edit()
            .putString("latitude", latitude.toString())
            .putString("longitude", longitude.toString())
            .putBoolean("location_configured", true)
            .apply()
        _assistantSettings.value = readAssistantSettings()
        _heroinesState.value = _heroinesState.value.copy(hasAccessKey = secretStore.hasApiKey())
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

    /** Parses a user chat message for local intents (alarms, timers, weather, jokes) or sends it to OpenRouter. */
    fun submitChat(rawText: String) {
        val text = rawText.trim().take(MaxChatInputChars)
        if (text.isEmpty() || _chatBusy.value || _snapshot.value == null) return
        _chatMessages.value = (_chatMessages.value + ChatMessage(text, false, messageCounter++)).takeLast(MaxChatHistorySize)
        val intent = pet.parseIntent(text)
        if (intent == null) {
            answerChat(phraseFor(PhraseContext.Talked) ?: text(R.string.pet_talked_fallback))
            return
        }
        when (intent.kind) {
            AssistantKind.SetAlarm -> {
                val at = nextAlarmAt(intent.hour, intent.minute, System.currentTimeMillis())
                reminders.schedule(at, text)
                answerChat(text(R.string.reminder_set, formatReminderTime(at)))
            }
            AssistantKind.Reminder -> {
                val at = System.currentTimeMillis() +
                    intent.afterMinutes.coerceIn(1, MaxReminderMinutes) * MsPerMinute
                reminders.schedule(at, text)
                answerChat(text(R.string.reminder_heard, formatReminderTime(at)))
            }
            AssistantKind.Pomodoro -> {
                val at = System.currentTimeMillis() + PomodoroMinutes * MsPerMinute
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
        val key = secretStore.readApiKey()
        if (key.isBlank()) {
            answerChat(phraseFor(PhraseContext.Talked) ?: text(R.string.chat_idle))
            return
        }
        val current = _snapshot.value ?: return
        val extraPrompt = activePackDialogues()?.systemPrompt
        _chatBusy.value = true
        viewModelScope.launch {
            val answer = try {
                withContext(Dispatchers.IO) {
                    assistantClient.chat(
                        apiKey = key,
                        petName = current.petName,
                        characterName = current.characterName,
                        messages = _chatMessages.value,
                        extraSystemPrompt = extraPrompt,
                    )
                }
            } catch (_: Exception) {
                if (!isActive) return@launch
                phraseFor(PhraseContext.Talked)?.let { text(R.string.chat_offline, it) }
                    ?: text(R.string.chat_offline_fallback)
            }
            _chatBusy.value = false
            answerChat(answer)
        }
    }

    private fun answerChat(text: String) {
        _chatMessages.value = (_chatMessages.value + ChatMessage(text, true, messageCounter++)).takeLast(MaxChatHistorySize)
        playSfx(Sfx.Talk)
    }

    /** Purchases one shop item into the bag (or unlocks equipment) and posts status feedback. */
    fun buyItem(itemId: Int) {
        val item = _shopItems.value.firstOrNull { it.id == itemId } ?: return
        val bought = pet.buy(itemId)
        _systemMessage.value = when {
            bought && item.kind == ShopItemKind.Consumable ->
                SystemMessage(messageCounter++, text(R.string.shop_bought_bag, item.name))
            bought -> SystemMessage(messageCounter++, text(R.string.shop_bought, item.name))
            item.owned -> SystemMessage(messageCounter++, text(R.string.shop_already_bought))
            item.kind == ShopItemKind.Consumable && item.count >= MaxConsumableStack ->
                SystemMessage(messageCounter++, text(R.string.shop_bag_full))
            else -> SystemMessage(messageCounter++, text(R.string.shop_not_enough))
        }
        playSfx(if (bought) Sfx.Feed else Sfx.Tap)
        refreshPet()
        refreshEconomy()
        savePet()
    }

    /** Feed from the menu: `itemId < 0` is the free rice, otherwise a dish. */
    fun feedWith(itemId: Int) {
        val result = pet.feedWith(itemId)
        _feedMenuOpen.value = false
        val message: String? = when (result) {
            UseResult.Ok -> null
            UseResult.NotOwned -> text(R.string.item_not_owned)
            UseResult.NotApplicable -> text(R.string.item_not_applicable)
            UseResult.NoCoins -> text(R.string.shop_not_enough)
            UseResult.RefusedDead,
            UseResult.FreeRiceRefusedDead -> text(R.string.pet_left)
            UseResult.FreeRiceRefusedSleeping -> text(R.string.action_still_sleeping)
            UseResult.FreeRiceRefusedCaprice -> text(R.string.action_caprice)
            UseResult.FreeRiceNotHungry -> text(R.string.action_already_full)
            else -> text(R.string.action_unavailable)
        }
        val speechText = if (result == UseResult.Ok) phraseFor(PhraseContext.Fed) else null
        if (message != null) {
            _systemMessage.value = SystemMessage(messageCounter++, message)
        }
        if (speechText != null) {
            _speech.value = PetSpeech(messageCounter++, speechText)
        }
        playSfx(if (result == UseResult.Ok) Sfx.Feed else Sfx.Tap)
        refreshPet()
        refreshEconomy()
        savePet()
    }

    /** Use a carried consumable from the bag. */
    fun useItem(itemId: Int) {
        val item = _shopItems.value.firstOrNull { it.id == itemId } ?: return
        val result = pet.useItem(itemId)
        val message = when (result) {
            UseResult.Ok -> text(R.string.item_used, item.name)
            UseResult.NotOwned -> text(R.string.item_not_owned)
            UseResult.NotApplicable -> text(R.string.item_not_applicable)
            UseResult.RefusedDead -> text(R.string.pet_left)
            else -> text(R.string.action_unavailable)
        }
        _systemMessage.value = SystemMessage(messageCounter++, message)
        playSfx(if (result == UseResult.Ok) Sfx.Feed else Sfx.Tap)
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
            CareFeedback.FellAsleep -> text(R.string.pet_fell_asleep)
            CareFeedback.NotTired -> text(R.string.pet_not_tired)
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
            context?.let { phraseFor(it) }
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
                    _updateState.value = UpdateUiState.Idle
                    postSystemMessage(text(R.string.update_none))
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
        refreshEconomy()
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
        hasApiKey = secretStore.hasApiKey(),
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
        super.onCleared()
        pet.close()
    }

    private companion object {
        const val SaveHeader = "VAIWU-ANDROID-1"
        const val SaveIntervalMs = 15_000L
        const val PetTickIntervalMs = 1_000L
        const val MiniGameTickIntervalMs = 100L
        const val MsPerMinute = 60_000L
        const val MaxPetNameLength = 24
        const val MaxChatInputChars = 2_000
        const val MaxChatHistorySize = 50
        const val MaxReminderMinutes = 1_440 // 24 hours
        const val PomodoroMinutes = 25
        const val MaxConsumableStack = 99
        val RpgStatRange = 0..4
        /** Defeat gold loss formula (`5 + level * 2`), mirroring native RPG defeat penalty. */
        const val DefeatGoldPenaltyBase = 5
        const val DefeatGoldPenaltyPerLevel = 2
        /** Murmur/golden-ratio mixing salts so dungeon rooms and chest rolls diverge cleanly. */
        const val DungeonChestSeedSalt = 0x85EBCA6BL
        const val DungeonRoomSeedSalt = 0x9E3779B9L
        const val SoundPreference = "sound"
        const val CoachPreference = "tactical_coach"
        const val FastModePreference = "tactical_fast"
    }
}
