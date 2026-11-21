package su.neit.vaiwu.combat

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.neit.vaiwu.Emotion
import su.neit.vaiwu.Palette
import su.neit.vaiwu.R
import su.neit.vaiwu.VaiwuRadius
import su.neit.vaiwu.characterIds
import su.neit.vaiwu.emotionIds
import su.neit.vaiwu.loadAssetBitmap
import su.neit.vaiwu.monsterAsset
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive 9x5 tactical arena rendered on Compose [Canvas] with:
 * - 2-tap movement preview (1st tap highlights BFS path + AP cost, 2nd tap moves)
 * - Teleport destination highlighting (for skills like Vesper's Shadow Step)
 * - Enemy intent badges, cover shading, aim line, coach highlights
 * - Playback sprites, poses, projectiles, slashes, shields, and floating damage
 */
@Composable
fun BattlefieldCanvas(
    state: CombatState,
    playback: PlaybackUiState,
    selectedTargetId: String?,
    pendingMovePath: List<Pos>?,
    teleportSkill: TacticalSkill?,
    intents: Map<String, EnemyIntent>,
    coachHint: CoachHint?,
    onCellTap: (Pos) -> Unit,
    onEnemyTap: (String) -> Unit,
    bottomOverlayText: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shown = playback.shown
    val grid = shown.grid
    val myTurn = shown.isPlayerTurn && !playback.busy
    val player = shown.player

    // Preload all needed combatant, cover, and arena backdrop sprites via SpriteBitmapCache.
    val bitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    val assetNames = remember(shown.combatants, grid.covers, grid.theme) {
        buildList {
            shown.combatants.forEach { add(combatantAssetName(it)) }
            grid.covers.forEach { add(coverAssetName(it.kind)) }
            arenaBackdropAssetName(grid.theme)?.let { add(it) }
        }.distinct()
    }
    LaunchedEffect(assetNames) {
        for (name in assetNames) {
            if (bitmaps.containsKey(name)) continue
            val decoded = withContext(Dispatchers.IO) { loadAssetBitmap(context.applicationContext, name) }
            if (decoded != null) bitmaps[name] = decoded
        }
    }

    // Screen shake offset on heavy hits or defeats.
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(playback.shakeCount) {
        if (playback.shakeCount == 0) return@LaunchedEffect
        val offsets = listOf(-8f, 8f, -5f, 5f, -2f, 0f)
        for (v in offsets) {
            shakeAnim.animateTo(v, tween(durationMillis = 36))
        }
    }

    val reachableSet = remember(shown, myTurn, teleportSkill) {
        if (!myTurn) {
            emptySet()
        } else if (teleportSkill != null) {
            buildSet {
                for (x in 0 until grid.width) {
                    for (y in 0 until grid.height) {
                        val p = Pos(x, y)
                        if (TacticalCombat.canTeleportTo(shown, player, teleportSkill, p)) add(p)
                    }
                }
            }
        } else {
            buildSet {
                for (x in 0 until grid.width) {
                    for (y in 0 until grid.height) {
                        val p = Pos(x, y)
                        if (TacticalCombat.reachablePath(shown, player, p) != null) add(p)
                    }
                }
            }
        }
    }

    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnEnemyTap by rememberUpdatedState(onEnemyTap)
    val pendingApLabel = if (!pendingMovePath.isNullOrEmpty()) {
        stringResource(R.string.tactical_ap_cost_short, pathCost(pendingMovePath, player.cellsPerAp))
    } else {
        null
    }
    val shieldLabel = stringResource(R.string.tactical_fx_blocked)
    val downLabel = stringResource(R.string.tactical_fx_down)
    val captionText = remember(playback.captionEvent, shown) {
        TacticalStrings.formatCaption(context, shown, playback.captionEvent)
    }

    val badgePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(grid.width.toFloat() / grid.height.toFloat())
            .offset { IntOffset(shakeAnim.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(VaiwuRadius.card))
            .border(1.5.dp, Color(Palette.Outline), RoundedCornerShape(VaiwuRadius.card)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(shown, myTurn, teleportSkill) {
                    detectTapGestures { offset ->
                        if (!myTurn) return@detectTapGestures
                        val cellW = size.width.toFloat() / grid.width
                        val cellH = size.height.toFloat() / grid.height
                        val col = (offset.x / cellW).toInt().coerceIn(0, grid.width - 1)
                        val row = (offset.y / cellH).toInt().coerceIn(0, grid.height - 1)
                        val tapped = Pos(col, row)
                        val enemy = shown.combatants.firstOrNull {
                            it.side == Side.Enemy && it.isAlive && it.pos == tapped
                        }
                        if (enemy != null && teleportSkill == null) {
                            currentOnEnemyTap(enemy.id)
                        } else {
                            currentOnCellTap(tapped)
                        }
                    }
                },
        ) {
            val cellW = size.width / grid.width
            val cellH = size.height / grid.height
            fun cellCenter(p: Pos): Offset = Offset((p.x + 0.5f) * cellW, (p.y + 0.5f) * cellH)

            // 1. Arena backdrop + semi-transparent tactical floor tiles
            val backdropBmp = arenaBackdropAssetName(grid.theme)?.let { bitmaps[it] }
            if (backdropBmp != null) {
                drawImage(
                    image = backdropBmp.asImageBitmap(),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                    alpha = 0.52f,
                )
            }
            val tileAlpha = if (backdropBmp != null) 0.56f else 1f
            val (tileA, tileB, gridLine) = floorPalette(grid.theme)
            for (y in 0 until grid.height) {
                for (x in 0 until grid.width) {
                    val p = Pos(x, y)
                    val baseColor = (if ((x + y) % 2 == 0) tileA else tileB).copy(alpha = tileAlpha)
                    drawRect(
                        color = baseColor,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f),
                    )
                    if (p in reachableSet) {
                        val tint = if (teleportSkill != null) {
                            Color(0x55B975FF)
                        } else {
                            Color(0x334CE3A0)
                        }
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(x * cellW + 2f, y * cellH + 2f),
                            size = Size(cellW - 4f, cellH - 4f),
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                    drawRect(
                        color = gridLine,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW, cellH),
                        style = Stroke(width = 1f),
                    )
                }
            }

            // 2. Coach cell & target highlights
            if (myTurn && coachHint != null) {
                val pick = coachHint.pick
                if (pick is CoachPick.Field && pick.to != null) {
                    val c = pick.to
                    drawRoundRect(
                        color = Color(Palette.Accent),
                        topLeft = Offset(c.x * cellW + 3f, c.y * cellH + 3f),
                        size = Size(cellW - 6f, cellH - 6f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                        ),
                    )
                }
                coachHint.focusEnemyId?.let { focusId ->
                    shown.combatants.firstOrNull { it.id == focusId && it.isAlive }?.let { foe ->
                        drawCircle(
                            color = Color(Palette.Accent),
                            radius = min(cellW, cellH) * 0.46f,
                            center = cellCenter(foe.pos),
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                            ),
                        )
                    }
                }
            }

            // 3. Cover tiles (generated sprites with crisp vector fallback)
            for (cover in grid.covers) {
                drawCoverTile(cover, cellW, cellH, bitmaps[coverAssetName(cover.kind)])
            }

            // 4. Aim line from player to selected target
            val selectedEnemy = shown.combatants.firstOrNull { it.id == selectedTargetId && it.isAlive }
            if (myTurn && selectedEnemy != null && teleportSkill == null) {
                drawLine(
                    color = Color(0x66FFD166),
                    start = cellCenter(player.pos),
                    end = cellCenter(selectedEnemy.pos),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )
            }

            // 5. 2-tap pending move path preview
            if (myTurn && !pendingMovePath.isNullOrEmpty()) {
                val pts = listOf(player.pos) + pendingMovePath
                val path = Path().apply {
                    val first = cellCenter(pts.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until pts.size) {
                        val pt = cellCenter(pts[i])
                        lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color(Palette.Secondary),
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                val dest = pendingMovePath.last()
                val destCenter = cellCenter(dest)
                drawRoundRect(
                    color = Color(0xCC1E1833),
                    topLeft = Offset(dest.x * cellW + 3f, dest.y * cellH + 3f),
                    size = Size(cellW - 6f, cellH - 6f),
                    cornerRadius = CornerRadius(8f, 8f),
                )
                drawRoundRect(
                    color = Color(Palette.Secondary),
                    topLeft = Offset(dest.x * cellW + 3f, dest.y * cellH + 3f),
                    size = Size(cellW - 6f, cellH - 6f),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 3f),
                )
                if (pendingApLabel != null) {
                    badgePaint.textSize = min(cellW, cellH) * 0.28f
                    badgePaint.color = android.graphics.Color.WHITE
                    drawContext.canvas.nativeCanvas.drawText(
                        pendingApLabel,
                        destCenter.x,
                        destCenter.y + badgePaint.textSize * 0.35f,
                        badgePaint,
                    )
                }
            }

            // 6. Combatants (sorted by alive then row)
            val ordered = shown.combatants.sortedWith(
                compareBy<Combatant> { it.isAlive }.thenBy { it.pos.y },
            )
            for (c in ordered) {
                val pose = playback.poses[c.id]
                val center = cellCenter(c.pos) + Offset(
                    (pose?.dx ?: 0f) * cellW,
                    (pose?.dy ?: 0f) * cellH,
                )
                val isSelected = c.id == selectedTargetId && c.isAlive
                val isActiveTurn = c.id == shown.activeCombatant.id && shown.winner == null
                val covered = c.side == Side.Enemy && c.isAlive && TacticalCombat.isCovered(grid, player, c)
                drawCombatant(
                    combatant = c,
                    center = center,
                    cellW = cellW,
                    cellH = cellH,
                    bitmap = bitmaps[combatantAssetName(c)],
                    isSelected = isSelected,
                    isActive = isActiveTurn,
                    covered = covered,
                    intent = if (c.side == Side.Enemy && c.isAlive && myTurn) intents[c.id] else null,
                    playerHp = player.hp,
                    paint = badgePaint,
                )
            }

            // 7. Active FX
            for (fxItem in playback.fx) {
                drawCombatFx(
                    fx = fxItem,
                    state = shown,
                    cellW = cellW,
                    cellH = cellH,
                    shieldLabel = shieldLabel,
                    downLabel = downLabel,
                    paint = badgePaint,
                )
            }
        }

        if (captionText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(Color(0xCC1A1430), RoundedCornerShape(VaiwuRadius.pill))
                    .border(1.dp, Color(Palette.Outline), RoundedCornerShape(VaiwuRadius.pill))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = captionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(Palette.TextPrimary),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Bottom overlay keeps 2-tap move & teleport prompts inside the arena
        // bounds so action controls below never shift vertically.
        if (bottomOverlayText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(Color(0xE01A1430), RoundedCornerShape(VaiwuRadius.pill))
                    .border(1.dp, Color(Palette.Secondary), RoundedCornerShape(VaiwuRadius.pill))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = bottomOverlayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(Palette.TextPrimary),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun coverAssetName(kind: CoverKind): String = when (kind) {
    CoverKind.Boulder -> "cover_boulder.png"
    CoverKind.TreeStump -> "cover_stump.png"
    CoverKind.Pillar -> "cover_pillar.png"
    CoverKind.Crystal -> "cover_crystal.png"
}

internal fun arenaBackdropAssetName(theme: ArenaTheme): String? = when (theme) {
    ArenaTheme.Dungeon -> "arena_dungeon.png"
    ArenaTheme.BossHall -> "arena_boss.png"
    ArenaTheme.Meadow, ArenaTheme.Forest -> null
}

private fun combatantAssetName(c: Combatant): String = if (c.side == Side.Player) {
    val ch = characterIds.getOrElse(c.characterId) { characterIds.first() }
    val emotion = when {
        !c.isAlive -> emotionIds[Emotion.Dead]
        c.stunned > 0 -> emotionIds[Emotion.Sleepy]
        c.hp < c.maxHp * 0.35 -> emotionIds[Emotion.Sad]
        else -> emotionIds[Emotion.Idle]
    }
    "sprite_${ch}_$emotion.png"
} else {
    monsterAsset(c.monsterKind?.nativeId ?: 0)
}

private fun floorPalette(theme: ArenaTheme): Triple<Color, Color, Color> = when (theme) {
    ArenaTheme.Meadow -> Triple(Color(0xFF2B3B36), Color(0xFF25342F), Color(0x28FFFFFF))
    ArenaTheme.Forest -> Triple(Color(0xFF22332F), Color(0xFF1C2B27), Color(0x24FFFFFF))
    ArenaTheme.Dungeon -> Triple(Color(0xFF26203A), Color(0xFF201A32), Color(0x28A78BFA))
    ArenaTheme.BossHall -> Triple(Color(0xFF331E36), Color(0xFF29172C), Color(0x30FF6B7A))
}

private fun DrawScope.drawCoverTile(
    cover: CoverTile,
    cellW: Float,
    cellH: Float,
    sprite: Bitmap?,
) {
    val minDim = min(cellW, cellH)
    val cx = (cover.pos.x + 0.5f) * cellW
    val cy = (cover.pos.y + 0.5f) * cellH
    if (sprite != null) {
        drawOval(
            color = Color(0x66000000),
            topLeft = Offset(cx - minDim * 0.36f, cy + minDim * 0.22f),
            size = Size(minDim * 0.72f, minDim * 0.18f),
        )
        val sizePx = (minDim * 0.88f).roundToInt().coerceAtLeast(16)
        val dstLeft = (cx - sizePx / 2f).roundToInt()
        val dstTop = (cy - sizePx * 0.54f).roundToInt()
        drawImage(
            image = sprite.asImageBitmap(),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(sizePx, sizePx),
        )
        return
    }
    val left = cover.pos.x * cellW + cellW * 0.12f
    val top = cover.pos.y * cellH + cellH * 0.14f
    val w = cellW * 0.76f
    val h = cellH * 0.72f
    val (fill, topHighlight, border) = when (cover.kind) {
        CoverKind.Boulder -> Triple(Color(0xFF6B6575), Color(0xFF938C9E), Color(0xFF3D3847))
        CoverKind.TreeStump -> Triple(Color(0xFF6B4C3B), Color(0xFF8C6650), Color(0xFF3E291F))
        CoverKind.Pillar -> Triple(Color(0xFF5A527A), Color(0xFF8379A8), Color(0xFF2E2845))
        CoverKind.Crystal -> Triple(Color(0xFF6D3FA8), Color(0xFFB975FF), Color(0xFF3D1C66))
    }
    drawRoundRect(
        color = Color(0x55000000),
        topLeft = Offset(left + 2f, top + 5f),
        size = Size(w, h),
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawRoundRect(
        color = topHighlight,
        topLeft = Offset(left + 4f, top + 3f),
        size = Size(w - 8f, h * 0.36f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawCombatant(
    combatant: Combatant,
    center: Offset,
    cellW: Float,
    cellH: Float,
    bitmap: Bitmap?,
    isSelected: Boolean,
    isActive: Boolean,
    covered: Boolean,
    intent: EnemyIntent?,
    playerHp: Int,
    paint: Paint,
) {
    val minDim = min(cellW, cellH)
    val alpha = if (combatant.isAlive) 1f else 0.32f

    // Ground shadow
    drawOval(
        color = Color(0x66000000),
        topLeft = Offset(center.x - minDim * 0.32f, center.y + minDim * 0.24f),
        size = Size(minDim * 0.64f, minDim * 0.18f),
    )

    // Active / selected ring
    if (isSelected) {
        drawCircle(
            color = Color(Palette.Accent),
            radius = minDim * 0.44f,
            center = center,
            style = Stroke(width = 3f),
        )
    } else if (isActive) {
        drawCircle(
            color = Color(Palette.Secondary),
            radius = minDim * 0.42f,
            center = center,
            style = Stroke(width = 2.2f),
        )
    }

    // Active elemental shield halo
    combatant.shield?.let { sh ->
        if (combatant.isAlive && sh.value >= 1.0) {
            val shieldColor = Color(elementColorArgb(sh.element))
            drawCircle(
                color = shieldColor.copy(alpha = 0.20f),
                radius = minDim * 0.43f,
                center = center,
            )
            drawCircle(
                color = shieldColor.copy(alpha = 0.85f),
                radius = minDim * 0.43f,
                center = center,
                style = Stroke(width = 2.5f),
            )
        }
    }

    // Sprite or fallback token
    if (bitmap != null) {
        val spriteH = (cellH * 0.86f).roundToInt().coerceAtLeast(16)
        val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val spriteW = (spriteH * aspect).roundToInt().coerceAtMost((cellW * 0.92f).roundToInt()).coerceAtLeast(16)
        val dstLeft = (center.x - spriteW / 2f).roundToInt()
        val dstTop = (center.y - spriteH * 0.56f).roundToInt()
        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(spriteW, spriteH),
            alpha = alpha,
        )
    } else {
        drawCircle(
            color = (if (combatant.side == Side.Player) Color(Palette.Primary) else Color(Palette.Danger)).copy(alpha = alpha),
            radius = minDim * 0.30f,
            center = center,
        )
    }

    if (!combatant.isAlive) return

    // HP bar at bottom of cell
    val barW = cellW * 0.78f
    val barH = (cellH * 0.11f).coerceIn(4f, 8f)
    val barLeft = center.x - barW / 2f
    val barTop = center.y + cellH * 0.31f
    drawRoundRect(
        color = Color(0xCC120E20),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(3f, 3f),
    )
    val hpFrac = (combatant.hp.toFloat() / combatant.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
    val hpColor = if (combatant.side == Side.Player) Color(Palette.Secondary) else Color(Palette.Danger)
    drawRoundRect(
        color = hpColor,
        topLeft = Offset(barLeft, barTop),
        size = Size(barW * hpFrac, barH),
        cornerRadius = CornerRadius(3f, 3f),
    )

    // Cover / Status dots
    var dotX = barLeft + 3f
    val dotY = barTop - 4f
    if (covered) {
        drawCircle(Color(Palette.Info), radius = 3.5f, center = Offset(dotX, dotY))
        dotX += 9f
    }
    if (combatant.burn > 0) {
        drawCircle(Color(0xFFFF8A3D), radius = 3.5f, center = Offset(dotX, dotY))
        dotX += 9f
    }
    if (combatant.poisonTurns > 0) {
        drawCircle(Color(0xFF7BE8B4), radius = 3.5f, center = Offset(dotX, dotY))
        dotX += 9f
    }
    if (combatant.stunned > 0) {
        drawCircle(Color(Palette.Accent), radius = 3.5f, center = Offset(dotX, dotY))
        dotX += 9f
    }
    if (combatant.marked > 0) {
        drawCircle(Color(Palette.Danger), radius = 3.5f, center = Offset(dotX, dotY))
        dotX += 9f
    }
    if (combatant.veiled > 0) {
        drawCircle(Color(Palette.Primary), radius = 3.5f, center = Offset(dotX, dotY))
    }

    // Enemy intent badge at top-right of cell
    if (intent != null) {
        val lethal = (intent.expectedDamage ?: 0) >= playerHp && intent.targetId == PLAYER_ID
        val badgeBg = when {
            lethal -> Color(0xE6D92D46)
            intent.kind == IntentKind.Attack || intent.kind == IntentKind.Control -> Color(0xDD3D1F35)
            intent.kind == IntentKind.Mend -> Color(0xDD1A4733)
            intent.kind == IntentKind.Command -> Color(0xDD5A2D12)
            else -> Color(0xCC221B3A)
        }
        val badgeBorder = if (lethal) Color(Palette.Accent) else Color(Palette.Outline)
        val symbol = TacticalStrings.intentBadgeSymbol(intent)
        val bw = (cellW * 0.72f).coerceAtLeast(28f)
        val bh = (cellH * 0.30f).coerceIn(14f, 22f)
        val bx = (center.x - bw / 2f).coerceIn(2f, size.width - bw - 2f)
        val by = (center.y - cellH * 0.48f).coerceAtLeast(2f)
        drawRoundRect(
            color = badgeBg,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bh / 2f, bh / 2f),
        )
        drawRoundRect(
            color = badgeBorder,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bh / 2f, bh / 2f),
            style = Stroke(width = 1.5f),
        )
        paint.textSize = bh * 0.68f
        paint.color = android.graphics.Color.WHITE
        drawContext.canvas.nativeCanvas.drawText(
            symbol,
            bx + bw / 2f,
            by + bh * 0.76f,
            paint,
        )
    }
}

private fun DrawScope.drawCombatFx(
    fx: FxItem,
    state: CombatState,
    cellW: Float,
    cellH: Float,
    shieldLabel: String,
    downLabel: String,
    paint: Paint,
) {
    fun centerOf(p: Pos): Offset = Offset((p.x + 0.5f) * cellW, (p.y + 0.5f) * cellH)
    fun fighterCenter(id: String): Offset {
        val pos = state.combatants.firstOrNull { it.id == id }?.pos ?: Pos(0, 0)
        val raw = centerOf(pos)
        return Offset(raw.x.coerceIn(28f, size.width - 28f), raw.y.coerceIn(22f, size.height - 14f))
    }

    val minDim = min(cellW, cellH)
    when (val e = fx.event) {
        is CombatEvent.SkillCast -> {
            val from = fx.from?.let(::centerOf) ?: return
            val to = fx.to?.let(::centerOf) ?: from
            val color = Color(elementColorArgb(e.element))
            if (e.effect == SkillEffect.Control) {
                for (r in listOf(0.28f, 0.42f, 0.54f)) {
                    drawCircle(color, radius = minDim * r, center = to, style = Stroke(width = 3f))
                }
            } else {
                drawLine(color.copy(alpha = 0.75f), start = from, end = to, strokeWidth = 6f, cap = StrokeCap.Round)
                drawCircle(color.copy(alpha = 0.35f), radius = minDim * 0.45f, center = to)
                drawCircle(Color.White, radius = minDim * 0.16f, center = to)
                for (i in 0 until 8) {
                    val a = i * (Math.PI / 4.0)
                    val p1 = Offset(to.x + (cos(a) * minDim * 0.18f).toFloat(), to.y + (sin(a) * minDim * 0.18f).toFloat())
                    val p2 = Offset(to.x + (cos(a) * minDim * 0.52f).toFloat(), to.y + (sin(a) * minDim * 0.52f).toFloat())
                    drawLine(color, start = p1, end = p2, strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
        }
        is CombatEvent.WeaponStrike -> {
            val from = fx.from?.let(::centerOf) ?: return
            val to = fx.to?.let(::centerOf) ?: return
            if (e.melee) {
                val r = minDim * 0.32f
                drawLine(Color(0xFFFFF8DC), Offset(to.x - r, to.y - r), Offset(to.x + r, to.y + r), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Color(0xFFFFD166), Offset(to.x + r, to.y - r), Offset(to.x - r, to.y + r), strokeWidth = 3.5f, cap = StrokeCap.Round)
            } else {
                drawLine(Color(0xFFFFE699), from, to, strokeWidth = 3.5f, cap = StrokeCap.Round)
                drawCircle(Color(0xFFFFE699), radius = minDim * 0.22f, center = to, style = Stroke(width = 3f))
            }
        }
        is CombatEvent.ShieldUp -> {
            val p = fighterCenter(e.actorId)
            val color = Color(elementColorArgb(e.element))
            drawCircle(color.copy(alpha = 0.28f), radius = minDim * 0.48f, center = p)
            drawCircle(color, radius = minDim * 0.48f, center = p, style = Stroke(width = 4f))
        }
        is CombatEvent.Blocked -> {
            val p = fighterCenter(e.targetId)
            drawCircle(Color.White, radius = minDim * 0.38f, center = p, style = Stroke(width = 3.5f))
            drawFloatingText(shieldLabel, p.x, p.y - minDim * 0.32f, android.graphics.Color.rgb(200, 230, 255), minDim * 0.28f, paint)
        }
        is CombatEvent.Damage -> {
            val p = fighterCenter(e.targetId)
            if (e.kind == DamageKind.Hit) {
                drawCircle(Color(0x66FF4A5A), radius = minDim * 0.34f, center = p)
            }
            val textColor = when (e.kind) {
                DamageKind.Burn -> android.graphics.Color.rgb(255, 148, 66)
                DamageKind.Poison -> android.graphics.Color.rgb(123, 232, 180)
                DamageKind.Hit -> if (e.crit) android.graphics.Color.rgb(255, 214, 102) else android.graphics.Color.rgb(255, 107, 122)
            }
            val label = if (e.crit) "-${e.amount}!" else "-${e.amount}"
            val sizeFactor = if (e.crit || e.amount >= 10) 0.36f else 0.30f
            drawFloatingText(label, p.x, p.y - minDim * 0.30f, textColor, minDim * sizeFactor, paint)
        }
        is CombatEvent.Heal -> {
            val p = fighterCenter(e.targetId)
            drawCircle(Color(0x554CE3A0), radius = minDim * 0.38f, center = p, style = Stroke(width = 3f))
            drawFloatingText("+${e.amount}", p.x, p.y - minDim * 0.30f, android.graphics.Color.rgb(76, 227, 160), minDim * 0.32f, paint)
        }
        is CombatEvent.MpRestore -> {
            val p = fighterCenter(e.targetId)
            drawCircle(Color(0x5572D9FF), radius = minDim * 0.38f, center = p, style = Stroke(width = 3f))
            drawFloatingText("+${e.amount} MP", p.x, p.y - minDim * 0.30f, android.graphics.Color.rgb(114, 217, 255), minDim * 0.26f, paint)
        }
        is CombatEvent.CommandMark -> {
            val p = fighterCenter(e.targetId)
            drawCircle(Color(Palette.Danger), radius = minDim * 0.44f, center = p, style = Stroke(width = 3.5f))
        }
        is CombatEvent.Down -> {
            val p = fighterCenter(e.targetId)
            drawCircle(Color(0x881A1430), radius = minDim * 0.36f, center = p)
            drawFloatingText(downLabel, p.x, p.y - minDim * 0.34f, android.graphics.Color.rgb(245, 238, 255), minDim * 0.26f, paint)
        }
        is CombatEvent.Move -> {
            val from = centerOf(e.from)
            drawCircle(
                color = if (e.teleport) Color(0x88B975FF) else Color(0x66D8D0C0),
                radius = minDim * 0.20f,
                center = from,
            )
        }
        else -> Unit
    }
}

private fun DrawScope.drawFloatingText(
    text: String,
    x: Float,
    y: Float,
    color: Int,
    textSizePx: Float,
    paint: Paint,
) {
    paint.textSize = textSizePx.coerceIn(14f, 34f)
    paint.color = android.graphics.Color.argb(210, 18, 12, 32)
    drawContext.canvas.nativeCanvas.drawText(text, x + 1.5f, y + 1.5f, paint)
    paint.color = color
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
