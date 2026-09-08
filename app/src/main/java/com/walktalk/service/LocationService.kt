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
import com.walktalk.data.firebase.LocationRepository
import com.walktalk.data.location.LocationTracker
import com.walktalk.util.Constants

class LocationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.walktalk.location.START"
        const val ACTION_STOP = "com.walktalk.location.STOP"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var locationTracker: LocationTracker? = null
    private var locationRepository: LocationRepository? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val familyId = prefs.getString(Constants.KEY_FAMILY_ID, Constants.DEFAULT_FAMILY_ID) ?: Constants.DEFAULT_FAMILY_ID
        val deviceId = prefs.getString(Constants.KEY_DEVICE_ID, Build.MODEL) ?: Build.MODEL
        val userName = prefs.getString(Constants.KEY_USER_NAME, Build.MODEL) ?: Build.MODEL

        locationRepository = LocationRepository(familyId, deviceId)
        locationTracker = LocationTracker(this) { location, activity ->
            locationRepository?.updateMyLocation(
                name = userName,
                lat = location.latitude,
                lng = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                heading = location.bearing,
                battery = 100, // Updated by battery receiver
                activity = activity
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildLocationNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                locationTracker?.startTracking(LocationTracker.TrackingProfile.STATIONARY)
            }
            ACTION_STOP -> {
                locationTracker?.stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildLocationNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingTapIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.LOCATION_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WalkTalk Family Location")
            .setContentText("Sharing real-time status with family")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingTapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        locationTracker?.stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
