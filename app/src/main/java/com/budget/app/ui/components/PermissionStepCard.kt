package com.budget.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budget.app.ui.theme.LocalStatus
import com.budget.app.ui.theme.Spacing

enum class StepState(val defaultStatus: String) {
    Todo("Nije urađeno"),
    Done("Uključeno"),
    Blocked("Android je blokirao ovu dozvolu"),
}

/** [action] is shown unless the step is done; [primaryAction] makes it a filled button (step 1). */
@Composable
fun PermissionStepCard(
    number: Int,
    title: String,
    state: StepState,
    modifier: Modifier = Modifier,
    description: String? = null,
    status: String = state.defaultStatus,
    action: String? = null,
    primaryAction: Boolean = false,
    onAction: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val statusColor = when (state) {
        StepState.Todo -> c.onSurfaceVariant
        StepState.Done -> s.ok
        StepState.Blocked -> c.error
    }
    Card(
        modifier,
        MaterialTheme.shapes.large,
        CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
        border = if (state == StepState.Blocked) BorderStroke(1.dp, c.error) else null,
    ) {
        Row(Modifier.padding(16.dp), Arrangement.spacedBy(16.dp), Alignment.Top) {
            Surface(
                Modifier.size(Spacing.avatar),
                CircleShape,
                color = when (state) {
                    StepState.Todo -> Color.Transparent
                    StepState.Done -> s.okContainer
                    StepState.Blocked -> c.errorContainer
                },
                contentColor = when (state) {
                    StepState.Todo -> c.onSurfaceVariant
                    StepState.Done -> s.onOkContainer
                    StepState.Blocked -> c.onErrorContainer
                },
                border = if (state == StepState.Todo) BorderStroke(2.dp, c.outline) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (state) {
                        StepState.Todo -> Text(number.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                        StepState.Done -> Icon(Icons.Rounded.Check, null, Modifier.size(22.dp))
                        StepState.Blocked -> Icon(Icons.Rounded.Block, null, Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant) }
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (state) {
                            StepState.Todo -> Icons.Rounded.RadioButtonUnchecked
                            StepState.Done -> Icons.Rounded.CheckCircle
                            StepState.Blocked -> Icons.Rounded.Error
                        },
                        null,
                        Modifier.size(18.dp),
                        statusColor,
                    )
                    Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor)
                }
                if (action != null && state != StepState.Done) {
                    val pad = PaddingValues(horizontal = 20.dp)
                    if (primaryAction && state == StepState.Todo) Button(onAction, Modifier.padding(top = 8.dp), contentPadding = pad) { Text(action) }
                    else FilledTonalButton(onAction, Modifier.padding(top = 8.dp), contentPadding = pad) { Text(action) }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun PermissionStepCardPreview() = PreviewBox {
    PermissionStepCard(1, "Pristup notifikacijama", StepState.Todo, description = "Obavezno.", action = "Uključi pristup", primaryAction = true)
    PermissionStepCard(2, "Slanje notifikacija", StepState.Todo, action = "Dozvoli")
    PermissionStepCard(1, "Pristup notifikacijama", StepState.Done)
    PermissionStepCard(1, "Pristup notifikacijama", StepState.Blocked, action = "Pogledaj kako se odblokira")
}
