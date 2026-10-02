package com.budget.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budget.app.ui.format.formatApproxEur
import com.budget.app.ui.format.formatCurrency
import com.budget.app.ui.format.formatEur
import com.budget.app.ui.theme.LocalStatus
import com.budget.app.ui.theme.Spacing
import com.budget.app.ui.theme.isLargeFont

enum class LabelKind { Confirmed, AiSuggested, Transfer, Salary }

/** 1dp dashed outline that follows a rounded corner (BorderStroke has no dash support). */
private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
    )
}

/** AI-suggested categories are dashed + tertiary until the owner confirms them. */
@Composable
fun CategoryLabel(text: String, modifier: Modifier = Modifier, kind: LabelKind = LabelKind.Confirmed) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val container = when (kind) {
        LabelKind.AiSuggested -> Color.Transparent
        LabelKind.Salary -> s.okContainer
        else -> c.surfaceContainerHighest
    }
    val content = when (kind) {
        LabelKind.AiSuggested -> c.tertiary
        LabelKind.Salary -> s.onOkContainer
        else -> c.onSurfaceVariant
    }
    val icon = when (kind) {
        LabelKind.AiSuggested -> Icons.Rounded.AutoAwesome
        LabelKind.Salary -> Icons.Rounded.Work
        LabelKind.Transfer -> Icons.Rounded.SyncAlt
        LabelKind.Confirmed -> null
    }
    val shape = MaterialTheme.shapes.small
    Surface(
        if (kind == LabelKind.AiSuggested) modifier.dashedBorder(content, 8.dp) else modifier,
        shape,
        container,
        content,
    ) {
        Row(
            Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp),
            Arrangement.spacedBy(4.dp),
            Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Income = any inflow that is not the salary ("Ostali prihodi"). */
enum class TxKind { Expense, Income, Transfer, Salary }

/**
 * Amounts in cents. [amountEurMinor] defaults to the amount for EUR and to null (= "bez kursa") otherwise.
 * Transfer and salary rows pick their own avatar icon; [icon] is the category icon of an expense.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    merchant: String,
    category: String,
    icon: ImageVector,
    time: String,
    amountMinor: Long,
    modifier: Modifier = Modifier,
    currency: String = "EUR",
    amountEurMinor: Long? = if (currency == "EUR") amountMinor else null,
    kind: TxKind = TxKind.Expense,
    aiSuggested: Boolean = false,
    onClick: () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val s = LocalStatus.current
    val salary = kind == TxKind.Salary
    val inflow = salary || kind == TxKind.Income
    val shown = if (currency == "EUR") amountMinor.formatEur() else amountMinor.formatCurrency(currency)
    val primary = if (inflow) "+$shown" else shown
    val secondary = when {
        kind == TxKind.Transfer -> "van budžeta"
        currency == "EUR" -> null
        else -> amountEurMinor?.formatApproxEur() ?: "bez kursa"
    }
    val amountColor = when {
        inflow -> s.ok
        kind == TxKind.Transfer -> c.onSurfaceVariant
        else -> c.onSurface
    }
    val labelKind = when {
        salary -> LabelKind.Salary
        kind == TxKind.Transfer -> LabelKind.Transfer
        aiSuggested -> LabelKind.AiSuggested
        else -> LabelKind.Confirmed
    }
    val amountStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val click = modifier.fillMaxWidth().clickable(onClick = onClick)

    if (isLargeFont()) {
        Column(click.padding(16.dp), Arrangement.spacedBy(6.dp)) {
            Text(merchant, style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Arrangement.spacedBy(4.dp)) {
                Text(primary, style = amountStyle, color = amountColor, softWrap = false)
                if (secondary != null) {
                    Text(
                        secondary,
                        Modifier.weight(1f),
                        c.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                    )
                }
            }
            CategoryLabel(category, kind = labelKind)
        }
        return
    }

    Row(
        click.heightIn(min = Spacing.transactionRowMin).padding(horizontal = 16.dp, vertical = 12.dp),
        Arrangement.spacedBy(12.dp),
        Alignment.CenterVertically,
    ) {
        CategoryAvatar(
            when (kind) {
                TxKind.Transfer -> Icons.Rounded.SyncAlt
                TxKind.Salary -> Icons.Rounded.Work
                TxKind.Expense, TxKind.Income -> icon
            },
            container = if (salary) s.okContainer else c.secondaryContainer,
            content = if (salary) s.onOkContainer else c.onSecondaryContainer,
        )
        Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
            Text(merchant, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryLabel(category, kind = labelKind)
                Text(time, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(primary, style = amountStyle, color = amountColor, softWrap = false)
            if (secondary != null) {
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, softWrap = false)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@ThemePreviews
@Composable
private fun CategoryLabelPreview() = PreviewBox {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryLabel("Market")
        CategoryLabel("Restorani i kafići", kind = LabelKind.AiSuggested)
        CategoryLabel("Prenos", kind = LabelKind.Transfer)
        CategoryLabel("Plata", kind = LabelKind.Salary)
    }
}

@ThemePreviews
@Composable
private fun TransactionRowPreview() = PreviewBox {
    ListCard(List(5) { it }) {
        when (it) {
            0 -> TransactionRow("Maxi", "Market", Icons.Rounded.ShoppingCart, "14:32", 1250)
            1 -> TransactionRow(
                "Pekara Zlatni klas", "Restorani i kafići", Icons.Rounded.Restaurant, "08:15",
                2500, currency = "KM", amountEurMinor = 1278, aiSuggested = true,
            )
            2 -> TransactionRow("Prenos na štednju", "Prenos", Icons.Rounded.SyncAlt, "25.09.", 30000, kind = TxKind.Transfer)
            3 -> TransactionRow("Uplata plate", "Plata", Icons.Rounded.Work, "25.09.", 150000, kind = TxKind.Salary)
            else -> TransactionRow("Steam", "Zabava", Icons.Rounded.LocalActivity, "27.09.", 1499, currency = "USD")
        }
    }
}
