package su.neit.vaiwu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Number of columns in the Games hub card grid. */
private const val GAMES_GRID_COLUMNS = 2

/** Percentage coordinate upper bound used by [heartOffsetPx]. */
private const val PLAYFIELD_PERCENT_MAX = 100

/** Milliseconds per second used to round countdown timers upward to whole seconds. */
private const val MS_PER_SECOND = 1_000

/** Visual sizing tokens for mini-game screens. */
private val RESULT_SPRITE_WIDTH = 160.dp
private val RESULT_SPRITE_HEIGHT = 210.dp
private val GUESS_SPRITE_WIDTH = 180.dp
private val GUESS_SPRITE_HEIGHT = 240.dp
private val HEART_TARGET_SIZE = 72.dp
private val MELODY_PAD_SIZE = 68.dp
private val MELODY_DOT_FONT_SIZE = 110.sp
private val MELODY_PAD_GAP = 10.dp
private val MELODY_REPEAT_SPACER = 40.dp
private val MATH_TOP_SPACER = 48.dp
private val MATH_ANSWER_SPACER = 32.dp
private val GUESS_BUTTON_PADDING = 3.dp

/** Rounds remaining milliseconds upward to whole seconds so the timer never shows 0 while still running. */
private fun remainingSecondsCeil(remainingMs: Int): Int =
    (remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND

/**
 * Games tab (UI-GAME): the hub with the game cards, the energy banner and the
 * battle entries. Cards are disabled while the pet has no energy.
 */
@Composable
fun GamesScreen(
    pet: PetSnapshot,
    onRps: () -> Unit,
    onMiniGame: (MiniGameKind, Int) -> Unit,
    onBattle: (Boolean) -> Unit,
    onGoHome: () -> Unit = {},
) {
    val canPlay = canPlayGames(pet)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!canPlay) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = VaiwuSpacing.medium),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, VaiwuColors.warning),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(VaiwuSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                ) {
                    Text(
                        text = stringResource(R.string.games_tired_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaiwuColors.warning,
                    )
                    VaiwuSecondaryButton(
                        text = stringResource(R.string.games_go_home),
                        onClick = onGoHome,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.games_energy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(VaiwuSpacing.large))
        }
        val cards = listOf(
            GameCardModel(R.string.mini_guess_title, R.string.mini_guess_reward) {
                onMiniGame(MiniGameKind.Guess, MELODY_DEFAULT_LENGTH)
            },
            GameCardModel(R.string.mini_hearts_title, R.string.mini_hearts_reward) {
                onMiniGame(MiniGameKind.Hearts, MELODY_DEFAULT_LENGTH)
            },
            GameCardModel(R.string.mini_math_title, R.string.mini_math_reward) {
                onMiniGame(MiniGameKind.Math, MELODY_DEFAULT_LENGTH)
            },
            GameCardModel(R.string.mini_melody_title, R.string.mini_melody_reward) {
                onMiniGame(MiniGameKind.Melody, MELODY_MAX_LENGTH)
            },
            GameCardModel(R.string.rps_title, R.string.rps_win_hint) { onRps() },
            GameCardModel(R.string.battle_adventure, R.string.battle_adventure_reward) { onBattle(false) },
            GameCardModel(R.string.battle_dungeon, R.string.battle_dungeon_reward) { onBattle(true) },
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
        ) {
            cards.chunked(GAMES_GRID_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
                ) {
                    row.forEach { card ->
                        GameCard(
                            title = stringResource(card.titleRes),
                            reward = stringResource(card.rewardRes),
                            onClick = card.onClick,
                            enabled = canPlayGames(pet),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Returns true when [pet] is awake and has enough energy to survive the upfront play cost. */
internal fun canPlayGames(pet: PetSnapshot): Boolean = pet.canPlay

/**
 * Maps percentage coordinates (`heartX`, `heartY` in 0..100) into pixel offsets
 * relative to `Alignment.TopStart` so the heart icon stays completely inside
 * the playfield.
 */
internal fun heartOffsetPx(
    containerWidth: Int,
    containerHeight: Int,
    iconSize: Int,
    heartX: Int,
    heartY: Int,
): Pair<Int, Int> {
    val maxX = (containerWidth - iconSize).coerceAtLeast(0)
    val maxY = (containerHeight - iconSize).coerceAtLeast(0)
    val clampedX = heartX.coerceIn(0, PLAYFIELD_PERCENT_MAX)
    val clampedY = heartY.coerceIn(0, PLAYFIELD_PERCENT_MAX)
    return (maxX * clampedX / PLAYFIELD_PERCENT_MAX.toFloat()).toInt() to
        (maxY * clampedY / PLAYFIELD_PERCENT_MAX.toFloat()).toInt()
}

private data class GameCardModel(
    val titleRes: Int,
    val rewardRes: Int,
    val onClick: () -> Unit,
)

/** Single selectable game entry card in the [GamesScreen] grid. */
@Composable
private fun GameCard(
    title: String,
    reward: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = VaiwuSizes.careActionHeight)
            .pressScale(interactionSource, enabled),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                reward,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Mini game screen. The overlay states (finished) keep the global top bar and
 * bottom bar behaviour of the app, the round itself is tracked by the
 * ViewModel (`UI-GAME-08`, `UI-GAME-09`).
 */
@Composable
fun MiniGameScreen(
    pet: PetSnapshot,
    state: MiniGameState,
    onChoice: (Int) -> Unit,
    onNote: (Int) -> Unit,
    onAgain: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(VaiwuSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (state.kind) {
                MiniGameKind.Guess -> stringResource(R.string.mini_guess_title)
                MiniGameKind.Hearts -> stringResource(R.string.mini_hearts_title)
                MiniGameKind.Melody -> stringResource(R.string.mini_melody_title)
                MiniGameKind.Math -> stringResource(R.string.mini_math_title)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state.finished) {
            Spacer(Modifier.height(VaiwuSpacing.xxl))
            PetSprite(
                characterId = pet.characterId,
                emotionId = if (state.outcome > 0) Emotion.Happy else Emotion.Sad,
                description = pet.petName,
                modifier = Modifier.size(RESULT_SPRITE_WIDTH, RESULT_SPRITE_HEIGHT),
            )
            Text(
                text = if (state.outcome > 0) {
                    stringResource(R.string.mini_won, state.reward)
                } else {
                    stringResource(R.string.mini_lost)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (state.outcome > 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(VaiwuSpacing.medium))
            VaiwuPrimaryButton(
                text = stringResource(R.string.rps_again),
                onClick = onAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            when (state.kind) {
                MiniGameKind.Guess -> MiniGameGuess(pet, state, onChoice)
                MiniGameKind.Hearts -> MiniGameHearts(state, onChoice)
                MiniGameKind.Melody -> MiniGameMelody(state, onChoice, onNote)
                MiniGameKind.Math -> MiniGameMath(state, onChoice)
            }
        }
        Spacer(Modifier.height(VaiwuSpacing.large))
        VaiwuSecondaryButton(
            text = stringResource(R.string.home),
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Guess the Emotion" mini-game view: shows a random expression and 4 emotion buttons. */
@Composable
private fun MiniGameGuess(
    pet: PetSnapshot,
    state: MiniGameState,
    onChoice: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.mini_time_left, remainingSecondsCeil(state.remainingMs)),
        style = MaterialTheme.typography.bodyLarge,
    )
    PetSprite(
        characterId = pet.characterId,
        emotionId = state.emotionId,
        description = pet.petName,
        modifier = Modifier.size(GUESS_SPRITE_WIDTH, GUESS_SPRITE_HEIGHT),
    )
    val labels = listOf(
        stringResource(R.string.mini_guess_radiance) to 0,
        stringResource(R.string.mini_guess_sadness) to 1,
        stringResource(R.string.mini_guess_anger) to 2,
        stringResource(R.string.mini_guess_sleepiness) to 3,
    )
    labels.forEach { (label, index) ->
        VaiwuPrimaryButton(
            text = label,
            onClick = { onChoice(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = GUESS_BUTTON_PADDING),
        )
    }
}

/** "Catch the Hearts" mini-game view: places a tappable heart inside the available bounds. */
@Composable
private fun MiniGameHearts(state: MiniGameState, onChoice: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.mini_hearts_caught, state.heartsCaught, HEARTS_TARGET_COUNT) +
            " " + stringResource(R.string.mini_time_left, remainingSecondsCeil(state.remainingMs)),
        style = MaterialTheme.typography.bodyLarge,
    )
    val heartPx = with(LocalDensity.current) { HEART_TARGET_SIZE.roundToPx() }
    val tapLabel = stringResource(R.string.mini_hearts_tap)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = VaiwuIcons.heart,
            contentDescription = tapLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val (x, y) = heartOffsetPx(
                        containerWidth = constraints.maxWidth,
                        containerHeight = constraints.maxHeight,
                        iconSize = heartPx,
                        heartX = state.heartX,
                        heartY = state.heartY,
                    )
                    IntOffset(x, y)
                }
                .size(HEART_TARGET_SIZE)
                .clickable(role = Role.Button) { onChoice(0) },
            tint = VaiwuColors.heartTint,
        )
    }
}

/** "Melody" Simon-says mini-game view: flashes notes during Watch, then enables pads in Repeat. */
@Composable
private fun MiniGameMelody(
    state: MiniGameState,
    onChoice: (Int) -> Unit,
    onNote: (Int) -> Unit,
) {
    val pads = VaiwuColors.melodyPads
    if (state.melodyPhase == MelodyPhase.Watch) {
        val totalWatchMs = state.sequence.size * MELODY_NOTE_DURATION_MS + MELODY_WATCH_LEAD_IN_MS
        val note = if (state.sequence.isEmpty()) {
            0
        } else {
            ((totalWatchMs - state.watchMs) / MELODY_NOTE_DURATION_MS)
                .coerceIn(0, state.sequence.lastIndex)
        }
        LaunchedEffect(state.sequence, note) {
            state.sequence.getOrNull(note)?.let(onNote)
        }
        Text(stringResource(R.string.mini_watch), style = MaterialTheme.typography.titleLarge)
        Text(
            "●",
            color = pads[state.sequence.getOrElse(note) { 0 }],
            fontSize = MELODY_DOT_FONT_SIZE,
        )
    } else {
        Text(stringResource(R.string.mini_repeat, state.inputIndex, state.sequence.size))
        Text(stringResource(R.string.mini_time_left, remainingSecondsCeil(state.remainingMs)))
        Spacer(Modifier.height(MELODY_REPEAT_SPACER))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MELODY_PAD_GAP)) {
        pads.forEachIndexed { note, color ->
            val enabled = state.melodyPhase == MelodyPhase.Repeat
            val interactionSource = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    onNote(note)
                    onChoice(note)
                },
                enabled = enabled,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(2.dp, if (enabled) color else color.copy(alpha = 0.35f)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = color,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContentColor = color.copy(alpha = 0.45f),
                ),
                interactionSource = interactionSource,
                modifier = Modifier
                    .size(MELODY_PAD_SIZE)
                    .pressScale(interactionSource, enabled),
            ) {
                Text(
                    text = "${note + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Arithmetic quiz mini-game view: displays the equation and 4 answer choices. */
@Composable
private fun MiniGameMath(state: MiniGameState, onChoice: (Int) -> Unit) {
    Spacer(Modifier.height(MATH_TOP_SPACER))
    Text(state.question, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(MATH_ANSWER_SPACER))
    state.options.forEachIndexed { index, answer ->
        VaiwuPrimaryButton(
            text = "$answer",
            onClick = { onChoice(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = VaiwuSpacing.xs),
        )
    }
}
