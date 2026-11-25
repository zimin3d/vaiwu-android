package su.neit.vaiwu.combat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.neit.vaiwu.AssetImage
import su.neit.vaiwu.Palette
import su.neit.vaiwu.R
import su.neit.vaiwu.VaiwuColors
import su.neit.vaiwu.VaiwuPrimaryButton
import su.neit.vaiwu.VaiwuRadius
import su.neit.vaiwu.VaiwuSecondaryButton
import su.neit.vaiwu.VaiwuSizes
import su.neit.vaiwu.VaiwuSpacing
import su.neit.vaiwu.battleBackgroundForHour
import su.neit.vaiwu.pressScale
import kotlin.math.roundToInt

/** Viewport width at which the battle switches from portrait stack to 2-column landscape layout. */
private val WIDE_LAYOUT_BREAKPOINT = 680.dp

/** Column weights in wide/landscape layout: arena gets 1.25x the width of the action controls. */
private const val WIDE_ARENA_COLUMN_WEIGHT = 1.25f
private const val WIDE_CONTROLS_COLUMN_WEIGHT = 1f

/** Dark backdrop scrim opacity keeping HUD cards legible over illustrated arena art. */
private const val BACKDROP_SCRIM_ALPHA = 0.80f

/** Maximum number of top damage-forecast lines shown in [TargetForecastCard]. */
private const val TARGET_FORECAST_MAX_LINES = 2

/** Number of recent combat log entries displayed when [CombatLogCard] is expanded. */
private const val COMBAT_LOG_VISIBLE_ENTRIES = 14

/** Indicator dot sizes for AP pips and skill element badges. */
private val AP_PIP_SIZE = 10.dp
private val SKILL_ELEMENT_DOT_SIZE = 8.dp
private val SKIP_BUTTON_MIN_HEIGHT = VaiwuSizes.minTouchTarget

/** Relative weight of the primary End Turn button next to the Flee button. */
private const val END_TURN_BUTTON_WEIGHT = 1.5f

/**
 * Full tactical 9x5 combat screen replacing the legacy `BattleScreen` for
 * Adventure and Dungeon modes in `vaiwu-android`.
 *
 * Adapts to portrait phones (vertical stack) and landscape / tablets
 * (side-by-side arena and controls) without vertical layout shifts on tap.
 */
@Composable
fun TacticalBattleScreen(
    session: TacticalBattleSession,
    hour: Int,
    coachEnabled: Boolean,
    fastMode: Boolean,
    onAction: (CombatAction) -> Unit,
    onUndo: () -> Unit,
    onNextRoom: () -> Unit,
    onExit: () -> Unit,
    onToggleCoach: (Boolean) -> Unit,
    onToggleFast: (Boolean) -> Unit,
    onEventSound: (CombatEvent) -> Unit,
) {
    val context = LocalContext.current
    val state = session.state
    val playback = rememberBattlePlayback(
        state = state,
        fastMode = fastMode,
        onEventSound = onEventSound,
    )
    val shown = playback.shown
    val player = shown.player
    val enemies = shown.aliveEnemies
    val canAct = state.isPlayerTurn && !playback.busy && !session.isOver

    var selectedTargetId by remember(session.room, session.dungeon) {
        mutableStateOf(enemies.firstOrNull()?.id)
    }
    var pendingMoveDest by remember { mutableStateOf<Pos?>(null) }
    var armedTeleportSkill by remember { mutableStateOf<TacticalSkill?>(null) }
    var logExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.eventSeq, state.activeIndex, state.round, session.room) {
        pendingMoveDest = null
        armedTeleportSkill = null
        val aliveIds = state.aliveEnemies.map { it.id }
        if (selectedTargetId == null || selectedTargetId !in aliveIds) {
            selectedTargetId = aliveIds.firstOrNull()
        }
    }

    val selectedTarget = enemies.firstOrNull { it.id == selectedTargetId } ?: enemies.firstOrNull()
    val pendingMovePath = remember(shown, pendingMoveDest, canAct) {
        if (!canAct || pendingMoveDest == null) {
            null
        } else {
            TacticalCombat.reachablePath(shown, player, pendingMoveDest!!)
        }
    }

    val intentsList = remember(state, canAct) {
        if (canAct) TacticalCombat.enemyIntents(state) else emptyList()
    }
    val intentsById = remember(intentsList) { intentsList.associateBy { it.actorId } }

    val coachHint = remember(state, selectedTarget?.id, coachEnabled, canAct) {
        if (coachEnabled && canAct) TacticalCoach.advise(state, selectedTarget?.id) else null
    }

    val overlayPrompt = when {
        armedTeleportSkill != null -> stringResource(R.string.tactical_teleport_prompt, armedTeleportSkill!!.range)
        pendingMovePath != null -> stringResource(
            R.string.tactical_confirm_move,
            pendingMovePath.size,
            pathCost(pendingMovePath, player.cellsPerAp),
        )
        else -> null
    }

    val backdropAsset = arenaBackdropAssetName(state.grid.theme) ?: battleBackgroundForHour(hour)

    val handleEnemyTap: (String) -> Unit = { enemyId ->
        selectedTargetId = enemyId
        pendingMoveDest = null
        armedTeleportSkill = null
    }

    val handleCellTap: (Pos) -> Unit = { tapped ->
        if (canAct) {
            val tp = armedTeleportSkill
            if (tp != null) {
                armedTeleportSkill = null
                if (TacticalCombat.canTeleportTo(state, player, tp, tapped)) {
                    onAction(CombatAction.UseSkill(tp.id, targetPos = tapped))
                }
            } else {
                val path = TacticalCombat.reachablePath(state, player, tapped)
                if (path == null) {
                    pendingMoveDest = null
                } else if (pendingMoveDest == tapped) {
                    pendingMoveDest = null
                    onAction(CombatAction.Move(tapped))
                } else {
                    pendingMoveDest = tapped
                }
            }
        }
    }

    val handleSkillTap: (TacticalSkill) -> Unit = { skill ->
        when (skill.effect) {
            SkillEffect.Defense, SkillEffect.Heal, SkillEffect.Veil -> {
                onAction(CombatAction.UseSkill(skill.id))
            }
            SkillEffect.Teleport -> {
                pendingMoveDest = null
                armedTeleportSkill = if (armedTeleportSkill?.id == skill.id) null else skill
            }
            SkillEffect.Attack, SkillEffect.Control -> {
                selectedTarget?.let { onAction(CombatAction.UseSkill(skill.id, targetId = it.id)) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AssetImage(
            assetName = backdropAsset,
            description = stringResource(R.string.battle_terrain),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(Palette.Background).copy(alpha = BACKDROP_SCRIM_ALPHA)),
        ) {
            val wideLayout = maxWidth >= WIDE_LAYOUT_BREAKPOINT

            if (wideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(WIDE_ARENA_COLUMN_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        TurnOrderStrip(state = shown, selectedTargetId = selectedTarget?.id, onSelectEnemy = handleEnemyTap)
                        BattlefieldCanvas(
                            state = state,
                            playback = playback,
                            selectedTargetId = selectedTarget?.id,
                            pendingMovePath = pendingMovePath,
                            teleportSkill = armedTeleportSkill,
                            intents = intentsById,
                            coachHint = coachHint,
                            onCellTap = handleCellTap,
                            onEnemyTap = handleEnemyTap,
                            bottomOverlayText = overlayPrompt,
                        )
                        CombatLogCard(
                            state = shown,
                            expanded = logExpanded,
                            onToggle = { logExpanded = !logExpanded },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(WIDE_CONTROLS_COLUMN_WEIGHT)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                        ) {
                            HeroHudCard(
                                session = session,
                                player = player,
                                shown = shown,
                                coachEnabled = coachEnabled,
                                fastMode = fastMode,
                                busy = playback.busy,
                                onToggleCoach = onToggleCoach,
                                onToggleFast = onToggleFast,
                                onSkip = playback.skip,
                            )
                            if (coachEnabled && coachHint != null && !session.isOver) {
                                CoachBannerCard(text = TacticalStrings.formatCoachHint(context, state, coachHint))
                            }
                            if (!session.isOver || playback.busy) {
                                selectedTarget?.let {
                                    TargetForecastCard(state = shown, target = it, intent = intentsById[it.id])
                                }
                            }
                        }
                        if (session.isOver && !playback.busy) {
                            BattleResultCard(session = session, onNextRoom = onNextRoom, onExit = onExit)
                        } else {
                            ActionDeck(
                                state = state,
                                player = player,
                                target = selectedTarget,
                                canAct = canAct,
                                canUndo = session.undoStack.isNotEmpty() && canAct,
                                coachHint = coachHint,
                                armedTeleportId = armedTeleportSkill?.id,
                                onWeapon = { selectedTarget?.let { onAction(CombatAction.WeaponAttack(it.id)) } },
                                onSkill = handleSkillTap,
                                onHpPotion = { onAction(CombatAction.UseHpPotion) },
                                onMpPotion = { onAction(CombatAction.UseMpPotion) },
                                onUndo = onUndo,
                                onFlee = { onAction(CombatAction.Flee) },
                                onEndTurn = { onAction(CombatAction.EndTurn) },
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        HeroHudCard(
                            session = session,
                            player = player,
                            shown = shown,
                            coachEnabled = coachEnabled,
                            fastMode = fastMode,
                            busy = playback.busy,
                            onToggleCoach = onToggleCoach,
                            onToggleFast = onToggleFast,
                            onSkip = playback.skip,
                        )
                        TurnOrderStrip(state = shown, selectedTargetId = selectedTarget?.id, onSelectEnemy = handleEnemyTap)
                        BattlefieldCanvas(
                            state = state,
                            playback = playback,
                            selectedTargetId = selectedTarget?.id,
                            pendingMovePath = pendingMovePath,
                            teleportSkill = armedTeleportSkill,
                            intents = intentsById,
                            coachHint = coachHint,
                            onCellTap = handleCellTap,
                            onEnemyTap = handleEnemyTap,
                            bottomOverlayText = overlayPrompt,
                        )
                        if (coachEnabled && coachHint != null && !session.isOver) {
                            CoachBannerCard(text = TacticalStrings.formatCoachHint(context, state, coachHint))
                        }
                        if (!session.isOver || playback.busy) {
                            selectedTarget?.let {
                                TargetForecastCard(state = shown, target = it, intent = intentsById[it.id])
                            }
                        }
                        CombatLogCard(
                            state = shown,
                            expanded = logExpanded,
                            onToggle = { logExpanded = !logExpanded },
                        )
                    }
                    // Pinned bottom controls dock: dynamic arena/coach/target height
                    // above never shifts the player's action buttons mid-turn.
                    Surface(
                        color = Color(Palette.Surface).copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color(Palette.OutlineMuted)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
                        ) {
                            if (session.isOver && !playback.busy) {
                                BattleResultCard(session = session, onNextRoom = onNextRoom, onExit = onExit)
                            } else {
                                ActionDeck(
                                    state = state,
                                    player = player,
                                    target = selectedTarget,
                                    canAct = canAct,
                                    canUndo = session.undoStack.isNotEmpty() && canAct,
                                    coachHint = coachHint,
                                    armedTeleportId = armedTeleportSkill?.id,
                                    onWeapon = { selectedTarget?.let { onAction(CombatAction.WeaponAttack(it.id)) } },
                                    onSkill = handleSkillTap,
                                    onHpPotion = { onAction(CombatAction.UseHpPotion) },
                                    onMpPotion = { onAction(CombatAction.UseMpPotion) },
                                    onUndo = onUndo,
                                    onFlee = { onAction(CombatAction.Flee) },
                                    onEndTurn = { onAction(CombatAction.EndTurn) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Highlighted banner displaying the tactical coach's current recommended move. */
@Composable
private fun CoachBannerCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(Palette.SurfaceElevated).copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, Color(Palette.Accent)),
        shape = RoundedCornerShape(VaiwuRadius.field),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(Palette.TextPrimary),
            modifier = Modifier.padding(VaiwuSpacing.medium),
        )
    }
}

/** Top hero HUD showing name/level/round, 2x & Coach toggles, HP/MP bars, AP pips, and active statuses. */
@Composable
private fun HeroHudCard(
    session: TacticalBattleSession,
    player: Combatant,
    shown: CombatState,
    coachEnabled: Boolean,
    fastMode: Boolean,
    busy: Boolean,
    onToggleCoach: (Boolean) -> Unit,
    onToggleFast: (Boolean) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(Palette.Surface).copy(alpha = 0.90f)),
        border = BorderStroke(1.dp, Color(Palette.OutlineMuted)),
        shape = RoundedCornerShape(VaiwuRadius.field),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(VaiwuSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val heroName = TacticalStrings.combatantName(context, player)
                    Text(
                        text = stringResource(R.string.tactical_hero_header, heroName, player.level, shown.round),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(Palette.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (session.isBossRoom) {
                        Text(
                            text = stringResource(R.string.battle_boss),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(Palette.Danger),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        OutlinedButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.xs),
                            modifier = Modifier.heightIn(min = SKIP_BUTTON_MIN_HEIGHT),
                        ) {
                            Text(
                                stringResource(R.string.tactical_skip),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    FilterChip(
                        selected = fastMode,
                        onClick = { onToggleFast(!fastMode) },
                        label = { Text(stringResource(R.string.tactical_speed_2x), style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = coachEnabled,
                        onClick = { onToggleCoach(!coachEnabled) },
                        label = { Text(stringResource(R.string.tactical_coach_toggle), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactResourceBar(
                    label = stringResource(R.string.battle_hp),
                    value = player.hp,
                    max = player.maxHp,
                    color = Color(Palette.Secondary),
                    modifier = Modifier.weight(1f),
                )
                CompactResourceBar(
                    label = stringResource(R.string.battle_mp),
                    value = player.mp,
                    max = player.maxMp,
                    color = Color(Palette.Info),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                Text(
                    text = stringResource(R.string.tactical_ap_label, player.ap, AP_PER_TURN),
                    style = MaterialTheme.typography.labelMedium,
                    color = VaiwuColors.accent,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs)) {
                    for (i in 0 until AP_PER_TURN) {
                        Box(
                            modifier = Modifier
                                .size(AP_PIP_SIZE)
                                .clip(CircleShape)
                                .background(if (i < player.ap) Color(Palette.Accent) else Color(Palette.OutlineMuted)),
                        )
                    }
                }
                player.shield?.let { sh ->
                    if (sh.value >= 1.0) {
                        StatusBadge(
                            text = stringResource(R.string.tactical_badge_shield, sh.value.roundToInt()),
                            color = Color(elementColorArgb(sh.element)),
                        )
                    }
                }
                if (player.veiled > 0) {
                    StatusBadge(text = stringResource(R.string.tactical_badge_veiled), color = Color(Palette.Primary))
                }
                if (player.marked > 0) {
                    StatusBadge(text = stringResource(R.string.tactical_badge_marked), color = Color(Palette.Danger))
                }
                if (player.burn > 0) {
                    StatusBadge(text = stringResource(R.string.tactical_badge_burn, player.burn), color = Color(elementColorArgb(Element.Fire)))
                }
                if (player.poisonTurns > 0) {
                    StatusBadge(text = stringResource(R.string.tactical_badge_poison, player.poisonTurns), color = Color(elementColorArgb(Element.Wind)))
                }
            }
        }
    }
}

/** Compact labelled progress bar for HP or MP inside [HeroHudCard]. */
@Composable
private fun CompactResourceBar(
    label: String,
    value: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(Palette.TextSecondary))
            Text("$value/$max", style = MaterialTheme.typography.labelSmall, color = Color(Palette.TextPrimary), fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(VaiwuSizes.statBarHeight)
                .clip(RoundedCornerShape(VaiwuRadius.pill)),
            color = color,
            trackColor = Color(Palette.SurfaceVariant),
        )
    }
}

/** Colored pill displaying an active combat buff or debuff (Shield, Veil, Mark, Burn, Poison). */
@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(VaiwuRadius.pill),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = VaiwuSpacing.small, vertical = 2.dp),
        )
    }
}

/** Horizontally scrollable initiative strip of living combatants; tapping an enemy selects it as target. */
@Composable
private fun TurnOrderStrip(
    state: CombatState,
    selectedTargetId: String?,
    onSelectEnemy: (String) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((idx, c) in state.combatants.withIndex()) {
            if (!c.isAlive) continue
            val isActive = idx == state.activeIndex && state.winner == null
            val isSelected = c.id == selectedTargetId
            val borderColor = when {
                isActive -> Color(Palette.Secondary)
                isSelected -> Color(Palette.Accent)
                else -> Color(Palette.OutlineMuted)
            }
            Surface(
                onClick = { if (c.side == Side.Enemy) onSelectEnemy(c.id) },
                color = if (c.side == Side.Player) {
                    Color(Palette.SurfaceElevated).copy(alpha = 0.88f)
                } else {
                    Color(Palette.SurfaceVariant).copy(alpha = 0.88f)
                },
                border = BorderStroke(if (isActive || isSelected) 1.5.dp else 1.dp, borderColor),
                shape = RoundedCornerShape(VaiwuRadius.pill),
            ) {
                Text(
                    text = "${TacticalStrings.combatantName(context, c)} ${c.hp}/${c.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(Palette.TextPrimary),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.xs),
                )
            }
        }
    }
}

/** Summary card for the currently targeted enemy: HP, distance, predicted intent, and top damage forecasts. */
@Composable
private fun TargetForecastCard(
    state: CombatState,
    target: Combatant,
    intent: EnemyIntent?,
) {
    val context = LocalContext.current
    val lines = remember(state, target) {
        TacticalCombat.forecast(state, state.player, target).take(TARGET_FORECAST_MAX_LINES)
    }
    val covered = TacticalCombat.isCovered(state.grid, state.player, target)
    val dist = distance(state.player.pos, target.pos)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(Palette.Surface).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(Palette.OutlineMuted)),
        shape = RoundedCornerShape(VaiwuRadius.field),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(VaiwuSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.tactical_target_header,
                        TacticalStrings.combatantName(context, target),
                        target.level,
                        target.hp,
                        target.maxHp,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(Palette.TextPrimary),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.tactical_distance_cells, dist),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(Palette.TextSecondary),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (intent != null) {
                    Text(
                        text = TacticalStrings.formatIntentSummary(context, intent),
                        style = MaterialTheme.typography.labelSmall,
                        color = VaiwuColors.warning,
                    )
                }
                if (covered) {
                    StatusBadge(text = stringResource(R.string.tactical_badge_covered), color = VaiwuColors.info)
                }
                target.shield?.let { sh ->
                    if (sh.value >= 1.0) {
                        StatusBadge(
                            text = stringResource(R.string.tactical_badge_shield, sh.value.roundToInt()),
                            color = Color(elementColorArgb(sh.element)),
                        )
                    }
                }
            }
            if (lines.isNotEmpty()) {
                Text(
                    text = lines.joinToString(" · ") { TacticalStrings.formatForecastLine(context, it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(Palette.TextSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Player action controls: weapon strike, 3 heroine skills, HP/MP potions, Undo, Flee, and End Turn. */
@Composable
private fun ActionDeck(
    state: CombatState,
    player: Combatant,
    target: Combatant?,
    canAct: Boolean,
    canUndo: Boolean,
    coachHint: CoachHint?,
    armedTeleportId: SkillId?,
    onWeapon: () -> Unit,
    onSkill: (TacticalSkill) -> Unit,
    onHpPotion: () -> Unit,
    onMpPotion: () -> Unit,
    onUndo: () -> Unit,
    onFlee: () -> Unit,
    onEndTurn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
        val weaponPreview = target?.let { TacticalCombat.previewWeaponAttack(state, player, it) }
        val weaponEnabled = canAct && target != null && player.ap >= player.weapon.apCost && (weaponPreview?.inRange == true)
        val weaponSubtitle = when {
            weaponPreview == null -> ""
            !weaponPreview.inRange -> stringResource(R.string.tactical_preview_out_of_range)
            weaponPreview.blocked -> stringResource(R.string.tactical_preview_blocked)
            weaponPreview.kills -> stringResource(R.string.tactical_preview_kills, weaponPreview.damage)
            weaponPreview.covered -> stringResource(R.string.tactical_preview_covered, weaponPreview.damage)
            else -> stringResource(R.string.tactical_preview_damage, weaponPreview.damage)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
            ActionSkillButton(
                title = stringResource(TacticalStrings.weaponNameRes(player.weapon.id)),
                meta = stringResource(R.string.tactical_weapon_meta, player.weapon.apCost, player.weapon.range),
                subtitle = weaponSubtitle,
                accentColor = Color(Palette.TextPrimary),
                highlighted = TacticalCoach.isPicked(coachHint, CoachPick.Weapon),
                enabled = weaponEnabled,
                onClick = onWeapon,
                modifier = Modifier.weight(1f),
            )
            player.skills.getOrNull(0)?.let { skill ->
                SkillActionTile(
                    state = state,
                    player = player,
                    target = target,
                    skill = skill,
                    canAct = canAct,
                    coachHint = coachHint,
                    armed = armedTeleportId == skill.id,
                    onClick = { onSkill(skill) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
            player.skills.getOrNull(1)?.let { skill ->
                SkillActionTile(
                    state = state,
                    player = player,
                    target = target,
                    skill = skill,
                    canAct = canAct,
                    coachHint = coachHint,
                    armed = armedTeleportId == skill.id,
                    onClick = { onSkill(skill) },
                    modifier = Modifier.weight(1f),
                )
            }
            player.skills.getOrNull(2)?.let { skill ->
                SkillActionTile(
                    state = state,
                    player = player,
                    target = target,
                    skill = skill,
                    canAct = canAct,
                    coachHint = coachHint,
                    armed = armedTeleportId == skill.id,
                    onClick = { onSkill(skill) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
            val hpEnabled = canAct && state.hpPotions > 0 && player.ap >= POTION_AP && player.hp < player.maxHp
            val mpEnabled = canAct && state.mpPotions > 0 && player.ap >= POTION_AP && player.mp < player.maxMp
            OutlinedButton(
                onClick = onHpPotion,
                enabled = hpEnabled,
                border = BorderStroke(
                    width = if (TacticalCoach.isPicked(coachHint, CoachPick.HpPotion)) 2.dp else 1.dp,
                    color = if (TacticalCoach.isPicked(coachHint, CoachPick.HpPotion)) Color(Palette.Accent) else Color(Palette.Outline),
                ),
                contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.small),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VaiwuSizes.secondaryButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.tactical_btn_hp_potion, state.hpPotions),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onMpPotion,
                enabled = mpEnabled,
                border = BorderStroke(
                    width = if (TacticalCoach.isPicked(coachHint, CoachPick.MpPotion)) 2.dp else 1.dp,
                    color = if (TacticalCoach.isPicked(coachHint, CoachPick.MpPotion)) Color(Palette.Accent) else Color(Palette.Outline),
                ),
                contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.small),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VaiwuSizes.secondaryButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.tactical_btn_mp_potion, state.mpPotions),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.small),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VaiwuSizes.secondaryButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.tactical_btn_undo),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small)) {
            OutlinedButton(
                onClick = onFlee,
                enabled = canAct && player.ap >= FLEE_AP,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VaiwuSizes.primaryButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.tactical_btn_flee, FLEE_AP),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val endHighlighted = TacticalCoach.isPicked(coachHint, CoachPick.EndTurn)
            Button(
                onClick = onEndTurn,
                enabled = canAct,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (endHighlighted) Color(Palette.Accent) else Color(Palette.Primary),
                    contentColor = if (endHighlighted) Color(Palette.OnAccent) else Color(Palette.OnPrimary),
                ),
                modifier = Modifier
                    .weight(END_TURN_BUTTON_WEIGHT)
                    .heightIn(min = VaiwuSizes.primaryButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.tactical_btn_end_turn),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Computes live range/resource availability and damage preview subtitle for one heroine [skill] tile. */
@Composable
private fun SkillActionTile(
    state: CombatState,
    player: Combatant,
    target: Combatant?,
    skill: TacticalSkill,
    canAct: Boolean,
    coachHint: CoachHint?,
    armed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasResources = player.ap >= skill.apCost && player.mp >= skill.mpCost
    val targetNeeded = skill.effect == SkillEffect.Attack || skill.effect == SkillEffect.Control
    val preview = if (targetNeeded && target != null) {
        TacticalCombat.previewSkillAttack(state, player, target, skill)
    } else {
        null
    }
    val inRange = !targetNeeded || (preview?.inRange == true)
    val enabled = canAct && hasResources && inRange && (skill.effect != SkillEffect.Veil || player.veiled == 0)

    val subtitle = when (skill.effect) {
        SkillEffect.Defense -> stringResource(R.string.tactical_preview_shield, skill.power)
        SkillEffect.Heal -> stringResource(R.string.tactical_preview_heal, skill.power)
        SkillEffect.Veil -> stringResource(R.string.tactical_preview_veil)
        SkillEffect.Teleport -> stringResource(R.string.tactical_preview_teleport, skill.range)
        SkillEffect.Control -> if (inRange) {
            stringResource(R.string.tactical_preview_stun)
        } else {
            stringResource(R.string.tactical_preview_out_of_range)
        }
        SkillEffect.Attack -> when {
            preview == null -> ""
            !preview.inRange -> stringResource(R.string.tactical_preview_out_of_range)
            preview.blocked -> stringResource(R.string.tactical_preview_blocked)
            preview.kills -> stringResource(R.string.tactical_preview_kills, preview.damage)
            preview.covered -> stringResource(R.string.tactical_preview_covered, preview.damage)
            else -> stringResource(R.string.tactical_preview_damage, preview.damage)
        }
    }

    ActionSkillButton(
        title = stringResource(TacticalStrings.skillNameRes(skill.id)),
        meta = stringResource(R.string.tactical_skill_meta, skill.apCost, skill.mpCost),
        subtitle = subtitle,
        accentColor = Color(elementColorArgb(skill.element)),
        highlighted = armed || TacticalCoach.isPicked(coachHint, CoachPick.Skill(skill.id)),
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    )
}

/** Reusable two-line tactile button for a weapon or skill with element color dot and coach highlight. */
@Composable
private fun ActionSkillButton(
    title: String,
    meta: String,
    subtitle: String,
    accentColor: Color,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = when {
            highlighted -> Color(Palette.PrimaryContainer)
            enabled -> Color(Palette.SurfaceElevated).copy(alpha = 0.92f)
            else -> Color(Palette.Surface).copy(alpha = 0.58f)
        },
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = when {
                highlighted -> Color(Palette.Accent)
                enabled -> accentColor.copy(alpha = 0.65f)
                else -> Color(Palette.OutlineMuted)
            },
        ),
        shape = RoundedCornerShape(VaiwuRadius.field),
        interactionSource = interactionSource,
        modifier = modifier
            .height(VaiwuSizes.primaryButtonHeight + 8.dp)
            .pressScale(interactionSource, enabled),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(SKILL_ELEMENT_DOT_SIZE)
                        .clip(CircleShape)
                        .background(if (enabled) accentColor else accentColor.copy(alpha = 0.35f)),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(Palette.TextPrimary) else Color(Palette.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(Palette.TextSecondary),
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) accentColor else Color(Palette.TextSecondary),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Post-room summary card displaying victory/defeat/escape outcome, XP/gold earned, and room transition buttons. */
@Composable
private fun BattleResultCard(
    session: TacticalBattleSession,
    onNextRoom: () -> Unit,
    onExit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(Palette.Surface).copy(alpha = 0.94f)),
        border = BorderStroke(1.5.dp, Color(Palette.Accent)),
        shape = RoundedCornerShape(VaiwuRadius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(VaiwuSpacing.large),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val title = when {
                session.dungeonCleared -> stringResource(R.string.battle_cleared, session.chestGold)
                session.awaitingRest -> stringResource(R.string.tactical_room_cleared, session.room, session.rooms)
                session.state.winner == CombatWinner.Player -> stringResource(R.string.battle_victory)
                session.state.winner == CombatWinner.Escaped -> stringResource(R.string.battle_loot_saved)
                else -> stringResource(R.string.battle_fainted)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (session.state.winner == CombatWinner.Enemy) Color(Palette.Danger) else Color(Palette.Accent),
            )
            if (session.totalXpThisRoom > 0 || session.totalGoldThisRoom != 0) {
                Text(
                    text = stringResource(
                        R.string.tactical_reward_summary,
                        session.totalXpThisRoom,
                        session.totalGoldThisRoom,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(Palette.TextPrimary),
                )
            }
            if (session.levelsGainedThisRoom > 0) {
                Text(
                    text = stringResource(R.string.tactical_level_up, session.levelsGainedThisRoom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(Palette.Secondary),
                    fontWeight = FontWeight.Bold,
                )
            }
            when {
                session.dungeonCleared -> {
                    VaiwuPrimaryButton(
                        text = stringResource(R.string.battle_claim),
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                session.awaitingRest -> {
                    Text(
                        text = stringResource(R.string.battle_rest),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(Palette.TextSecondary),
                    )
                    VaiwuPrimaryButton(
                        text = stringResource(R.string.battle_rest_next),
                        onClick = onNextRoom,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VaiwuSecondaryButton(
                        text = stringResource(R.string.battle_exit_loot),
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    VaiwuPrimaryButton(
                        text = stringResource(R.string.battle_back_home),
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Collapsible combat log showing either the latest event or a scrollable feed of recent entries. */
@Composable
private fun CombatLogCard(
    state: CombatState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(Palette.Surface).copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, Color(Palette.OutlineMuted)),
        shape = RoundedCornerShape(VaiwuRadius.field),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tactical_log_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(Palette.TextSecondary),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.heightIn(min = VaiwuSizes.minTouchTarget),
                    contentPadding = PaddingValues(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.xs),
                ) {
                    Text(
                        text = stringResource(if (expanded) R.string.tactical_log_hide else R.string.tactical_log_show),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (expanded) {
                val recent = state.log.takeLast(COMBAT_LOG_VISIBLE_ENTRIES).asReversed()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(VaiwuSizes.battleLogHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (entry in recent) {
                        Text(
                            text = TacticalStrings.formatLogEntry(context, state, entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(Palette.TextPrimary),
                        )
                    }
                }
            } else {
                val latest = state.log.lastOrNull()
                if (latest != null) {
                    Text(
                        text = TacticalStrings.formatLogEntry(context, state, latest),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(Palette.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
