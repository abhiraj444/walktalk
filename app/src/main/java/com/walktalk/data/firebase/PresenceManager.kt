package com.walktalk.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class MemberPresence(
    val deviceId: String = "",
    val name: String = "",
    val online: Boolean = false,
    val status: String = "offline", // "available", "reachable", "dnd", "offline"
    val battery: Int = 100,
    val activity: String = "still",
    val lastSeen: Long = 0L
)

class PresenceManager(
    private val familyId: String,
    private val deviceId: String
) {

    private val db = FirebaseHelper.getDatabase().reference
    private val presenceRef = db.child("families").child(familyId).child("presence").child(deviceId)

    fun startPresence(name: String, batteryLevel: Int = 100) {
        val connectedRef = FirebaseHelper.getDatabase().getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // Configure onDisconnect handler on Google's edge
                    val offlineData = mapOf(
                        "online" to false,
                        "status" to "offline",
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    presenceRef.onDisconnect().updateChildren(offlineData)

                    // Set online state
                    val onlineData = mapOf(
                        "name" to name,
                        "online" to true,
                        "status" to "available",
                        "battery" to batteryLevel,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    presenceRef.updateChildren(onlineData)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateStatus(status: String) {
        presenceRef.child("status").setValue(status)
    }

    fun updateBattery(level: Int) {
        presenceRef.child("battery").setValue(level)
    }

    fun observeFamilyPresence(): Flow<List<MemberPresence>> = callbackFlow {
        val allPresenceRef = db.child("families").child(familyId).child("presence")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<MemberPresence>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    if (id == deviceId) continue // Exclude self
                    val name = child.child("name").getValue(String::class.java) ?: id
                    val online = child.child("online").getValue(Boolean::class.java) ?: false
                    val status = child.child("status").getValue(String::class.java) ?: "offline"
                    val battery = child.child("battery").getValue(Int::class.java) ?: 100
                    val activity = child.child("activity").getValue(String::class.java) ?: "still"
                    val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L

                    list.add(
                        MemberPresence(
                            deviceId = id,
                            name = name,
                            online = online,
                            status = status,
                            battery = battery,
                            activity = activity,
                            lastSeen = lastSeen
                        )
                    )
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        allPresenceRef.addValueEventListener(listener)
        awaitClose { allPresenceRef.removeEventListener(listener) }
    }
}
