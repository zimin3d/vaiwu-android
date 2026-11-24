package su.neit.vaiwu

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val HERO_VIDEO_ASPECT_RATIO = 832f / 480f
private val HERO_VIDEO_CORNER = 16.dp
private val HERO_VIDEO_INNER_CORNER = 12.dp

/**
 * Resolves the bundled `res/raw/video_*.mp4` resource ID for the active heroine or built-in character.
 * Returns `0` when no bundled video exists for the selection.
 */
@Suppress("DiscouragedApi")
internal fun resolveHeroVideoRawResId(
    context: Context,
    characterId: Int,
    activeHeroineId: String?,
): Int {
    val rawName = when {
        activeHeroineId == "luna" -> "video_luna"
        else -> when (characterId) {
            0 -> "video_aoi"
            1 -> "video_hina"
            2 -> "video_rin"
            3 -> "video_yuki"
            4 -> "video_akane"
            5 -> "video_uni"
            6 -> "video_kolobok"
            7 -> "video_vesper"
            else -> null
        }
    } ?: return 0
    return context.resources.getIdentifier(rawName, "raw", context.packageName)
}

/**
 * Looping video showcase dialog for the active character using Android's built-in [VideoView].
 */
@Composable
fun HeroVideoDialog(
    title: String,
    rawResId: Int,
    onDismiss: () -> Unit,
) {
    var muted by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.screenPadding),
            shape = RoundedCornerShape(HERO_VIDEO_CORNER),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(VaiwuSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$rawResId"))
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                activePlayer = mp
                                val vol = if (muted) 0f else 1f
                                mp.setVolume(vol, vol)
                                start()
                            }
                        }
                    },
                    update = {
                        val vol = if (muted) 0f else 1f
                        runCatching { activePlayer?.setVolume(vol, vol) }
                    },
                    onRelease = { view ->
                        activePlayer = null
                        runCatching { view.stopPlayback() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(HERO_VIDEO_ASPECT_RATIO)
                        .clip(RoundedCornerShape(HERO_VIDEO_INNER_CORNER)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { muted = !muted }) {
                        Text(
                            text = stringResource(
                                if (muted) R.string.hero_video_unmute else R.string.hero_video_mute,
                            ),
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.hero_video_close))
                    }
                }
            }
        }
    }
}
