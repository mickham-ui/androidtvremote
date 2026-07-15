package com.out.androidtvremote.ui.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.gms.common.util.CollectionUtils.listOf
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()




        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left   = bars.left,
                top    = bars.top,
                right  = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }


        // ── Entrance animations ───────────────────────────────────────────────
        // Stagger the welcome screen elements in with a slide-up + fade
        animateIn()

        // ── Mark first launch done + navigate ────────────────────────────────
        // We mark it HERE (not in WelcomeActivity.onCreate) so that if the user
        // force-kills before reaching MainActivity, they'll see welcome again.
        binding.welcomeTransferOrRestore.setOnClickListener {
            // Mark as seen only when user actively taps "Get Started"
            getSharedPreferences("AppPreferences", MODE_PRIVATE)
                .edit()
                .putBoolean("isFirstLaunch", false)
                .apply()

            // Navigate to MainActivity, clearing the back stack.
            // SplashActivity has already handled the App Open Ad on this launch.
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )

        }
    }

    private fun animateIn() {
        // Slide up + fade in each element with a stagger delay.
        // Adjust the view IDs to match your actual welcome layout IDs.
        val views = listOf<View>(
            // Add your actual view IDs here, e.g.:
            // binding.tvWelcomeTitle,
            // binding.tvWelcomeSubtitle,
            // binding.ivWelcomeIllustration,
            binding.welcomeTransferOrRestore
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 80f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay((index * 120 + 200).toLong())
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .start()
        }
    }
}