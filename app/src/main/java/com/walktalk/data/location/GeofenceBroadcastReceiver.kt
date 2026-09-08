package com.walktalk.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.walktalk.util.Constants

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Geofence error code: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val transitionName = when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN"
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val familyId = prefs.getString(Constants.KEY_FAMILY_ID, Constants.DEFAULT_FAMILY_ID) ?: Constants.DEFAULT_FAMILY_ID
        val deviceId = prefs.getString(Constants.KEY_DEVICE_ID, "unknown") ?: "unknown"

        val db = FirebaseDatabase.getInstance().reference

        for (geofence in triggeringGeofences) {
            val fenceId = geofence.requestId
            Log.d("GeofenceReceiver", "Geofence $fenceId transition: $transitionName")

            val eventData = mapOf(
                "deviceId" to deviceId,
                "fenceId" to fenceId,
                "transition" to transitionName,
                "timestamp" to ServerValue.TIMESTAMP
            )
            db.child("families").child(familyId).child("geofence_events").push().setValue(eventData)
        }
    }
}
