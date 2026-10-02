package com.budget.app.ui

import com.budget.app.ai.AnalysisSection
import com.budget.app.ai.SavingTip
import com.budget.app.budget.PeriodRange
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.ui.components.BackTopBar
import com.budget.app.ui.components.BudzetNavigationBar
import com.budget.app.ui.components.NavTab
import com.budget.app.ui.format.DayFormat
import com.budget.app.ui.format.TimeFormat
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.format.formatPlain
import com.budget.app.ui.theme.BudzetTheme
import com.budget.app.ui.theme.LocalStatus
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- Analysis model (also what the AI answer is parsed into in phase 6) ----

sealed interface AnalysisStatus {
    /** [teaser] is the one-line summary on the list card. */
    data class Ready(val teaser: String, val sections: List<AnalysisSection>, val tips: List<SavingTip>, val writtenAt: LocalDateTime) : AnalysisStatus
    data object Loading : AnalysisStatus
    /** [keyProblem] = the API key was rejected, offer "Provjeri API ključ". */
    data class Failed(val reason: String, val keyProblem: Boolean) : AnalysisStatus
}

data class AnalysisUi(val id: Long, val period: PeriodRange, val spentMinor: Long, val budgetMinor: Long, val salaryMinor: Long?, val status: AnalysisStatus)

private val AnalysisStatus.Ready.totalMinor get() = tips.sumOf { it.monthlyEurMinor }

data class IzvjestajiState(
    /** Newest first; index 0 is the running period. */
    val reports: List<ReportUi>,
    val analyses: List<AnalysisUi>,
)

// ---- Izvještaji (Pregled | Analize) ----

@Composable
fun IzvjestajiScreen(
    state: IzvjestajiState,
    modifier: Modifier = Modifier,
    onAnalysis: (Long) -> Unit = {},
    onRetry: (Long) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Text(
            "Izvještaji",
            Modifier.heightIn(min = 64.dp).padding(16.dp, 18.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        PrimaryTabRow(tab) {
            listOf("Pregled", "Analize").forEachIndexed { i, t ->
                Tab(
                    tab == i, { tab = i },
                    text = { Text(t, fontWeight = FontWeight.Bold) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tab == 0) Pregled(state.reports, Modifier.weight(1f))
        else AnalizeList(state.reports.firstOrNull()?.period, state.analyses, Modifier.weight(1f), onAnalysis, onRetry)
    }
}

@Composable
private fun Pregled(reports: List<ReportUi>, modifier: Modifier) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val r = reports.getOrNull(index) ?: return
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 24.dp), Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ index++ }, enabled = index < reports.lastIndex) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Prethodni period")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.period.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(if (index == 0) "Tekući period" else "Završen period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ index-- }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sljedeći period")
            }
        }
        if (r.byCategory.isEmpty()) {
            Text("U ovom periodu još nema troškova.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        ReportSection("Po kategorijama") { ByCategory(r.byCategory) }
        ReportSection("Po danima") { DaysChart(r) }
        ReportSection("Najveći trgovci") { Merchants(r.merchants) }
        if (r.compareRange != null && r.compare.isNotEmpty()) {
            ReportSection("U odnosu na prethodni period", "Isti broj dana od početka perioda (${r.compareRange})") { Compare(r.compare) }
        }
    }
}

@Composable
private fun ReportSection(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }
}

@Composable
private fun ByCategory(rows: List<Pair<String, Long>>) {
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.padding(16.dp, 8.dp)) {
        rows.forEach { (name, amount) ->
            Column(Modifier.padding(vertical = 10.dp), Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(amount.formatEur(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), softWrap = false)
                }
                Box(
                    Modifier.fillMaxWidth(amount.toFloat() / max).height(12.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun DaysChart(r: ReportUi) {
    val c = MaterialTheme.colorScheme
    val chartH = 120.dp
    val daily = r.dailyBudgetMinor
    val max = (r.days.maxOfOrNull { it.eurMinor } ?: 0).coerceAtLeast(daily ?: 0).coerceAtLeast(1)
    Column(Modifier.padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = r.days.joinToString { "${it.label} ${it.eurMinor.formatEur()}" }
            },
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
                r.days.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.eurMinor.formatPlain(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = c.onSurfaceVariant, softWrap = false, maxLines = 1)
                        Box(
                            Modifier.fillMaxWidth().height(chartH * (d.eurMinor.toFloat() / max))
                                .background(if (d.today) c.primaryContainer else c.primary, RoundedCornerShape(6.dp, 6.dp, 2.dp, 2.dp)),
                        )
                    }
                }
            }
            if (daily != null) {
                val lineColor = c.onSurfaceVariant
                Canvas(Modifier.matchParentSize()) {
                    val y = size.height - chartH.toPx() * (daily.toFloat() / max)
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            r.days.forEach { Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) }
        }
        if (daily != null && r.budgetMinor != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(20.dp).height(2.dp)) {
                drawLine(c.onSurfaceVariant, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            Text(
                "Dnevni prosjek budžeta: ${daily.formatEur()} (${r.budgetMinor.formatEur()} / ${r.periodDays} dana)",
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Merchants(rows: List<MerchantRow>) {
    Column {
        rows.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(16.dp, 12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(m.meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(m.eurMinor.formatEur(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), softWrap = false)
            }
        }
    }
}

private fun signed(minor: Long) = when {
    minor > 0 -> "+" + minor.formatEur()
    minor < 0 -> "−" + abs(minor).formatEur()
    else -> 0L.formatEur()
}

@Composable
private fun Compare(rows: List<CompareRow>) {
    val c = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = c.outlineVariant)
            val d = r.nowMinor - r.beforeMinor
            // DESIGN.md rule 6: rast potrošnje je neutralna strelica, ne crvena
            val icon = when { d > 0 -> Icons.Rounded.ArrowUpward; d < 0 -> Icons.Rounded.ArrowDownward; else -> Icons.Rounded.Remove }
            val pct = if (r.beforeMinor == 0L) "novo" else ((d * 100.0 / r.beforeMinor).roundToInt()).let { if (it > 0) "+$it%" else if (it < 0) "−${-it}%" else "0%" }
            Row(Modifier.padding(16.dp, 12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("Sada ${r.nowMinor.formatEur()} · prije ${r.beforeMinor.formatEur()}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                }
                Icon(icon, null, Modifier.size(18.dp), c.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.End) {
                    Text(signed(d), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), softWrap = false)
                    Text(pct, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Analize (lista) ----

@Composable
private fun AnalizeList(running: PeriodRange?, analyses: List<AnalysisUi>, modifier: Modifier, onOpen: (Long) -> Unit, onRetry: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp, 16.dp, 16.dp, 24.dp), Arrangement.spacedBy(12.dp)) {
        if (running != null) Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outlineVariant), color = c.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.HourglassTop, null, tint = c.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(running.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Period je u toku. Analiza se piše kad stigne sljedeća plata.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
        }
        if (analyses.isEmpty()) Text(
            "Još nema analiza. Prva stiže kad se završi prvi period od plate do plate.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
        )
        analyses.forEach { AnalysisCard(it, { onOpen(it.id) }, { onRetry(it.id) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisCard(a: AnalysisUi, onOpen: () -> Unit, onRetry: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val t = MaterialTheme.typography
    val st = a.status
    Surface(
        Modifier.fillMaxWidth().then(if (st is AnalysisStatus.Failed) Modifier else Modifier.clickable(onClick = onOpen)),
        MaterialTheme.shapes.large,
        c.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
            FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(a.period.label, Modifier.semantics { heading() }, style = t.bodyLarge.copy(fontWeight = FontWeight.Bold))
                when (st) {
                    is AnalysisStatus.Ready -> StatusChip("Spremna", Icons.Rounded.CheckCircle, s.okContainer, s.onOkContainer)
                    AnalysisStatus.Loading -> StatusChip("Priprema se…", Icons.Rounded.Autorenew, c.surfaceContainerHighest, c.onSurfaceVariant)
                    is AnalysisStatus.Failed -> StatusChip("Nije uspjelo", Icons.Rounded.Error, c.errorContainer, c.onErrorContainer)
                }
            }
            when (st) {
                is AnalysisStatus.Ready -> {
                    Text(st.teaser, style = t.bodyMedium, color = c.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Savings, null, Modifier.size(18.dp), c.primary)
                        Text(
                            "${st.tips.size} savjeta · do ${st.totalMinor.formatEur()} mjesečno",
                            style = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.primary,
                        )
                    }
                }
                AnalysisStatus.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Analiza se priprema… Javićemo ti notifikacijom.", style = t.bodyMedium, color = c.onSurfaceVariant)
                }
                is AnalysisStatus.Failed -> {
                    Text(st.reason, style = t.bodyMedium, color = c.onSurfaceVariant)
                    OutlinedButton(onRetry, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Text("Pokušaj ponovo", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, icon: ImageVector, bg: Color, fg: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = bg, contentColor = fg) {
        Row(Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// ---- Analiza perioda (detalj) ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalizaScreen(
    a: AnalysisUi,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onApiKey: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Column(modifier.fillMaxSize()) {
        BackTopBar("Analiza perioda", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp, 8.dp, 20.dp, 32.dp), Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.period.label, style = t.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Potrošeno ${a.spentMinor.formatEur()} od ${a.budgetMinor.formatEur()}" + (a.salaryMinor?.let { " · plata ${it.formatEur()}" } ?: ""),
                    style = t.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
            when (val st = a.status) {
                is AnalysisStatus.Ready -> {
                    st.sections.forEach { sec ->
                        Text(sec.heading, Modifier.padding(top = 12.dp).semantics { heading() }, style = t.titleLarge.copy(fontWeight = FontWeight.Bold))
                        sec.paragraphs.forEach { Text(it, style = t.bodyLarge.copy(lineHeight = 26.sp)) }
                    }
                    Text("Tri savjeta za uštedu", Modifier.padding(top = 12.dp).semantics { heading() }, style = t.titleLarge.copy(fontWeight = FontWeight.Bold))
                    st.tips.forEachIndexed { i, tip -> TipCard(i + 1, tip) }
                    Text("Ukupno procijenjeno: do ${st.totalMinor.formatEur()} mjesečno", style = t.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = c.outlineVariant)
                    Text(
                        "Napisao AI na osnovu sažetka tvojih transakcija, ${st.writtenAt.format(DayFormat)} u ${st.writtenAt.format(TimeFormat)}. Iznosi su procjene.",
                        style = t.labelMedium.copy(lineHeight = 18.sp),
                        color = c.onSurfaceVariant,
                    )
                    TextButton(onRetry, contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Text("Napiši ponovo", Modifier.padding(start = 8.dp))
                    }
                }
                AnalysisStatus.Loading -> Column(Modifier.padding(top = 16.dp), Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(40.dp))
                        Column {
                            Text("Analiza se priprema…", style = t.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text("Obično traje manje od minute.", style = t.bodyMedium, color = c.onSurfaceVariant)
                        }
                    }
                    Text("Možeš izaći iz aplikacije. Kad bude spremna, stići će notifikacija.", style = t.bodyMedium, color = c.onSurfaceVariant)
                    Column(Modifier.padding(top = 16.dp), Arrangement.spacedBy(12.dp)) {
                        listOf(0.4f to 22, 1f to 14, 0.94f to 14, 0.7f to 14, 0.55f to 22, 1f to 14, 0.88f to 14).forEach { (w, h) ->
                            Box(Modifier.fillMaxWidth(w).height(h.dp).background(c.surfaceContainerHigh, RoundedCornerShape(6.dp)))
                        }
                        Box(Modifier.fillMaxWidth().height(96.dp).background(c.surfaceContainerLow, MaterialTheme.shapes.large))
                    }
                }
                is AnalysisStatus.Failed -> Column(Modifier.padding(top = 24.dp), Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(56.dp), CircleShape, c.errorContainer, c.onErrorContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (st.keyProblem) Icons.Rounded.KeyOff else Icons.Rounded.CloudOff, null, Modifier.size(28.dp))
                        }
                    }
                    Text("Analiza nije uspjela", Modifier.padding(top = 8.dp), style = t.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(st.reason, style = t.bodyLarge)
                    Text(
                        if (st.keyProblem) "Tvoji podaci su sačuvani i ništa nije izgubljeno. Provjeri ključ u podešavanjima pa pokušaj ponovo."
                        else "Tvoji podaci su sačuvani i ništa nije izgubljeno. Pokušaj ponovo kad bude internet veze.",
                        style = t.bodyMedium,
                        color = c.onSurfaceVariant,
                    )
                    FlowRow(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
                        Button(onRetry, contentPadding = PaddingValues(start = 16.dp, end = 20.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Text("Pokušaj ponovo", Modifier.padding(start = 8.dp))
                        }
                        if (st.keyProblem) OutlinedButton(onApiKey, contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Provjeri API ključ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipCard(n: Int, tip: SavingTip) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(32.dp), CircleShape, c.primaryContainer, c.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text("$n", style = t.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)) }
                }
                Text(tip.title, Modifier.padding(top = 4.dp).semantics { heading() }, style = t.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
            Text(tip.body, style = t.bodyMedium.copy(lineHeight = 22.sp), color = c.onSurfaceVariant)
            Surface(shape = MaterialTheme.shapes.small, color = c.secondaryContainer, contentColor = c.onSecondaryContainer) {
                Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Savings, null, Modifier.size(18.dp))
                    Text("≈ ${tip.monthlyEurMinor.formatEur()} mjesečno", style = t.bodyMedium.copy(fontWeight = FontWeight.Bold), softWrap = false)
                }
            }
        }
    }
}

// ---- Previews ----

@Composable
private fun RepFrame(content: @Composable (Modifier) -> Unit) = BudzetTheme {
    Scaffold(bottomBar = { BudzetNavigationBar(NavTab.Izvjestaji) }) { content(Modifier.padding(it)) }
}

@Preview(name = "Izvještaji", widthDp = 390, heightDp = 2150)
@Preview(name = "Izvještaji · tamno", widthDp = 390, heightDp = 2150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Izvještaji · font 200%", widthDp = 390, heightDp = 3200, fontScale = 2f)
@Composable
private fun IzvjestajiPreview() = RepFrame { IzvjestajiScreen(Sample.izvjestaji, it) }

@Composable
private fun AnalizaFrame(a: AnalysisUi) = BudzetTheme { Surface(color = MaterialTheme.colorScheme.surface) { AnalizaScreen(a) } }

@Preview(name = "Analiza", widthDp = 390, heightDp = 2350)
@Preview(name = "Analiza · tamno", widthDp = 390, heightDp = 2350, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalizaPreview() = AnalizaFrame(Sample.analyses[0])

@Preview(name = "Analiza · priprema", widthDp = 390, heightDp = 900)
@Composable
private fun AnalizaLoadingPreview() = AnalizaFrame(Sample.analyses[2])

@Preview(name = "Analiza · greška", widthDp = 390, heightDp = 844)
@Preview(name = "Analiza · greška · tamno", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalizaFailedPreview() = AnalizaFrame(Sample.analyses[0].copy(status = AnalysisStatus.Failed("Server nije prihvatio API ključ (greška 401).", keyProblem = true)))
