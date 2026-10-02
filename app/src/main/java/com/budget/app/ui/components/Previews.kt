package com.budget.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.budget.app.ui.theme.BudzetTheme

/** Light, dark and 200% font from a single annotation. */
@Preview(name = "Svijetla", widthDp = 390)
@Preview(name = "Tamna", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Font 200%", widthDp = 390, fontScale = 2f)
annotation class ThemePreviews

@Composable
internal fun PreviewBox(content: @Composable ColumnScope.() -> Unit) {
    BudzetTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), Arrangement.spacedBy(16.dp), content = content)
        }
    }
}
