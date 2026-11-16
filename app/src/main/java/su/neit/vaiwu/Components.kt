package su.neit.vaiwu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared UI components from `docs/ui-spec.md` (section 4). Screens compose
 * these instead of re-styling Material widgets by hand.
 */

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
    max: Float = 100f,
) {
    val safeMax = if (max <= 0f) 100f else max
    val fraction = (value / safeMax).coerceIn(0f, 1f)
    val points = fraction * 100f
    val color = toneColor(statTone(points))
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300),
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
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

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
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(VaiwuColors.accent),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = coins.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = VaiwuColors.accent,
            maxLines = 1,
        )
    }
}

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
