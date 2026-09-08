package com.walktalk.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import java.util.concurrent.TimeUnit

class LocationTracker(
    private val context: Context,
    private val onLocationUpdated: (Location, String) -> Unit
) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    enum class TrackingProfile {
        STATIONARY,   // < 0.5% battery/hr (Interval 5 min, displacement 50m)
        WALKING,      // ~ 1.5% battery/hr (Interval 60s, displacement 25m)
        DRIVING,      // ~ 3-5% battery/hr (Interval 15s, displacement 50m)
        EMERGENCY_SOS // Max accuracy (Interval 5s, displacement 0m)
    }

    private var currentProfile: TrackingProfile = TrackingProfile.STATIONARY

    @SuppressLint("MissingPermission")
    fun startTracking(profile: TrackingProfile = TrackingProfile.STATIONARY) {
        stopTracking()
        currentProfile = profile

        val (intervalSec, minDistanceMeters, priority) = when (profile) {
            TrackingProfile.STATIONARY -> Triple(300L, 50f, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            TrackingProfile.WALKING -> Triple(60L, 25f, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            TrackingProfile.DRIVING -> Triple(20L, 50f, Priority.PRIORITY_HIGH_ACCURACY)
            TrackingProfile.EMERGENCY_SOS -> Triple(5L, 0f, Priority.PRIORITY_HIGH_ACCURACY)
        }

        val locationRequest = LocationRequest.Builder(priority, TimeUnit.SECONDS.toMillis(intervalSec))
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(intervalSec / 2))
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val activityName = when (currentProfile) {
                        TrackingProfile.STATIONARY -> "still"
                        TrackingProfile.WALKING -> "walking"
                        TrackingProfile.DRIVING -> "in_vehicle"
                        TrackingProfile.EMERGENCY_SOS -> "sos_alert"
                    }
                    onLocationUpdated(location, activityName)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationTracker", "Started location updates with profile: $profile")
        } catch (e: SecurityException) {
            Log.e("LocationTracker", "Missing location permissions: ${e.message}")
        }
    }

    fun updateProfile(newProfile: TrackingProfile) {
        if (currentProfile != newProfile) {
            Log.d("LocationTracker", "Switching location profile: $currentProfile -> $newProfile")
            startTracking(newProfile)
        }
    }

    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
