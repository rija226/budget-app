package com.budget.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

// ponytail: ikonica po nazivu predložene kategorije; kategorije koje vlasnik doda dobijaju opštu ikonicu
private val icons = mapOf(
    "Market" to Icons.Rounded.ShoppingCart,
    "Restorani i kafići" to Icons.Rounded.Restaurant,
    "Dostava" to Icons.Rounded.DeliveryDining,
    "Gorivo i prevoz" to Icons.Rounded.LocalGasStation,
    "Režije" to Icons.Rounded.Bolt,
    "Stanovanje" to Icons.Rounded.Home,
    "Zdravlje" to Icons.Rounded.LocalHospital,
    "Odjeća" to Icons.Rounded.Checkroom,
    "Zabava" to Icons.Rounded.LocalActivity,
    "Pretplate" to Icons.Rounded.Subscriptions,
    "Putovanja" to Icons.Rounded.Flight,
    "Gotovina" to Icons.Rounded.Payments,
    "Plata" to Icons.Rounded.Work,
    "Ostali prihodi" to Icons.Rounded.Savings,
)

fun categoryIcon(name: String): ImageVector = icons[name] ?: Icons.Rounded.Category
