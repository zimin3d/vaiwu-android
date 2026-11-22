package su.neit.vaiwu

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Heroine art ratio (width / height) of the sprite: 180x240 dp. */
private const val SPRITE_ASPECT_RATIO = 0.75f

/** Share of screen height allocated to the top pet stage + side stat panel row. */
private const val STAGE_HEIGHT_FRACTION = 0.48f

/** Floor for the stage height so short landscape viewports still fit the sprite and speech bubble. */
private val STAGE_MIN_HEIGHT = 280.dp

/** Fixed width of the vertical stat bar panel beside the stage. */
private val SIDE_STAT_PANEL_WIDTH = 132.dp

/** Semi-opaque backdrop alpha for the side stat card so room art softly shows through. */
private const val SIDE_PANEL_ALPHA = 0.9f

/** Subtle top scrim behind the speech bubble to keep text legible over bright morning art. */
private const val STAGE_TOP_SCRIM_ALPHA = 0.25f

/** Vertical stop (0..1) where the bottom fade into screen background starts. */
private const val STAGE_BOTTOM_SCRIM_START = 0.55f

/** Maximum speech bubble height and visible line count so long phrases never push the sprite off-stage. */
private val SPEECH_BUBBLE_MAX_HEIGHT = 96.dp
private const val SPEECH_BUBBLE_MAX_LINES = 3

/** Number of columns in the care-action button grid. */
private const val CARE_GRID_COLUMNS = 2

/** Sentinel item id passed to `onFeed` for the free rice bowl (paid dishes use non-negative `ShopItem` ids). */
private const val FREE_RICE_ITEM_ID = -1

/** Icon dimensions and dialog elevation for Home cards and the feed picker. */
private val HOME_LINK_ICON_SIZE = 32.dp
private val FEED_OPTION_ICON_SIZE = 28.dp
private val FEED_OPTION_ICON_BOX_SIZE = 32.dp
private val FEED_DIALOG_TONAL_ELEVATION = 6.dp

/** Per-emotion sprite animation frame intervals (ms): slower for calm/sleepy, livelier for happy. */
private const val IDLE_FRAME_DURATION_MS = 420L
private const val SLEEPY_FRAME_DURATION_MS = 650L
private const val HAPPY_FRAME_DURATION_MS = 260L
private const val OTHER_EMOTION_FRAME_DURATION_MS = 220L

/**
 * Home tab (UI-HOME): the pet stage, the stats, the care grid, the sickness
 * banner and the entry cards. Every pet state keeps the navigation available.
 */
@Composable
fun HomeScreen(
    pet: PetSnapshot,
    speech: String?,
    hour: Int,
    onAction: (Int) -> Unit,
    onFeedMenu: () -> Unit,
    onGames: () -> Unit,
    onShop: () -> Unit,
    onPet: () -> Unit,
    onRestart: () -> Unit,
    inventory: InventorySnapshot? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stageHeight = (maxHeight * STAGE_HEIGHT_FRACTION).coerceAtLeast(STAGE_MIN_HEIGHT)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
            ) {
                PetStage(
                    pet = pet,
                    speech = speech,
                    backgroundAsset = homeBackgroundForHour(hour),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SideStatPanel(
                    pet = pet,
                    inventory = inventory,
                    modifier = Modifier
                        .width(SIDE_STAT_PANEL_WIDTH)
                        .fillMaxHeight()
                        .padding(
                            top = VaiwuSpacing.small,
                            end = VaiwuSpacing.small,
                            bottom = VaiwuSpacing.small,
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaiwuSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
            ) {
                if (pet.isSick && pet.lifeState == LifeState.Awake) {
                    SicknessBanner(
                        onMedicine = { onAction(PetAction.Medicine) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when (pet.lifeState) {
                    LifeState.Dead -> DeadCard(onRestart = onRestart, modifier = Modifier.fillMaxWidth())
                    LifeState.Sleeping -> SleepingCard(
                        pet = pet,
                        onWake = { onAction(PetAction.Wake) },
                        onMedicine = { onAction(PetAction.Medicine) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LifeState.Awake -> {
                        HomeCareGrid(
                            kinds = homeOffers(pet).filterIsInstance<HomeOffer.Care>().map { it.kind }.distinct(),
                            onAction = onAction,
                            onFeedMenu = onFeedMenu,
                            onGames = onGames,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        VaiwuSecondaryButton(
                            text = stringResource(R.string.action_sleep),
                            onClick = { onAction(PetAction.Sleep) },
                            enabled = pet.canSleep,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(VaiwuSpacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                ) {
                    HomeLinkCard(
                        iconRes = R.drawable.icon_shop,
                        label = stringResource(R.string.shop_link_label),
                        onClick = onShop,
                        modifier = Modifier.weight(1f),
                    )
                    HomeLinkCard(
                        iconRes = R.drawable.icon_pet,
                        label = stringResource(R.string.pet_tab_label),
                        onClick = onPet,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(VaiwuSpacing.small))
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/**
 * Pet needs and battle bars, shown beside the pet instead of under it. The
 * battle bars (HP, MP, XP) come from the same snapshot the battle screen uses.
 */
@Composable
private fun SideStatPanel(
    pet: PetSnapshot,
    inventory: InventorySnapshot?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = SIDE_PANEL_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(VaiwuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.xs),
        ) {
            StatMeter(
                label = stringResource(R.string.stat_hunger),
                value = pet.hunger,
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_happiness),
                value = pet.happiness,
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_energy),
                value = pet.energy,
                modifier = Modifier.fillMaxWidth(),
            )
            StatMeter(
                label = stringResource(R.string.stat_discipline),
                value = pet.discipline,
                modifier = Modifier.fillMaxWidth(),
            )
            if (inventory != null) {
                Spacer(Modifier.height(VaiwuSpacing.small))
                StatMeter(
                    label = stringResource(R.string.battle_hp),
                    value = inventory.hp.toFloat(),
                    max = inventory.hpMax.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                StatMeter(
                    label = stringResource(R.string.battle_mp),
                    value = inventory.mp.toFloat(),
                    max = inventory.mpMax.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                StatMeter(
                    label = stringResource(R.string.stat_xp),
                    value = inventory.xp.toFloat(),
                    max = inventory.xpNext.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Main pet backdrop card showing the time-of-day room art, optional speech
 * bubble, animated heroine sprite, and sleeping/sick status chips.
 */
@Composable
private fun PetStage(
    pet: PetSnapshot,
    speech: String?,
    backgroundAsset: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(VaiwuRadius.card))
            .border(
                width = 1.dp,
                color = Color(Palette.OutlineMuted),
                shape = RoundedCornerShape(VaiwuRadius.card),
            ),
    ) {
        val spriteHeight = petSpriteHeightForStage(maxHeight)
        val spriteWidth = spriteHeight * SPRITE_ASPECT_RATIO
        AssetImage(
            assetName = backgroundAsset,
            description = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(Palette.Background).copy(alpha = STAGE_TOP_SCRIM_ALPHA),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        STAGE_BOTTOM_SCRIM_START to Color.Transparent,
                        1f to Color(Palette.Background),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = VaiwuSpacing.screenPadding,
                    vertical = VaiwuSpacing.small,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (speech != null) {
                SpeechBubble(text = speech)
                Spacer(Modifier.height(VaiwuSpacing.small))
            }
            PetSprite(
                characterId = pet.characterId,
                emotionId = pet.emotionId,
                description = pet.petName,
                modifier = Modifier.size(spriteWidth, spriteHeight),
                frameDurationMs = when (pet.emotionId) {
                    Emotion.Idle -> IDLE_FRAME_DURATION_MS
                    Emotion.Sleepy -> SLEEPY_FRAME_DURATION_MS
                    Emotion.Happy -> HAPPY_FRAME_DURATION_MS
                    else -> OTHER_EMOTION_FRAME_DURATION_MS
                },
            )
            Spacer(Modifier.height(VaiwuSpacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pet.lifeState == LifeState.Sleeping) {
                    StatusChip(
                        text = stringResource(R.string.status_sleeping),
                        tone = StatTone.Warning,
                    )
                }
                if (pet.isSick) {
                    StatusChip(
                        text = stringResource(R.string.status_sick),
                        tone = StatTone.Danger,
                    )
                }
            }
        }
    }
}

/** Capped dialogue bubble shown above the heroine sprite when she reacts to an action. */
@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(max = SPEECH_BUBBLE_MAX_HEIGHT),
        shape = RoundedCornerShape(VaiwuRadius.card),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
            textAlign = TextAlign.Center,
            maxLines = SPEECH_BUBBLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Alert banner displayed on Home when an awake pet needs medicine. */
@Composable
private fun SicknessBanner(
    onMedicine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.size(VaiwuSpacing.small))
                Text(
                    text = stringResource(R.string.sick_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.sick_text),
                style = MaterialTheme.typography.bodySmall,
            )
            VaiwuSecondaryButton(
                text = stringResource(R.string.action_medicine),
                onClick = onMedicine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Recovery card shown when the pet has passed away, offering to start a new run. */
@Composable
private fun DeadCard(onRestart: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.dead_title), modifier = modifier) {
        Text(
            text = stringResource(R.string.dead_text),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(VaiwuSpacing.small))
        VaiwuPrimaryButton(
            text = stringResource(R.string.start_over),
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Action panel shown while the pet is asleep, exposing Wake and (if needed) Medicine. */
@Composable
private fun SleepingCard(
    pet: PetSnapshot,
    onWake: () -> Unit,
    onMedicine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
    ) {
        VaiwuPrimaryButton(
            text = stringResource(R.string.action_wake),
            onClick = onWake,
            modifier = Modifier.fillMaxWidth(),
        )
        if (pet.isSick) {
            VaiwuSecondaryButton(
                text = stringResource(R.string.action_medicine),
                onClick = onMedicine,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Two-column grid of primary daily care actions (Feed, Wash, Clean, Talk, Hug, Games). */
@Composable
private fun HomeCareGrid(
    kinds: List<CareKind>,
    onAction: (Int) -> Unit,
    onFeedMenu: () -> Unit,
    onGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
    ) {
        kinds.chunked(CARE_GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
            ) {
                row.forEach { kind ->
                    CareActionButton(
                        iconRes = careIcon(kind),
                        label = stringResource(careLabelRes(kind)),
                        onClick = {
                            when (kind) {
                                // Feeding opens the menu (free rice + paid
                                // dishes) instead of applying one fixed meal.
                                CareKind.Feed -> onFeedMenu()
                                CareKind.Games -> onGames()
                                else -> onAction(careAction(kind))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Secondary navigation card linking Home directly to the Shop or Pet profile screen. */
@Composable
private fun HomeLinkCard(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = VaiwuSizes.careActionHeight),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small, Alignment.CenterHorizontally),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(HOME_LINK_ICON_SIZE),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun careLabelRes(kind: CareKind): Int = when (kind) {
    CareKind.Feed -> R.string.action_feed
    CareKind.Wash -> R.string.action_wash
    CareKind.Clean -> R.string.action_clean
    CareKind.Talk -> R.string.action_talk
    CareKind.Hug -> R.string.action_hug
    CareKind.Games -> R.string.action_games
}

private fun careIcon(kind: CareKind): Int = when (kind) {
    CareKind.Feed -> R.drawable.icon_feed
    CareKind.Wash -> R.drawable.icon_wash
    CareKind.Clean -> R.drawable.icon_clean
    CareKind.Talk -> R.drawable.icon_talk
    CareKind.Hug -> R.drawable.icon_hug
    CareKind.Games -> R.drawable.icon_games
}

/**
 * Feed menu: free rice plus the shop's food items, like the ESP firmware.
 * A dish already in the bag is eaten for free; otherwise the row shows its
 * price and buys-and-feeds in one tap. Rows whose effect would be wasted
 * (an energy drink at full energy, a treat at full mood) are disabled, so a
 * purchase from this menu can never be money thrown away.
 */
@Composable
fun FeedDialog(
    options: List<ShopItemSnapshot>,
    onFeed: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.large),
            shape = RoundedCornerShape(VaiwuRadius.sheet),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = FEED_DIALOG_TONAL_ELEVATION,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(VaiwuSpacing.large),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                Text(
                    text = stringResource(R.string.feed_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.feed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FeedOption(
                    name = stringResource(R.string.feed_free_rice),
                    note = stringResource(R.string.feed_free),
                    enabled = true,
                    onClick = { onFeed(FREE_RICE_ITEM_ID) },
                ) {
                    Image(
                        painter = painterResource(R.drawable.icon_feed),
                        contentDescription = null,
                        modifier = Modifier.size(FEED_OPTION_ICON_SIZE),
                    )
                }
                options.forEach { item ->
                    FeedOption(
                        name = item.name,
                        note = if (item.count > 0) {
                            stringResource(R.string.feed_in_bag, item.count)
                        } else {
                            stringResource(R.string.buy_with_price, item.price)
                        },
                        enabled = item.usable,
                        onClick = { onFeed(item.id) },
                    ) {
                        Image(
                            painter = painterResource(shopIconFor(item.id)),
                            contentDescription = null,
                            modifier = Modifier.size(FEED_OPTION_ICON_SIZE),
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/** Single selectable dish row inside [FeedDialog]. */
@Composable
private fun FeedOption(
    name: String,
    note: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.medium, vertical = VaiwuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
        ) {
            Box(modifier = Modifier.size(FEED_OPTION_ICON_BOX_SIZE), contentAlignment = Alignment.Center) {
                icon()
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(name = "Home", showBackground = false)
@Composable
fun HomePreviewNormal() {
    VaiwuTheme {
        HomeScreen(
            pet = HomePreviewPet(lifeState = LifeState.Awake, emotionId = 1),
            speech = null,
            hour = 12,
            onAction = {},
            onFeedMenu = {},
            onGames = {},
            onShop = {},
            onPet = {},
            onRestart = {},
        )
    }
}

@Preview(name = "HomeSleep", showBackground = false)
@Composable
fun HomePreviewSleep() {
    VaiwuTheme {
        HomeScreen(
            pet = HomePreviewPet(lifeState = LifeState.Sleeping, emotionId = 4, isSick = true),
            speech = null,
            hour = 23,
            onAction = {},
            onFeedMenu = {},
            onGames = {},
            onShop = {},
            onPet = {},
            onRestart = {},
        )
    }
}

@Preview(name = "HomeIllness", showBackground = false)
@Composable
fun HomePreviewIllness() {
    VaiwuTheme {
        HomeScreen(
            pet = HomePreviewPet(lifeState = LifeState.Awake, emotionId = 5, isSick = true),
            speech = "Длинная строка для проверки переноса текста на экране при большом масштабе.",
            hour = 9,
            onAction = {},
            onFeedMenu = {},
            onGames = {},
            onShop = {},
            onPet = {},
            onRestart = {},
        )
    }
}

@Preview(name = "HomeDeath", showBackground = false)
@Composable
fun HomePreviewDeath() {
    VaiwuTheme {
        HomeScreen(
            pet = HomePreviewPet(lifeState = LifeState.Dead, emotionId = 6),
            speech = null,
            hour = 3,
            onAction = {},
            onFeedMenu = {},
            onGames = {},
            onShop = {},
            onPet = {},
            onRestart = {},
        )
    }
}

@Preview(name = "HomeLong", showBackground = false, fontScale = 1.3f)
@Composable
fun HomePreviewLong() {
    VaiwuTheme {
        HomeScreen(
            pet = HomePreviewPet(lifeState = LifeState.Awake, emotionId = 1),
            speech = "Очень длинная фраза питомца, которая должна оставаться читаемой при увеличенном масштабе интерфейса без обрезки кнопок и заголовков.",
            hour = 15,
            onAction = {},
            onFeedMenu = {},
            onGames = {},
            onShop = {},
            onPet = {},
            onRestart = {},
        )
    }
}

private fun HomePreviewPet(
    lifeState: Int,
    emotionId: Int,
    isSick: Boolean = false,
) = PetSnapshot(
    characterId = 0,
    characterName = "Aoi",
    petName = "Aoi",
    hunger = 64f,
    happiness = 71f,
    energy = 82f,
    discipline = 55f,
    weightKg = 12.4f,
    ageSeconds = 3_456_000,
    coins = 12,
    emotionId = emotionId,
    lifeState = lifeState,
    isSick = isSick,
)

