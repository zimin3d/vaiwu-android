package su.neit.vaiwu

import kotlin.random.Random

enum class MiniGameKind(val reward: Int) {
    Guess(2),
    Hearts(2),
    Melody(2),
    Math(3),
}

enum class MelodyPhase {
    Watch,
    Repeat,
}

data class MiniGameState(
    val kind: MiniGameKind,
    val question: String = "",
    val options: List<Int> = emptyList(),
    val emotionId: Int = 0,
    val answer: Int = 0,
    val sequence: List<Int> = emptyList(),
    val inputIndex: Int = 0,
    val heartsCaught: Int = 0,
    val heartX: Int = 40,
    val heartY: Int = 35,
    val remainingMs: Int = 0,
    val melodyPhase: MelodyPhase = MelodyPhase.Watch,
    val watchMs: Int = 0,
    val finished: Boolean = false,
    val outcome: Int = 0,
    val reward: Int = kind.reward,
)

class MiniGameEngine(private val random: Random = Random.Default) {
    fun start(kind: MiniGameKind, melodyLength: Int = 4): MiniGameState = when (kind) {
        MiniGameKind.Guess -> {
            val answer = random.nextInt(4)
            MiniGameState(
                kind = kind,
                answer = answer,
                emotionId = answer + 1,
                remainingMs = 3_000,
            )
        }
        MiniGameKind.Hearts -> MiniGameState(
            kind = kind,
            heartX = random.nextInt(10, 86),
            heartY = random.nextInt(15, 76),
            remainingMs = 8_000,
        )
        MiniGameKind.Melody -> {
            val length = melodyLength.coerceIn(4, 8)
            MiniGameState(
                kind = kind,
                sequence = List(length) { random.nextInt(4) },
                watchMs = length * 600 + 400,
                reward = 2 + (length - 4) / 2,
            )
        }
        MiniGameKind.Math -> mathQuestion()
    }

    fun choose(state: MiniGameState, choice: Int): MiniGameState {
        if (state.finished) return state
        return when (state.kind) {
            MiniGameKind.Guess -> finish(state, if (choice == state.answer) 1 else -1)
            MiniGameKind.Math -> finish(state, if (state.options.getOrNull(choice) == state.answer) 1 else -1)
            MiniGameKind.Hearts -> {
                val caught = state.heartsCaught + 1
                if (caught >= 5) {
                    finish(state.copy(heartsCaught = caught), 1)
                } else {
                    state.copy(
                        heartsCaught = caught,
                        heartX = random.nextInt(10, 86),
                        heartY = random.nextInt(15, 76),
                    )
                }
            }
            MiniGameKind.Melody -> {
                if (state.melodyPhase != MelodyPhase.Repeat ||
                    state.sequence.getOrNull(state.inputIndex) != choice
                ) {
                    finish(state, -1)
                } else {
                    val next = state.inputIndex + 1
                    if (next == state.sequence.size) {
                        finish(state.copy(inputIndex = next), 1)
                    } else {
                        state.copy(inputIndex = next)
                    }
                }
            }
        }
    }

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
                    remainingMs = 10_000,
                )
            }
        }
        if (state.kind != MiniGameKind.Guess && state.kind != MiniGameKind.Hearts &&
            state.kind != MiniGameKind.Melody
        ) return state
        val remaining = (state.remainingMs - elapsedMs).coerceAtLeast(0)
        return if (remaining == 0) finish(state.copy(remainingMs = 0), -1) else {
            state.copy(remainingMs = remaining)
        }
    }

    private fun mathQuestion(): MiniGameState {
        val a = random.nextInt(1, 13)
        val b = random.nextInt(1, 13)
        return when (random.nextInt(3)) {
            0 -> mathState("$a + $b = ?", a + b)
            1 -> {
                val high = maxOf(a, b)
                val low = minOf(a, b)
                mathState("$high − $low = ?", high - low)
            }
            else -> mathState("$a × $b = ?", a * b)
        }
    }

    private fun mathState(question: String, answer: Int): MiniGameState {
        val correctSlot = random.nextInt(4)
        val options = MutableList(4) { -1 }
        options[correctSlot] = answer
        for (index in options.indices) {
            if (index == correctSlot) continue
            var candidate: Int
            do {
                val delta = random.nextInt(-5, 6)
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

    private fun finish(state: MiniGameState, outcome: Int): MiniGameState = state.copy(
        finished = true,
        outcome = outcome,
        remainingMs = 0,
    )
}
