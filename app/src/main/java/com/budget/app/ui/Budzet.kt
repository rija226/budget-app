package com.budget.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.budget.app.ui.components.BudzetNavigationBar
import com.budget.app.ui.components.CategoryAvatar
import com.budget.app.ui.components.NavTab
import com.budget.app.ui.components.categoryIcon
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.format.formatPlain
import com.budget.app.ui.format.parseAmountMinor
import com.budget.app.ui.theme.BudzetTheme

/** Expense categories only; [txCount] = transactions in the category (shown in the delete dialog). */
data class BudzetCategory(val name: String, val limitMinor: Long?, val txCount: Int)

data class BudzetState(val periodBudgetMinor: Long, val categories: List<BudzetCategory>)

/** "Ostalo" is the fallback that takes over a deleted category's transactions, so it cannot be deleted itself. */
const val FALLBACK_CATEGORY = "Ostalo"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudzetScreen(
    state: BudzetState,
    modifier: Modifier = Modifier,
    initialDelete: String? = null,
    onBudget: (Long) -> Unit = {},
    onLimit: (String, Long?) -> Unit = { _, _ -> },
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
    onAdd: (String) -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(initialDelete) }
    val allocated = state.categories.mapNotNull { it.limitMinor }.sum()
    val rest = state.periodBudgetMinor - allocated

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Budžet",
                Modifier.heightIn(min = 64.dp).padding(16.dp, 18.dp).semantics { heading() },
                style = t.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 112.dp),
                Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Budžet po periodu", style = t.bodyMedium.copy(fontWeight = FontWeight.Bold), color = c.onSurfaceVariant)
                                Text(state.periodBudgetMinor.formatEur(), style = t.headlineMedium.copy(fontWeight = FontWeight.Bold), softWrap = false)
                            }
                            IconButton({ editBudget = true }) { Icon(Icons.Rounded.Edit, "Izmijeni budžet", tint = c.onSurfaceVariant) }
                        }
                        Text(
                            "Zbir limita ${allocated.formatEur()} · " +
                                if (rest >= 0) "${rest.formatEur()} nije raspoređeno" else "${(-rest).formatEur()} više od budžeta",
                            style = t.bodyMedium,
                            color = c.onSurfaceVariant,
                        )
                    }
                }

                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
                    Text("Kategorije i limiti", Modifier.semantics { heading() }, style = t.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Prazno polje = bez limita", style = t.labelMedium, color = c.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
                    Column {
                        state.categories.forEachIndexed { i, k ->
                            if (i > 0) HorizontalDivider(color = c.outlineVariant)
                            LimitRow(k, { onLimit(k.name, it) }, { renaming = k.name }, { deleting = k.name })
                        }
                    }
                }
                Text("Izmjena limita važi odmah, za tekući period.", style = t.labelMedium, color = c.onSurfaceVariant)
            }
        }
        ExtendedFloatingActionButton(
            { adding = true },
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = c.primaryContainer,
            contentColor = c.onPrimaryContainer,
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("Nova kategorija", Modifier.padding(start = 12.dp))
        }
    }

    if (editBudget) AmountDialog("Budžet po periodu", state.periodBudgetMinor, { editBudget = false }) { editBudget = false; onBudget(it) }
    if (adding) NameDialog("Nova kategorija", "", "Dodaj", { adding = false }) { adding = false; onAdd(it) }
    renaming?.let { old -> NameDialog("Preimenuj kategoriju", old, "Sačuvaj", { renaming = null }) { renaming = null; onRename(old, it) } }
    deleting?.let { name ->
        val k = state.categories.find { it.name == name }
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Obrisati kategoriju „$name“?", textAlign = TextAlign.Center) },
            text = {
                val moved = k?.txCount ?: 0
                Text(
                    (if (moved > 0) "$moved ${transakcija(moved)} iz ove kategorije ${if (moved % 10 == 1 && moved % 100 != 11) "prelazi" else "prelaze"} u „$FALLBACK_CATEGORY“. " else "") +
                        (k?.limitMinor?.let { "Limit od ${it.formatEur()} se briše, a budžet po periodu ostaje isti." } ?: "Budžet po periodu ostaje isti."),
                )
            },
            confirmButton = {
                TextButton({ deleting = null; onDelete(name) }, colors = ButtonDefaults.textButtonColors(contentColor = c.error)) { Text("Obriši") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Otkaži") } },
        )
    }
}

@Composable
private fun LimitRow(k: BudzetCategory, onLimit: (Long?) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var text by rememberSaveable(k.name) { mutableStateOf(k.limitMinor?.formatPlain() ?: "") }
    var menu by remember { mutableStateOf(false) }
    val parsed = parseAmountMinor(text)
    val invalid = text.isNotBlank() && parsed == null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        Arrangement.spacedBy(12.dp),
        Alignment.CenterVertically,
    ) {
        CategoryAvatar(categoryIcon(k.name))
        Text(k.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        OutlinedTextField(
            text,
            {
                text = it
                if (it.isBlank()) onLimit(null) else parseAmountMinor(it)?.let(onLimit)
            },
            Modifier.width(124.dp).semantics { contentDescription = "Limit za ${k.name}" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            placeholder = { Text("Bez limita", Modifier.fillMaxWidth(), textAlign = TextAlign.End, maxLines = 1) },
            suffix = { Text("€", Modifier.padding(start = 4.dp)) },
            isError = invalid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Više opcija za ${k.name}", tint = c.onSurfaceVariant) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Preimenuj") }, { menu = false; onRename() })
                if (k.name != FALLBACK_CATEGORY) DropdownMenuItem({ Text("Obriši") }, { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun AmountDialog(title: String, initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (initial > 0) initial.formatPlain() else "") }
    val parsed = parseAmountMinor(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it },
                label = { Text("Iznos") },
                suffix = { Text("€") },
                isError = text.isNotBlank() && parsed == null,
                supportingText = if (text.isNotBlank() && parsed == null) {
                    { Text("Upiši iznos, npr. 780,00") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton({ parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Sačuvaj") } },
        dismissButton = { TextButton(onDismiss) { Text("Otkaži") } },
    )
}

// ---- Previews ----

@Composable
private fun BudzetFrame(delete: String? = null) = BudzetTheme {
    Scaffold(bottomBar = { BudzetNavigationBar(NavTab.Budzet) }) { BudzetScreen(Sample.budzet, Modifier.padding(it), initialDelete = delete) }
}

@Preview(name = "Budžet", widthDp = 390, heightDp = 1150)
@Preview(name = "Budžet · tamno", widthDp = 390, heightDp = 1150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Budžet · font 200%", widthDp = 390, heightDp = 2200, fontScale = 2f)
@Composable
private fun BudzetPreview() = BudzetFrame()

@Preview(name = "Budžet · brisanje", widthDp = 390, heightDp = 1150)
@Preview(name = "Budžet · brisanje · tamno", widthDp = 390, heightDp = 1150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudzetDeletePreview() = BudzetFrame("Zabava")
