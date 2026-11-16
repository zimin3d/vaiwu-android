package su.neit.vaiwu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.nav_games),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = VaiwuSpacing.small),
        )
        Text(
            text = stringResource(R.string.games_energy),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(VaiwuSpacing.large))
        val cards = listOf(
            GameCardModel(R.string.mini_guess_title, R.string.mini_guess_reward) {
                onMiniGame(MiniGameKind.Guess, 4)
            },
            GameCardModel(R.string.mini_hearts_title, R.string.mini_hearts_reward) {
                onMiniGame(MiniGameKind.Hearts, 4)
            },
            GameCardModel(R.string.mini_math_title, R.string.mini_math_reward) {
                onMiniGame(MiniGameKind.Math, 4)
            },
            GameCardModel(R.string.mini_melody_title, R.string.mini_melody_reward) {
                onMiniGame(MiniGameKind.Melody, 8)
            },
            GameCardModel(R.string.rps_title, R.string.rps_win_hint) { onRps() },
            GameCardModel(R.string.battle_adventure, R.string.battle_adventure_reward) { onBattle(false) },
            GameCardModel(R.string.battle_dungeon, R.string.battle_dungeon_reward) { onBattle(true) },
        )
        cards.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
            ) {
                row.forEach { card ->
                    GameCard(
                        title = stringResource(card.titleRes),
                        reward = stringResource(card.rewardRes),
                        onClick = card.onClick,
                        enabled = pet.energy >= GameScreenEnergyThreshold,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val GameScreenEnergyThreshold = 40f

private data class GameCardModel(
    val titleRes: Int,
    val rewardRes: Int,
    val onClick: () -> Unit,
)

@Composable
private fun GameCard(
    title: String,
    reward: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(reward, style = MaterialTheme.typography.bodySmall)
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
            Spacer(Modifier.height(24.dp))
            PetSprite(
                characterId = pet.characterId,
                emotionId = if (state.outcome > 0) 1 else 2,
                description = pet.petName,
                modifier = Modifier.size(160.dp, 210.dp),
            )
            Text(
                text = if (state.outcome > 0) {
                    stringResource(R.string.mini_won, state.reward)
                } else {
                    stringResource(R.string.mini_lost)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAgain) { Text(stringResource(R.string.rps_again)) }
        } else {
            when (state.kind) {
                MiniGameKind.Guess -> MiniGameGuess(pet, state, onChoice)
                MiniGameKind.Hearts -> MiniGameHearts(state, onChoice)
                MiniGameKind.Melody -> MiniGameMelody(state, onChoice, onNote)
                MiniGameKind.Math -> MiniGameMath(state, onChoice)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.home)) }
    }
}

@Composable
private fun MiniGameGuess(
    pet: PetSnapshot,
    state: MiniGameState,
    onChoice: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.mini_time_left, (state.remainingMs + 999) / 1000),
        style = MaterialTheme.typography.bodyLarge,
    )
    PetSprite(
        characterId = pet.characterId,
        emotionId = state.emotionId,
        description = pet.petName,
        modifier = Modifier.size(180.dp, 240.dp),
    )
    val labels = listOf(
        stringResource(R.string.mini_guess_radiance) to 0,
        stringResource(R.string.mini_guess_sadness) to 1,
        stringResource(R.string.mini_guess_anger) to 2,
        stringResource(R.string.mini_guess_sleepiness) to 3,
    )
    labels.forEach { (label, index) ->
        Button(onClick = { onChoice(index) }, modifier = Modifier.fillMaxWidth().padding(3.dp)) {
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MiniGameHearts(state: MiniGameState, onChoice: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.mini_hearts_caught, state.heartsCaught, 5) + " " + stringResource(R.string.mini_time_left, (state.remainingMs + 999) / 1000),
        style = MaterialTheme.typography.bodyLarge,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "♥",
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        (constraints.maxWidth * state.heartX / 100f).toInt(),
                        (constraints.maxHeight * state.heartY / 100f).toInt(),
                    )
                }
                .size(72.dp)
                .clickable { onChoice(0) },
            color = Color(0xFFFF8FA8),
            fontSize = 54.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MiniGameMelody(
    state: MiniGameState,
    onChoice: (Int) -> Unit,
    onNote: (Int) -> Unit,
) {
    val colors = listOf(Color(0xFFFF7D9C), Color(0xFF72D9FF), Color(0xFFFFD166), Color(0xFF9F8CFF))
    if (state.melodyPhase == MelodyPhase.Watch) {
        val note = if (state.sequence.isEmpty()) {
            0
        } else {
            ((state.sequence.size * 600 + 400 - state.watchMs) / 600)
                .coerceIn(0, state.sequence.lastIndex)
        }
        LaunchedEffect(state.sequence, note) {
            state.sequence.getOrNull(note)?.let(onNote)
        }
        Text(stringResource(R.string.mini_watch), style = MaterialTheme.typography.titleLarge)
        Text(
            "●",
            color = colors[state.sequence.getOrElse(note) { 0 }],
            fontSize = 110.sp,
        )
    } else {
        Text(stringResource(R.string.mini_repeat, state.inputIndex, state.sequence.size))
        Text(stringResource(R.string.mini_time_left, (state.remainingMs + 999) / 1000))
        Spacer(Modifier.height(40.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { note, color ->
            Button(
                onClick = {
                    onNote(note)
                    onChoice(note)
                },
                enabled = state.melodyPhase == MelodyPhase.Repeat,
                modifier = Modifier.size(68.dp),
            ) { Text("${note + 1}", color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MiniGameMath(state: MiniGameState, onChoice: (Int) -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text(state.question, style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(32.dp))
    state.options.forEachIndexed { index, answer ->
        Button(onClick = { onChoice(index) }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text("$answer")
        }
    }
}

@Composable
fun BattleScreen(
    battle: BattleSnapshot,
    onAction: (Int) -> Unit,
    onNextRoom: () -> Unit,
    onExit: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    Box(modifier = Modifier.fillMaxSize()) {
        AssetImage(battleBackgroundForHour(hour), "Местность", Modifier.fillMaxSize(), ContentScale.Crop)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x9918122B))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (battle.dungeon) {
                    stringResource(R.string.battle_dungeon, battle.room, battle.rooms)
                } else {
                    stringResource(R.string.battle_adventure)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (battle.boss) {
                Text(
                    stringResource(R.string.battle_boss),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
            AssetImage(monsterAsset(battle.foeKind), battle.foeName, Modifier.size(170.dp), ContentScale.Fit)
            Text(
                stringResource(R.string.battle_monster_level, battle.foeName, battle.foeLevel),
                style = MaterialTheme.typography.titleLarge,
            )
            CombatBar(stringResource(R.string.battle_foe_hp), battle.foeHp, battle.foeHpMax, MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.battle_level_xp, battle.level, battle.xp, battle.xpNext),
                style = MaterialTheme.typography.bodyMedium,
            )
            CombatBar(stringResource(R.string.battle_hp), battle.hp, battle.hpMax, Color(0xFF37E08D))
            CombatBar(stringResource(R.string.battle_mp), battle.mp, battle.mpMax, Color(0xFF72D9FF))
            Text(
                stringResource(R.string.battle_potions, battle.potions, battle.mpPotions),
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    battle.log,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            when {
                battle.cleared -> {
                    Text(
                        stringResource(R.string.battle_cleared, battle.chestGold),
                        color = Color(0xFFFFD166),
                    )
                    Button(onClick = onExit) { Text(stringResource(R.string.battle_claim)) }
                }
                battle.awaitingRest -> {
                    Text(stringResource(R.string.battle_rest))
                    Button(onClick = onNextRoom) { Text(stringResource(R.string.battle_rest_next)) }
                    OutlinedButton(onClick = onExit) { Text(stringResource(R.string.battle_exit_loot)) }
                }
                battle.over -> Button(onClick = onExit) { Text(stringResource(R.string.battle_back_home)) }
                else -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onAction(RpgAction.Attack) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.battle_action_attack)) }
                        Button(
                            onClick = { onAction(RpgAction.Skill) },
                            modifier = Modifier.weight(1f),
                        ) { Text(battle.skillName, textAlign = TextAlign.Center) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onAction(RpgAction.Potion) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.battle_action_hp_potion)) }
                        OutlinedButton(
                            onClick = { onAction(RpgAction.MpPotion) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.battle_action_mp_potion)) }
                        OutlinedButton(
                            onClick = { onAction(RpgAction.Run) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.battle_action_run)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombatBar(label: String, value: Int, maximum: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { if (maximum > 0) (value.toFloat() / maximum).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.weight(2f),
            color = color,
        )
        Text(" $value/$maximum", style = MaterialTheme.typography.bodySmall)
    }
}
