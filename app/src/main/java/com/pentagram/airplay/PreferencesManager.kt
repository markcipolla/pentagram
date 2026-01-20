package com.pentagram.airplay

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Manages app preferences using SharedPreferences.
 * Handles onboarding state, theme preferences, and other user settings.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the user has completed the onboarding flow.
     */
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    /**
     * The current theme mode setting.
     * Returns one of AppCompatDelegate.MODE_NIGHT_* values.
     * Defaults to MODE_NIGHT_YES (always dark mode).
     */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) {
            prefs.edit().putInt(KEY_THEME_MODE, value).apply()
            AppCompatDelegate.setDefaultNightMode(value)
        }

    /**
     * Applies the saved theme mode setting.
     * Should be called early in app startup (before Activity.onCreate).
     */
    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    companion object {
        private const val PREFS_NAME = "pentagram_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
