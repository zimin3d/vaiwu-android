package su.neit.vaiwu

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BYTES_PER_KB = 1024.0
private const val BYTES_PER_MB = 1024.0 * 1024.0
private const val KIND_OUTFIT = "outfit"
private val PREVIEW_THUMB_WIDTH = 76.dp
private val PREVIEW_THUMB_HEIGHT = 104.dp
private val PREVIEW_THUMB_CORNER = 12.dp

/**
 * Heroines & outfits catalog and offline manager screen (Settings -> Heroines).
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
    val characterNames = stringArrayResource(R.array.character_names)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(VaiwuSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
    ) {
        SectionCard(title = stringResource(R.string.heroines_active_title)) {
            val active = state.installed.firstOrNull { it.id == state.activeHeroineId }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (active != null) {
                    HeroinePreviewThumb(
                        previewPath = state.previewPaths[active.id],
                        contentDescription = stringResource(R.string.heroines_preview_desc, active.name),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
                ) {
                    Text(
                        text = if (active != null) {
                            stringResource(R.string.heroines_active_custom, active.name, active.version)
                        } else {
                            stringResource(R.string.heroines_active_builtin)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (active != null) {
                        Text(
                            text = packKindLabel(active.kind, active.targetCharacterId, characterNames),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
        val remoteOutfits = state.catalog.filter { it.kind == KIND_OUTFIT }
        val remoteHeroines = state.catalog.filter { it.kind != KIND_OUTFIT }

        if (remoteOutfits.isNotEmpty()) {
            Text(
                text = stringResource(R.string.heroines_section_outfits),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            remoteOutfits.forEach { remote ->
                RemotePackCard(
                    remote = remote,
                    installed = installedById[remote.id],
                    previewPath = state.previewPaths[remote.id],
                    isDownloading = state.downloadingId == remote.id,
                    downloadProgress = state.downloadProgress,
                    anyDownloading = state.downloadingId != null,
                    isActive = state.activeHeroineId == remote.id,
                    kindLabel = packKindLabel(remote.kind, remote.targetCharacterId, characterNames),
                    onDownload = onDownload,
                    onActivate = onActivate,
                    onDelete = onDelete,
                )
            }
        }

        if (remoteHeroines.isNotEmpty()) {
            Text(
                text = stringResource(R.string.heroines_section_heroines),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            remoteHeroines.forEach { remote ->
                RemotePackCard(
                    remote = remote,
                    installed = installedById[remote.id],
                    previewPath = state.previewPaths[remote.id],
                    isDownloading = state.downloadingId == remote.id,
                    downloadProgress = state.downloadProgress,
                    anyDownloading = state.downloadingId != null,
                    isActive = state.activeHeroineId == remote.id,
                    kindLabel = packKindLabel(remote.kind, remote.targetCharacterId, characterNames),
                    onDownload = onDownload,
                    onActivate = onActivate,
                    onDelete = onDelete,
                )
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HeroinePreviewThumb(
                            previewPath = state.previewPaths[local.id],
                            contentDescription = stringResource(R.string.heroines_preview_desc, local.name),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
                        ) {
                            Text(
                                text = packKindLabel(local.kind, local.targetCharacterId, characterNames),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
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
                        }
                    }
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

@Composable
private fun RemotePackCard(
    remote: RemoteHeroine,
    installed: InstalledHeroine?,
    previewPath: String?,
    isDownloading: Boolean,
    downloadProgress: Float?,
    anyDownloading: Boolean,
    isActive: Boolean,
    kindLabel: String,
    onDownload: (RemoteHeroine) -> Unit,
    onActivate: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    val needsUpdate = installed != null && installed.version != remote.version
    SectionCard(title = remote.name) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeroinePreviewThumb(
                previewPath = previewPath,
                contentDescription = stringResource(R.string.heroines_preview_desc, remote.name),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
            ) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
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
            }
        }
        if (isDownloading) {
            Spacer(Modifier.height(VaiwuSpacing.xs))
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
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
                    enabled = !anyDownloading,
                    modifier = Modifier.weight(1f),
                )
            }
            if (installed != null) {
                VaiwuSecondaryButton(
                    text = stringResource(
                        if (isActive) R.string.heroines_selected else R.string.heroines_apply,
                    ),
                    onClick = { onActivate(remote.id) },
                    enabled = !isActive && !anyDownloading,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (installed != null) {
            TextButton(
                onClick = { onDelete(remote.id) },
                enabled = !anyDownloading,
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

@Composable
internal fun HeroinePreviewThumb(
    previewPath: String?,
    contentDescription: String,
) {
    var bitmap by remember(previewPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(previewPath) {
        bitmap = if (previewPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(previewPath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .width(PREVIEW_THUMB_WIDTH)
            .height(PREVIEW_THUMB_HEIGHT)
            .clip(RoundedCornerShape(PREVIEW_THUMB_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun packKindLabel(
    kind: String,
    targetCharacterId: Int?,
    characterNames: Array<String>,
): String = if (kind == KIND_OUTFIT) {
    val targetName = targetCharacterId?.let { characterNames.getOrNull(it) }
    if (targetName != null) {
        stringResource(R.string.heroines_outfit_for, targetName)
    } else {
        stringResource(R.string.heroines_outfit_generic)
    }
} else {
    stringResource(R.string.heroines_new_heroine)
}

internal fun formatPackSize(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> String.format(java.util.Locale.US, "%.1f MB", bytes / BYTES_PER_MB)
    bytes >= BYTES_PER_KB -> String.format(java.util.Locale.US, "%.0f KB", bytes / BYTES_PER_KB)
    else -> "$bytes B"
}
