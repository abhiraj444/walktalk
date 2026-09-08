package com.walktalk.service

import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.walktalk.ui.talk.IncomingBlastActivity
import com.walktalk.util.Constants

class WalkTalkFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM token received: $token")
        // Stored in preferences and uploaded to RTDB under /families/{id}/members/{deviceId}/fcmToken
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data
        val type = data["type"] ?: return

        when (type) {
            "incoming_call" -> {
                val callId = data["call_id"] ?: ""
                val callerId = data["caller_id"] ?: ""
                val callerName = data["caller_name"] ?: "Family Member"
                showIncomingCallFullScreen(callId, callerId, callerName)
            }
            "sos_alert" -> {
                val senderName = data["sender_name"] ?: "Family Member"
                val lat = data["lat"] ?: "0.0"
                val lng = data["lng"] ?: "0.0"
                showSosEmergencyNotification(senderName, lat, lng)
            }
        }
    }

    private fun showIncomingCallFullScreen(callId: String, callerId: String, callerName: String) {
        val fullScreenIntent = Intent(this, IncomingBlastActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("caller_id", callerId)
            putExtra("caller_name", callerName)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentTitle("Incoming WalkTalk Blast")
            .setContentText("$callerName is speaking...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e("FCM", "Permission missing for notification: ${e.message}")
        }
    }

    private fun showSosEmergencyNotification(senderName: String, lat: String, lng: String) {
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(this, Constants.SOS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 SOS EMERGENCY: $senderName")
            .setContentText("Emergency alert triggered! Tap to see live coordinates ($lat, $lng).")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .build()

        try {
            NotificationManagerCompat.from(this).notify(9999, notification)
        } catch (e: SecurityException) {
            Log.e("FCM", "Permission missing for notification: ${e.message}")
        }
    }
}
