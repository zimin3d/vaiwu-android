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

/** Ordered character slugs matching native `CharacterId` values (0..7). */
internal val characterIds = listOf("aoi", "hina", "rin", "yuki", "akane", "unicorn", "kolobok", "vesper")

/** Ordered emotion slugs matching native `Emotion` values (0..6). */
internal val emotionIds = listOf("idle", "happy", "sad", "angry", "sleepy", "sick", "dead")

/** Fallback delay between animation frames when the caller does not override it. */
private const val DEFAULT_FRAME_DURATION_MS = 180L

/** Lower bound for the in-memory sprite LRU cache (bytes). */
private const val CACHE_MIN_BYTES = 16 * 1024 * 1024

/** Upper bound for the in-memory sprite LRU cache (bytes) so sprites do not crowd out the UI heap. */
private const val CACHE_MAX_BYTES = 32 * 1024 * 1024

/** Fraction denominator of the JVM max heap allotted to [SpriteBitmapCache] (`maxMemory / 6`). */
private const val CACHE_HEAP_DIVISOR = 6L

/** Neutral vertical scale at the bottom of the breathing cycle. */
private const val BREATH_SCALE_BASE = 1.0f

/** Peak vertical scale for awake breathing (+1.5% height stretch anchored at the feet). */
private const val BREATH_SCALE_AWAKE = 1.015f

/** Peak vertical scale while sleepy (+2.2% deeper inhale). */
private const val BREATH_SCALE_SLEEPY = 1.022f

/** Half-cycle duration (inhale or exhale) for normal awake breathing in milliseconds. */
private const val BREATH_PERIOD_AWAKE_MS = 1800

/** Half-cycle duration for slower, deeper sleepy breathing in milliseconds. */
private const val BREATH_PERIOD_SLEEPY_MS = 2400

/** Horizontal pivot (center) and vertical pivot (feet) so breathing expands upward without floating. */
private const val BREATH_PIVOT_X = 0.5f
private const val BREATH_PIVOT_Y = 1.0f

/**
 * Immutable frame sequence and playback cadence for a sprite animation.
 *
 * @property frames Ordered asset filenames inside `app/src/main/assets/`.
 * @property frameDurationMs Time each frame remains on screen before advancing.
 */
data class SpriteAnimation(
    val frames: List<String>,
    val frameDurationMs: Long = DEFAULT_FRAME_DURATION_MS,
) {
    init {
        require(frames.isNotEmpty())
        require(frameDurationMs > 0)
    }
}

/**
 * Resolves animation frames for [base] against the shipped asset filenames.
 *
 * Supports both 0-based sequences (`base_00.png`, `base_01.png`, ...) and
 * 1-based extensions (`base.png`, `base_01.png`, `base_02.png`, ...), falling
 * back to `[base.png]` when no extra frames exist.
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

/** Lazily caches the root `assets/` directory listing so frame lookup never hits disk repeatedly. */
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

/** Process-wide LRU bitmap cache sized between [CACHE_MIN_BYTES] and [CACHE_MAX_BYTES]. */
private object SpriteBitmapCache {
    private val maxBytes = maxOf(
        CACHE_MIN_BYTES,
        minOf(CACHE_MAX_BYTES, (Runtime.getRuntime().maxMemory() / CACHE_HEAP_DIVISOR).toInt()),
    )
    private val bitmaps = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(assetName: String): Bitmap? = bitmaps.get(assetName)

    fun put(assetName: String, bitmap: Bitmap) {
        bitmaps.put(assetName, bitmap)
    }
}

/**
 * Renders the active pet's sprite for [characterId] and [emotionId].
 *
 * Automatically cycles through multi-frame assets when available and layers a
 * subtle 60-FPS vertical breathing scale anchored at the feet for all living
 * emotions (`emotionId != Emotion.Dead`).
 */
@Composable
fun PetSprite(
    characterId: Int,
    emotionId: Int,
    description: String,
    modifier: Modifier,
    frameDurationMs: Long = DEFAULT_FRAME_DURATION_MS,
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
    val reducedMotion = LocalReducedMotion.current
    val breathingModifier = if (alive && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "pet-breathing")
        val breathScaleY by transition.animateFloat(
            initialValue = BREATH_SCALE_BASE,
            targetValue = if (emotionId == Emotion.Sleepy) BREATH_SCALE_SLEEPY else BREATH_SCALE_AWAKE,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (emotionId == Emotion.Sleepy) BREATH_PERIOD_SLEEPY_MS else BREATH_PERIOD_AWAKE_MS,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pet-breath-scale-y",
        )
        modifier.graphicsLayer {
            scaleY = breathScaleY
            transformOrigin = TransformOrigin(BREATH_PIVOT_X, BREATH_PIVOT_Y)
        }
    } else {
        modifier
    }
    SpriteImage(animation = animation, description = description, modifier = breathingModifier)
}

/**
 * Decodes and cycles [animation] frames without layout jitter or placeholder flashes.
 *
 * Frame 0 is decoded and painted immediately so emotion transitions feel instant;
 * remaining frames preload on [Dispatchers.IO] before the playback loop begins.
 */
@Composable
fun SpriteImage(animation: SpriteAnimation, description: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    var frameIndex by remember(animation) { mutableIntStateOf(0) }
    var currentBitmap by remember(animation) {
        mutableStateOf(SpriteBitmapCache.get(animation.frames.first()))
    }
    LaunchedEffect(animation) {
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

/**
 * Asynchronously loads and displays a single static image from `assets/[assetName]`,
 * reserving its layout bounds with a tinted placeholder until decoding finishes.
 */
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

/** Maximum pixel dimension (width or height) to decode into memory to prevent OOMs on large source art. */
private const val MAX_DECODED_SPRITE_DIMENSION = 640

/** Calculates power-of-two `inSampleSize` for [options] downsampling towards [maxDimension]. */
internal fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > maxDimension || width > maxDimension) {
        while ((height / inSampleSize) > maxDimension || (width / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/** Synchronous cache-backed bitmap loader used by Canvas renderers on [Dispatchers.IO]. */
internal fun loadAssetBitmap(context: Context, assetName: String): Bitmap? =
    decodeAsset(context, assetName)

/** Reads [assetName] from [SpriteBitmapCache], decoding from `assets/` with bounded memory on a cache miss. */
private fun decodeAsset(
    context: Context,
    assetName: String,
    maxDimension: Int = MAX_DECODED_SPRITE_DIMENSION,
): Bitmap? {
    SpriteBitmapCache.get(assetName)?.let { return it }
    val bitmap = runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetName).use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val sampleSize = calculateInSampleSize(boundsOptions, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.assets.open(assetName).use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }.getOrNull() ?: return null
    SpriteBitmapCache.put(assetName, bitmap)
    return bitmap
}
