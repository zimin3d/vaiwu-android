package su.neit.vaiwu

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val release: ReleaseInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val pet = NativePet()
    private val saveStore = AtomicFile(File(application.filesDir, "pet.save"))
    private val updater = GitHubUpdateManager(application)
    private val saveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)
    private var lastSaveAt = 0L

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
            while (true) {
                delay(1_000)
                if (_snapshot.value != null) {
                    pet.tick(System.currentTimeMillis())
                    refreshPet()
                    if (System.currentTimeMillis() - lastSaveAt >= 15_000) {
                        savePet()
                    }
                }
            }
        }
    }

    fun createPet(characterId: Int) {
        pet.startNew(characterId, "", System.currentTimeMillis())
        refreshPet()
        _message.value = "Рада знакомству!"
        savePet()
    }

    fun resetPet() {
        flushSave()
        saveStore.delete()
        _snapshot.value = null
        _screen.value = AppScreen.Home
        _shopItems.value = emptyList()
        _inventory.value = null
        _rpsResult.value = null
        _message.value = "Выбери свою Vaiwu"
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
        if (result == ActionOk) {
            _rpsResult.value = null
            _screen.value = AppScreen.Rps
            _message.value = "Выбери камень, бумагу или ножницы"
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
            1 -> "Победа! +${result.reward} монеты"
            0 -> "Ничья"
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
        pet.equip(slot, itemId)
        _inventory.value = pet.inventory()
        _shopItems.value = pet.shop().toList()
        _message.value = if (itemId >= 0) "Снаряжение надето" else "Слот освобождён"
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
            action == 5 && result == 0 -> "Пора вставать!"
            action == 6 -> "Лекарство принято"
            result == ActionOk -> when (action) {
                0 -> "Вкусно!"
                1 -> "С тобой весело!"
                2 -> "Теперь я чистенькая"
                3 -> "Так-то лучше"
                4 -> "Мне есть что тебе сказать"
                7 -> "Мне тепло рядом с тобой"
                else -> ""
            }
            result == ActionRefusedDead -> "Питомец ушёл..."
            result == ActionRefusedSleeping -> "Тихо, она спит"
            result == ActionRefusedCaprice -> "Она капризничает"
            else -> actionMessage(result)
        }
        savePet()
    }

    fun savePet() {
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

    fun flushSave() {
        val data = saveData() ?: return
        val saved = runBlocking {
            withContext(saveDispatcher) { writeSave(data) }
        }
        if (saved) {
            lastSaveAt = System.currentTimeMillis()
        } else {
            _message.value = "Не удалось сохранить игру"
        }
    }

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
                val apk = withContext(Dispatchers.IO) { updater.download(available.release) }
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
        if (!saveStore.baseFile.exists()) return
        try {
            val data = saveStore.openRead().bufferedReader().use { it.readText() }
            val separator = data.indexOf('\n')
            val header = if (separator >= 0) data.substring(0, separator) else ""
            val savedAt = if (header.startsWith(SaveHeader)) {
                header.removePrefix(SaveHeader).trim().toLong()
            } else {
                System.currentTimeMillis()
            }
            val coreSave = if (separator >= 0) data.substring(separator + 1) else data
            if (pet.load(coreSave, savedAt)) {
                pet.tick(System.currentTimeMillis())
                refreshPet()
                _message.value = "С возвращением!"
            }
        } catch (_: Exception) {
            _message.value = "Сохранение повреждено, можно начать заново"
        }
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
        ActionRefusedDead -> "Питомец ушёл..."
        ActionRefusedSleeping -> "Тихо, она спит"
        ActionRefusedCaprice -> "Она капризничает"
        ActionNotHungry -> "Я уже сыта"
        ActionTooTired -> "Слишком мало энергии"
        else -> "Действие недоступно"
    }

    override fun onCleared() {
        flushSave()
        saveScope.cancel()
        saveDispatcher.close()
        pet.close()
        super.onCleared()
    }

    private companion object {
        const val SaveHeader = "VAIWU-ANDROID-1"
        const val ActionOk = 0
        const val ActionRefusedDead = 1
        const val ActionRefusedSleeping = 2
        const val ActionRefusedCaprice = 3
        const val ActionNotHungry = 4
        const val ActionTooTired = 5
    }
}
