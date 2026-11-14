package su.neit.vaiwu

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    private var lastSaveAt = 0L

    private val _snapshot = MutableStateFlow<PetSnapshot?>(null)
    val snapshot: StateFlow<PetSnapshot?> = _snapshot.asStateFlow()

    private val _message = MutableStateFlow("Выбери свою Vaiwu")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

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
        saveStore.delete()
        _snapshot.value = null
        _message.value = "Выбери свою Vaiwu"
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
            result == ActionNotHungry -> "Я уже сыта"
            result == ActionTooTired -> "Слишком мало энергии"
            else -> "Действие недоступно"
        }
        savePet()
    }

    fun savePet() {
        if (_snapshot.value == null) return
        val output = try {
            saveStore.startWrite()
        } catch (_: Exception) {
            return
        }
        try {
            val savedAt = System.currentTimeMillis()
            val data = "$SaveHeader $savedAt\n${pet.save()}"
            output.write(data.toByteArray(Charsets.UTF_8))
            output.fd.sync()
            saveStore.finishWrite(output)
            lastSaveAt = System.currentTimeMillis()
        } catch (_: Exception) {
            saveStore.failWrite(output)
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

    override fun onCleared() {
        savePet()
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
