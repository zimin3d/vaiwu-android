package su.neit.vaiwu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Resolves animation frames for [base] against the shipped asset filenames.
 * Supports both 0-based sequences (`base_00.png`, `base_01.png`, ...) and
 * 1-based extensions (`base.png`, `base_01.png`, `base_02.png`, ...).
 */
internal fun resolveSpriteFrames(base: String, availableAssets: Set<String>): List<String> {
    val zeroBased = generateSequence(0) { it + 1 }
        .map { frame -> "${base}_${frame.toString().padStart(2, '0')}.png" }
        .takeWhile(availableAssets::contains)
        .toList()
    if (zeroBased.isNotEmpty()) return zeroBased

    val oneBased = generateSequence(1) { it + 1 }
        .map { frame -> "${base}_${frame.toString().padStart(2, '0')}.png" }
        .takeWhile(availableAssets::contains)
        .toList()
    val baseFile = "$base.png"
    if (oneBased.isNotEmpty()) {
        return if (baseFile in availableAssets) listOf(baseFile) + oneBased else oneBased
    }
    return listOf(baseFile)
}

private object SpriteAssetIndex {
    @Volatile
    private var cached: Set<String>? = null

    fun list(context: Context): Set<String> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: (context.applicationContext.assets.list("")?.toSet().orEmpty()).also {
                cached = it
            }
        }
    }
}

private object SpriteBitmapCache {
    private val maxBytes = maxOf(
        16 * 1024 * 1024,
        minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 6L).toInt()),
    )
    private val bitmaps = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(assetName: String): Bitmap? = bitmaps.get(assetName)

    fun put(assetName: String, bitmap: Bitmap) {
        bitmaps.put(assetName, bitmap)
    }
}

@Composable
fun PetSprite(
    characterId: Int,
    emotionId: Int,
    description: String,
    modifier: Modifier,
    frameDurationMs: Long = 180L,
) {
    val context = LocalContext.current
    val animation = remember(characterId, emotionId, frameDurationMs) {
        val character = characterIds.getOrElse(characterId) { characterIds.first() }
        val emotion = emotionIds.getOrElse(emotionId) { emotionIds.first() }
        val base = "sprite_${character}_${emotion}"
        val available = SpriteAssetIndex.list(context)
        SpriteAnimation(resolveSpriteFrames(base, available), frameDurationMs)
    }
    val alive = emotionId != Emotion.Dead
    val breathingModifier = if (alive) {
        val transition = rememberInfiniteTransition(label = "pet-breathing")
        val breathScaleY by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (emotionId == Emotion.Sleepy) 1.022f else 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (emotionId == Emotion.Sleepy) 2400 else 1800,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pet-breath-scale-y",
        )
        modifier.graphicsLayer {
            scaleY = breathScaleY
            transformOrigin = TransformOrigin(0.5f, 1.0f)
        }
    } else {
        modifier
    }
    SpriteImage(animation = animation, description = description, modifier = breathingModifier)
}

@Composable
fun SpriteImage(animation: SpriteAnimation, description: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    var frameIndex by remember(animation) { mutableIntStateOf(0) }
    var currentBitmap by remember(animation) {
        mutableStateOf(SpriteBitmapCache.get(animation.frames.first()))
    }
    LaunchedEffect(animation) {
        // Show frame 0 immediately, then preload remaining frames on IO before
        // cycling so emotion switches feel instant and frame swaps never flash.
        val first = withContext(Dispatchers.IO) { decodeAsset(context, animation.frames.first()) }
        if (first != null) currentBitmap = first
        if (animation.frames.size > 1) {
            val rest = withContext(Dispatchers.IO) {
                animation.frames.drop(1).map { decodeAsset(context, it) }
            }
            val loaded = listOf(first) + rest
            if (currentBitmap == null) {
                currentBitmap = loaded.firstOrNull { it != null }
            }
            while (true) {
                delay(animation.frameDurationMs)
                frameIndex = (frameIndex + 1) % animation.frames.size
                loaded[frameIndex]?.let { currentBitmap = it }
            }
        }
    }
    val bmp = currentBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
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

internal fun loadAssetBitmap(context: Context, assetName: String): Bitmap? =
    decodeAsset(context, assetName)

private fun decodeAsset(context: Context, assetName: String): Bitmap? {
    SpriteBitmapCache.get(assetName)?.let { return it }
    val bitmap = runCatching {
        context.assets.open(assetName).use(BitmapFactory::decodeStream)
    }.getOrNull() ?: return null
    SpriteBitmapCache.put(assetName, bitmap)
    return bitmap
}
