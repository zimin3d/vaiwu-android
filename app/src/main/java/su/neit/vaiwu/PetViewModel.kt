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
import java.util.concurrent.Executors

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(
        val release: ReleaseInfo,
        val receivedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val pet = NativePet()
    private val saveStore = AtomicFile(File(application.filesDir, "pet.save"))
    private val updater = GitHubUpdateManager(application)
    private val saveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)
    private var lastSaveAt = 0L
    private var inForeground = true
    private var backgroundedAt = 0L
    private var backgroundSave: String? = null

    private val _snapshot = MutableStateFlow<PetSnapshot?>(null)
    val snapshot: StateFlow<PetSnapshot?> = _snapshot.asStateFlow()

    private val _message = MutableStateFlow("Выбери свою Vaiwu")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.Home)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _shopItems = MutableStateFlow<List<ShopItemSnapshot>>(emptyList())
    val shopItems: StateFlow<List<ShopItemSnapshot>> = _shopItems.asStateFlow()

    private val _inventory = MutableStateFlow<InventorySnapshot?>(null)
    val inventory: StateFlow<InventorySnapshot?> = _inventory.asStateFlow()

    private val _rpsResult = MutableStateFlow<RpsResult?>(null)
    val rpsResult: StateFlow<RpsResult?> = _rpsResult.asStateFlow()

    init {
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
            if (_screen.value == AppScreen.Rps) {
                _screen.value = AppScreen.Home
                _rpsResult.value = null
            }
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

    fun createPet(characterId: Int) {
        pet.startNew(characterId, "", System.currentTimeMillis())
        refreshPet()
        _message.value = "Рада знакомству!"
        savePet()
    }

    fun resetPet() {
        backgroundSave = null
        backgroundedAt = 0L
        lastSaveAt = 0L
        _snapshot.value = null
        _screen.value = AppScreen.Home
        _shopItems.value = emptyList()
        _inventory.value = null
        _rpsResult.value = null
        _message.value = "Выбери свою Vaiwu"
        // Queued on the same single-thread dispatcher, so pending saves land
        // before the files are removed.
        saveScope.launch { saveStore.delete() }
    }

    fun openHome() {
        if (_screen.value == AppScreen.Rps) {
            pet.cancelPlay()
        }
        _screen.value = AppScreen.Home
        _rpsResult.value = null
    }

    fun openShop() {
        refreshEconomy()
        _screen.value = AppScreen.Shop
    }

    fun openInventory() {
        _inventory.value = pet.inventory()
        _screen.value = AppScreen.Inventory
    }

    fun startRps() {
        val result = pet.startPlay()
        refreshPet()
        val awake = _snapshot.value?.lifeState == LifeState.Awake
        if (result == ActionResult.Ok && awake) {
            _rpsResult.value = null
            _screen.value = AppScreen.Rps
            _message.value = "Выбери камень, бумагу или ножницы"
        } else if (result == ActionResult.Ok) {
            pet.cancelPlay()
            _message.value = "Она слишком устала для игры"
        } else {
            _message.value = actionMessage(result)
        }
        savePet()
    }

    fun playRps(choice: Int) {
        if (_screen.value != AppScreen.Rps || _rpsResult.value != null) return
        val result = pet.playRps(choice)
        if (result == null) {
            _screen.value = AppScreen.Home
            _message.value = "Раунд уже завершён или питомец недоступен"
            return
        }
        _rpsResult.value = result
        refreshPet()
        refreshEconomy()
        _message.value = when (result.outcome) {
            RpsOutcome.Win -> "Победа! +${result.reward} монеты"
            RpsOutcome.Draw -> "Ничья"
            else -> "В этот раз не повезло"
        }
        savePet()
    }

    fun buyItem(itemId: Int) {
        val item = _shopItems.value.firstOrNull { it.id == itemId } ?: return
        val bought = pet.buy(itemId)
        _message.value = when {
            bought -> "Куплено: ${item.name}"
            item.owned -> "Эта вещь уже куплена"
            else -> "Не хватает монет"
        }
        refreshPet()
        refreshEconomy()
        savePet()
    }

    fun equip(slot: Int, itemId: Int) {
        val changed = pet.equip(slot, itemId)
        _inventory.value = pet.inventory()
        _shopItems.value = pet.shop().toList()
        _message.value = when {
            !changed -> "Не удалось изменить снаряжение"
            itemId >= 0 -> "Снаряжение надето"
            else -> "Слот освобождён"
        }
        savePet()
    }

    fun sell(itemId: Int) {
        val gold = pet.sell(itemId)
        _message.value = if (gold > 0) "Продано за $gold монет" else "Эту вещь нельзя продать"
        refreshPet()
        refreshEconomy()
        savePet()
    }

    fun performAction(action: Int) {
        val result = pet.action(action)
        refreshPet()
        _message.value = when {
            action == PetAction.Wake && result == ActionResult.Ok -> "Пора вставать!"
            action == PetAction.Medicine -> "Лекарство принято"
            result == ActionResult.Ok -> when (action) {
                PetAction.Feed -> "Вкусно!"
                PetAction.Wash -> "Теперь я чистенькая"
                PetAction.Clean -> "Так-то лучше"
                PetAction.Talk -> "Мне есть что тебе сказать"
                PetAction.Hug -> "Мне тепло рядом с тобой"
                else -> ""
            }
            result == ActionResult.RefusedDead -> "Питомец ушёл..."
            result == ActionResult.RefusedSleeping -> "Тихо, она спит"
            result == ActionResult.RefusedCaprice -> "Она капризничает"
            else -> actionMessage(result)
        }
        savePet()
    }

    fun savePet() = enqueueSave()

    fun flushSave() = enqueueSave()

    fun checkForUpdates() {
        if (_updateState.value !is UpdateUiState.Idle &&
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
                    _message.value = "Новых версий нет"
                } else {
                    _updateState.value = UpdateUiState.Available(release)
                }
            } catch (error: Exception) {
                _updateState.value = UpdateUiState.Error(
                    error.message ?: "Не удалось проверить обновления",
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
                _message.value = "Подтверди установку в системном окне"
            } catch (_: UnknownAppSourcesRequiredException) {
                _updateState.value = UpdateUiState.Error(
                    "Разреши установку из этого источника в настройках, затем повтори загрузку",
                )
            } catch (error: Exception) {
                _updateState.value = UpdateUiState.Error(
                    error.message ?: "Не удалось загрузить обновление",
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
                    _message.value = "Не удалось сохранить игру"
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
            _message.value = "Сохранение повреждено, можно начать заново"
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
            _message.value = "Сохранение повреждено, можно начать заново"
            return
        }
        val now = System.currentTimeMillis()
        // Hunger is only known after parsing, so reload with a capped start
        // time: offline decay must not starve the pet to death.
        val effectiveStart = OfflineDecay.effectiveSaveTime(savedAt, now, pet.snapshot().hunger)
        if (effectiveStart != savedAt && !pet.load(coreSave, effectiveStart)) return
        pet.tick(now)
        refreshPet()
        _message.value = "С возвращением!"
    }

    private fun refreshPet() {
        _snapshot.value = pet.snapshot()
    }

    private fun refreshEconomy() {
        if (_snapshot.value == null) return
        _shopItems.value = pet.shop().toList()
        _inventory.value = pet.inventory()
    }

    private fun actionMessage(result: Int): String = when (result) {
        ActionResult.RefusedDead -> "Питомец ушёл..."
        ActionResult.RefusedSleeping -> "Тихо, она спит"
        ActionResult.RefusedCaprice -> "Она капризничает"
        ActionResult.NotHungry -> "Я уже сыта"
        ActionResult.TooTired -> "Слишком мало энергии"
        else -> "Действие недоступно"
    }

    override fun onCleared() {
        // Enqueue the final save, then let the executor drain queued writes.
        // Cancelling the scope here would drop the save.
        flushSave()
        saveDispatcher.close()
        pet.close()
        super.onCleared()
    }

    private companion object {
        const val SaveHeader = "VAIWU-ANDROID-1"
        const val SaveIntervalMs = 15_000L
    }
}
