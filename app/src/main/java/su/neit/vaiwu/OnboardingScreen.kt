package su.neit.vaiwu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

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
    var selectedId by rememberSaveable { mutableStateOf(-1) }
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
            columns = GridCells.Fixed(2),
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
            onValueChange = { petName = it.take(24) },
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

@Composable
private fun CharacterCard(
    id: Int,
    name: String,
    profile: CharacterProfile?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(VaiwuRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(VaiwuSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VaiwuSpacing.small),
        ) {
            PetSprite(
                characterId = id,
                emotionId = 0,
                description = name,
                modifier = Modifier.size(96.dp, 128.dp),
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
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
