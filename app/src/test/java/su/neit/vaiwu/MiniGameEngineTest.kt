package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MiniGameEngineTest {
    @Test
    fun guessHasMatchingEmotionAndCannotFinishTwice() {
        val engine = MiniGameEngine(Random(1))
        val started = engine.start(MiniGameKind.Guess)
        assertEquals(started.answer + 1, started.emotionId)
        val won = engine.choose(started, started.answer)
        assertTrue(won.finished)
        assertEquals(1, won.outcome)
        assertEquals(2, won.reward)
        assertSame(won, engine.choose(won, started.answer))
        assertSame(won, engine.tick(won, 1_000))
    }

    @Test
    fun guessAndHeartsLoseOnTimeout() {
        val engine = MiniGameEngine(Random(2))
        val guess = engine.tick(engine.start(MiniGameKind.Guess), 3_000)
        val hearts = engine.tick(engine.start(MiniGameKind.Hearts), 8_000)
        assertEquals(-1, guess.outcome)
        assertEquals(-1, hearts.outcome)
        assertEquals(0, guess.remainingMs)
        assertEquals(0, hearts.remainingMs)
    }

    @Test
    fun heartsRequireFiveCatchesAndMoveBetweenCatches() {
        val engine = MiniGameEngine(Random(3))
        var state = engine.start(MiniGameKind.Hearts)
        repeat(4) {
            val oldPosition = state.heartX to state.heartY
            state = engine.choose(state, 0)
            assertFalse(state.finished)
            assertTrue(oldPosition != (state.heartX to state.heartY))
        }
        state = engine.choose(state, 0)
        assertEquals(5, state.heartsCaught)
        assertEquals(1, state.outcome)
        assertTrue(state.finished)
    }

    @Test
    fun melodyRewardsLengthsAndRejectsWrongNote() {
        listOf(4 to 2, 6 to 3, 8 to 4).forEach { (length, reward) ->
            val engine = MiniGameEngine(Random(length))
            var state = engine.start(MiniGameKind.Melody, length)
            assertEquals(reward, state.reward)
            state = engine.tick(state, state.watchMs)
            assertEquals(MelodyPhase.Repeat, state.melodyPhase)
            assertEquals(10_000, state.remainingMs)
            state.sequence.forEach { note -> state = engine.choose(state, note) }
            assertTrue(state.finished)
            assertEquals(1, state.outcome)
        }
        val engine = MiniGameEngine(Random(9))
        var state = engine.tick(engine.start(MiniGameKind.Melody), 3_000)
        val wrong = (state.sequence.first() + 1) % 4
        state = engine.choose(state, wrong)
        assertEquals(-1, state.outcome)
        assertTrue(state.finished)
    }

    @Test
    fun melodyTimesOutDuringInputButNotDuringPlayback() {
        val engine = MiniGameEngine(Random(4))
        var state = engine.start(MiniGameKind.Melody)
        state = engine.tick(state, 500)
        assertEquals(MelodyPhase.Watch, state.melodyPhase)
        assertFalse(state.finished)
        state = engine.tick(state, state.watchMs)
        state = engine.tick(state, 10_000)
        assertTrue(state.finished)
        assertEquals(-1, state.outcome)
    }

    @Test
    fun mathOptionsAreUniqueAndContainExactlyOneAnswer() {
        repeat(100) { seed ->
            val engine = MiniGameEngine(Random(seed))
            val state = engine.start(MiniGameKind.Math)
            assertEquals(4, state.options.size)
            assertEquals(4, state.options.toSet().size)
            assertEquals(1, state.options.count { it == state.answer })
            val won = engine.choose(state, state.options.indexOf(state.answer))
            assertEquals(1, won.outcome)
            assertEquals(3, won.reward)
            val wrongIndex = state.options.indexOfFirst { it != state.answer }
            assertEquals(-1, engine.choose(state, wrongIndex).outcome)
        }
    }

    @Test
    fun invalidMathChoiceLosesWithoutReadingOutsideOptions() {
        val engine = MiniGameEngine(Random(5))
        val state = engine.start(MiniGameKind.Math)
        assertEquals(-1, engine.choose(state, -1).outcome)
        assertEquals(-1, engine.choose(state, 99).outcome)
    }
}
