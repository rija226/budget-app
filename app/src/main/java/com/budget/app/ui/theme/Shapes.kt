package com.budget.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BudzetShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // text fields
    small = RoundedCornerShape(8.dp),       // chips, category labels, status pills
    medium = RoundedCornerShape(12.dp),     // log entries, code boxes
    large = RoundedCornerShape(16.dp),      // cards, FAB
    extraLarge = RoundedCornerShape(28.dp), // hero card, dialog, bottom sheet
)

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    val screenHorizontal = 16.dp
    val transactionRowMin = 72.dp
    val limitBarHeight = 8.dp
    val avatar = 40.dp
}
