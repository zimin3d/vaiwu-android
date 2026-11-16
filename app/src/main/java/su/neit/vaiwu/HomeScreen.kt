package su.neit.vaiwu

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

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
    onGames: () -> Unit,
    onShop: () -> Unit,
    onPet: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stageHeight = (maxHeight * 0.4f).coerceAtLeast(240.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PetStage(
                pet = pet,
                speech = speech,
                backgroundAsset = homeBackgroundForHour(hour),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaiwuSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
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
                    LifeState.Awake -> HomeCareGrid(
                        kinds = homeOffers(pet).filterIsInstance<HomeOffer.Care>().map { it.kind }.distinct(),
                        onAction = onAction,
                        onGames = onGames,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(VaiwuSpacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.medium),
                ) {
                    HomeLinkCard(
                        icon = Icons.Default.ShoppingCart,
                        label = stringResource(R.string.shop_link_label),
                        onClick = onShop,
                        modifier = Modifier.weight(1f),
                    )
                    HomeLinkCard(
                        icon = Icons.Default.Person,
                        label = stringResource(R.string.pet_tab_label),
                        onClick = onPet,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PetStage(
    pet: PetSnapshot,
    speech: String?,
    backgroundAsset: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AssetImage(
            assetName = backgroundAsset,
            description = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(Palette.Background).copy(alpha = 0.6f),
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
                        listOf(
                            Color.Transparent,
                            Color(Palette.Background),
                        ),
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
                modifier = Modifier.size(144.dp, 192.dp),
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

@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
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
        )
    }
}

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
                Spacer(Modifier.size(8.dp))
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

@Composable
private fun HomeCareGrid(
    kinds: List<CareKind>,
    onAction: (Int) -> Unit,
    onGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
    ) {
        kinds.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.itemGap),
            ) {
                row.forEach { kind ->
                    CareActionButton(
                        icon = careIcon(kind),
                        label = stringResource(careLabelRes(kind)),
                        onClick = {
                            if (kind == CareKind.Games) {
                                onGames()
                            } else {
                                onAction(careAction(kind))
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

@Composable
private fun HomeLinkCard(
    icon: ImageVector,
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
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
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

private fun careIcon(kind: CareKind): ImageVector = when (kind) {
    CareKind.Feed -> VaiwuIcons.bowl
    CareKind.Wash -> VaiwuIcons.drop
    CareKind.Clean -> VaiwuIcons.sparkles
    CareKind.Talk -> VaiwuIcons.speech
    CareKind.Hug -> Icons.Default.Favorite
    CareKind.Games -> Icons.Default.PlayArrow
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

