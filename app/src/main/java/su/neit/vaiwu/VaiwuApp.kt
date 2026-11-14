package su.neit.vaiwu

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val characterIds = listOf("aoi", "hina", "rin", "yuki", "akane", "unicorn", "kolobok")
private val characterNames = listOf("Aoi", "Hina", "Rin", "Yuki", "Akane", "Uni", "Kolobok")
private val emotionIds = listOf("idle", "happy", "sad", "angry", "sleepy", "sick", "dead")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaiwuApp(viewModel: PetViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

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
                    PetHome(
                        pet = currentPet,
                        onAction = viewModel::performAction,
                        onReset = viewModel::resetPet,
                    )
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
                        AssetImage(spriteAsset(index, 0), name)
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PetHome(pet: PetSnapshot, onAction: (Int) -> Unit, onReset: () -> Unit) {
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
        AssetImage(spriteAsset(pet.characterId, pet.emotionId), pet.petName, large = true)
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
                ActionRow("Покормить", 0, "Поиграть", 1, onAction)
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
private fun AssetImage(assetName: String, description: String, large: Boolean = false) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(assetName) {
        runCatching {
            context.assets.open(assetName).use { stream ->
                BitmapFactory.decodeStream(stream).asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = description,
            modifier = if (large) Modifier.size(144.dp, 192.dp) else Modifier.size(96.dp, 128.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun spriteAsset(characterId: Int, emotionId: Int): String {
    val character = characterIds.getOrElse(characterId) { characterIds.first() }
    val emotion = emotionIds.getOrElse(emotionId) { emotionIds.first() }
    return "sprite_${character}_${emotion}.png"
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
