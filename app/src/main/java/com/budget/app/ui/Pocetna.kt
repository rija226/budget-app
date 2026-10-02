package com.budget.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.budget.app.ui.components.BudzetNavigationBar
import com.budget.app.ui.components.CategoryLimitRow
import com.budget.app.ui.components.HeroCard
import com.budget.app.ui.components.HeroState
import com.budget.app.ui.components.LimitBar
import com.budget.app.ui.components.ListCard
import com.budget.app.ui.components.NavTab
import com.budget.app.ui.components.ReviewChip
import com.budget.app.ui.components.TransactionRow
import com.budget.app.ui.components.TxKind
import com.budget.app.ui.components.limitInfo
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.TimeFormat
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.theme.BudzetTheme
import com.budget.app.ui.theme.LocalStatus
import com.budget.app.ui.theme.isLargeFont
import java.time.LocalDate
import java.time.LocalDateTime

data class CategoryUi(val name: String, val icon: ImageVector, val spent: Long, val limit: Long?)

data class TransactionUi(
    val id: Long,
    val merchant: String,
    val category: String,
    val icon: ImageVector,
    val at: LocalDateTime,
    val amountMinor: Long,
    val currency: String = "EUR",
    val amountEurMinor: Long? = if (currency == "EUR") amountMinor else null,
    val kind: TxKind = TxKind.Expense,
    val aiSuggested: Boolean = false,
)

/** Amounts in cents. The period ends the day before [salaryExpected]. */
data class PeriodUi(val start: LocalDate, val salaryExpected: LocalDate, val spent: Long, val budget: Long)

data class PocetnaState(
    val today: LocalDate,
    val hero: HeroState,
    /** Set while notification access is lost: when the shown numbers were last up to date. */
    val pausedSince: LocalDateTime? = null,
    val reviewCount: Int = 0,
    val period: PeriodUi? = null,
    val categories: List<CategoryUi> = emptyList(),
    val transactions: List<TransactionUi> = emptyList(),
)

@Composable
fun PocetnaScreen(
    state: PocetnaState,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
    onReview: () -> Unit = {},
    onMarkSalary: () -> Unit = {},
    onSalaryRule: () -> Unit = {},
    onEditLimits: () -> Unit = {},
    onAllTransactions: () -> Unit = {},
    onTransaction: (Long) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        TopBar(onSettings)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 24.dp),
            Arrangement.spacedBy(16.dp),
        ) {
            state.pausedSince?.let { PausedBanner(it, onSettings) }
            HeroCard(state.hero, onMarkSalary = onMarkSalary, onSalaryRule = onSalaryRule)
            ReviewChip(state.reviewCount, onClick = onReview)
            state.period?.let { PeriodCard(it) }
            if (state.categories.isNotEmpty()) Section("Kategorije", "Uredi limite", onEditLimits) {
                ListCard(state.categories) { CategoryLimitRow(it.name, it.icon, it.spent, it.limit) }
            }
            Section("Posljednje transakcije", "Sve", onAllTransactions) {
                if (state.transactions.isEmpty()) NoTransactions()
                else ListCard(state.transactions) { t ->
                    TransactionRow(
                        t.merchant, t.category, t.icon, t.at.label(state.today), t.amountMinor,
                        currency = t.currency, amountEurMinor = t.amountEurMinor, kind = t.kind,
                        aiSuggested = t.aiSuggested, onClick = { onTransaction(t.id) },
                    )
                }
            }
        }
    }
}

/** "Danas, 14:32", "Juče, 20:41", "28.09., 11:20" */
private fun LocalDateTime.label(today: LocalDate): String =
    when (toLocalDate()) {
        today -> "Danas"
        today.minusDays(1) -> "Juče"
        else -> format(DayFormat)
    } + ", " + format(TimeFormat)

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        Modifier.heightIn(min = 64.dp).padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Budžet", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        IconButton(onSettings) {
            Icon(Icons.Rounded.Settings, "Podešavanja", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PausedBanner(since: LocalDateTime, onEnable: () -> Unit) {
    val s = LocalStatus.current
    Card(
        Modifier.fillMaxWidth(),
        MaterialTheme.shapes.large,
        CardDefaults.cardColors(containerColor = s.warningContainer, contentColor = s.onWarningContainer),
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Praćenje je pauzirano", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Pristup notifikacijama je isključen, pa se nova plaćanja ne bilježe. " +
                            "Iznos ispod važi za stanje od ${since.format(DayFormat)} u ${since.format(TimeFormat)}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(
                onEnable,
                Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = s.onWarningContainer, contentColor = s.warningContainer),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) { Text("Uključi pristup") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodCard(period: PeriodUi) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val info = limitInfo(period.spent, period.budget)
    val large = isLargeFont()
    Card(
        Modifier.fillMaxWidth(),
        MaterialTheme.shapes.large,
        CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
            FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Arrangement.spacedBy(8.dp)) {
                val end = period.salaryExpected.minusDays(1)
                Text("Period ${period.start.format(DayFormat)}–${end.format(DayFormat)}", style = t.titleSmall, color = c.onSurfaceVariant)
                if (!large) Text("${info.pct}%", style = t.titleSmall, color = c.onSurfaceVariant)
            }
            Text(
                buildAnnotatedString {
                    append("Potrošeno ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(period.spent.formatEur()) }
                    append(" od ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(period.budget.formatEur()) }
                },
                style = t.bodyLarge,
            )
            LimitBar(info.fraction, c.primary)
            FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Arrangement.spacedBy(8.dp)) {
                Text("Preostalo ${(period.budget - period.spent).formatEur()}", style = t.bodyMedium, color = c.onSurfaceVariant)
                if (!large) Text("Plata oko ${period.salaryExpected.format(DayFormat)}", style = t.bodyMedium, color = c.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            TextButton(onAction, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(action) }
        }
        content()
    }
}

@Composable
private fun NoTransactions() {
    val c = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        MaterialTheme.shapes.large,
        CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterHorizontally,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, Modifier.size(32.dp), c.onSurfaceVariant)
            Text("Još nema transakcija", style = MaterialTheme.typography.titleMedium)
            Text(
                "Plaćanja karticom pojaviće se ovdje sama, čim stigne notifikacija banke ili Walleta.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Fake data + previews. Replace with real state in step 5 (wire real data). ----

internal object FakePocetna {
    private val today = Sample.today
    private fun at(day: Int, month: Int, h: Int, m: Int) = LocalDateTime.of(2026, month, day, h, m)
    private val categories = Sample.categories.filter { it.limit != null }
    private val transactions = Sample.transactions.take(5)

    val normal = PocetnaState(
        today = today,
        hero = HeroState.Normal(2340, 14),
        reviewCount = 3,
        period = PeriodUi(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25), 41260, 78000),
        categories = categories,
        transactions = transactions,
    )
    val over = normal.copy(hero = HeroState.Over(810, 14))
    val empty = PocetnaState(
        today = today,
        hero = HeroState.Empty("Moja banka", 100000),
        categories = categories.map { it.copy(spent = 0) },
    )
    val paused = normal.copy(pausedSince = at(28, 9, 19, 2))
}

@Composable
private fun Frame(state: PocetnaState) = BudzetTheme {
    Scaffold(bottomBar = { BudzetNavigationBar(NavTab.Pocetna) }) { PocetnaScreen(state, Modifier.padding(it)) }
}

@Preview(name = "Normalno", widthDp = 390, heightDp = 1900)
@Preview(name = "Normalno · tamno", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Normalno · font 200%", widthDp = 390, heightDp = 2800, fontScale = 2f)
@Composable
private fun PocetnaNormalPreview() = Frame(FakePocetna.normal)

@Preview(name = "Prekoračeno", widthDp = 390, heightDp = 1960)
@Preview(name = "Prekoračeno · tamno", widthDp = 390, heightDp = 1960, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PocetnaOverPreview() = Frame(FakePocetna.over)

@Preview(name = "Bez plate", widthDp = 390, heightDp = 1560)
@Preview(name = "Bez plate · tamno", widthDp = 390, heightDp = 1560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PocetnaEmptyPreview() = Frame(FakePocetna.empty)

@Preview(name = "Pauzirano", widthDp = 390, heightDp = 2060)
@Preview(name = "Pauzirano · tamno", widthDp = 390, heightDp = 2060, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PocetnaPausedPreview() = Frame(FakePocetna.paused)
