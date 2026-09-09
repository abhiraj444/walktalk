package com.walktalk.data.firebase

import com.google.firebase.database.FirebaseDatabase

object FirebaseHelper {
    const val DATABASE_URL = "https://walkietalkie-462a2-default-rtdb.asia-southeast1.firebasedatabase.app"

    fun getDatabase(): FirebaseDatabase {
        return try {
            FirebaseDatabase.getInstance(DATABASE_URL)
        } catch (e: Exception) {
            try {
                FirebaseDatabase.getInstance()
            } catch (e2: Exception) {
                FirebaseDatabase.getInstance(DATABASE_URL)
            }
        }
    }
}
