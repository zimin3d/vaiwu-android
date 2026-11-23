package su.neit.vaiwu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The application frame: one top bar, an optional bottom bar and a snackbar
 * host. Screens provide their content only, so insets and navigation live in
 * one place instead of being repeated per screen.
 */
@Composable
fun VaiwuScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    // The top bar owns the status bar inset and the bottom bar owns the
    // navigation bar inset, so the content only needs the horizontal part.
    // Screens without a bottom bar (focus screens, onboarding) need the
    // bottom inset too.
    val contentWindowInsets = if (bottomBar == null) {
        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    }
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar ?: {},
        // SnackbarHost is intentionally empty here: system messages are
        // rendered at the TOP of the content area by VaiwuApp, because the
        // bottom snackbar was covered by the Android navigation bar.
        snackbarHost = {},
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * Global top bar. On tabs it shows the title of the section, on focus screens
 * a back arrow plus contextually styled actions; the gear is always present
 * because settings must stay reachable in every pet state (I-1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaiwuTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Opacity for unselected raster tab icons so the active tab stands out clearly. */
private const val UNSELECTED_TAB_ICON_ALPHA = 0.55f

/** Bottom navigation bar switching between the four top-level tabs (`Home`, `Games`, `Pet`, `Chat`). */
@Composable
fun VaiwuBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        // The bar height is owned by the Material component (its own minimum
        // keeps icon and label inside the bar); forcing a smaller height here
        // pushed the icon and the label out of bounds.
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Image(
                        painter = painterResource(tab.iconRes()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(VaiwuSizes.icon)
                            .alpha(if (isSelected) 1f else UNSELECTED_TAB_ICON_ALPHA),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun AppTab.iconRes(): Int = when (this) {
    AppTab.Home -> R.drawable.icon_home
    AppTab.Games -> R.drawable.icon_games
    AppTab.Pet -> R.drawable.icon_pet
    AppTab.Chat -> R.drawable.icon_chat
}

private fun AppTab.labelRes(): Int = when (this) {
    AppTab.Home -> R.string.nav_home
    AppTab.Games -> R.string.nav_games
    AppTab.Pet -> R.string.nav_pet
    AppTab.Chat -> R.string.nav_chat
}
