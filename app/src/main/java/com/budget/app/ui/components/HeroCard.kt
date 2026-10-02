package com.budget.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budget.app.ui.format.formatEur

sealed interface HeroState {
    /** Daily allowance left until the next salary. */
    data class Normal(val dailyCents: Long, val daysLeft: Int) : HeroState

    /** Today's allowance is spent; [overCents] is today's overspend. */
    data class Over(val overCents: Long, val daysLeft: Int) : HeroState

    /** No salary detected yet; source and minimum are null while the salary rule is not set. */
    data class Empty(val salarySource: String?, val salaryMinCents: Long?) : HeroState

    /** A salary exists but the period budget is not set. */
    data object NoBudget : HeroState
}

private fun daysLeftText(n: Int) = "još $n ${if (n % 10 == 1 && n % 100 != 11) "dan" else "dana"} do plate"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroCard(
    state: HeroState,
    modifier: Modifier = Modifier,
    onMarkSalary: () -> Unit = {},
    onSalaryRule: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val normal = state is HeroState.Normal
    Card(
        modifier.fillMaxWidth(),
        MaterialTheme.shapes.extraLarge,
        CardDefaults.cardColors(
            containerColor = if (normal) c.primaryContainer else c.surfaceContainerHigh,
            contentColor = if (normal) c.onPrimaryContainer else c.onSurface,
        ),
    ) {
        when (state) {
            is HeroState.Normal -> Column(Modifier.padding(24.dp, 24.dp, 24.dp, 20.dp), Arrangement.spacedBy(4.dp)) {
                Text("Danas smiješ da potrošiš", style = t.titleMedium)
                Text(state.dailyCents.formatEur(), style = t.displayLarge, softWrap = false)
                DaysLeft(state.daysLeft)
            }

            is HeroState.Over -> Column(Modifier.padding(24.dp, 24.dp, 24.dp, 20.dp), Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Error, null, Modifier.size(22.dp), c.error)
                    Text("Prekoračeno za", style = t.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.error)
                }
                Text(state.overCents.formatEur(), style = t.displayLarge, color = c.error, softWrap = false)
                Text(
                    "Današnji iznos je potrošen. Razlika se raspoređuje na preostale dane, pa će sutrašnji iznos biti nešto manji.",
                    Modifier.padding(top = 8.dp),
                    c.onSurfaceVariant,
                    style = t.bodyMedium,
                )
                DaysLeft(state.daysLeft)
            }

            is HeroState.Empty -> Column(Modifier.padding(24.dp), Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.HourglassTop, null, Modifier.size(32.dp), c.primary)
                Text("Čeka se prva plata", Modifier.padding(top = 4.dp), style = t.titleLarge)
                Text(
                    "Dnevni iznos se računa od plate do plate. " +
                        if (state.salarySource != null && state.salaryMinCents != null) {
                            "Period počinje sam kad stigne uplata iz izvora „${state.salarySource}“ veća od ${state.salaryMinCents.formatEur()}."
                        } else {
                            "Podesi pravilo za platu da bi je aplikacija sama prepoznala, ili je označi ručno kad stigne."
                        },
                    color = c.onSurfaceVariant,
                    style = t.bodyMedium,
                )
                FlowRow(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onMarkSalary, contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Označi platu ručno") }
                    TextButton(onSalaryRule, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Pravilo za platu") }
                }
            }

            HeroState.NoBudget -> Column(Modifier.padding(24.dp), Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Event, null, Modifier.size(32.dp), c.primary)
                Text("Upiši budžet perioda", Modifier.padding(top = 4.dp), style = t.titleLarge)
                Text(
                    "Plata je stigla. Upiši koliko želiš da potrošiš do sljedeće plate, pa ćeš ovdje vidjeti koliko smiješ danas.",
                    color = c.onSurfaceVariant,
                    style = t.bodyMedium,
                )
                FilledTonalButton(onSalaryRule, Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Postavi budžet") }
            }
        }
    }
}

@Composable
private fun DaysLeft(days: Int) {
    Row(
        Modifier.padding(top = 8.dp),
        Arrangement.spacedBy(8.dp),
        Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Event, null, Modifier.size(20.dp))
        Text(daysLeftText(days), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Hidden when [count] is 0. */
@Composable
fun ReviewChip(count: Int, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    if (count == 0) return
    Surface(
        onClick,
        modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = 12.dp, end = 8.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.FactCheck, null, Modifier.size(18.dp))
            Text("Za provjeru ($count)", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
        }
    }
}

@ThemePreviews
@Composable
private fun HeroCardPreview() = PreviewBox {
    HeroCard(HeroState.Normal(2340, 14))
    HeroCard(HeroState.Over(810, 14))
    HeroCard(HeroState.Empty("Moja banka", 100000))
}

@ThemePreviews
@Composable
private fun ReviewChipPreview() = PreviewBox {
    ReviewChip(3)
    ReviewChip(0) // hidden: renders nothing
}
