package com.budget.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budget.app.ui.components.PermissionStepCard
import com.budget.app.ui.components.StepState
import com.budget.app.ui.theme.BudzetTheme
import kotlinx.coroutines.launch

// ---- Permission state, shared with Podešavanja and Početna ----

internal fun listenerEnabled(c: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

internal fun postNotificationsGranted(c: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

internal fun batteryUnrestricted(c: Context) =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

private fun Context.pkgUri() = Uri.parse("package:$packageName")
internal fun Context.openListenerSettings() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
internal fun Context.openAppInfo() = startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()))
internal fun Context.openBatterySettings() =
    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri()))
internal fun Context.openAppNotificationSettings() =
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

// ---- Uvod ----

data class UvodState(val listener: StepState, val notifs: StepState, val battery: StepState)

@Composable
fun UvodScreen(tick: Int, refresh: () -> Unit, onStart: () -> Unit) {
    val ctx = LocalContext.current
    val listener = remember(tick) { listenerEnabled(ctx) }
    val notifs = remember(tick) { postNotificationsGranted(ctx) }
    val battery = remember(tick) { batteryUnrestricted(ctx) }
    val askNotifs = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    // ponytail: Android ne javlja da je dozvola blokirana; ako se korisnik vrati bez uključenog pristupa, pretpostavljamo blokadu
    var tried by rememberSaveable { mutableStateOf(false) }
    val blocked = tried && !listener
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(blocked) { if (blocked) helpOpen = true }

    fun state(on: Boolean) = if (on) StepState.Done else StepState.Todo
    Scaffold { pad ->
        UvodContent(
            UvodState(if (blocked) StepState.Blocked else state(listener), state(notifs), state(battery)),
            helpOpen,
            Modifier.padding(pad),
            onToggleHelp = { helpOpen = !helpOpen },
            onListener = { tried = true; ctx.openListenerSettings() },
            onNotifs = { askNotifs.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onBattery = { ctx.openBatterySettings() },
            onAppInfo = { tried = false; ctx.openAppInfo() },
            onStart = onStart,
        )
    }
}

@Composable
fun UvodContent(
    state: UvodState,
    helpOpen: Boolean,
    modifier: Modifier = Modifier,
    onToggleHelp: () -> Unit = {},
    onListener: () -> Unit = {},
    onNotifs: () -> Unit = {},
    onBattery: () -> Unit = {},
    onAppInfo: () -> Unit = {},
    onStart: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val steps = listOf(state.listener, state.notifs, state.battery)
    val done = steps.count { it == StepState.Done }
    val ready = state.listener == StepState.Done

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(16.dp, 40.dp, 16.dp, 24.dp),
            Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp), Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(56.dp), RoundedCornerShape(16.dp), c.primaryContainer, c.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(28.dp)) }
                }
                Text(
                    "Podesimo praćenje",
                    Modifier.padding(top = 8.dp).semantics { heading() },
                    style = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "Budžet čita notifikacije banke i Google Walleta na ovom telefonu i sam bilježi plaćanja karticom. " +
                        "Podaci ostaju na telefonu.",
                    style = t.bodyLarge,
                    color = c.onSurfaceVariant,
                )
                Text(
                    "$done od 3 koraka završeno",
                    Modifier.padding(top = 4.dp),
                    style = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.primary,
                )
            }
            PermissionStepCard(
                1, "Pristup notifikacijama", state.listener,
                description = "Obavezno. Bez ovoga se plaćanja ne bilježe sama.",
                action = if (state.listener == StepState.Blocked) "Pogledaj kako se odblokira" else "Uključi pristup",
                primaryAction = true,
                onAction = if (state.listener == StepState.Blocked) {
                    {
                        if (!helpOpen) onToggleHelp()
                        scope.launch { scroll.scrollToEnd() }
                        Unit
                    }
                } else onListener,
            )
            PermissionStepCard(
                2, "Slanje notifikacija", state.notifs,
                description = "Za upozorenja o limitu i obavještenje kad je analiza spremna.",
                action = "Dozvoli",
                onAction = onNotifs,
            )
            PermissionStepCard(
                3, "Isključi optimizaciju baterije", state.battery,
                description = "Da Android ne bi zaustavio praćenje dok je telefon u džepu.",
                action = "Otvori podešavanja baterije",
                onAction = onBattery,
            )
            BlockedHelp(helpOpen, onToggleHelp, onAppInfo)
        }

        HorizontalDivider(color = c.outlineVariant)
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 24.dp), Arrangement.spacedBy(8.dp)) {
            Button(
                onStart,
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = ready,
                shape = RoundedCornerShape(28.dp),
            ) { Text(if (ready) "Počni" else "Nastavi", style = t.titleMedium.copy(fontWeight = FontWeight.Bold)) }
            Text(
                when {
                    !ready -> "Za nastavak je potreban prvi korak."
                    done == 3 -> "Sve je spremno. Prva plata započinje prvi period."
                    else -> "Ostale korake možeš završiti kasnije u Podešavanjima."
                },
                Modifier.fillMaxWidth(),
                style = t.bodySmall,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private suspend fun ScrollState.scrollToEnd() = animateScrollTo(maxValue)

@Composable
private fun BlockedHelp(open: Boolean, onToggle: () -> Unit, onAppInfo: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outlineVariant), color = c.surface) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = if (open) "Otvoreno" else "Zatvoreno" }
                    .padding(16.dp, 12.dp),
                Arrangement.spacedBy(12.dp),
                Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = c.onSurfaceVariant)
                Text(
                    "Ako je pristup notifikacijama blokiran",
                    Modifier.weight(1f),
                    style = t.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 22.sp),
                )
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.onSurfaceVariant)
            }
            if (open) Column(Modifier.padding(16.dp, 0.dp, 16.dp, 16.dp), Arrangement.spacedBy(16.dp)) {
                Text(
                    "Android ovo radi za aplikacije instalirane van Play prodavnice. Odblokiraš jednom, u četiri koraka:",
                    style = t.bodyMedium,
                    color = c.onSurfaceVariant,
                )
                HelpStep(1, bold("Otvori ", "Info o aplikaciji", " za Budžet.")) {
                    OutlinedButton(onAppInfo, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Text("Otvori Info o aplikaciji", Modifier.padding(start = 8.dp))
                    }
                }
                HelpStep(2, bold("Dodirni ", "⋮", " u gornjem desnom uglu."))
                HelpStep(3, bold("Izaberi ", "Dozvoli ograničena podešavanja", " i potvrdi otiskom ili PIN-om."))
                HelpStep(4, bold("Vrati se ovdje i ponovo dodirni ", "Uključi pristup", "."))
            }
        }
    }
}

private fun bold(before: String, strong: String, after: String) = buildAnnotatedString {
    append(before)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(strong) }
    append(after)
}

@Composable
private fun HelpStep(n: Int, text: AnnotatedString, extra: @Composable () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(24.dp), CircleShape, c.secondaryContainer, c.onSecondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(n.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
            extra()
        }
    }
}

// ---- Previews ----

@Composable
private fun UvodFrame(state: UvodState, helpOpen: Boolean = false) = BudzetTheme {
    Surface(color = MaterialTheme.colorScheme.surface) { UvodContent(state, helpOpen) }
}

@Preview(name = "Početak", widthDp = 390, heightDp = 1040)
@Preview(name = "Početak · tamno", widthDp = 390, heightDp = 1040, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Početak · font 200%", widthDp = 390, heightDp = 2000, fontScale = 2f)
@Composable
private fun UvodStartPreview() = UvodFrame(UvodState(StepState.Todo, StepState.Todo, StepState.Todo))

@Preview(name = "Blokirano", widthDp = 390, heightDp = 1420)
@Preview(name = "Blokirano · tamno", widthDp = 390, heightDp = 1420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UvodBlockedPreview() = UvodFrame(UvodState(StepState.Blocked, StepState.Done, StepState.Todo), helpOpen = true)

@Preview(name = "Gotovo", widthDp = 390, heightDp = 1040)
@Preview(name = "Gotovo · tamno", widthDp = 390, heightDp = 1040, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UvodDonePreview() = UvodFrame(UvodState(StepState.Done, StepState.Done, StepState.Done))
