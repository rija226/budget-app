package rs.budzet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density

@Composable
fun BudzetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic color can be layered later (dynamicLightColorScheme / dynamicDarkColorScheme).
    val colors = if (darkTheme) DarkColors else LightColors
    val status = if (darkTheme) DarkStatus else LightStatus
    CompositionLocalProvider(LocalStatus provides status) {
        MaterialTheme(
            colorScheme = colors,
            typography = BudzetType,
            shapes = BudzetShapes,
            content = content,
        )
    }
}

/** Bottom navigation labels: font scale capped at 1.3x, single line, ellipsis. */
@Composable
fun CappedNavLabel(text: String) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.3f))) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
