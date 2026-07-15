package com.out.androidtvremote.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Instant-launch splash — navigates immediately to MainActivity or WelcomeActivity
 * without any animation delay, keeping startup time minimal.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigateNext()
    }

    private fun navigateNext() {
        val prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)

        val intent = if (isFirstLaunch) {
            Intent(this, WelcomeActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }

        // Clear back-stack so the back button doesn't return to splash
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        // Suppress the default window transition — no slide animation leaving splash
        overridePendingTransition(0, 0)
    }
}
