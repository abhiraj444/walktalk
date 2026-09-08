package com.walktalk.data.location

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class SosManager(
    private val context: Context,
    private val familyId: String,
    private val deviceId: String
) {

    private val db = FirebaseDatabase.getInstance().reference

    fun triggerSos(
        senderName: String,
        latitude: Double,
        longitude: Double,
        emergencyPhoneNumbers: List<String> = emptyList()
    ) {
        val sosId = "sos_${System.currentTimeMillis()}"
        val sosData = mapOf(
            "deviceId" to deviceId,
            "senderName" to senderName,
            "lat" to latitude,
            "lng" to longitude,
            "active" to true,
            "timestamp" to ServerValue.TIMESTAMP
        )

        // 1. Write SOS event to Firebase RTDB (local server detects and triggers FCM alarm to all family phones)
        db.child("families").child(familyId).child("sos").child(sosId).setValue(sosData)
            .addOnSuccessListener {
                Log.d("SosManager", "SOS event broadcast successfully to Firebase")
            }
            .addOnFailureListener {
                Log.e("SosManager", "Failed to write SOS to Firebase: ${it.message}")
            }

        // 2. Fallback SMS if emergency phone numbers are configured
        for (phoneNumber in emergencyPhoneNumbers) {
            sendEmergencySms(phoneNumber, senderName, latitude, longitude)
        }
    }

    fun cancelSos(sosId: String) {
        db.child("families").child(familyId).child("sos").child(sosId).child("active").setValue(false)
    }

    private fun sendEmergencySms(phoneNumber: String, name: String, lat: Double, lng: Double) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val mapsLink = "https://maps.google.com/?q=$lat,$lng"
            val text = "EMERGENCY SOS: $name triggered an emergency alert! Live location: $mapsLink"
            smsManager.sendTextMessage(phoneNumber, null, text, null, null)
            Log.d("SosManager", "Emergency SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SosManager", "Failed to send emergency SMS: ${e.message}")
        }
    }
}
