package com.budget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.budget.app.budget.Notify
import com.budget.app.ui.App
import com.budget.app.ui.theme.BudzetTheme

class MainActivity : ComponentActivity() {
    // povećava se na svaki povratak u aplikaciju da ekrani ponovo pročitaju dozvole i današnji datum
    private var tick by mutableIntStateOf(0)

    /** Route requested by a notification tap (Notify.EXTRA_ROUTE). */
    private var openRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.channels(this)
        if (savedInstanceState == null) openRoute = intent.getStringExtra(Notify.EXTRA_ROUTE)
        setContent {
            BudzetTheme {
                App(tick, { tick++ }, openRoute) { openRoute = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notify.EXTRA_ROUTE)?.let { openRoute = it }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}
