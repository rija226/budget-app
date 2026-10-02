package com.budget.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.budget.app.data.AppDatabase
import com.budget.app.data.Repo
import com.budget.app.data.Settings
import com.budget.app.ingest.Pipeline
import com.budget.app.ingest.normalizeCurrency
import com.budget.app.ui.components.BackTopBar
import com.budget.app.ui.components.BudzetNavigationBar
import com.budget.app.ui.components.HeroState
import com.budget.app.ui.components.NavTab
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface Route {
    data class Tab(val tab: NavTab) : Route
    data object ZaProvjeru : Route
    data object Podesavanja : Route
    data object SiroviLog : Route
    /** id 0 = new manual transaction, negative = new transaction replacing unrecognized raw notification -id. */
    data class Transakcija(val id: Long) : Route
    data object NovaPlata : Route
    data class Analiza(val id: Long) : Route
}

private val Home = Route.Tab(NavTab.Pocetna)

internal fun Route.key(): String = when (this) {
    is Route.Tab -> "tab:${tab.name}"
    is Route.Transakcija -> "tx:$id"
    is Route.Analiza -> "an:$id"
    Route.ZaProvjeru -> "zp:"
    Route.Podesavanja -> "set:"
    Route.SiroviLog -> "log:"
    Route.NovaPlata -> "salary:"
}

internal fun routeOf(key: String): Route? {
    val (k, v) = key.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return when (k) {
        "tab" -> NavTab.entries.find { it.name == v }?.let { Route.Tab(it) }
        "tx" -> v.toLongOrNull()?.let { Route.Transakcija(it) }
        "an" -> v.toLongOrNull()?.let { Route.Analiza(it) }
        "zp" -> Route.ZaProvjeru
        "set" -> Route.Podesavanja
        "log" -> Route.SiroviLog
        "salary" -> Route.NovaPlata
        else -> null
    }
}

// ponytail: vlastiti back stack umjesto navigation biblioteke; dovoljno za ovih desetak ekrana
private val StackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.key() } },
    restore = { keys -> keys.mapNotNull(::routeOf).ifEmpty { listOf(Home) }.toMutableStateList() },
)

private fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** [openRoute]: route key from a notification tap, consumed once via [onRouteOpened]. */
@Composable
fun App(tick: Int, refresh: () -> Unit, openRoute: String? = null, onRouteOpened: () -> Unit = {}) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var uvodDone by remember { mutableStateOf(settings.uvodDone) }
    if (!uvodDone) {
        UvodScreen(tick, refresh) { settings.uvodDone = true; uvodDone = true }
        return
    }

    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    fun io(block: suspend () -> Unit) { scope.launch { block() } }

    val txs by remember { db.transactions().all() }.collectAsState(emptyList())
    val cats by remember { db.categories().all() }.collectAsState(emptyList())
    val raws by remember { db.rawNotifications().all() }.collectAsState(emptyList())
    val analyses by remember { db.analyses().all() }.collectAsState(emptyList())
    val settingsVersion by remember { settings.changes().map { System.nanoTime() } }.collectAsState(0L)
    val s = remember(settingsVersion) {
        SettingsUi(settings.budgetMinor, settings.salarySource, settings.salaryMinMinor, settings.firstDay, settings.rates, settings.apiKey)
    }
    val today = remember(tick) { LocalDate.now() }
    val live = remember(txs, cats, raws, analyses, s, today) { Live(txs, cats, raws, analyses, s, today) }
    val listener = remember(tick) { listenerEnabled(ctx) }

    val stack = rememberSaveable(saver = StackSaver) { mutableStateListOf<Route>(Home) }
    val holder = rememberSaveableStateHolder()
    val top = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun back() {
        if (stack.size > 1) holder.removeState(stack.removeAt(stack.lastIndex).key()) else stack[0] = Home
    }
    fun tab(t: NavTab) {
        stack.filter { it !is Route.Tab }.forEach { holder.removeState(it.key()) }
        stack.clear(); stack.add(Route.Tab(t))
    }
    // sa korijena drugog taba nazad vodi na Početnu, sa Početne izlazi iz aplikacije
    BackHandler(top != Home) { back() }
    LaunchedEffect(openRoute) {
        openRoute?.let(::routeOf)?.let { r -> if (r is Route.Tab) tab(r.tab) else { tab(NavTab.Pocetna); go(r) } }
        if (openRoute != null) onRouteOpened()
    }

    Scaffold(bottomBar = { if (top is Route.Tab) BudzetNavigationBar(top.tab, onSelect = ::tab) }) { pad ->
        val m = Modifier.padding(pad)
        holder.SaveableStateProvider(top.key()) {
            when (top) {
                is Route.Tab -> when (top.tab) {
                    NavTab.Pocetna -> {
                        val state = live.pocetna(listener)
                        PocetnaScreen(
                            state, m,
                            onSettings = { go(Route.Podesavanja) },
                            onReview = { go(Route.ZaProvjeru) },
                            onMarkSalary = { go(Route.NovaPlata) },
                            onSalaryRule = { if (state.hero is HeroState.NoBudget) tab(NavTab.Budzet) else go(Route.Podesavanja) },
                            onEditLimits = { tab(NavTab.Budzet) },
                            onAllTransactions = { tab(NavTab.Transakcije) },
                            onTransaction = { go(Route.Transakcija(it)) },
                        )
                    }
                    NavTab.Transakcije -> TransakcijeScreen(
                        live.transakcije(), m,
                        onTransaction = { go(Route.Transakcija(it)) },
                        onAdd = { go(Route.Transakcija(0)) },
                    )
                    NavTab.Izvjestaji -> IzvjestajiScreen(
                        live.izvjestaji(), m,
                        onAnalysis = { go(Route.Analiza(it)) },
                        onRetry = { id -> io { Repo.retryAnalysis(ctx, id) } },
                    )
                    NavTab.Budzet -> {
                        fun id(name: String) = live.expenseCategories.find { it.name == name }?.id
                        BudzetScreen(
                            live.budzet(), m,
                            onBudget = { settings.budgetMinor = it },
                            onLimit = { name, v -> id(name)?.let { io { Repo.setLimit(ctx, it, v) } } },
                            onRename = { old, new -> id(old)?.let { io { Repo.renameCategory(ctx, it, new) } } },
                            onDelete = { name -> id(name)?.let { io { Repo.deleteCategory(ctx, it) } } },
                            onAdd = { name -> if (live.categoryId(name) == null) io { Repo.addCategory(ctx, name) } },
                        )
                    }
                }
                Route.Podesavanja -> PodesavanjaScreen(
                    tick,
                    PodesavanjaState(
                        PermissionsUi(listener, true, true), raws.size, raws.firstOrNull()?.postedAt?.toLocal(), today,
                        s.apiKey, s.salarySource, s.salaryMinMinor, s.rates,
                    ),
                    PodesavanjaActions(
                        onApiKey = { settings.apiKey = it },
                        onSalarySource = { settings.salarySource = it },
                        onSalaryMin = { settings.salaryMinMinor = it },
                        onRate = { cur, rate -> io { Repo.setRate(ctx, cur, rate) } },
                        onReprocess = {
                            io {
                                val n = Pipeline.reprocess(ctx)
                                Toast.makeText(ctx, if (n == 0) "Nema novih transakcija" else "Nove transakcije: $n", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
                    ::back, { go(Route.SiroviLog) }, m,
                )
                Route.SiroviLog -> SiroviLogScreen(::back, m)
                Route.ZaProvjeru -> ZaProvjeruScreen(
                    live.zaProvjeru(), m,
                    onBack = ::back,
                    onConfirm = { id -> io { Repo.confirmCategory(ctx, id) } },
                    onChange = { go(Route.Transakcija(it)) },
                    onAddFromRaw = { go(Route.Transakcija(-it)) },
                    onIgnore = { id -> io { Repo.ignoreRaw(ctx, id) } },
                    onRate = { cur, rate -> io { Repo.setRate(ctx, cur, rate) } },
                    onEurAmount = { id, cents -> io { Repo.setEurAmount(ctx, id, cents) } },
                )
                is Route.Transakcija -> {
                    val existing = if (top.id > 0) live.detail(top.id) else null
                    if (top.id > 0 && existing == null) Missing("Transakcija", m, ::back)
                    else {
                        val raw = if (top.id < 0) raws.find { it.id == -top.id } else null
                        TransakcijaScreen(
                            existing, cats.map { it.name }, m,
                            prefillMerchant = raw?.title ?: "",
                            onBack = ::back,
                            onSave = { e ->
                                val catId = live.categoryId(e.category)
                                io {
                                    if (existing != null) Repo.edit(ctx, top.id, e.merchant, catId, e.categoryConfirmed, e.transfer, e.salary)
                                    else Repo.addManual(ctx, e.amountMinor!!, normalizeCurrency(e.currency), e.merchant, catId, e.transfer, e.salary, raw?.id)
                                }
                                back()
                            },
                            onDelete = { io { Repo.delete(ctx, top.id) }; back() },
                        )
                    }
                }
                Route.NovaPlata -> TransakcijaScreen(
                    null, cats.map { it.name }, m,
                    prefillMerchant = "Plata", newSalary = true,
                    onBack = ::back,
                    onSave = { e ->
                        io { Repo.addManual(ctx, e.amountMinor!!, normalizeCurrency(e.currency), e.merchant, live.categoryId(e.category), false, true) }
                        back()
                    },
                )
                is Route.Analiza -> analyses.find { it.id == top.id }?.let(live::analysis)?.let {
                    AnalizaScreen(it, m, onBack = ::back, onRetry = { io { Repo.retryAnalysis(ctx, it.id) } }, onApiKey = { go(Route.Podesavanja) })
                } ?: Missing("Analiza", m, ::back)
            }
        }
    }
}

/** A detail whose row was deleted meanwhile (e.g. opened from an old notification). */
@Composable
private fun Missing(title: String, modifier: Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        BackTopBar(title, onBack)
        Box(Modifier.weight(1f).padding(24.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ovo više ne postoji", style = MaterialTheme.typography.titleMedium)
                Text("Stavka je u međuvremenu obrisana.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
