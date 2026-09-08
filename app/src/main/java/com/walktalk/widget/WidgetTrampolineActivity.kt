package com.walktalk.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.walktalk.MainActivity

class WidgetTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetId = intent.getStringExtra("target_id") ?: "everyone"

        // Launch MainActivity with destination target
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_call_target_id", targetId)
        }
        startActivity(mainIntent)

        // Close trampoline immediately
        finish()
    }
}
