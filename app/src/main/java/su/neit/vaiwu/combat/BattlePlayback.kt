package su.neit.vaiwu.combat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

const val FX_LIFETIME_MS = 1050L

enum class PoseKind {
    Lunge,
    Cast,
    Hit,
    Fall,
}

data class FighterPose(
    val kind: PoseKind,
    val dx: Float,
    val dy: Float,
    val key: Int,
)

data class FxItem(
    val key: Int,
    val event: CombatEvent,
    val from: Pos? = null,
    val to: Pos? = null,
)

data class BeatTiming(
    val impactMs: Long,
    val lengthMs: Long,
)

data class ScheduledBeat(
    val atMs: Long,
    val event: CombatEvent,
    val fxItem: FxItem,
    val poseTargetId: String?,
    val pose: FighterPose?,
    val shake: Boolean,
)

data class PlaybackUiState(
    val shown: CombatState,
    val fx: List<FxItem>,
    val poses: Map<String, FighterPose>,
    val captionEvent: CombatEvent?,
    val shakeCount: Int,
    val busy: Boolean,
    val rate: Float,
    val skip: () -> Unit,
)

/** Impact delay and beat length (ms) for each primary combat event. */
fun beatTiming(event: CombatEvent): BeatTiming = when (event) {
    is CombatEvent.Move -> BeatTiming(impactMs = 0L, lengthMs = if (event.teleport) 380L else 360L)
    is CombatEvent.SkillCast -> if (event.effect == SkillEffect.Control) {
        BeatTiming(impactMs = 360L, lengthMs = 640L)
    } else {
        BeatTiming(impactMs = 420L, lengthMs = 700L)
    }
    is CombatEvent.WeaponStrike -> if (event.melee) {
        BeatTiming(impactMs = 180L, lengthMs = 500L)
    } else {
        BeatTiming(impactMs = 160L, lengthMs = 480L)
    }
    is CombatEvent.ShieldUp -> BeatTiming(impactMs = 0L, lengthMs = 500L)
    is CombatEvent.Heal -> BeatTiming(impactMs = 0L, lengthMs = 520L)
    is CombatEvent.MpRestore -> BeatTiming(impactMs = 0L, lengthMs = 460L)
    is CombatEvent.CommandMark -> BeatTiming(impactMs = 0L, lengthMs = 540L)
    is CombatEvent.FleeAttempt -> BeatTiming(impactMs = 0L, lengthMs = 500L)
    else -> BeatTiming(impactMs = 0L, lengthMs = 0L)
}

/** True if [event] is a consequence of a preceding strike/cast and should play on impact. */
fun isConsequenceEvent(event: CombatEvent): Boolean = when (event) {
    is CombatEvent.Damage,
    is CombatEvent.Blocked,
    is CombatEvent.StatusApplied,
    is CombatEvent.Down,
    -> true
    else -> false
}

/**
 * Rewinds [state] back before all [pending] events so HP bars, positions, and fallen
 * sprites reflect the world BEFORE unplayed impacts land.
 */
fun rewindState(state: CombatState, pending: List<CombatEvent>): CombatState {
    if (pending.isEmpty()) return state
    val rewound = state.combatants.map { c ->
        var hp = c.hp
        var mp = c.mp
        var pos: Pos? = null
        var falling = false
        for (e in pending) {
            when (e) {
                is CombatEvent.Damage -> if (e.targetId == c.id) hp += e.amount
                is CombatEvent.Heal -> if (e.targetId == c.id) hp -= e.amount
                is CombatEvent.MpRestore -> if (e.targetId == c.id) mp -= e.amount
                is CombatEvent.Move -> if (e.actorId == c.id && pos == null) pos = e.from
                is CombatEvent.Down -> if (e.targetId == c.id) falling = true
                else -> Unit
            }
        }
        if (hp == c.hp && mp == c.mp && pos == null && !falling) {
            c
        } else {
            val clampedHp = min(c.maxHp, if (falling) max(1, hp) else max(0, hp))
            c.copy(
                hp = clampedHp,
                mp = mp.coerceIn(0, c.maxMp),
                pos = pos ?: c.pos,
            )
        }
    }
    return state.copy(combatants = rewound)
}

private fun unitVector(from: Pos, to: Pos): Pair<Float, Float> {
    val dx = (to.x - from.x).toFloat()
    val dy = (to.y - from.y).toFloat()
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > 0.001f } ?: 1f
    return (dx / len) to (dy / len)
}

/**
 * Builds the deterministic playback timeline (beats + total duration in ms) for [freshEvents].
 */
fun buildPlaybackSchedule(
    state: CombatState,
    freshEvents: List<CombatEvent>,
): Pair<List<ScheduledBeat>, Long> {
    if (freshEvents.isEmpty()) return emptyList<ScheduledBeat>() to 0L
    val where = HashMap<String, Pos>()
    for (c in rewindState(state, freshEvents).combatants) {
        where[c.id] = c.pos
    }

    val beats = mutableListOf<ScheduledBeat>()
    var cursor = 0L
    var lastStart = 0L
    var lastImpact = 0L
    var lastActorPos: Pos? = null
    var lastTargetId: String? = null

    for (event in freshEvents) {
        val at: Long
        if (isConsequenceEvent(event)) {
            val targetId = when (event) {
                is CombatEvent.Damage -> event.targetId
                is CombatEvent.Blocked -> event.targetId
                is CombatEvent.StatusApplied -> event.targetId
                is CombatEvent.Down -> event.targetId
                else -> null
            }
            val own = targetId != null && targetId == lastTargetId
            at = if (own) lastStart + lastImpact else cursor
            cursor = max(
                cursor,
                at + when {
                    event is CombatEvent.Down -> 560L
                    own -> 220L
                    else -> 380L
                },
            )
        } else {
            at = cursor
            val timing = beatTiming(event)
            lastStart = at
            lastImpact = timing.impactMs
            cursor = at + timing.lengthMs
        }

        val fxItem = when (event) {
            is CombatEvent.SkillCast -> {
                val from = where[event.actorId]
                val to = event.targetId?.let { where[it] } ?: from
                lastActorPos = from
                lastTargetId = event.targetId
                FxItem(event.id, event, from, to)
            }
            is CombatEvent.WeaponStrike -> {
                val from = where[event.actorId]
                val to = where[event.targetId]
                lastActorPos = from
                lastTargetId = event.targetId
                FxItem(event.id, event, from, to)
            }
            is CombatEvent.Move -> {
                lastActorPos = null
                lastTargetId = null
                FxItem(event.id, event, event.from, event.to)
            }
            is CombatEvent.ShieldUp,
            is CombatEvent.Heal,
            is CombatEvent.MpRestore,
            is CombatEvent.CommandMark,
            is CombatEvent.FleeAttempt,
            -> {
                lastActorPos = null
                lastTargetId = null
                FxItem(event.id, event)
            }
            else -> FxItem(event.id, event)
        }

        val (poseTarget, pose) = computePose(event, fxItem, lastActorPos, where)
        val shake = (event is CombatEvent.Damage && (event.amount >= 8 || event.crit)) ||
            event is CombatEvent.Down
        beats += ScheduledBeat(
            atMs = at,
            event = event,
            fxItem = fxItem,
            poseTargetId = poseTarget,
            pose = pose,
            shake = shake,
        )
        if (event is CombatEvent.Move) {
            where[event.actorId] = event.to
        }
    }
    return beats to (cursor + 150L)
}

private fun computePose(
    event: CombatEvent,
    fxItem: FxItem,
    attackerPos: Pos?,
    where: Map<String, Pos>,
): Pair<String?, FighterPose?> = when (event) {
    is CombatEvent.WeaponStrike -> {
        val from = fxItem.from
        val to = fxItem.to
        if (from != null && to != null) {
            val (ux, uy) = unitVector(from, to)
            val reach = if (event.melee) 0.34f else 0.12f
            val kind = if (event.melee) PoseKind.Lunge else PoseKind.Cast
            event.actorId to FighterPose(kind, ux * reach, uy * reach, event.id)
        } else {
            null to null
        }
    }
    is CombatEvent.SkillCast -> {
        val from = fxItem.from
        val to = fxItem.to
        if (from != null && to != null && from != to) {
            val (ux, uy) = unitVector(from, to)
            event.actorId to FighterPose(PoseKind.Cast, ux * 0.14f, uy * 0.14f, event.id)
        } else {
            event.actorId to FighterPose(PoseKind.Cast, 0f, -0.10f, event.id)
        }
    }
    is CombatEvent.Damage -> {
        val at = where[event.targetId]
        val (ux, uy) = if (attackerPos != null && at != null) unitVector(attackerPos, at) else 0f to -0.3f
        event.targetId to FighterPose(PoseKind.Hit, ux * 0.14f, uy * 0.14f, event.id)
    }
    is CombatEvent.Blocked -> {
        val at = where[event.targetId]
        val (ux, uy) = if (attackerPos != null && at != null) unitVector(attackerPos, at) else 0f to -0.2f
        event.targetId to FighterPose(PoseKind.Hit, ux * 0.08f, uy * 0.08f, event.id)
    }
    is CombatEvent.Down -> event.targetId to FighterPose(PoseKind.Fall, 0f, 0.15f, event.id)
    else -> null to null
}

/**
 * Coroutine-driven playback controller for Compose UI. Rewinds [state] while
 * events play out step-by-step, triggers SFX on each beat, and supports `×2`
 * speed plus instant `skip()`.
 */
@Composable
fun rememberBattlePlayback(
    state: CombatState,
    fastMode: Boolean,
    onEventSound: (CombatEvent) -> Unit,
): PlaybackUiState {
    val rate = if (fastMode) 2f else 1f
    var lastPlayedSeq by remember { mutableIntStateOf(state.eventSeq) }
    var pending by remember { mutableStateOf<List<CombatEvent>>(emptyList()) }
    var activeFx by remember { mutableStateOf<List<FxItem>>(emptyList()) }
    var poses by remember { mutableStateOf<Map<String, FighterPose>>(emptyMap()) }
    var captionEvent by remember { mutableStateOf<CombatEvent?>(null) }
    var shakeCount by remember { mutableIntStateOf(0) }
    var skipNonce by remember { mutableIntStateOf(0) }
    val currentRate = remember { mutableFloatStateOf(rate) }
    currentRate.floatValue = rate

    val skip = remember {
        {
            skipNonce++
            pending = emptyList()
            activeFx = emptyList()
            poses = emptyMap()
            captionEvent = null
        }
    }

    LaunchedEffect(state.eventSeq, state.events, skipNonce) {
        if (state.eventSeq < lastPlayedSeq) {
            // Undo or fresh battle reset the sequence counter.
            lastPlayedSeq = state.eventSeq
            pending = emptyList()
            activeFx = emptyList()
            poses = emptyMap()
            captionEvent = null
            return@LaunchedEffect
        }
        val fresh = state.events.filter { it.id > lastPlayedSeq }
        lastPlayedSeq = state.eventSeq
        if (fresh.isEmpty()) {
            pending = emptyList()
            return@LaunchedEffect
        }

        val (beats, totalMs) = buildPlaybackSchedule(state, fresh)
        pending = fresh
        val sortedBeats = beats.sortedBy { it.atMs }
        var elapsed = 0L

        for (beat in sortedBeats) {
            val wait = ((beat.atMs - elapsed).coerceAtLeast(0L) / currentRate.floatValue).toLong()
            if (wait > 0L) delay(wait)
            elapsed = beat.atMs

            pending = pending.filterNot { it.id == beat.event.id }
            onEventSound(beat.event)
            if (!isConsequenceEvent(beat.event)) {
                captionEvent = beat.event
            }
            if (beat.shake) {
                shakeCount++
            }
            activeFx = (activeFx + beat.fxItem).takeLast(8)
            if (beat.poseTargetId != null && beat.pose != null) {
                poses = poses + (beat.poseTargetId to beat.pose)
            }
        }

        val tailWait = ((totalMs - elapsed).coerceAtLeast(0L) / currentRate.floatValue).toLong()
        if (tailWait > 0L) delay(tailWait)
        pending = emptyList()
        activeFx = emptyList()
        poses = emptyMap()
        captionEvent = null
    }

    val shown = remember(state, pending) { rewindState(state, pending) }
    return PlaybackUiState(
        shown = shown,
        fx = activeFx,
        poses = poses,
        captionEvent = captionEvent,
        shakeCount = shakeCount,
        busy = pending.isNotEmpty(),
        rate = rate,
        skip = skip,
    )
}
