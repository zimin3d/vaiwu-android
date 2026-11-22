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

/** Default lifetime (ms) for transient combat VFX badges on the battlefield. */
const val FX_LIFETIME_MS = 1050L

// Primary beat timelines (ms): impactMs is when Damage/Status fires; lengthMs is the full beat duration.
private const val MOVE_TELEPORT_BEAT_MS = 380L
private const val MOVE_WALK_BEAT_MS = 360L
private const val CONTROL_IMPACT_MS = 360L
private const val CONTROL_BEAT_MS = 640L
private const val SPELL_IMPACT_MS = 420L
private const val SPELL_BEAT_MS = 700L
private const val MELEE_IMPACT_MS = 180L
private const val MELEE_BEAT_MS = 500L
private const val RANGED_IMPACT_MS = 160L
private const val RANGED_BEAT_MS = 480L
private const val SHIELD_BEAT_MS = 500L
private const val HEAL_BEAT_MS = 520L
private const val MP_RESTORE_BEAT_MS = 460L
private const val COMMAND_BEAT_MS = 540L
private const val FLEE_BEAT_MS = 500L

// Tail padding (ms) reserved after consequence events and after the entire playback sequence.
private const val CONSEQUENCE_DOWN_TAIL_MS = 560L
private const val CONSEQUENCE_OWN_TAIL_MS = 220L
private const val CONSEQUENCE_OTHER_TAIL_MS = 380L
private const val PLAYBACK_TAIL_PADDING_MS = 150L

/** Minimum non-crit hit damage required to trigger a camera shake on impact. */
private const val SHAKE_MIN_DAMAGE = 8

/** Maximum simultaneous VFX items kept visible so multi-hit bursts stay legible. */
private const val MAX_ACTIVE_FX = 8

/** Playback speed multipliers for normal (`1x`) and fast (`2x`) combat animation modes. */
private const val PLAYBACK_NORMAL_RATE = 1f
private const val PLAYBACK_FAST_RATE = 2f

/** Vector length epsilon below which [unitVector] treats two positions as coincident. */
private const val UNIT_VECTOR_EPSILON = 0.001f

// Fighter sprite nudge distances expressed as fractions of one grid cell.
private const val POSE_MELEE_LUNGE_REACH = 0.34f
private const val POSE_RANGED_RECOIL_REACH = 0.12f
private const val POSE_CAST_LEAN_REACH = 0.14f
private const val POSE_SELF_CAST_LIFT = -0.10f
private const val POSE_HIT_KNOCKBACK = 0.14f
private const val POSE_HIT_FALLBACK_DY = -0.3f
private const val POSE_BLOCK_KNOCKBACK = 0.08f
private const val POSE_BLOCK_FALLBACK_DY = -0.2f
private const val POSE_FALL_DROP = 0.15f

/** Visual pose animation applied to a combatant sprite during a playback beat. */
enum class PoseKind {
    Lunge,
    Cast,
    Hit,
    Fall,
}

/**
 * Per-combatant fractional cell offset (`dx`, `dy`) applied by [BattlefieldCanvas]
 * to animate lunges, spell casts, hit knockback, and defeats.
 */
data class FighterPose(
    val kind: PoseKind,
    val dx: Float,
    val dy: Float,
    val key: Int,
)

/** Renderable VFX entry pairing a [CombatEvent] with resolved source/target grid cells. */
data class FxItem(
    val key: Int,
    val event: CombatEvent,
    val from: Pos? = null,
    val to: Pos? = null,
)

/** Timing envelope for a primary combat beat: [impactMs] until hit lands, [lengthMs] total duration. */
data class BeatTiming(
    val impactMs: Long,
    val lengthMs: Long,
)

/** One scheduled step on the deterministic combat playback timeline. */
data class ScheduledBeat(
    val atMs: Long,
    val event: CombatEvent,
    val fxItem: FxItem,
    val poseTargetId: String?,
    val pose: FighterPose?,
    val shake: Boolean,
)

/** Snapshot of in-flight animation state consumed by [TacticalBattleScreen] and [BattlefieldCanvas]. */
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

/** Returns the impact delay and total beat duration (ms) for each primary [CombatEvent]. */
fun beatTiming(event: CombatEvent): BeatTiming = when (event) {
    is CombatEvent.Move -> BeatTiming(
        impactMs = 0L,
        lengthMs = if (event.teleport) MOVE_TELEPORT_BEAT_MS else MOVE_WALK_BEAT_MS,
    )
    is CombatEvent.SkillCast -> if (event.effect == SkillEffect.Control) {
        BeatTiming(impactMs = CONTROL_IMPACT_MS, lengthMs = CONTROL_BEAT_MS)
    } else {
        BeatTiming(impactMs = SPELL_IMPACT_MS, lengthMs = SPELL_BEAT_MS)
    }
    is CombatEvent.WeaponStrike -> if (event.melee) {
        BeatTiming(impactMs = MELEE_IMPACT_MS, lengthMs = MELEE_BEAT_MS)
    } else {
        BeatTiming(impactMs = RANGED_IMPACT_MS, lengthMs = RANGED_BEAT_MS)
    }
    is CombatEvent.ShieldUp -> BeatTiming(impactMs = 0L, lengthMs = SHIELD_BEAT_MS)
    is CombatEvent.Heal -> BeatTiming(impactMs = 0L, lengthMs = HEAL_BEAT_MS)
    is CombatEvent.MpRestore -> BeatTiming(impactMs = 0L, lengthMs = MP_RESTORE_BEAT_MS)
    is CombatEvent.CommandMark -> BeatTiming(impactMs = 0L, lengthMs = COMMAND_BEAT_MS)
    is CombatEvent.FleeAttempt -> BeatTiming(impactMs = 0L, lengthMs = FLEE_BEAT_MS)
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

/** Returns a normalized direction vector `(dx, dy)` pointing from [from] toward [to]. */
private fun unitVector(from: Pos, to: Pos): Pair<Float, Float> {
    val dx = (to.x - from.x).toFloat()
    val dy = (to.y - from.y).toFloat()
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > UNIT_VECTOR_EPSILON } ?: 1f
    return (dx / len) to (dy / len)
}

/**
 * Builds the deterministic playback timeline (ordered beats + total duration in ms) for [freshEvents].
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
                    event is CombatEvent.Down -> CONSEQUENCE_DOWN_TAIL_MS
                    own -> CONSEQUENCE_OWN_TAIL_MS
                    else -> CONSEQUENCE_OTHER_TAIL_MS
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
        val shake = (event is CombatEvent.Damage && (event.amount >= SHAKE_MIN_DAMAGE || event.crit)) ||
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
    return beats to (cursor + PLAYBACK_TAIL_PADDING_MS)
}

/** Maps a combat event into the target combatant id and sprite offset pose to play on that beat. */
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
            val reach = if (event.melee) POSE_MELEE_LUNGE_REACH else POSE_RANGED_RECOIL_REACH
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
            event.actorId to FighterPose(PoseKind.Cast, ux * POSE_CAST_LEAN_REACH, uy * POSE_CAST_LEAN_REACH, event.id)
        } else {
            event.actorId to FighterPose(PoseKind.Cast, 0f, POSE_SELF_CAST_LIFT, event.id)
        }
    }
    is CombatEvent.Damage -> {
        val at = where[event.targetId]
        val (ux, uy) = if (attackerPos != null && at != null) unitVector(attackerPos, at) else 0f to POSE_HIT_FALLBACK_DY
        event.targetId to FighterPose(PoseKind.Hit, ux * POSE_HIT_KNOCKBACK, uy * POSE_HIT_KNOCKBACK, event.id)
    }
    is CombatEvent.Blocked -> {
        val at = where[event.targetId]
        val (ux, uy) = if (attackerPos != null && at != null) unitVector(attackerPos, at) else 0f to POSE_BLOCK_FALLBACK_DY
        event.targetId to FighterPose(PoseKind.Hit, ux * POSE_BLOCK_KNOCKBACK, uy * POSE_BLOCK_KNOCKBACK, event.id)
    }
    is CombatEvent.Down -> event.targetId to FighterPose(PoseKind.Fall, 0f, POSE_FALL_DROP, event.id)
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
    val rate = if (fastMode) PLAYBACK_FAST_RATE else PLAYBACK_NORMAL_RATE
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
            activeFx = (activeFx + beat.fxItem).takeLast(MAX_ACTIVE_FX)
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
