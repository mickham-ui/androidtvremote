package com.out.androidtvremote.ui.frags.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RatingBar
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.out.androidtvremote.App
import com.out.androidtvremote.ui.activities.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.out.androidtvremote.BuildConfig
import com.out.androidtvremote.R
import com.out.androidtvremote.ui.guide.GuideDialog
import timber.log.Timber
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.out.androidtvremote.utils.QuickAccessApps

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey)

        findPreference<Preference>("connect_guide")?.setOnPreferenceClickListener {
            showGuide()
            true
        }

        findPreference<Preference>(QuickAccessApps.PREF_QUICK_ACCESS)?.setOnPreferenceClickListener {
            showQuickAccessDialog()
            true
        }

        // ── Open Source ──────────────────────────────────────────────────────
        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0.16"
        findPreference<Preference>("app_version")?.summary = versionName

        findPreference<Preference>("github_repo")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/outofcontextapps/androidtvremote"))
            startActivity(intent)
            true
        }

        findPreference<Preference>("app_license")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/outofcontextapps/androidtvremote/blob/main/LICENSE"))
            startActivity(intent)
            true
        }

        val share: Preference? = findPreference("share")
        share?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val shareText = """
🎮 Control your Android TV from your phone.

${getString(R.string.app_name)} turns your phone into a full-featured Android TV remote.
No setup headaches. No ads. No nonsense.

✅ D-Pad, touchpad & number pad
✅ Launch apps instantly
🇮🇳 Made with ❤️ in India

👇 Get it free on Play Store:
https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}
            """.trimIndent()

            val sharingIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            }
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_using)))
            true
        }

        val feedback: Preference? = findPreference("feedback")
        feedback?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showFeedbackDialog()
            true
        }

        val rateUs: Preference? = findPreference("rate_us")
        rateUs?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showReviewDialog(requireContext())
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun showFeedbackDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Enter your feedback here..."
            minLines = 3
        }
        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            val margin = resources.getDimensionPixelSize(com.google.android.material.R.dimen.m3_card_elevated_disabled_z)
            marginStart = margin
            marginEnd = margin
            topMargin = margin
        }
        container.addView(editText, params)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Feedback")
            .setView(container)
            .setPositiveButton("Send") { _, _ ->
                val feedbackText = editText.text.toString()
                if (feedbackText.isNotBlank()) {
                    openEmailFeedback(requireContext(), feedbackText)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun showQuickAccessDialog() {
        val prefs = preferenceManager.sharedPreferences
        val order = QuickAccessApps.parseOrder(prefs?.getString(QuickAccessApps.PREF_ORDER, null))
        val enabled = prefs?.getStringSet(QuickAccessApps.PREF_ENABLED, null)
            ?: QuickAccessApps.defaultEnabled()

        val items = order.map { appName ->
            QuickAccessItem(appName, enabled.contains(appName))
        }.toMutableList()

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_quick_access_apps, null, false)

        val recycler = dialogView.findViewById<RecyclerView>(R.id.quick_access_list)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = QuickAccessAdapter(items)
        recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                items.add(to, items.removeAt(from))
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(recycler)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quick Access Apps")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newOrder = items.map { it.name }
                val newEnabled = items.filter { it.enabled }.map { it.name }.toSet()
                prefs?.edit()
                    ?.putString(QuickAccessApps.PREF_ORDER, QuickAccessApps.serializeOrder(newOrder))
                    ?.putStringSet(QuickAccessApps.PREF_ENABLED, newEnabled)
                    ?.apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showReviewDialog(context: Context) {
        val customAlertDialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_review, null, false)

        MaterialAlertDialogBuilder(context)
            .setView(customAlertDialogView)
            .setPositiveButton("Submit") { dialog, _ ->
                val ratingBar = customAlertDialogView.findViewById<RatingBar>(R.id.ratingBar)
                val rating = ratingBar.rating
                if (rating >= 4) {
                    openPlayStoreForReview(context)
                } else {
                    openEmailFeedback(context, "Rating: $rating stars")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.capitalize()
        } else {
            "$manufacturer $model".capitalize()
        }
    }

    private fun openPlayStoreForReview(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$packageName")
                )
            )
        } catch (e: android.content.ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    private fun openEmailFeedback(context: Context, extraText: String = "") {
        val deviceInfo = "Device: ${Build.MODEL}\n" +
                "Android Version: ${Build.VERSION.RELEASE}\n" +
                "Manufacturer: ${Build.MANUFACTURER}\n" +
                "Model: ${Build.MODEL}\n" +
                "Device ID: ${
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                }\n"

        val subject = "Feedback on ${getString(R.string.app_name)}"
        val body = "Dear Developer,\n\n" +
                "I would like to provide feedback on the application.\n\n" +
                (if (extraText.isNotEmpty()) "Feedback: $extraText\n\n" else "") +
                "$deviceInfo"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("helpoutofcontext@outlook.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)

            val gmailPackage = "com.google.android.gm"
            val pm = context.packageManager
            val gmailIntent = pm.getLaunchIntentForPackage(gmailPackage)
            if (gmailIntent != null) {
                setPackage(gmailPackage)
            }
        }

        try {
            startActivity(Intent.createChooser(intent, "Choose an Email client :"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to send email")
        }
    }

    private fun hotReloadActivity(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit().putBoolean("refreshed", true).apply()
        ActivityCompat.recreate(requireActivity())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "haptic_feedback" -> {
                val isEnabled = sharedPreferences?.getBoolean("haptic_feedback", true) ?: true
                if (isEnabled) {
                    view?.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }
            }
            "local_media_server" -> {
                val isEnabled = sharedPreferences?.getBoolean("local_media_server", true) ?: true
                val app = requireActivity().application as App
                if (!isEnabled) {
                    app.stopLocalMediaServer()
                } else {
                    app.ensureLocalMediaServer()
                }
            }
            "app_theme" -> {
                val themePref = sharedPreferences?.getString("app_theme", "system") ?: "system"
                when (themePref) {
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
    }

    private fun showGuide() {
        GuideDialog(requireContext()).show()
    }

    private data class QuickAccessItem(
        val name: String,
        var enabled: Boolean
    )

    private class QuickAccessAdapter(
        private val items: List<QuickAccessItem>
    ) : RecyclerView.Adapter<QuickAccessAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_quick_access_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.checkBox.text = item.name
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.enabled
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.enabled = isChecked
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val checkBox: MaterialCheckBox = itemView.findViewById(R.id.quick_access_checkbox)
            val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        }
    }
}
