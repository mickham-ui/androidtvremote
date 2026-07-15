package com.out.androidtvremote.ui.frags.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.out.androidtvremote.R

class RemoteLayoutPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        // Title only — summary set in onAttached() once sharedPreferences is available
        title = "Default Remote Layout"
    }

    /**
     * Called after the preference is attached to its SharedPreferences store.
     * This is the earliest point where sharedPreferences is non-null, so we
     * read the saved value here to display the correct summary on every app launch.
     */
    override fun onAttached() {
        super.onAttached()
        summary = getLayoutSummary()
    }

    override fun onClick() {
        val currentLayout = sharedPreferences?.getString(key, "touchpad") ?: "touchpad"
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_remote_layout, null)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.remoteLayoutToggleGroup)

        val checkedId = if (currentLayout == "touchpad") R.id.btnLayoutTouchpad else R.id.btnLayoutDPad
        toggleGroup.check(checkedId)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Default Remote Layout")
            .setMessage("Choose the default pad for controlling the TV")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val newLayout = if (toggleGroup.checkedButtonId == R.id.btnLayoutTouchpad) "touchpad" else "dpad"
                if (sharedPreferences?.getString(key, "touchpad") != newLayout) {
                    sharedPreferences?.edit()?.putString(key, newLayout)?.apply()
                    summary = getLayoutSummary()
                    notifyChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun getLayoutSummary(): String {
        val layout = sharedPreferences?.getString(key, "touchpad") ?: "touchpad"
        return if (layout == "touchpad") "Touchpad" else "D-Pad"
    }
}
