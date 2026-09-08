package com.walktalk.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.walktalk.MainActivity
import com.walktalk.R
import com.walktalk.data.webrtc.WalkTalkAudioManager
import com.walktalk.util.Constants

class CallService : Service() {

    companion object {
        const val ACTION_START_CALL = "com.walktalk.action.START_CALL"
        const val ACTION_END_CALL = "com.walktalk.action.END_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context, callerName: String) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_END_CALL
            }
            context.startService(intent)
        }
    }

    private var audioManager: WalkTalkAudioManager? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = WalkTalkAudioManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Family Intercom"
                val notification = buildCallNotification(callerName)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                audioManager?.setIntercomAudioMode()
            }
            ACTION_END_CALL -> {
                audioManager?.restoreAudioMode()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildCallNotification(callerName: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingTapIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CALL_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WalkTalk Intercom Active")
            .setContentText("Talking with $callerName")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setOngoing(true)
            .setContentIntent(pendingTapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    override fun onDestroy() {
        audioManager?.restoreAudioMode()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
