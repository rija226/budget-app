package com.budget.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.budget.app.R

// Manrope comes from the Google Fonts provider (res/values/font_certs.xml); until it downloads, the system font is used.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val manrope = GoogleFont("Manrope")

val Manrope = FontFamily(
    Font(manrope, provider, FontWeight.Normal),
    Font(manrope, provider, FontWeight.SemiBold),
    Font(manrope, provider, FontWeight.Bold),
    Font(manrope, provider, FontWeight.ExtraBold),
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = "tnum", // tabular figures so amounts align
)

val BudzetType = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -0.5),   // hero "23,40 €"
    displayMedium = style(45, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),        // transaction detail amount
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.Bold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.2),
    bodySmall = style(12, 16, FontWeight.Normal, 0.3),
    labelLarge = style(14, 20, FontWeight.Bold, 0.1),
    labelMedium = style(12, 16, FontWeight.Bold, 0.4),
    labelSmall = style(11, 16, FontWeight.Bold, 0.4),
)
