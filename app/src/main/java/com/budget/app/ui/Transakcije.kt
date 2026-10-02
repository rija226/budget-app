package com.budget.app.ui

import com.budget.app.budget.PeriodRange
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.ui.components.BackTopBar
import com.budget.app.ui.components.BudzetNavigationBar
import com.budget.app.ui.components.NavTab
import com.budget.app.ui.components.TransactionRow
import com.budget.app.ui.components.TxKind
import com.budget.app.ui.components.categoryIcon
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.TimeFormat
import com.budget.app.ui.format.dayLabel
import com.budget.app.ui.format.formatApproxEur
import com.budget.app.ui.format.formatCurrency
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.format.formatPlain
import com.budget.app.ui.format.parseAmountMinor
import com.budget.app.ui.theme.BudzetTheme
import java.time.LocalDate

data class TransakcijeState(
    val today: LocalDate,
    val periods: List<PeriodRange>,
    val categories: List<String>,
    val transactions: List<TransactionUi>,
)

// ---- Transakcije (lista) ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransakcijeScreen(
    state: TransakcijeState,
    modifier: Modifier = Modifier,
    onTransaction: (Long) -> Unit = {},
    onAdd: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    var period by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var aiOnly by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val range = state.periods.getOrNull(period)
    val shown = remember(state, period, category, aiOnly, query) {
        state.transactions.filter { t ->
            (range == null || t.at.toLocalDate() in range) &&
                (category == null || t.category == category) &&
                (!aiOnly || t.aiSuggested) &&
                (query.isBlank() || t.merchant.contains(query, true) || t.category.contains(query, true))
        }.sortedByDescending { it.at }
    }
    val groups = remember(shown) { shown.groupBy { it.at.toLocalDate() }.toList() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Transakcije",
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                IconButton({ searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, if (searchOpen) "Zatvori pretragu" else "Pretraga", tint = c.onSurfaceVariant)
                }
            }
            if (searchOpen) SearchField(query, { query = it }, "Traži po trgovcu ili kategoriji")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 4.dp, 16.dp, 12.dp),
                Arrangement.spacedBy(8.dp),
            ) {
                DropdownChip(range?.label ?: "Period", true, state.periods.map { it.label }) { period = it }
                DropdownChip(category ?: "Kategorija", category != null, listOf("Sve kategorije") + state.categories) {
                    category = if (it == 0) null else state.categories[it - 1]
                }
                FilterChip(
                    aiOnly, { aiOnly = !aiOnly }, { Text("AI nepotvrđeno") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
            if (groups.isEmpty()) EmptyTransactions(Modifier.weight(1f))
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                groups.forEach { (day, items) ->
                    item(key = day.toString()) { DayHeader(day.dayLabel(state.today), groupTotal(items)) }
                    items(items, key = { it.id }) { t ->
                        TransactionRow(
                            t.merchant, t.category, t.icon, t.at.format(TimeFormat), t.amountMinor,
                            currency = t.currency, amountEurMinor = t.amountEurMinor, kind = t.kind,
                            aiSuggested = t.aiSuggested, onClick = { onTransaction(t.id) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onAdd,
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = c.primaryContainer,
            contentColor = c.onPrimaryContainer,
        ) { Icon(Icons.Rounded.Add, "Dodaj transakciju ručno") }
    }
}

/** Sum of the day's spending; a day with the salary marks the start of the period instead. */
private fun groupTotal(items: List<TransactionUi>): String =
    if (items.any { it.kind == TxKind.Salary }) "Početak perioda"
    else items.filter { it.kind == TxKind.Expense }.sumOf { it.amountEurMinor ?: 0 }.formatEur()

@Composable
private fun DayHeader(label: String, total: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
        Text(
            label,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(total, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
    }
}

@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        query, onQuery,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).focusRequester(focus),
        placeholder = { Text(placeholder) },
        singleLine = true,
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Filter chip that opens a menu; [onPick] gets the index in [options]. */
@Composable
internal fun DropdownChip(label: String, selected: Boolean, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected, { open = true }, { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o) }, { onPick(i); open = false }) }
        }
    }
}

@Composable
private fun EmptyTransactions(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        Alignment.CenterHorizontally,
    ) {
        Surface(Modifier.size(80.dp), CircleShape, c.surfaceContainerHigh, c.onSurfaceVariant) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, Modifier.size(40.dp)) }
        }
        Text(
            "Još nema transakcija u ovom periodu",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            "Plaćanja karticom se bilježe sama čim stigne notifikacija banke ili Walleta. Gotovinu i ostalo možeš dodati ručno.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Transakcija (detalj, izmjena, ručni unos) ----

/** [source] e.g. "Google Wallet" or "Ručni unos"; [raw] is the original notification; [rate] e.g. "1 € = 1,95583 KM". */
data class TransakcijaUi(val tx: TransactionUi, val source: String, val raw: String? = null, val rate: String? = null)

/** What the owner saved. For a new manual entry [amountMinor] and [currency] come from the form. */
data class TransakcijaEdit(
    val merchant: String,
    val category: String?,
    val categoryConfirmed: Boolean,
    val transfer: Boolean,
    val salary: Boolean,
    val amountMinor: Long?,
    val currency: String,
)

/** [ui] null = new manual transaction; with [newSalary] it is a salary entered by hand (starts a period). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransakcijaScreen(
    ui: TransakcijaUi?,
    categories: List<String>,
    modifier: Modifier = Modifier,
    startWithPicker: Boolean = false,
    prefillMerchant: String = "",
    newSalary: Boolean = false,
    onBack: () -> Unit = {},
    onSave: (TransakcijaEdit) -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val tx = ui?.tx
    val inflow = tx?.kind == TxKind.Salary || tx?.kind == TxKind.Income || newSalary
    var merchant by rememberSaveable { mutableStateOf(tx?.merchant ?: prefillMerchant) }
    var category by rememberSaveable { mutableStateOf(tx?.category?.takeIf { it in categories } ?: if (newSalary) "Plata" else null) }
    var confirmed by rememberSaveable { mutableStateOf(tx?.aiSuggested != true) }
    var transfer by rememberSaveable { mutableStateOf(tx?.kind == TxKind.Transfer) }
    var salary by rememberSaveable { mutableStateOf(tx?.kind == TxKind.Salary || newSalary) }
    var amountText by rememberSaveable { mutableStateOf(tx?.amountMinor?.formatPlain() ?: "") }
    var currency by rememberSaveable { mutableStateOf(tx?.currency ?: "EUR") }
    var picker by rememberSaveable { mutableStateOf(startWithPicker) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var extraCategories by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allCategories = categories + extraCategories
    val amount = parseAmountMinor(amountText)
    val canSave = merchant.isNotBlank() && (ui != null || (amount != null && currency.length == 3))

    Column(modifier.fillMaxSize()) {
        BackTopBar(if (ui != null) "Transakcija" else if (newSalary) "Nova plata" else "Nova transakcija", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 24.dp),
            Arrangement.spacedBy(20.dp),
        ) {
            if (tx != null) AmountHeader(ui) else ManualAmount(amountText, { amountText = it }, currency, { currency = it.uppercase().take(3) }, amount)

            if (tx != null && !confirmed && category != null) AiSuggestion(category!!, { confirmed = true }, { picker = true })

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    merchant, { merchant = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Trgovac") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences),
                )
                Box {
                    OutlinedTextField(
                        category ?: "", {},
                        Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Kategorija") },
                        placeholder = { Text("Izaberi kategoriju") },
                        leadingIcon = if (!confirmed) {
                            { Icon(Icons.Rounded.AutoAwesome, "AI prijedlog", tint = c.tertiary) }
                        } else null,
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                    )
                    Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = "Izaberi kategoriju") { picker = true })
                }
            }

            Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
                Column {
                    ToggleRow("Prenos", "Novac ide između tvojih računa. Ne računa se u potrošnju.", transfer) { transfer = it }
                    if (inflow) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.outlineVariant)
                        ToggleRow("Označi kao platu", "Ova uplata započinje novi period budžeta.", salary) { salary = it }
                    }
                }
            }

            ui?.raw?.let { RawNotificationCard(it) }
        }

        Surface(color = c.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 24.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                if (ui != null) TextButton(
                    { confirmDelete = true },
                    Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = c.error),
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp))
                    Text("Obriši", Modifier.padding(start = 8.dp))
                } else Box(Modifier)
                Button(
                    {
                        onSave(TransakcijaEdit(merchant.trim(), category, confirmed, transfer, salary, amount ?: tx?.amountMinor, currency))
                    },
                    Modifier.heightIn(min = 48.dp),
                    enabled = canSave,
                    contentPadding = PaddingValues(horizontal = 32.dp),
                ) { Text("Sačuvaj") }
            }
        }
    }

    if (picker) {
        ModalBottomSheet({ picker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceContainerLow) {
            CategoryPicker(
                subtitle = listOfNotNull(merchant.ifBlank { null }, tx?.let { amountText(it) }).joinToString(" · "),
                categories = allCategories,
                selected = category,
                aiSuggested = if (!confirmed) category else null,
                onPick = { category = it; confirmed = true; picker = false },
                onNew = { name -> extraCategories = extraCategories + name; category = name; confirmed = true; picker = false },
            )
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Obrisati transakciju?") },
        text = { Text("Transakcija nestaje iz liste i budžeta. Originalna notifikacija ostaje u sirovom logu.") },
        confirmButton = {
            TextButton({ confirmDelete = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = c.error)) { Text("Obriši") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Otkaži") } },
    )
}

private fun amountText(tx: TransactionUi) =
    if (tx.currency == "EUR") tx.amountMinor.formatEur() else tx.amountMinor.formatCurrency(tx.currency)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountHeader(ui: TransakcijaUi) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val tx = ui.tx
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(amountText(tx), style = t.headlineLarge.copy(fontWeight = FontWeight.Bold), softWrap = false)
        if (tx.currency != "EUR") Text(
            tx.amountEurMinor?.let { listOfNotNull(it.formatApproxEur(), ui.rate).joinToString(" · ") } ?: "Kurs nije unesen",
            style = t.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurfaceVariant,
        )
        FlowRow(Modifier.padding(top = 4.dp), Arrangement.spacedBy(12.dp), Arrangement.spacedBy(4.dp)) {
            Meta(Icons.Rounded.Schedule, "${tx.at.format(DayFormat)} u ${tx.at.format(TimeFormat)}")
            Meta(Icons.Rounded.Notifications, ui.source)
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManualAmount(text: String, onText: (String) -> Unit, currency: String, onCurrency: (String) -> Unit, parsed: Long?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            text, onText,
            Modifier.weight(1f),
            label = { Text("Iznos") },
            singleLine = true,
            isError = text.isNotBlank() && parsed == null,
            supportingText = if (text.isNotBlank() && parsed == null) {
                { Text("Upiši iznos, npr. 12,50") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        OutlinedTextField(
            currency, onCurrency,
            Modifier.weight(0.45f),
            label = { Text("Valuta") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
        )
    }
}

/** 1dp dashed tertiary outline around the AI suggestion card. */
private fun Modifier.dashedCard(color: Color) = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSuggestion(category: String, onConfirm: () -> Unit, onOther: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Column(Modifier.fillMaxWidth().dashedCard(c.tertiary).padding(16.dp), Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), c.tertiary)
            Text("AI prijedlog kategorije", style = t.bodyMedium.copy(fontWeight = FontWeight.Bold), color = c.tertiary)
        }
        Text(category, style = t.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Predloženo na osnovu naziva trgovca. Dok ne potvrdiš, iznos se već računa u ovu kategoriju.",
            style = t.bodyMedium,
            color = c.onSurfaceVariant,
        )
        FlowRow(Modifier.padding(top = 4.dp), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onConfirm,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer),
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                Text("Potvrdi", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onOther, contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Izaberi drugu") }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!on) }.padding(16.dp),
        Arrangement.spacedBy(16.dp),
        Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, null)
    }
}

@Composable
private fun RawNotificationCard(raw: String) {
    val c = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(true) }
    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { open = !open },
                Arrangement.spacedBy(12.dp),
                Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DataObject, null, Modifier.size(20.dp), c.onSurfaceVariant)
                Text("Originalna notifikacija", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Sakrij" else "Prikaži", tint = c.onSurfaceVariant)
            }
            if (open) Surface(Modifier.fillMaxWidth().padding(bottom = 16.dp), MaterialTheme.shapes.medium, c.surfaceContainerHighest, c.onSurfaceVariant) {
                Text(
                    raw,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    subtitle: String,
    categories: List<String>,
    selected: String?,
    aiSuggested: String?,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var naming by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(24.dp, 0.dp, 24.dp, 12.dp), Arrangement.spacedBy(4.dp)) {
            Text("Kategorija", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        categories.forEach { name ->
            val sel = name == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.RadioButton) { onPick(name) }
                    .then(if (sel) Modifier.drawBehind { drawRect(c.secondaryContainer) } else Modifier)
                    .padding(24.dp, 8.dp),
                Arrangement.spacedBy(16.dp),
                Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(name), null, tint = c.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    if (name == aiSuggested) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), c.tertiary)
                        Text("AI prijedlog", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.tertiary)
                    }
                }
                RadioButton(sel, null)
            }
        }
        TextButton({ naming = true }, Modifier.padding(start = 16.dp, top = 8.dp), contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text("Nova kategorija", Modifier.padding(start = 8.dp))
        }
    }
    if (naming) NameDialog("Nova kategorija", "", "Dodaj", { naming = false }) { naming = false; onNew(it) }
}

/** Small dialog with one text field (new category, rename). */
@Composable
internal fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it },
                label = { Text("Naziv") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton({ onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Otkaži") } },
    )
}

// ---- Previews ----

private val sampleState get() = TransakcijeState(Sample.today, Sample.periods, Sample.categoryNames, Sample.transactions)

@Composable
private fun TxFrame(state: TransakcijeState) = BudzetTheme {
    Scaffold(bottomBar = { BudzetNavigationBar(NavTab.Transakcije) }) { TransakcijeScreen(state, Modifier.padding(it)) }
}

@Preview(name = "Transakcije", widthDp = 390, heightDp = 1080)
@Preview(name = "Transakcije · tamno", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Transakcije · font 200%", widthDp = 390, heightDp = 1600, fontScale = 2f)
@Composable
private fun TransakcijePreview() = TxFrame(sampleState)

@Preview(name = "Transakcije · prazno", widthDp = 390, heightDp = 844)
@Preview(name = "Transakcije · prazno · tamno", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransakcijePraznoPreview() = TxFrame(sampleState.copy(transactions = emptyList()))

@Composable
private fun DetailFrame(ui: TransakcijaUi?, picker: Boolean = false) = BudzetTheme {
    Surface(color = MaterialTheme.colorScheme.surface) { TransakcijaScreen(ui, Sample.categoryNames, startWithPicker = picker) }
}

@Preview(name = "Transakcija", widthDp = 390, heightDp = 1250)
@Preview(name = "Transakcija · tamno", widthDp = 390, heightDp = 1250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransakcijaPreview() = DetailFrame(Sample.detail(2))

@Preview(name = "Transakcija · kategorija", widthDp = 390, heightDp = 1250)
@Preview(name = "Transakcija · kategorija · tamno", widthDp = 390, heightDp = 1250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransakcijaPickerPreview() = DetailFrame(Sample.detail(2), picker = true)

@Preview(name = "Nova transakcija", widthDp = 390, heightDp = 900)
@Composable
private fun NovaTransakcijaPreview() = DetailFrame(null)
