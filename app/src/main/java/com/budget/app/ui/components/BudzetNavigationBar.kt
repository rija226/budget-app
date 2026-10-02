package com.budget.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.budget.app.ui.theme.CappedNavLabel

enum class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Pocetna("Početna", Icons.Outlined.Home, Icons.Rounded.Home),
    Transakcije("Transakcije", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong),
    Izvjestaji("Izvještaji", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
    Budzet("Budžet", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
}

@Composable
fun BudzetNavigationBar(selected: NavTab, modifier: Modifier = Modifier, onSelect: (NavTab) -> Unit = {}) {
    NavigationBar(modifier) {
        NavTab.entries.forEach { tab ->
            val on = tab == selected
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(tab) },
                icon = { Icon(if (on) tab.selectedIcon else tab.icon, null) },
                label = {
                    ProvideTextStyle(TextStyle(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)) {
                        CappedNavLabel(tab.label)
                    }
                },
            )
        }
    }
}

@ThemePreviews
@Composable
private fun BudzetNavigationBarPreview() = PreviewBox {
    BudzetNavigationBar(NavTab.Pocetna)
    BudzetNavigationBar(NavTab.Budzet)
}
