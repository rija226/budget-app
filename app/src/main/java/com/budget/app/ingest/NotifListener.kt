package com.budget.app.ingest

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.budget.app.data.AppDatabase
import com.budget.app.data.RawNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // ponytail: faza 1 bilježi sve aplikacije; filter na poznate izvore dolazi u fazi 2
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras
        val row = RawNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            postedAt = sbn.postTime,
            tag = sbn.tag,
        )
        scope.launch {
            val id = AppDatabase.get(applicationContext).rawNotifications().insert(row)
            Pipeline.process(applicationContext, row.copy(id = id))
        }
    }
}
