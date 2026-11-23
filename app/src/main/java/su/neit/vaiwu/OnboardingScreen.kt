package su.neit.vaiwu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

/** Sentinel character id indicating no heroine has been selected yet. */
private const val NO_CHARACTER_SELECTED = -1

/** Number of columns in the heroine selection grid. */
private const val CHARACTER_GRID_COLUMNS = 2

/** Maximum length (characters) allowed for a custom pet name (matches `PetViewModel.createPet`). */
private const val MAX_PET_NAME_LENGTH = 24

/** Preview sprite dimensions inside each onboarding character card (3:4 portrait aspect). */
private val CHARACTER_CARD_SPRITE_WIDTH = 96.dp
private val CHARACTER_CARD_SPRITE_HEIGHT = 128.dp

/**
 * Onboarding: the pet is only chosen here, it is settled with the explicit
 * «Поселить» action. The selection itself never creates a pet (UI-ONB-01).
 */
@Composable
fun OnboardingScreen(
    profile: (Int) -> CharacterProfile?,
    onSettle: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val characterNames = stringArrayResource(R.array.character_names)
    var selectedId by rememberSaveable { mutableStateOf(NO_CHARACTER_SELECTED) }
    var petName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = VaiwuSpacing.screenPadding,
                    end = VaiwuSpacing.screenPadding,
                    top = VaiwuSpacing.large,
                    bottom = VaiwuSpacing.small,
                ),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(CHARACTER_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                vertical = VaiwuSpacing.small,
                horizontal = VaiwuSpacing.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            itemsIndexed(characterNames) { index, name ->
                CharacterCard(
                    id = index,
                    name = name,
                    profile = profile(index),
                    selected = index == selectedId,
                    onClick = { selectedId = index },
                )
            }
        }
        OutlinedTextField(
            value = petName,
            onValueChange = { petName = it.take(MAX_PET_NAME_LENGTH) },
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            supportingText = { Text(stringResource(R.string.onboarding_name_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.large, vertical = VaiwuSpacing.small),
        )
        VaiwuPrimaryButton(
            text = stringResource(R.string.onboarding_settle),
            onClick = { onSettle(selectedId, petName) },
            enabled = selectedId >= 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VaiwuSpacing.large, vertical = VaiwuSpacing.large),
        )
    }
}

/** Selectable heroine card showing her idle sprite, base RPG stats, and favorite food. */
@Composable
private fun CharacterCard(
    id: Int,
    name: String,
    profile: CharacterProfile?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = selected
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .pressScale(interactionSource)
            .semantics { this.selected = isSelected },
        shape = RoundedCornerShape(VaiwuRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VaiwuSpacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
            ) {
                PetSprite(
                    characterId = id,
                    emotionId = Emotion.Idle,
                    description = name,
                    modifier = Modifier.size(CHARACTER_CARD_SPRITE_WIDTH, CHARACTER_CARD_SPRITE_HEIGHT),
                )
                Text(name, style = MaterialTheme.typography.titleMedium)
                profile?.let {
                    Text(
                        text = stringResource(
                            R.string.onboarding_stats,
                            it.strength,
                            it.vitality,
                            it.agility,
                            it.magic,
                            it.luck,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_food, it.favoriteFood),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(VaiwuSpacing.small),
                )
            }
        }
    }
}
