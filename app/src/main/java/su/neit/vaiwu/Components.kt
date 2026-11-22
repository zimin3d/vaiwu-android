package su.neit.vaiwu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Default percentage scale and transition duration for [StatMeter] bars. */
private const val STAT_PERCENT_SCALE = 100f
private const val STAT_BAR_ANIMATION_MS = 300

/** Status chip backdrop and border opacities over the dark surface palette. */
private const val STATUS_CHIP_BG_ALPHA = 0.16f
private const val STATUS_CHIP_BORDER_ALPHA = 0.45f
private val CHIP_HORIZONTAL_PADDING = 10.dp
private val COIN_DOT_SIZE = 12.dp
private val COIN_DOT_GAP = 6.dp

/**
 * Shared UI components from `docs/ui-spec.md` (section 4). Screens compose
 * these instead of re-styling Material widgets by hand.
 */

/** High-emphasis filled action button meeting the minimum primary touch height. */
@Composable
fun VaiwuPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = VaiwuSizes.primaryButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Medium-emphasis outlined action button for secondary actions. */
@Composable
fun VaiwuSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = VaiwuSizes.secondaryButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Resolves a semantic [StatTone] to its theme color (danger/warning/healthy). */
@Composable
fun toneColor(tone: StatTone): Color = when (tone) {
    StatTone.Danger -> MaterialTheme.colorScheme.error
    StatTone.Warning -> VaiwuColors.warning
    StatTone.Healthy -> MaterialTheme.colorScheme.secondary
}

/**
 * A labelled stat row with a numeric value and a colour-coded bar. The
 * numeric value is required by the spec: the bar alone is not enough.
 */
@Composable
fun StatMeter(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    max: Float = STAT_PERCENT_SCALE,
) {
    val safeMax = if (max <= 0f) STAT_PERCENT_SCALE else max
    val fraction = (value / safeMax).coerceIn(0f, 1f)
    val points = fraction * STAT_PERCENT_SCALE
    val color = toneColor(statTone(points))
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = STAT_BAR_ANIMATION_MS),
        label = "stat-$label",
    )
    val description = stringResource(R.string.stat_a11y, label, points.toInt())

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = points.toInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
        Spacer(Modifier.height(VaiwuSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VaiwuSizes.statBarHeight)
                .clip(RoundedCornerShape(VaiwuSizes.statBarHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(VaiwuSizes.statBarHeight / 2))
                    .background(color),
            )
        }
    }
}

/** Compact pill badge tinted by [tone] for status flags like Sleeping or Sick. */
@Composable
fun StatusChip(
    text: String,
    tone: StatTone,
    modifier: Modifier = Modifier,
) {
    val color = toneColor(tone)
    val shape = RoundedCornerShape(VaiwuRadius.pill)
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = STATUS_CHIP_BG_ALPHA))
            .border(1.dp, color.copy(alpha = STATUS_CHIP_BORDER_ALPHA), shape)
            .padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = VaiwuSpacing.xs),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

/** Pill displaying the player's current coin balance with an accent coin dot. */
@Composable
fun CoinChip(
    coins: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(VaiwuRadius.pill)
    val description = stringResource(R.string.coins, coins)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = VaiwuSpacing.xs)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(COIN_DOT_SIZE)
                .clip(CircleShape)
                .background(VaiwuColors.accent),
        )
        Spacer(Modifier.width(COIN_DOT_GAP))
        Text(
            text = coins.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = VaiwuColors.accent,
            maxLines = 1,
        )
    }
}

/** Bordered surface card grouping related content under an optional [title]. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(VaiwuSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content()
    }
}

/** Centered placeholder message with an optional call-to-action button for empty lists. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(VaiwuSpacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            VaiwuSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * A care action of the home screen: icon plus caption, at least 64 dp tall
 * so that both the tap target and the label stay comfortable at font scale 1.3
 * (UI-CMP-09).
 */
@Composable
fun CareActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = VaiwuSizes.careActionHeight),
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
                .padding(horizontal = VaiwuSpacing.small, vertical = VaiwuSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(VaiwuSizes.icon),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * Confirmation dialog for destructive actions. The confirm button names the
 * action ("Выйти", "Удалить"), not a neutral "ОК" (UI-CMP-11).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
