package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Width fraction of pet reply bubbles so they visually contrast with full-width user bubbles. */
private const val PET_BUBBLE_WIDTH_FRACTION = 0.9f

/** Maximum input lengths enforced in Chat and Settings fields. */
private const val CHAT_INPUT_MAX_CHARS = 2_000
private const val CHAT_INPUT_MAX_LINES = 3
private const val API_KEY_INPUT_MAX_CHARS = 300

/** Spacing between Help accordion cards and their title/body blocks. */
private val HELP_CARD_VERTICAL_GAP = 6.dp

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
    Column(modifier = Modifier.fillMaxSize().padding(VaiwuSpacing.screenPadding)) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            items(messages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(if (message.fromPet) PET_BUBBLE_WIDTH_FRACTION else 1f),
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
                        modifier = Modifier.padding(VaiwuSpacing.medium),
                    )
                }
            }
            if (busy) {
                item {
                    CircularProgressIndicator()
                }
            }
        }
        val lastPetMessage = messages.lastOrNull()?.takeIf { it.fromPet }
        var lastSpokenId by rememberSaveable {
            mutableLongStateOf(messages.lastOrNull { it.fromPet }?.id ?: -1L)
        }
        LaunchedEffect(speechEnabled, lastPetMessage?.id) {
            // Speak only newly arrived pet replies, keyed by monotonic message
            // id so identical consecutive replies still speak while tab
            // switches never replay old messages.
            if (speechEnabled && lastPetMessage != null && lastPetMessage.id > lastSpokenId) {
                lastSpokenId = lastPetMessage.id
                voice?.speak(lastPetMessage.text)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(CHAT_INPUT_MAX_CHARS) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.chat_input_label)) },
                maxLines = CHAT_INPUT_MAX_LINES,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onDone = {
                    if (input.isNotBlank() && !busy) {
                        onSend(input)
                        input = ""
                    }
                }),
            )
            OutlinedButton(
                onClick = { voice?.listen() },
                enabled = voice != null && !busy,
                contentPadding = PaddingValues(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
                modifier = Modifier.heightIn(min = VaiwuSizes.minTouchTarget),
            ) {
                Text(stringResource(R.string.chat_voice_button), maxLines = 1)
            }
            FilledIconButton(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !busy,
                modifier = Modifier.size(VaiwuSizes.minTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                )
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
    onSpeechEnabled: (Boolean) -> Unit,
    onClearApiKey: () -> Unit,
    onSave: (String, String, String) -> Boolean,
    onUpdate: () -> Unit,
    onHelp: () -> Unit,
    onReset: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
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
            Switch(checked = settings.speechEnabled, onCheckedChange = onSpeechEnabled)
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
            onValueChange = { apiKey = it.take(API_KEY_INPUT_MAX_CHARS) },
            label = { Text(stringResource(R.string.settings_api_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings.hasApiKey) {
            TextButton(onClick = onClearApiKey) {
                Text(stringResource(R.string.settings_api_clear))
            }
        }
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
                if (onSave(apiKey, latitude, longitude)) {
                    apiKey = ""
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
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            R.string.help_care to R.string.help_care_text,
            R.string.help_feed to R.string.help_feed_text,
            R.string.help_games to R.string.help_games_text,
            R.string.help_rpg to R.string.help_rpg_text,
            R.string.help_shop to R.string.help_shop_text,
            R.string.help_pet to R.string.help_pet_text,
            R.string.help_sleep to R.string.help_sleep_text,
            R.string.help_progress to R.string.help_progress_text,
            R.string.help_save to R.string.help_save_text,
            R.string.help_assistant to R.string.help_assistant_text,
        ).forEach { (titleRes, textRes) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = HELP_CARD_VERTICAL_GAP),
            ) {
                Column(modifier = Modifier.padding(VaiwuSpacing.cardPadding)) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(HELP_CARD_VERTICAL_GAP))
                    Text(stringResource(textRes))
                }
            }
        }
    }
}
