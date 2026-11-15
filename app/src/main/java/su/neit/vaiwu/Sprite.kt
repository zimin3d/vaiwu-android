package su.neit.vaiwu

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

internal val characterIds = listOf("aoi", "hina", "rin", "yuki", "akane", "unicorn", "kolobok")
internal val emotionIds = listOf("idle", "happy", "sad", "angry", "sleepy", "sick", "dead")

data class SpriteAnimation(
    val frames: List<String>,
    val frameDurationMs: Long = 180,
) {
    init {
        require(frames.isNotEmpty())
        require(frameDurationMs > 0)
    }
}

@Composable
fun PetSprite(characterId: Int, emotionId: Int, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val animation = remember(characterId, emotionId) {
        val character = characterIds.getOrElse(characterId) { characterIds.first() }
        val emotion = emotionIds.getOrElse(emotionId) { emotionIds.first() }
        val base = "sprite_${character}_${emotion}"
        val available = context.assets.list("")?.toSet().orEmpty()
        val frames = generateSequence(0) { it + 1 }
            .map { frame -> "${base}_${frame.toString().padStart(2, '0')}.png" }
            .takeWhile(available::contains)
            .toList()
        SpriteAnimation(frames.ifEmpty { listOf("$base.png") })
    }
    SpriteImage(animation = animation, description = description, modifier = modifier)
}

@Composable
fun SpriteImage(animation: SpriteAnimation, description: String, modifier: Modifier) {
    var frameIndex by remember(animation) { mutableIntStateOf(0) }
    LaunchedEffect(animation) {
        if (animation.frames.size > 1) {
            while (true) {
                delay(animation.frameDurationMs)
                frameIndex = (frameIndex + 1) % animation.frames.size
            }
        }
    }
    AssetImage(
        assetName = animation.frames[frameIndex],
        description = description,
        modifier = modifier,
    )
}

@Composable
private fun AssetImage(assetName: String, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(assetName) {
        runCatching {
            context.assets.open(assetName).use { stream ->
                BitmapFactory.decodeStream(stream).asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier)
    }
}
