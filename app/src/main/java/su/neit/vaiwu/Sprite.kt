package su.neit.vaiwu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val characterIds = listOf("aoi", "hina", "rin", "yuki", "akane", "unicorn", "kolobok", "vesper")
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

private object SpriteBitmapCache {
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(assetName: String): Bitmap? = bitmaps.get(assetName)

    fun put(assetName: String, bitmap: Bitmap) {
        bitmaps.put(assetName, bitmap)
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
fun AssetImage(
    assetName: String,
    description: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = FilterQuality.Low,
) {
    val context = LocalContext.current.applicationContext
    val bitmap = remember(assetName) { mutableStateOf(SpriteBitmapCache.get(assetName)) }
    LaunchedEffect(assetName) {
        bitmap.value = withContext(Dispatchers.IO) { decodeAsset(context, assetName) }
    }
    val currentBitmap = bitmap.value
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = description,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = filterQuality,
        )
    } else {
        // Placeholder keeps the layout stable while the sprite is loading.
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private fun decodeAsset(context: Context, assetName: String): Bitmap? {
    SpriteBitmapCache.get(assetName)?.let { return it }
    val bitmap = runCatching {
        context.assets.open(assetName).use(BitmapFactory::decodeStream)
    }.getOrNull() ?: return null
    SpriteBitmapCache.put(assetName, bitmap)
    return bitmap
}
