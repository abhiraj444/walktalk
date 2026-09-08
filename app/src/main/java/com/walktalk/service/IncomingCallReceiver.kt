package com.walktalk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", 0)

        when (action) {
            "com.walktalk.action.DISMISS_CALL" -> {
                NotificationManagerCompat.from(context).cancel(notificationId)
                CallService.stop(context)
            }
        }
    }
}
