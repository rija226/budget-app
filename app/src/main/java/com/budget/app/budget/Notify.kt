package com.budget.app.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.ui.format.formatEur

/** System notifications (DESIGN.md "Notifications", Notifikacije.dc.html). */
object Notify {
    private const val LIMITS = "limits"
    private const val ANALYSES = "analyses"

    /** Extra on the launch intent: the route to open (same keys as the back stack in ui/App.kt). */
    const val EXTRA_ROUTE = "route"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LIMITS, "Upozorenja o limitu", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(ANALYSES, "Analize", NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(ctx: Context, route: String, request: Int) = PendingIntent.getActivity(
        ctx, request,
        Intent(ctx, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String, action: String, route: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        channels(ctx)
        val pi = open(ctx, route, id)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF33618D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .addAction(0, action, pi)
            .setAutoCancel(true)
            .setGroup(channel)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** "Market: potrošeno 80% limita" / "Dostava: limit je prekoračen". */
    fun limit(ctx: Context, categoryId: Long, category: String, pct: Int, spent: Long, limit: Long, daysLeft: Int) {
        val (title, text, action, route) = if (pct < 100) {
            listOf("$category: potrošeno $pct% limita", "${spent.formatEur()} od ${limit.formatEur()}. Do plate još $daysLeft ${if (daysLeft % 10 == 1 && daysLeft % 100 != 11) "dan" else "dana"}.", "Otvori budžet", "tab:Budzet")
        } else {
            val over = spent - limit
            listOf(
                if (over == 0L) "$category: limit je dostignut" else "$category: limit je prekoračen",
                "${spent.formatEur()} od ${limit.formatEur()}" + if (over > 0) ", ${over.formatEur()} preko limita." else ".",
                "Pogledaj transakcije", "tab:Transakcije",
            )
        }
        post(ctx, (categoryId * 10 + pct / 100).toInt(), LIMITS, title, text, action, route)
    }

    fun analysisReady(ctx: Context, analysisId: Long, period: String, tips: Int, savingMinor: Long) = post(
        ctx, 100_000 + analysisId.toInt(), ANALYSES,
        "Analiza perioda je spremna",
        "$period: ${if (tips == 3) "tri savjeta" else "$tips savjeta"} za uštedu, procjena do ${savingMinor.formatEur()} mjesečno.",
        "Otvori analizu", "an:$analysisId",
    )
}
