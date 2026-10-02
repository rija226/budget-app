package com.budget.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.format.formatPlain
import com.budget.app.ui.format.parseAmountMinor
import java.math.BigDecimal
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.AppDatabase
import com.budget.app.data.RawNotification
import com.budget.app.data.RawStatus
import com.budget.app.ui.components.BackTopBar
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.TimeFormat
import com.budget.app.ui.theme.BudzetTheme
import com.budget.app.ui.theme.LocalStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun Long.toLocal(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

// ---- Podešavanja ----

data class PermissionsUi(val listener: Boolean, val notifs: Boolean, val battery: Boolean)

/** [lastLogAt] is null when the log is empty. [salarySource]: "any", "bankA", "bankB" or null. */
data class PodesavanjaState(
    val perms: PermissionsUi,
    val logCount: Int,
    val lastLogAt: LocalDateTime?,
    val today: LocalDate,
    val apiKey: String? = null,
    val salarySource: String? = null,
    val salaryMinMinor: Long? = null,
    val rates: Map<String, BigDecimal> = emptyMap(),
)

/** Settings writes; the screen only collects values. */
class PodesavanjaActions(
    val onApiKey: (String?) -> Unit = {},
    val onSalarySource: (String) -> Unit = {},
    val onSalaryMin: (Long) -> Unit = {},
    val onRate: (String, BigDecimal) -> Unit = { _, _ -> },
    val onReprocess: () -> Unit = {},
)

@Composable
fun PodesavanjaScreen(tick: Int, state: PodesavanjaState, actions: PodesavanjaActions, onBack: () -> Unit, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val perms = remember(tick) { PermissionsUi(listenerEnabled(ctx), postNotificationsGranted(ctx), batteryUnrestricted(ctx)) }
    PodesavanjaContent(
        state.copy(perms = perms),
        modifier,
        actions,
        onBack = onBack,
        onLog = onLog,
        onListener = { ctx.openListenerSettings() },
        onNotifs = { ctx.openAppNotificationSettings() },
        onBattery = { ctx.openBatterySettings() },
    )
}

private val SALARY_SOURCES = listOf("any" to "Bilo koja banka", "bankA" to "Banka A", "bankB" to "Banka B")

@Composable
fun PodesavanjaContent(
    state: PodesavanjaState,
    modifier: Modifier = Modifier,
    actions: PodesavanjaActions = PodesavanjaActions(),
    onBack: () -> Unit = {},
    onLog: () -> Unit = {},
    onListener: () -> Unit = {},
    onNotifs: () -> Unit = {},
    onBattery: () -> Unit = {},
) {
    val p = state.perms
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize()) {
        BackTopBar("Podešavanja", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp), Arrangement.spacedBy(8.dp)) {
            SettingsSection("Dozvole") {
                if (p.listener) SettingRow(Icons.Rounded.Notifications, "Pristup notifikacijama", "Uključeno", Tone.Ok)
                else SettingRow(Icons.Rounded.Notifications, "Pristup notifikacijama", "Isključeno · plaćanja se ne bilježe", Tone.Err, "Uključi", onListener)
                if (p.notifs) SettingRow(Icons.Rounded.NotificationsActive, "Slanje notifikacija", "Uključeno", Tone.Ok)
                else SettingRow(Icons.Rounded.NotificationsActive, "Slanje notifikacija", "Isključeno · nema upozorenja o limitu", Tone.Warn, "Uključi", onNotifs)
                if (p.battery) SettingRow(Icons.Rounded.BatteryAlert, "Optimizacija baterije", "Isključena", Tone.Ok)
                else SettingRow(Icons.Rounded.BatteryAlert, "Optimizacija baterije", "Uključena · Android može zaustaviti praćenje", Tone.Warn, "Isključi", onBattery)
            }
            SettingsSection("AI analiza") {
                SettingRow(
                    Icons.Rounded.Key, "API ključ",
                    state.apiKey?.let { "${it.take(7)}…${it.takeLast(4)}" } ?: "Nije unesen · bez njega nema AI kategorija ni analize",
                    if (state.apiKey != null) Tone.Ok else Tone.Neutral,
                    onClick = { dialog = "key" },
                )
                SettingRow(Icons.Rounded.Schedule, "Kada se piše analiza", "Kad stigne plata, čim ima interneta")
            }
            SettingsSection("Pravilo za platu") {
                SettingRow(
                    Icons.Rounded.AccountBalance, "Izvor plate",
                    SALARY_SOURCES.find { it.first == state.salarySource }?.second ?: "Nije podešen",
                    onClick = { dialog = "source" },
                )
                SettingRow(
                    Icons.Rounded.Payments, "Minimalni iznos",
                    state.salaryMinMinor?.let { "${it.formatEur()} · manje uplate ne počinju novi period" } ?: "Nije podešen",
                    onClick = { dialog = "min" },
                )
            }
            SettingsSection("Kursevi valuta") {
                SettingRow(Icons.Rounded.Euro, "Osnovna valuta", "EUR")
                SettingRow(Icons.Rounded.CurrencyExchange, "KM (BAM)", "1 € = 1,95583 KM · fiksni kurs")
                state.rates.toSortedMap().forEach { (cur, rate) ->
                    SettingRow(
                        Icons.Rounded.CurrencyExchange, cur, "1 € = ${rate.toPlainString().replace('.', ',')} $cur · uneseno ručno",
                        onClick = { dialog = "rate:$cur" },
                    )
                }
                SettingRow(Icons.Rounded.Add, "Dodaj valutu", "Kurs unosiš ručno", onClick = { dialog = "rate:" })
            }
            SettingsSection("Podaci") {
                val last = state.lastLogAt?.let { at ->
                    val day = if (at.toLocalDate() == state.today) "danas" else at.format(DayFormat)
                    " · posljednji $day u ${at.format(TimeFormat)}"
                } ?: ""
                SettingRow(Icons.Rounded.DataObject, "Sirovi log notifikacija", "${state.logCount} zapisa$last", onClick = onLog)
            }
            SettingsSection("Izgled") {
                SettingRow(Icons.Rounded.Contrast, "Tema", "Prati sistem")
            }
            Column(Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ponovna obrada", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Ponovo čita sačuvane notifikacije koje još nisu postale transakcije. Ručne izmjene i potvrđene kategorije ostaju.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(actions.onReprocess, contentPadding = PaddingValues(start = 16.dp, end = 24.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Text("Obradi ponovo sve", Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    when (val d = dialog) {
        "key" -> TextDialog("API ključ", "Ključ sa console.anthropic.com. Čuva se samo na ovom telefonu.", "", "Sačuvaj", { dialog = null }, secret = true,
            extra = if (state.apiKey != null) ({ TextButton({ dialog = null; actions.onApiKey(null) }) { Text("Obriši ključ") } }) else null,
        ) { dialog = null; actions.onApiKey(it.trim()) }
        "source" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Izvor plate") },
            text = {
                Column {
                    SALARY_SOURCES.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { dialog = null; actions.onSalarySource(id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(state.salarySource == id, null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = null }) { Text("Zatvori") } },
        )
        "min" -> TextDialog("Minimalni iznos plate", "Uplata manja od ovoga ne počinje novi period.", state.salaryMinMinor?.formatPlain() ?: "", "Sačuvaj", { dialog = null },
            suffix = "€", valid = { parseAmountMinor(it) != null },
        ) { dialog = null; parseAmountMinor(it)?.let(actions.onSalaryMin) }
        null -> {}
        else -> if (d.startsWith("rate:")) {
            val fixed = d.removePrefix("rate:").ifBlank { null }
            RateEditDialog(fixed, fixed?.let { state.rates[it] }, { dialog = null }) { cur, rate -> dialog = null; actions.onRate(cur, rate) }
        }
    }
}

/** One text field; [valid] gates the confirm button. */
@Composable
private fun TextDialog(
    title: String, text: String, initial: String, confirm: String, onDismiss: () -> Unit,
    secret: Boolean = false, suffix: String? = null, valid: (String) -> Boolean = { it.isNotBlank() },
    extra: (@Composable () -> Unit)? = null, onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(
                    value, { value = it },
                    singleLine = true,
                    suffix = suffix?.let { { Text(it) } },
                    isError = value.isNotBlank() && !valid(value),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (suffix != null) KeyboardType.Decimal else if (secret) KeyboardType.Password else KeyboardType.Text),
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(value) }, enabled = valid(value)) { Text(confirm) } },
        dismissButton = { Row { extra?.invoke(); TextButton(onDismiss) { Text("Otkaži") } } },
    )
}

/** New currency ([fixed] null) or a new rate for an existing one. */
@Composable
private fun RateEditDialog(fixed: String?, current: BigDecimal?, onDismiss: () -> Unit, onConfirm: (String, BigDecimal) -> Unit) {
    var cur by rememberSaveable { mutableStateOf(fixed ?: "") }
    var text by rememberSaveable { mutableStateOf(current?.toPlainString()?.replace('.', ',') ?: "") }
    val code = cur.trim().uppercase()
    val rate = parseRate(text)
    val ok = code.length == 3 && code.all { it.isLetter() } && code != "EUR" && code != "BAM" && rate != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fixed == null) "Dodaj valutu" else "Kurs za $fixed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fixed == null) OutlinedTextField(
                    cur, { cur = it.take(3) },
                    label = { Text("Valuta (npr. RSD, USD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    text, { text = it },
                    prefix = { Text("1 € = ") },
                    suffix = { Text(code.ifBlank { "…" }) },
                    singleLine = true,
                    isError = text.isNotBlank() && rate == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(code, rate!!) }, enabled = ok) { Text("Sačuvaj") } },
        dismissButton = { TextButton(onDismiss) { Text("Otkaži") } },
    )
}

private enum class Tone { Neutral, Ok, Warn, Err }

@Composable
private fun SettingsSection(title: String, rows: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            title,
            Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp).semantics { heading() },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
        )
        rows()
    }
}

/** With [button] the action is the button; without it the whole row is clickable (chevron) when [onClick] is set. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String,
    tone: Tone = Tone.Neutral,
    button: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val (subColor, subIcon) = when (tone) {
        Tone.Neutral -> c.onSurfaceVariant to null
        Tone.Ok -> s.ok to Icons.Rounded.CheckCircle
        Tone.Warn -> s.warning to Icons.Rounded.Warning
        Tone.Err -> c.error to Icons.Rounded.Error
    }
    val rowClick = if (button == null) onClick else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (rowClick != null) Modifier.clickable(onClick = rowClick) else Modifier)
            .padding(16.dp, 12.dp),
        Arrangement.spacedBy(16.dp),
        Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurfaceVariant)
        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                subIcon?.let { Icon(it, null, Modifier.padding(top = 2.dp).size(16.dp), subColor) }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = subColor)
            }
        }
        if (button != null && onClick != null) {
            FilledTonalButton(onClick, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(button) }
        } else if (rowClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
        }
    }
}

// ---- Sirovi log ----

@Composable
fun SiroviLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val rows by remember { AppDatabase.get(ctx).rawNotifications().all() }.collectAsState(emptyList())
    val pm = ctx.packageManager
    val names = remember { mutableMapOf<String, String>() }
    // ponytail: bez QUERY_ALL_PACKAGES Android često ne da naziv aplikacije; tada ostaje naziv paketa
    SiroviLogContent(rows, modifier, onBack) { pkg ->
        names.getOrPut(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    }
}

private enum class LogFilter { All, Failed, Ignored }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiroviLogContent(
    rows: List<RawNotification>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    appName: (String) -> String = { it },
) {
    val c = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableStateOf(LogFilter.All) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(rows, filter, query) {
        rows.filter { r ->
            when (filter) {
                LogFilter.All -> true
                LogFilter.Failed -> r.status == RawStatus.FAILED
                LogFilter.Ignored -> r.status == RawStatus.IGNORED
            } && (query.isBlank() || listOf(r.packageName, appName(r.packageName), r.title, r.text).any { it?.contains(query, true) == true })
        }
    }

    Column(modifier.fillMaxSize()) {
        BackTopBar("Sirovi log", onBack) {
            IconButton({ searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, if (searchOpen) "Zatvori pretragu" else "Pretraga", tint = c.onSurfaceVariant)
            }
        }
        if (searchOpen) SearchField(query, { query = it }, "Traži po tekstu ili aplikaciji")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 4.dp, 16.dp, 12.dp),
            Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                LogFilter.All to "Sve (${rows.size})",
                LogFilter.Failed to "Neprepoznate (${rows.count { it.status == RawStatus.FAILED }})",
                LogFilter.Ignored to "Zanemarene",
            ).forEach { (f, label) ->
                FilterChip(
                    filter == f, { filter = f }, { Text(label) },
                    leadingIcon = if (filter == f) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item {
                Text(
                    if (rows.isEmpty()) "Još nema zabilježenih notifikacija." else "Nema zapisa za ovaj filter.",
                    Modifier.padding(vertical = 24.dp),
                    color = c.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { r -> LogEntry(r, appName(r.packageName)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogEntry(r: RawNotification, app: String) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val at = r.postedAt.toLocal()
    Surface(shape = MaterialTheme.shapes.medium, color = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(8.dp)) {
            FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text("${at.format(DayFormat)} ${at.format(TimeFormat)}", style = t.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.onSurfaceVariant)
                // naziv paketa je potreban za fazu 2, pa se prikazuje i kad postoji naziv aplikacije
                Text(if (app == r.packageName) "· $app" else "· $app (${r.packageName})", style = t.labelMedium, color = c.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                StatusTag(r.status)
            }
            SelectionContainer {
                Text(
                    listOfNotNull(r.title, r.text).joinToString("\n"),
                    style = t.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                )
            }
        }
    }
}

@Composable
private fun StatusTag(status: RawStatus) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val (label, icon, colors) = when (status) {
        RawStatus.PARSED -> Triple("Transakcija", Icons.Rounded.CheckCircle, s.okContainer to s.onOkContainer)
        RawStatus.FAILED -> Triple("Neprepoznato", Icons.AutoMirrored.Rounded.HelpOutline, s.warningContainer to s.onWarningContainer)
        RawStatus.IGNORED -> Triple("Nije plaćanje", Icons.Rounded.Block, c.surfaceContainerHighest to c.onSurfaceVariant)
        RawStatus.NEW -> Triple("Čeka obradu", Icons.Rounded.Schedule, c.surfaceContainerHighest to c.onSurfaceVariant)
    }
    Tag(label, icon, colors.first, colors.second)
}

@Composable
private fun Tag(label: String, icon: ImageVector, bg: Color, fg: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = bg, contentColor = fg) {
        Row(Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// ---- Previews ----

private fun sampleLog(): List<RawNotification> {
    fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 9, d, h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val wallet = "com.google.android.apps.walletnfcrel"
    val bank = "rs.mojabanka.mobile"
    return listOf(
        RawNotification(1, wallet, "Maxi", "EUR 12,50", at(30, 14, 32), RawStatus.PARSED),
        RawNotification(2, wallet, "Pekara Zlatni klas", "KM 25.00", at(30, 8, 15), RawStatus.PARSED),
        RawNotification(3, bank, "Rezervacija sredstava 30,00 EUR", "HAC ENC LUCKO", at(29, 21, 14), RawStatus.FAILED),
        RawNotification(4, bank, null, "Vaš izvod za septembar je spreman.", at(29, 18, 3), RawStatus.IGNORED),
        RawNotification(5, bank, "Plaćanje karticom 45,00 EUR", "SHELL", at(29, 17, 5), RawStatus.NEW),
    )
}

@Composable
private fun Frame(content: @Composable () -> Unit) = BudzetTheme { Surface(color = MaterialTheme.colorScheme.surface) { content() } }

@Preview(name = "Podešavanja", widthDp = 390, heightDp = 1500)
@Preview(name = "Podešavanja · tamno", widthDp = 390, heightDp = 1500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PodesavanjaPreview() = Frame {
    PodesavanjaContent(
        PodesavanjaState(
            PermissionsUi(true, true, false), 248, LocalDateTime.of(2026, 10, 2, 14, 32), LocalDate.of(2026, 10, 2),
            apiKey = "sk-ant-api03-xxxx4f2a", salarySource = "bankA", salaryMinMinor = 100000, rates = mapOf("USD" to BigDecimal("1.08")),
        ),
    )
}

@Preview(name = "Sirovi log", widthDp = 390, heightDp = 900)
@Preview(name = "Sirovi log · tamno", widthDp = 390, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SiroviLogPreview() = Frame {
    SiroviLogContent(sampleLog()) { if (it.startsWith("com.google")) "Google Wallet" else it }
}
