package su.neit.vaiwu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val BYTES_PER_KB = 1024.0
private const val BYTES_PER_MB = 1024.0 * 1024.0

/**
 * Heroines catalog & offline manager screen (Settings -> Heroines).
 *
 * Installed packs remain usable offline even if the access key is cleared or revoked.
 */
@Composable
fun HeroinesScreen(
    state: HeroinesUiState,
    onRefresh: () -> Unit,
    onDownload: (RemoteHeroine) -> Unit,
    onActivate: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
    ) {
        SectionCard(title = stringResource(R.string.heroines_active_title)) {
            val active = state.installed.firstOrNull { it.id == state.activeHeroineId }
            Text(
                text = if (active != null) {
                    stringResource(R.string.heroines_active_custom, active.name, active.version)
                } else {
                    stringResource(R.string.heroines_active_builtin)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (active != null) {
                VaiwuSecondaryButton(
                    text = stringResource(R.string.heroines_use_builtin),
                    onClick = { onActivate(null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(title = stringResource(R.string.heroines_catalog_title)) {
            if (!state.hasAccessKey) {
                Text(
                    text = stringResource(R.string.heroines_key_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            VaiwuPrimaryButton(
                text = stringResource(R.string.heroines_refresh),
                onClick = onRefresh,
                enabled = !state.loading && state.downloadingId == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.heroines_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val installedById = state.installed.associateBy { it.id }
        val remoteIds = state.catalog.map { it.id }.toSet()

        if (state.catalog.isNotEmpty()) {
            state.catalog.forEach { remote ->
                val installed = installedById[remote.id]
                val isDownloading = state.downloadingId == remote.id
                val isActive = state.activeHeroineId == remote.id
                val needsUpdate = installed != null && installed.version != remote.version

                SectionCard(title = remote.name) {
                    if (remote.tagline.isNotBlank()) {
                        Text(
                            text = remote.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.heroines_item_meta,
                            remote.version,
                            formatPackSize(remote.sizeBytes),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isDownloading) {
                        Spacer(Modifier.height(VaiwuSpacing.xs))
                        val progress = state.downloadProgress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        if (installed == null || needsUpdate) {
                            VaiwuPrimaryButton(
                                text = stringResource(
                                    if (needsUpdate) R.string.heroines_update else R.string.heroines_download,
                                ),
                                onClick = { onDownload(remote) },
                                enabled = state.downloadingId == null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (installed != null) {
                            VaiwuSecondaryButton(
                                text = stringResource(
                                    if (isActive) R.string.heroines_selected else R.string.heroines_apply,
                                ),
                                onClick = { onActivate(remote.id) },
                                enabled = !isActive && state.downloadingId == null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (installed != null) {
                        TextButton(
                            onClick = { onDelete(remote.id) },
                            enabled = state.downloadingId == null,
                            modifier = Modifier.heightIn(min = VaiwuSizes.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.heroines_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        val offlineOnly = state.installed.filter { it.id !in remoteIds }
        if (offlineOnly.isNotEmpty()) {
            Text(
                text = stringResource(R.string.heroines_installed_offline_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            offlineOnly.forEach { local ->
                val isActive = state.activeHeroineId == local.id
                SectionCard(title = local.name) {
                    if (local.tagline.isNotBlank()) {
                        Text(
                            text = local.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.heroines_installed_version, local.version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                    ) {
                        VaiwuSecondaryButton(
                            text = stringResource(
                                if (isActive) R.string.heroines_selected else R.string.heroines_apply,
                            ),
                            onClick = { onActivate(local.id) },
                            enabled = !isActive,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onDelete(local.id) },
                            modifier = Modifier.heightIn(min = VaiwuSizes.minTouchTarget),
                        ) {
                            Text(
                                text = stringResource(R.string.heroines_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        if (!state.loading && state.catalog.isEmpty() && state.installed.isEmpty() && state.hasAccessKey) {
            EmptyState(title = stringResource(R.string.heroines_empty))
        }
    }
}

internal fun formatPackSize(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> String.format(java.util.Locale.US, "%.1f MB", bytes / BYTES_PER_MB)
    bytes >= BYTES_PER_KB -> String.format(java.util.Locale.US, "%.0f KB", bytes / BYTES_PER_KB)
    else -> "$bytes B"
}
