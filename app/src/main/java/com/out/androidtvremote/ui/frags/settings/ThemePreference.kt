package com.out.androidtvremote.ui.frags.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.out.androidtvremote.R
import com.google.android.material.button.MaterialButtonToggleGroup

class ThemePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    init {
        layoutResource = R.layout.pref_theme_chips
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val toggleGroup = holder.findViewById(R.id.themeButtonToggleGroup)
                as? MaterialButtonToggleGroup ?: return

        // ── Remove any stale listener before re-binding ───────────────────────
        // RecyclerView recycles ViewHolders — without this, tapping fires the
        // listener multiple times (once per recycle) causing duplicate writes.
        toggleGroup.clearOnButtonCheckedListeners()

        // ── Restore saved selection ───────────────────────────────────────────
        val currentTheme = sharedPreferences?.getString(key, "system") ?: "system"
        val checkedId = when (currentTheme) {
            "light"  -> R.id.btnThemeLight
            "dark"   -> R.id.btnThemeDark
            else     -> R.id.btnThemeSystem
        }
        // Suppress listener during programmatic check
        toggleGroup.check(checkedId)

        // ── Attach fresh listener ─────────────────────────────────────────────
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener  // ignore uncheck events

            val newTheme = when (checkedId) {
                R.id.btnThemeLight  -> "light"
                R.id.btnThemeDark   -> "dark"
                else                -> "system"
            }

            // Only write + notify if value actually changed
            if (sharedPreferences?.getString(key, "system") != newTheme) {
                sharedPreferences?.edit()?.putString(key, newTheme)?.apply()
                notifyChanged()  // lets the PreferenceFragment know the value changed
            }
        }
    }
}