package com.walktalk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import com.walktalk.util.AudioFeedback
import com.walktalk.util.Constants
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtcCertificatePem

class WalkTalkApp : Application() {

    companion object {
        lateinit var instance: WalkTalkApp
            private set

        // Pre-cached ECDSA certificate for fast WebRTC handshake (<20ms vs 200ms RSA)
        var cachedCertificate: RtcCertificatePem? = null
            private set
    }

    lateinit var audioFeedback: AudioFeedback
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Safe global exception logger
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("WalkTalkApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            audioFeedback = AudioFeedback(this)
        } catch (t: Throwable) {
            android.util.Log.e("WalkTalkApp", "AudioFeedback init error: ${t.message}")
        }

        // 1. Pre-warm WebRTC native libraries and PeerConnectionFactory
        initializeWebRtc()

        // 2. Create notification channels
        createNotificationChannels()
    }

    private fun initializeWebRtc() {
        Thread {
            try {
                val initOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(initOptions)

                // Pre-generate ECDSA certificate in background
                cachedCertificate = RtcCertificatePem.generateCertificate(
                    org.webrtc.PeerConnection.KeyType.ECDSA
                )
            } catch (t: Throwable) {
                android.util.Log.e("WalkTalkApp", "WebRTC init warning: ${t.message}")
            }
        }.start()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 1. High Priority Incoming Call Channel
            val callChannel = NotificationChannel(
                Constants.CALL_NOTIFICATION_CHANNEL_ID,
                "Incoming Intercom Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent incoming family intercom audio calls"
                enableVibration(true)
                setSound(null, null) // Custom sound handled by CallService
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // 2. SOS Emergency Channel (Bypasses DND)
            val sosChannel = NotificationChannel(
                Constants.SOS_NOTIFICATION_CHANNEL_ID,
                "Family SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS emergency alerts from family members"
                enableVibration(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setBypassDnd(true)
                }
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // 3. Background Location Tracking Channel (Silent ongoing)
            val locationChannel = NotificationChannel(
                Constants.LOCATION_NOTIFICATION_CHANNEL_ID,
                "Family Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active location synchronization service"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(callChannel, sosChannel, locationChannel)
            )
        }
    }
}
