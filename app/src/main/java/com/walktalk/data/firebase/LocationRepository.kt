package com.walktalk.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FamilyLocation(
    val deviceId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val heading: Float = 0f,
    val battery: Int = 100,
    val activity: String = "still",
    val timestamp: Long = 0L
)

class LocationRepository(
    private val familyId: String,
    private val deviceId: String
) {

    private val db = FirebaseHelper.getDatabase().reference
    private val locationsRef = db.child("families").child(familyId).child("live_locations")

    fun updateMyLocation(
        name: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float,
        heading: Float,
        battery: Int,
        activity: String
    ) {
        val data = mapOf(
            "name" to name,
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy,
            "speed" to speed,
            "heading" to heading,
            "battery" to battery,
            "activity" to activity,
            "timestamp" to ServerValue.TIMESTAMP
        )
        locationsRef.child(deviceId).updateChildren(data)
    }

    fun observeFamilyLocations(): Flow<List<FamilyLocation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<FamilyLocation>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val name = child.child("name").getValue(String::class.java) ?: id
                    val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java) ?: 0.0
                    val accuracy = child.child("accuracy").getValue(Float::class.java) ?: 0f
                    val speed = child.child("speed").getValue(Float::class.java) ?: 0f
                    val heading = child.child("heading").getValue(Float::class.java) ?: 0f
                    val battery = child.child("battery").getValue(Int::class.java) ?: 100
                    val activity = child.child("activity").getValue(String::class.java) ?: "still"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                    list.add(
                        FamilyLocation(
                            deviceId = id,
                            name = name,
                            lat = lat,
                            lng = lng,
                            accuracy = accuracy,
                            speed = speed,
                            heading = heading,
                            battery = battery,
                            activity = activity,
                            timestamp = timestamp
                        )
                    )
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        locationsRef.addValueEventListener(listener)
        awaitClose { locationsRef.removeEventListener(listener) }
    }
}
