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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun GamesScreen(
    onRps: () -> Unit,
    onMiniGame: (MiniGameKind, Int) -> Unit,
    onBattle: (Boolean) -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Игры и приключения", onHome)
        Text("Игры тратят 8 энергии. Победы приносят монеты и улучшают настроение.")
        Spacer(Modifier.height(16.dp))
        GameButton("Угадай эмоцию · 2 монеты") { onMiniGame(MiniGameKind.Guess, 4) }
        GameButton("Поймай 5 сердец · 2 монеты") { onMiniGame(MiniGameKind.Hearts, 4) }
        GameButton("Быстрая математика · 3 монеты") { onMiniGame(MiniGameKind.Math, 4) }
        GameButton("Камень · ножницы · бумага", onRps)
        Text("Повтори мелодию", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 6, 8).forEach { length ->
                OutlinedButton(onClick = { onMiniGame(MiniGameKind.Melody, length) }) {
                    Text("$length нот")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        GameButton("Приключение · один бой") { onBattle(false) }
        GameButton("Данж · 3–5 комнат и босс") { onBattle(true) }
    }
}

@Composable
private fun GameButton(title: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, textAlign = TextAlign.Center)
    }
}

@Composable
fun MiniGameScreen(
    pet: PetSnapshot,
    state: MiniGameState,
    onChoice: (Int) -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            when (state.kind) {
                MiniGameKind.Guess -> "Угадай эмоцию"
                MiniGameKind.Hearts -> "Поймай сердца"
                MiniGameKind.Melody -> "Повтори мелодию"
                MiniGameKind.Math -> "Быстрая математика"
            },
            onHome,
        )
        if (state.finished) {
            Spacer(Modifier.weight(1f))
            PetSprite(pet.characterId, if (state.outcome > 0) 1 else 2, pet.petName, Modifier.size(160.dp, 210.dp))
            Text(
                if (state.outcome > 0) "Победа! +${state.reward} монеты" else "Попробуй ещё раз",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(onClick = onAgain, modifier = Modifier.padding(16.dp)) { Text("Ещё раз") }
            Spacer(Modifier.weight(1f))
            return@Column
        }
        when (state.kind) {
            MiniGameKind.Guess -> GuessGame(pet, state, onChoice)
            MiniGameKind.Hearts -> HeartsGame(state, onChoice)
            MiniGameKind.Melody -> MelodyGame(state, onChoice)
            MiniGameKind.Math -> MathGame(state, onChoice)
        }
    }
}

@Composable
private fun GuessGame(pet: PetSnapshot, state: MiniGameState, onChoice: (Int) -> Unit) {
    Text("Осталось: ${(state.remainingMs + 999) / 1000} с")
    PetSprite(pet.characterId, state.emotionId, "Загаданная эмоция", Modifier.size(180.dp, 240.dp))
    val labels = listOf("Радость", "Грусть", "Злость", "Сонливость")
    labels.forEachIndexed { index, label ->
        Button(onClick = { onChoice(index) }, modifier = Modifier.fillMaxWidth().padding(3.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun HeartsGame(state: MiniGameState, onChoice: (Int) -> Unit) {
    Text("${state.heartsCaught}/5 · ${(state.remainingMs + 999) / 1000} с")
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            "♥",
            modifier = Modifier.offset {
                IntOffset(
                    (constraints.maxWidth * state.heartX / 100f).toInt(),
                    (constraints.maxHeight * state.heartY / 100f).toInt(),
                )
            }.clickable { onChoice(0) }.padding(20.dp),
            color = Color(0xFFFF8FA8),
            fontSize = 54.sp,
        )
    }
}

@Composable
private fun MelodyGame(state: MiniGameState, onChoice: (Int) -> Unit) {
    val colors = listOf(Color(0xFFFF7D9C), Color(0xFF72D9FF), Color(0xFFFFD166), Color(0xFF9F8CFF))
    if (state.melodyPhase == MelodyPhase.Watch) {
        val note = ((state.sequence.size * 600 + 400 - state.watchMs) / 600)
            .coerceIn(0, state.sequence.lastIndex)
        Text("Запоминай…", style = MaterialTheme.typography.titleLarge)
        Text("●", color = colors[state.sequence[note]], fontSize = 110.sp)
    } else {
        Text("Повтори: ${state.inputIndex}/${state.sequence.size}")
        Text("Осталось: ${(state.remainingMs + 999) / 1000} с")
        Spacer(Modifier.height(40.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { note, color ->
            Button(
                onClick = { onChoice(note) },
                enabled = state.melodyPhase == MelodyPhase.Repeat,
                modifier = Modifier.size(68.dp),
            ) { Text("${note + 1}", color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MathGame(state: MiniGameState, onChoice: (Int) -> Unit) {
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
    onAllocate: (Int) -> Unit,
    onHome: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    Box(modifier = Modifier.fillMaxSize()) {
        AssetImage(battleBackgroundForHour(hour), "Местность", Modifier.fillMaxSize(), ContentScale.Crop)
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0x9918122B)).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader(if (battle.dungeon) "Данж ${battle.room}/${battle.rooms}" else "Приключение", onHome)
            if (battle.boss) Text("БОСС", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            AssetImage(monsterAsset(battle.foeKind), battle.foeName, Modifier.size(170.dp), ContentScale.Fit)
            Text("${battle.foeName} · ур. ${battle.foeLevel}", style = MaterialTheme.typography.titleLarge)
            CombatBar("HP противника", battle.foeHp, battle.foeHpMax, MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text("Уровень ${battle.level} · XP ${battle.xp}/${battle.xpNext}")
            CombatBar("HP", battle.hp, battle.hpMax, Color(0xFF37E08D))
            CombatBar("MP", battle.mp, battle.mpMax, Color(0xFF72D9FF))
            Text("Зелья: HP ${battle.potions} · MP ${battle.mpPotions}")
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(battle.log, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
            when {
                battle.cleared -> {
                    Text("Данж пройден! Сундук: +${battle.chestGold} монет", color = Color(0xFFFFD166))
                    Button(onClick = onHome) { Text("Забрать добычу домой") }
                }
                battle.awaitingRest -> {
                    Text("Комната пройдена. Короткий отдых восстановит HP и MP.")
                    Button(onClick = onNextRoom) { Text("Отдохнуть и идти дальше") }
                    OutlinedButton(onClick = onHome) { Text("Выйти с добычей") }
                }
                battle.over -> Button(onClick = onHome) { Text("Вернуться домой") }
                else -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onAction(RpgAction.Attack) }, modifier = Modifier.weight(1f)) { Text("Атака") }
                        Button(onClick = { onAction(RpgAction.Skill) }, modifier = Modifier.weight(1f)) {
                            Text(battle.skillName, textAlign = TextAlign.Center)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onAction(RpgAction.Potion) }, modifier = Modifier.weight(1f)) { Text("HP-зелье") }
                        OutlinedButton(onClick = { onAction(RpgAction.MpPotion) }, modifier = Modifier.weight(1f)) { Text("MP-зелье") }
                        OutlinedButton(onClick = { onAction(RpgAction.Run) }, modifier = Modifier.weight(1f)) { Text("Бежать") }
                    }
                }
            }
            if (battle.statPoints > 0) StatAllocation(battle.statPoints, onAllocate)
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

@Composable
fun StatsScreen(inventory: InventorySnapshot?, onAllocate: (Int) -> Unit, onEquipment: () -> Unit, onHome: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenHeader("Характеристики", onHome) }
        inventory?.let { stats ->
            item { Text("Уровень ${stats.level} · XP ${stats.xp}/${stats.xpNext}", style = MaterialTheme.typography.titleLarge) }
            item { Text("HP ${stats.hp}/${stats.hpMax} · MP ${stats.mp}/${stats.mpMax}") }
            item { Text("Зелья здоровья: ${stats.potions} · Зелья маны: ${stats.mpPotions}") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("СИЛ ${stats.strength} · ЖИВ ${stats.vitality} · ЛОВ ${stats.agility}")
                        Text("МАГ ${stats.magic} · УДЧ ${stats.luck}")
                    }
                }
            }
            if (stats.statPoints > 0) item { StatAllocation(stats.statPoints, onAllocate) }
        }
        item { Button(onClick = onEquipment, modifier = Modifier.fillMaxWidth()) { Text("Снаряжение") } }
    }
}

@Composable
private fun StatAllocation(points: Int, onAllocate: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Свободные очки: $points", fontWeight = FontWeight.Bold)
            val stats = listOf("СИЛ", "ЖИВ", "ЛОВ", "МАГ", "УДЧ")
            stats.forEachIndexed { index, name ->
                TextButton(onClick = { onAllocate(index) }) { Text("+1 $name") }
            }
        }
    }
}
