package com.out.androidtvremote.ui.guide

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.out.androidtvremote.R


class GuideDialog(private val context: Context) {
    private lateinit var dialog: AlertDialog
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var prevButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private val slides = listOf(
        GuideSlide(

//            R.drawable.wifi_connection,
            "Connect to WiFi",
            "Ensure your Android device and Android TV are connected to the same WiFi network"
        ),
        GuideSlide(
//            R.drawable.discover_device,
            "Discover Android TV",
            "Tap the discover button to find your Android TV device on the network"
        ),
        GuideSlide(
//            R.drawable.pin_verification,
            "PIN Verification",
            "Enter the PIN shown on your Android TV screen to complete the pairing process"
        ),
        GuideSlide(
//            R.drawable.remote_control,
            "Control Your Android TV",
            "Use the app to navigate, control playback, and launch your favorite apps"
        ),
        GuideSlide(
//            R.drawable.app_launcher,
            "Quick App Launch",
            "Access your favorite Android TV apps directly from the app launcher section"
        )
    )

    private val adapter = GuideAdapter(slides)

    fun show() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.guide_dialog_layout, null)

        setupViews(dialogView)
        setupViewPager()
        setupButtons()

        val builder  = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(true)


        dialog =builder.show()
    }

    private fun setupViews(view: View) {
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        prevButton = view.findViewById(R.id.prevButton)
        nextButton = view.findViewById(R.id.nextButton)
    }

    private fun setupViewPager() {
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonsState(position)
            }
        })
    }

    private fun setupButtons() {
        prevButton.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem -= 1
            }
        }

        nextButton.setOnClickListener {
            if (viewPager.currentItem < slides.size - 1) {
                viewPager.currentItem += 1
            } else {
                if (dialog.isShowing){
                    dialog.dismiss()
                }
            }
        }

        updateButtonsState(0)
    }

    private fun updateButtonsState(position: Int) {
        prevButton.isEnabled = position > 0
        nextButton.text = if (position == slides.size - 1) "Done" else "Next"
    }
}