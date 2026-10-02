package com.budget.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.ui.components.BackTopBar
import com.budget.app.ui.components.CategoryLabel
import com.budget.app.ui.components.LabelKind
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.TimeFormat
import com.budget.app.ui.format.dayLabel
import com.budget.app.ui.format.displayCurrency
import com.budget.app.ui.format.formatApproxEur
import com.budget.app.ui.format.formatCurrency
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.format.parseAmountMinor
import com.budget.app.ui.theme.BudzetTheme
import com.budget.app.ui.theme.LocalStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class UnknownNotification(val rawId: Long, val app: String, val at: LocalDateTime, val text: String)

data class ZaProvjeruState(
    val today: LocalDate,
    /** Transaction + where it came from ("Google Wallet"). */
    val aiCategories: List<Pair<TransactionUi, String>>,
    val unknown: List<UnknownNotification>,
    val noRate: List<TransactionUi>,
) {
    val count get() = aiCategories.size + unknown.size + noRate.size
}

private fun stavke(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "$n stavka čeka"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n stavke čekaju"
    else -> "$n stavki čeka"
}

/** "1,08" or "117.2" -> rate; null unless positive. */
internal fun parseRate(text: String): BigDecimal? =
    text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

@Composable
fun ZaProvjeruScreen(
    state: ZaProvjeruState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onConfirm: (Long) -> Unit = {},
    onChange: (Long) -> Unit = {},
    onAddFromRaw: (Long) -> Unit = {},
    onIgnore: (Long) -> Unit = {},
    onRate: (String, BigDecimal) -> Unit = { _, _ -> },
    onEurAmount: (Long, Long) -> Unit = { _, _ -> },
) {
    val c = MaterialTheme.colorScheme
    var rateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var eurFor by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(modifier.fillMaxSize()) {
        BackTopBar("Za provjeru", onBack)
        if (state.count == 0) {
            AllChecked(Modifier.weight(1f))
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp), Arrangement.spacedBy(24.dp)) {
            Text(
                "${stavke(state.count)} tvoju odluku. Ništa se ne briše samo od sebe.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            // aiSuggested = false: trošak još bez kategorije (npr. kad nema API ključa), može samo da se izabere
            val allAi = state.aiCategories.all { it.first.aiSuggested }
            if (state.aiCategories.isNotEmpty()) ReviewSection(
                Icons.Rounded.AutoAwesome, c.tertiary,
                "${if (allAi) "Nepotvrđene AI kategorije" else "Kategorije za provjeru"} (${state.aiCategories.size})",
            ) {
                state.aiCategories.forEach { (t, source) ->
                    ReviewCard {
                        TxHeader(t, "${t.at.toLocalDate().dayLabel(state.today)}, ${t.at.format(TimeFormat)} · $source")
                        CategoryLabel(t.category, kind = if (t.aiSuggested) LabelKind.AiSuggested else LabelKind.Confirmed)
                        Actions {
                            if (t.aiSuggested) {
                                FilledTonalButton(
                                    { onConfirm(t.id) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer),
                                    contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
                                ) { IconText(Icons.Rounded.Check, "Potvrdi") }
                                TextButton({ onChange(t.id) }) { Text("Promijeni") }
                            } else {
                                FilledTonalButton({ onChange(t.id) }, contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Izaberi kategoriju") }
                            }
                        }
                    }
                }
            }
            if (state.unknown.isNotEmpty()) ReviewSection(Icons.AutoMirrored.Rounded.HelpOutline, c.onSurfaceVariant, "Neprepoznate notifikacije (${state.unknown.size})") {
                state.unknown.forEach { n ->
                    ReviewCard {
                        Text(
                            "${n.app} · ${n.at.format(DayFormat)} u ${n.at.format(TimeFormat)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.onSurfaceVariant,
                        )
                        Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium, c.surfaceContainerHighest) {
                            Text(
                                n.text,
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                            )
                        }
                        Text("Izgleda kao plaćanje, ali format nije poznat.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        Actions {
                            FilledTonalButton({ onAddFromRaw(n.rawId) }, contentPadding = PaddingValues(start = 16.dp, end = 20.dp)) {
                                IconText(Icons.Rounded.Add, "Dodaj kao transakciju")
                            }
                            TextButton({ onIgnore(n.rawId) }) { Text("Zanemari") }
                        }
                    }
                }
            }
            if (state.noRate.isNotEmpty()) ReviewSection(Icons.Rounded.CurrencyExchange, c.onSurfaceVariant, "Transakcije bez kursa (${state.noRate.size})") {
                state.noRate.forEach { t ->
                    ReviewCard {
                        TxHeader(t, "${t.at.format(DayFormat)} u ${t.at.format(TimeFormat)} · ${t.category}")
                        Text(
                            "Nema kursa za ${displayCurrency(t.currency)}, pa iznos još nije uračunat u budžet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant,
                        )
                        Actions {
                            FilledTonalButton({ rateFor = t.currency }, contentPadding = PaddingValues(start = 16.dp, end = 20.dp)) {
                                IconText(Icons.Rounded.Edit, "Unesi kurs za ${displayCurrency(t.currency)}")
                            }
                            TextButton({ eurFor = t.id }) { Text("Unesi iznos u €") }
                        }
                    }
                }
            }
        }
    }

    rateFor?.let { cur -> RateDialog(cur, { rateFor = null }) { rateFor = null; onRate(cur, it) } }
    eurFor?.let { id -> EurDialog({ eurFor = null }) { eurFor = null; onEurAmount(id, it) } }
}

@Composable
private fun ReviewSection(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint)
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
        content()
    }
}

@Composable
private fun ReviewCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun TxHeader(t: TransactionUi, meta: String) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
            Text(t.merchant, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(meta, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (t.currency == "EUR") t.amountMinor.formatEur() else t.amountMinor.formatCurrency(t.currency),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                softWrap = false,
            )
            if (t.currency != "EUR") Text(t.amountEurMinor?.formatApproxEur() ?: "bez kursa", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun IconText(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Text(text, Modifier.padding(start = 8.dp))
}

@Composable
private fun AllChecked(modifier: Modifier) {
    val s = LocalStatus.current
    Column(
        modifier.fillMaxWidth().padding(32.dp, 32.dp, 32.dp, 120.dp),
        Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        Alignment.CenterHorizontally,
    ) {
        Surface(Modifier.size(80.dp), CircleShape, s.okContainer, s.onOkContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.TaskAlt, null, Modifier.size(40.dp)) }
        }
        Text("Sve je provjereno", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Kad neka notifikacija ili kategorija ne bude jasna, pojaviće se ovdje.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RateDialog(currency: String, onDismiss: () -> Unit, onConfirm: (BigDecimal) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val rate = parseRate(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kurs za ${displayCurrency(currency)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Koliko ${displayCurrency(currency)} vrijedi 1 €? Kurs se pamti za sve buduće transakcije u ovoj valuti.")
                OutlinedTextField(
                    text, { text = it },
                    prefix = { Text("1 € = ") },
                    suffix = { Text(displayCurrency(currency)) },
                    isError = text.isNotBlank() && rate == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton({ rate?.let(onConfirm) }, enabled = rate != null) { Text("Sačuvaj") } },
        dismissButton = { TextButton(onDismiss) { Text("Otkaži") } },
    )
}

@Composable
private fun EurDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val cents = parseAmountMinor(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Iznos u eurima") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Upiši koliko je ovo plaćanje skinuto sa računa u eurima, npr. iz izvoda.")
                OutlinedTextField(
                    text, { text = it },
                    suffix = { Text("€") },
                    isError = text.isNotBlank() && cents == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton({ cents?.let(onConfirm) }, enabled = cents != null) { Text("Sačuvaj") } },
        dismissButton = { TextButton(onDismiss) { Text("Otkaži") } },
    )
}

// ---- Previews ----

@Composable
private fun ZpFrame(state: ZaProvjeruState) = BudzetTheme { Surface(color = MaterialTheme.colorScheme.surface) { ZaProvjeruScreen(state) } }

@Preview(name = "Za provjeru", widthDp = 390, heightDp = 1300)
@Preview(name = "Za provjeru · tamno", widthDp = 390, heightDp = 1300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Za provjeru · font 200%", widthDp = 390, heightDp = 2400, fontScale = 2f)
@Composable
private fun ZaProvjeruPreview() = ZpFrame(Sample.zaProvjeru)

@Preview(name = "Za provjeru · prazno", widthDp = 390, heightDp = 844)
@Preview(name = "Za provjeru · prazno · tamno", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ZaProvjeruPraznoPreview() = ZpFrame(ZaProvjeruState(Sample.today, emptyList(), emptyList(), emptyList()))
