package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Chat tab (UI-AST-01…04): the conversation with the pet, the assistant
 * replies and the input. New messages scroll into view and the input respects
 * the ime.
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    speechEnabled: Boolean,
    voice: VoiceController?,
    onSend: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(if (message.fromPet) 0.9f else 1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.fromPet) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (busy) {
                item {
                    CircularProgressIndicator()
                }
            }
        }
        val lastPetReply = messages.lastOrNull()?.takeIf { it.fromPet }?.text
        LaunchedEffect(speechEnabled, lastPetReply) {
            // The pet speaks through the voice channel, never through the chat
            // input (I-6). Key on the reply itself, otherwise a newly arrived
            // message never re-triggers the effect.
            if (speechEnabled && lastPetReply != null) {
                voice?.speak(lastPetReply)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(2_000) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.chat_input_label)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onDone = { onSend(input); input = "" }),
            )
            Button(
                onClick = { voice?.listen() },
                enabled = voice != null && !busy,
            ) {
                Text(stringResource(R.string.chat_voice_button))
            }
            Button(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !busy,
            ) {
                Text(stringResource(R.string.chat_send))
            }
        }
    }
}

/**
 * Settings of the assistant: sound, voice, API key, coordinates, updates and
 * the reset action. Technical fields are kept apart from the daily ones.
 */
@Composable
fun SettingsScreen(
    settings: AssistantSettings,
    soundEnabled: Boolean,
    voice: VoiceController?,
    onSoundEnabled: (Boolean) -> Unit,
    onSave: (String, Boolean, Double, Double) -> Unit,
    onUpdate: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var speech by remember(settings.speechEnabled) { mutableStateOf(settings.speechEnabled) }
    var latitude by remember(settings.latitude.toString()) { mutableStateOf(settings.latitude.toString()) }
    var longitude by remember(settings.longitude.toString()) { mutableStateOf(settings.longitude.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.settings_sound_title), style = MaterialTheme.typography.titleLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_sound_label))
                Text(
                    text = stringResource(R.string.settings_sound_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = soundEnabled, onCheckedChange = onSoundEnabled)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.settings_voice_title), style = MaterialTheme.typography.titleLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_voice_label))
                Text(
                    text = stringResource(R.string.settings_voice_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = speech, onCheckedChange = { speech = it })
        }
        OutlinedButton(
            onClick = { voice?.requestNotifications() },
            enabled = voice != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_voice_notify))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.settings_api_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (settings.hasApiKey) {
                stringResource(R.string.settings_api_key_saved)
            } else {
                stringResource(R.string.settings_api_key_missing)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.take(300) },
            label = { Text(stringResource(R.string.settings_api_label)) },
            visualTransformation = androidx.compose.material3.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_location_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(stringResource(R.string.settings_latitude_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(stringResource(R.string.settings_longitude_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat != null && lon != null) {
                    onSave(apiKey, speech, lat, lon)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(stringResource(R.string.settings_save))
        }
        Text(
            text = stringResource(R.string.settings_location_note),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_update_check))
        }
        TextButton(onClick = onHelp) {
            Text(stringResource(R.string.settings_help))
        }
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.settings_reset))
        }
    }
}

/**
 * Help screen: plain instructions for care, games, RPG, progression and the
 * assistant. It is reached from the settings gear.
 */
@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.help_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        listOf(
            R.string.help_care to R.string.help_care_text,
            R.string.help_games to R.string.help_games_text,
            R.string.help_rpg to R.string.help_rpg_text,
            R.string.help_progress to R.string.help_progress_text,
            R.string.help_assistant to R.string.help_assistant_text,
        ).forEach { (titleRes, textRes) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(textRes))
                }
            }
        }
    }
}
