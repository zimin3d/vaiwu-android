package su.neit.vaiwu

import kotlin.random.Random

/** Default coin rewards granted on winning each mini-game. */
private const val GUESS_REWARD_COINS = 2
private const val HEARTS_REWARD_COINS = 2
private const val MELODY_BASE_REWARD_COINS = 2
private const val MATH_REWARD_COINS = 3

/** Round outcomes reported back to the native engine (`+1` win, `-1` loss). */
private const val OUTCOME_WIN = 1
private const val OUTCOME_LOSS = -1

/** Guess-the-emotion rules: 4 emotions (Happy..Sleepy, native ids 1..4) and a 3-second timer. */
private const val GUESS_OPTION_COUNT = 4
private const val GUESS_EMOTION_ID_OFFSET = 1
private const val GUESS_DURATION_MS = 3_000

/** Catch-the-hearts rules: tap 5 hearts within 8 seconds inside safe screen-percentage bounds. */
const val HEARTS_TARGET_COUNT = 5
private const val HEARTS_DURATION_MS = 8_000
private const val HEARTS_SPAWN_MIN_X = 10
private const val HEARTS_SPAWN_MAX_X = 86
private const val HEARTS_SPAWN_MIN_Y = 15
private const val HEARTS_SPAWN_MAX_Y = 76
private const val HEARTS_DEFAULT_X = 40
private const val HEARTS_DEFAULT_Y = 35

/** Melody rules: 4 note pads, sequence length 4..8, 600 ms/note + 400 ms lead-in, 10 s repeat window. */
const val MELODY_DEFAULT_LENGTH = 4
const val MELODY_MIN_LENGTH = 4
const val MELODY_MAX_LENGTH = 8
const val MELODY_NOTE_DURATION_MS = 600
const val MELODY_WATCH_LEAD_IN_MS = 400
private const val MELODY_PAD_COUNT = 4
private const val MELODY_REPEAT_DURATION_MS = 10_000
private const val MELODY_BONUS_NOTES_PER_COIN = 2

/** Math quiz rules: operands in `1..12`, 3 operators (+, -, x), 4 choices within `+-5` of the answer. */
private const val MATH_OPERAND_MIN = 1
private const val MATH_OPERAND_MAX_EXCLUSIVE = 13
private const val MATH_OPERATOR_COUNT = 3
private const val MATH_OPTION_COUNT = 4
private const val MATH_DISTRACTOR_MIN_DELTA = -5
private const val MATH_DISTRACTOR_MAX_EXCLUSIVE = 6

/** Available quick mini-games and their baseline coin rewards. */
enum class MiniGameKind(val reward: Int) {
    Guess(GUESS_REWARD_COINS),
    Hearts(HEARTS_REWARD_COINS),
    Melody(MELODY_BASE_REWARD_COINS),
    Math(MATH_REWARD_COINS),
}

/** Sub-phase of the Simon-says Melody mini-game. */
enum class MelodyPhase {
    Watch,
    Repeat,
}

/** Immutable snapshot of an active or finished mini-game round rendered by [MiniGameScreen]. */
data class MiniGameState(
    val kind: MiniGameKind,
    val question: String = "",
    val options: List<Int> = emptyList(),
    val emotionId: Int = 0,
    val answer: Int = 0,
    val sequence: List<Int> = emptyList(),
    val inputIndex: Int = 0,
    val heartsCaught: Int = 0,
    val heartX: Int = HEARTS_DEFAULT_X,
    val heartY: Int = HEARTS_DEFAULT_Y,
    val remainingMs: Int = 0,
    val melodyPhase: MelodyPhase = MelodyPhase.Watch,
    val watchMs: Int = 0,
    val finished: Boolean = false,
    val outcome: Int = 0,
    val reward: Int = kind.reward,
)

/** Pure, deterministic state machine for all four Android mini-games. */
class MiniGameEngine(private val random: Random = Random.Default) {

    /** Initializes a new round of [kind] (with optional [melodyLength] clamped to `4..8`). */
    fun start(kind: MiniGameKind, melodyLength: Int = MELODY_DEFAULT_LENGTH): MiniGameState = when (kind) {
        MiniGameKind.Guess -> {
            val answer = random.nextInt(GUESS_OPTION_COUNT)
            MiniGameState(
                kind = kind,
                answer = answer,
                emotionId = answer + GUESS_EMOTION_ID_OFFSET,
                remainingMs = GUESS_DURATION_MS,
            )
        }
        MiniGameKind.Hearts -> MiniGameState(
            kind = kind,
            heartX = random.nextInt(HEARTS_SPAWN_MIN_X, HEARTS_SPAWN_MAX_X),
            heartY = random.nextInt(HEARTS_SPAWN_MIN_Y, HEARTS_SPAWN_MAX_Y),
            remainingMs = HEARTS_DURATION_MS,
        )
        MiniGameKind.Melody -> {
            val length = melodyLength.coerceIn(MELODY_MIN_LENGTH, MELODY_MAX_LENGTH)
            MiniGameState(
                kind = kind,
                sequence = List(length) { random.nextInt(MELODY_PAD_COUNT) },
                watchMs = length * MELODY_NOTE_DURATION_MS + MELODY_WATCH_LEAD_IN_MS,
                reward = MELODY_BASE_REWARD_COINS + (length - MELODY_MIN_LENGTH) / MELODY_BONUS_NOTES_PER_COIN,
            )
        }
        MiniGameKind.Math -> mathQuestion()
    }

    /** Processes one player tap ([choice]) and returns the updated or completed [MiniGameState]. */
    fun choose(state: MiniGameState, choice: Int): MiniGameState {
        if (state.finished) return state
        return when (state.kind) {
            MiniGameKind.Guess -> finish(state, if (choice == state.answer) OUTCOME_WIN else OUTCOME_LOSS)
            MiniGameKind.Math -> finish(
                state,
                if (state.options.getOrNull(choice) == state.answer) OUTCOME_WIN else OUTCOME_LOSS,
            )
            MiniGameKind.Hearts -> {
                val caught = state.heartsCaught + 1
                if (caught >= HEARTS_TARGET_COUNT) {
                    finish(state.copy(heartsCaught = caught), OUTCOME_WIN)
                } else {
                    state.copy(
                        heartsCaught = caught,
                        heartX = random.nextInt(HEARTS_SPAWN_MIN_X, HEARTS_SPAWN_MAX_X),
                        heartY = random.nextInt(HEARTS_SPAWN_MIN_Y, HEARTS_SPAWN_MAX_Y),
                    )
                }
            }
            MiniGameKind.Melody -> {
                if (state.melodyPhase != MelodyPhase.Repeat ||
                    state.sequence.getOrNull(state.inputIndex) != choice
                ) {
                    finish(state, OUTCOME_LOSS)
                } else {
                    val next = state.inputIndex + 1
                    if (next == state.sequence.size) {
                        finish(state.copy(inputIndex = next), OUTCOME_WIN)
                    } else {
                        state.copy(inputIndex = next)
                    }
                }
            }
        }
    }

    /** Advances timers by [elapsedMs], switching Melody from Watch to Repeat or expiring the round. */
    fun tick(state: MiniGameState, elapsedMs: Int): MiniGameState {
        if (state.finished || elapsedMs <= 0) return state
        if (state.kind == MiniGameKind.Melody && state.melodyPhase == MelodyPhase.Watch) {
            val remainingWatch = state.watchMs - elapsedMs
            return if (remainingWatch > 0) {
                state.copy(watchMs = remainingWatch)
            } else {
                state.copy(
                    melodyPhase = MelodyPhase.Repeat,
                    watchMs = 0,
                    remainingMs = MELODY_REPEAT_DURATION_MS,
                )
            }
        }
        if (state.kind != MiniGameKind.Guess && state.kind != MiniGameKind.Hearts &&
            state.kind != MiniGameKind.Melody
        ) return state
        val remaining = (state.remainingMs - elapsedMs).coerceAtLeast(0)
        return if (remaining == 0) finish(state.copy(remainingMs = 0), OUTCOME_LOSS) else {
            state.copy(remainingMs = remaining)
        }
    }

    /** Generates a random addition, subtraction, or multiplication question with operands in `1..12`. */
    private fun mathQuestion(): MiniGameState {
        val a = random.nextInt(MATH_OPERAND_MIN, MATH_OPERAND_MAX_EXCLUSIVE)
        val b = random.nextInt(MATH_OPERAND_MIN, MATH_OPERAND_MAX_EXCLUSIVE)
        return when (random.nextInt(MATH_OPERATOR_COUNT)) {
            0 -> mathState("$a + $b = ?", a + b)
            1 -> {
                val high = maxOf(a, b)
                val low = minOf(a, b)
                mathState("$high − $low = ?", high - low)
            }
            else -> mathState("$a × $b = ?", a * b)
        }
    }

    /** Builds [MATH_OPTION_COUNT] distinct multiple-choice answers around [answer]. */
    private fun mathState(question: String, answer: Int): MiniGameState {
        val correctSlot = random.nextInt(MATH_OPTION_COUNT)
        val options = MutableList(MATH_OPTION_COUNT) { -1 }
        options[correctSlot] = answer
        for (index in options.indices) {
            if (index == correctSlot) continue
            var candidate: Int
            do {
                val delta = random.nextInt(MATH_DISTRACTOR_MIN_DELTA, MATH_DISTRACTOR_MAX_EXCLUSIVE)
                candidate = (answer + if (delta == 0) 1 else delta).coerceAtLeast(0)
            } while (candidate == answer || candidate in options)
            options[index] = candidate
        }
        return MiniGameState(
            kind = MiniGameKind.Math,
            question = question,
            options = options,
            answer = answer,
        )
    }

    /** Marks [state] as finished with [outcome] and clears its countdown timer. */
    private fun finish(state: MiniGameState, outcome: Int): MiniGameState = state.copy(
        finished = true,
        outcome = outcome,
        remainingMs = 0,
    )
}
