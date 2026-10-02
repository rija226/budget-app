package com.budget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.theme.LimitStatus
import com.budget.app.ui.theme.LocalStatus
import com.budget.app.ui.theme.Spacing
import com.budget.app.ui.theme.isLargeFont
import com.budget.app.ui.theme.limitStatus

/** Numbers and texts shared by the pill, the bar and the note. Amounts in cents. */
internal class LimitInfo(val status: LimitStatus, val pct: Int, val title: String, val note: String, val fraction: Float)

internal fun limitInfo(spent: Long, limit: Long?): LimitInfo {
    val status = limitStatus(spent, limit)
    if (limit == null) return LimitInfo(status, 0, "Bez limita", "Bez limita", 0f)
    val pct = if (limit > 0) ((spent * 100 + limit / 2) / limit).toInt() else 100
    val fraction = if (limit > 0) (spent.toFloat() / limit).coerceIn(0f, 1f) else 1f
    val (title, note) = when {
        status == LimitStatus.Over && spent == limit -> "Limit dostignut" to "Limit dostignut"
        status == LimitStatus.Over -> "Preko limita" to "Preko limita za ${(spent - limit).formatEur()}"
        status == LimitStatus.Warning -> "Blizu limita" to "Blizu limita · ostalo ${(limit - spent).formatEur()}"
        else -> "U redu" to "U redu · ostalo ${(limit - spent).formatEur()}"
    }
    return LimitInfo(status, pct, title, note, fraction)
}

private class LimitLook(val icon: ImageVector, val container: Color, val content: Color, val bar: Color, val note: Color)

@Composable
private fun limitLook(status: LimitStatus): LimitLook {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    return when (status) {
        LimitStatus.Ok -> LimitLook(Icons.Rounded.CheckCircle, s.okContainer, s.onOkContainer, c.primary, c.onSurfaceVariant)
        LimitStatus.Warning -> LimitLook(Icons.Rounded.Warning, s.warningContainer, s.onWarningContainer, s.warning, s.warning)
        LimitStatus.Over -> LimitLook(Icons.Rounded.Error, c.errorContainer, c.onErrorContainer, c.error, c.error)
        LimitStatus.NoLimit -> LimitLook(Icons.Rounded.AllInclusive, c.surfaceContainerHighest, c.onSurfaceVariant, c.outline, c.onSurfaceVariant)
    }
}

/** Icon + percentage; [withTitle] adds the status word ("Blizu limita · 96%"). Never color alone. */
@Composable
fun StatusPill(spent: Long, limit: Long?, modifier: Modifier = Modifier, withTitle: Boolean = false) {
    val info = limitInfo(spent, limit)
    val look = limitLook(info.status)
    val text = when {
        limit == null -> if (withTitle) info.title else ""
        withTitle -> "${info.title} · ${info.pct}%"
        else -> "${info.pct}%"
    }
    Surface(modifier, MaterialTheme.shapes.small, look.container, look.content) {
        Row(
            Modifier.heightIn(min = 28.dp).padding(horizontal = if (withTitle) 10.dp else 8.dp),
            Arrangement.spacedBy(4.dp),
            Alignment.CenterVertically,
        ) {
            Icon(look.icon, if (text.isEmpty()) info.title else null, Modifier.size(16.dp))
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
        }
    }
}

@Composable
internal fun CategoryAvatar(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier.size(Spacing.avatar), CircleShape, container, content) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
    }
}

@Composable
internal fun LimitBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val h = if (isLargeFont()) 12.dp else Spacing.limitBarHeight
    val shape = RoundedCornerShape(h / 2)
    Box(modifier.fillMaxWidth().height(h).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(color, shape))
    }
}

/** [limit] null = no limit. Large font: stacked, the pill carries the status text and the note is dropped. */
@Composable
fun CategoryLimitRow(name: String, icon: ImageVector, spent: Long, limit: Long?, modifier: Modifier = Modifier) {
    val info = limitInfo(spent, limit)
    val look = limitLook(info.status)
    val amounts = if (limit == null) "${spent.formatEur()} potrošeno" else "${spent.formatEur()} od ${limit.formatEur()}"
    val nameText = @Composable { Text(name, style = MaterialTheme.typography.titleSmall) }
    val amountsText = @Composable { Text(amounts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), Arrangement.spacedBy(10.dp)) {
        if (isLargeFont()) {
            nameText()
            amountsText()
            LimitBar(info.fraction, look.bar)
            StatusPill(spent, limit, Modifier.align(Alignment.Start), withTitle = true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(icon)
                Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                    nameText()
                    amountsText()
                }
                StatusPill(spent, limit)
            }
            LimitBar(info.fraction, look.bar)
            Text(info.note, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = look.note)
        }
    }
}

/** surfaceContainerLow card with a hairline between rows. */
@Composable
fun <T> ListCard(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        MaterialTheme.shapes.large,
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider()
            row(item)
        }
    }
}

private class Sample(val name: String, val icon: ImageVector, val spent: Long, val limit: Long?)

@ThemePreviews
@Composable
private fun CategoryLimitRowPreview() = PreviewBox {
    val rows = listOf(
        Sample("Market", Icons.Rounded.ShoppingCart, 18000, 25000),
        Sample("Restorani i kafići", Icons.Rounded.Restaurant, 9600, 10000),
        Sample("Gotovina", Icons.Rounded.Payments, 10000, 10000),
        Sample("Dostava", Icons.Rounded.DeliveryDining, 6200, 5000),
        Sample("Ostalo", Icons.Rounded.Category, 1420, null),
    )
    ListCard(rows) { CategoryLimitRow(it.name, it.icon, it.spent, it.limit) }
}

@OptIn(ExperimentalLayoutApi::class)
@ThemePreviews
@Composable
private fun StatusPillPreview() = PreviewBox {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(7200, 10000, withTitle = true)
        StatusPill(9600, 10000, withTitle = true)
        StatusPill(10000, 10000, withTitle = true)
        StatusPill(12400, 10000, withTitle = true)
        StatusPill(1420, null, withTitle = true)
        StatusPill(7200, 10000)
        StatusPill(1420, null)
    }
}
