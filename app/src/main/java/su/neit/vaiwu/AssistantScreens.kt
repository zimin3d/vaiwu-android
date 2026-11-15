package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    speechEnabled: Boolean,
    voice: VoiceController?,
    onSend: (String) -> Unit,
    onHome: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Разговор", onHome)
        Text(
            "Можно спросить время и погоду, поставить будильник, напоминание или помодоро, попросить анекдот и перевести единицы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(if (message.fromPet) 0.9f else 1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.fromPet) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) { Text(message.text, modifier = Modifier.padding(12.dp)) }
            }
            if (busy) item { CircularProgressIndicator() }
        }
        LaunchedEffect(messages.lastOrNull()?.text, speechEnabled) {
            val last = messages.lastOrNull()
            if (speechEnabled && last?.fromPet == true) voice?.speak(last.text)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(2_000) },
                modifier = Modifier.weight(1f),
                label = { Text("Сообщение") },
                maxLines = 3,
            )
            Button(onClick = voice?.let { { it.listen() } } ?: {}, enabled = voice != null && !busy) { Text("Голос") }
            Button(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !busy,
            ) { Text("→") }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AssistantSettings,
    voice: VoiceController?,
    onSave: (String, Boolean, Double, Double) -> Unit,
    onUpdate: () -> Unit,
    onReset: () -> Unit,
    onHome: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var speech by remember(settings.speechEnabled) { mutableStateOf(settings.speechEnabled) }
    var latitude by remember { mutableStateOf(settings.latitude.toString()) }
    var longitude by remember { mutableStateOf(settings.longitude.toString()) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader("Настройки", onHome)
        Text("Голос и ассистент", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Озвучивать ответы")
                Text("Используется системный синтез речи", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = speech, onCheckedChange = { speech = it })
        }
        OutlinedButton(onClick = { voice?.requestNotifications() }, enabled = voice != null, modifier = Modifier.fillMaxWidth()) {
            Text("Разрешить уведомления")
        }
        Spacer(Modifier.height(20.dp))
        Text("Свободный чат через OpenRouter", style = MaterialTheme.typography.titleMedium)
        Text(
            if (settings.hasApiKey) "Ключ сохранён. Введи новый, чтобы заменить; пустое поле удалит ключ при сохранении."
            else "Без ключа работают локальные команды и короткие offline-диалоги.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.take(300) },
            label = { Text("API-ключ OpenRouter") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("Координаты для погоды", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("Широта") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("Долгота") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat != null && lon != null) onSave(apiKey, speech, lat, lon)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text("Сохранить") }
        Text("Ключ и координаты не попадают в облачные резервные копии.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text("Проверить обновление") }
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Удалить питомца и начать заново") }
    }
}

@Composable
fun HelpScreen(onHome: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader("Справка", onHome)
        val sections = listOf(
            "Уход" to "Корми, мой, убирай и обнимай питомца. Следи за сытостью: при нуле питомец умирает. Низкая энергия вызывает сон, болезнь лечится лекарством.",
            "Игры" to "Каждый раунд тратит 8 энергии. Доступны эмоции, сердца, мелодия, математика и камень-ножницы-бумага. Выход до конца раунда не даёт награды.",
            "RPG" to "Приключение — один бой. Данж состоит из 3–5 комнат, между ними можно отдохнуть или выйти с добычей. В последней комнате ждут босс и сундук. Поражение не убивает питомца.",
            "Прокачка" to "Победы дают опыт и монеты. За уровень начисляются 3 очка характеристик. Снаряжение покупается в магазине и меняется в инвентаре.",
            "Ассистент" to "Примеры: «который час?», «расскажи анекдот», «напомни через 10 минут», «разбуди в 7:30», «помодоро», «5 км в мили», «какая погода?». Микрофон использует системное распознавание речи.",
            "Обновления" to "APK скачивается только из GitHub Releases этого проекта. Приложение проверяет хеш и совпадение подписи перед установкой.",
        )
        sections.forEach { (title, text) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(text)
                }
            }
        }
    }
}
