package com.pentagram.airplay

import android.content.Context

/**
 * Manages app preferences using SharedPreferences.
 * Handles onboarding state and other user settings.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the user has completed the onboarding flow.
     */
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    companion object {
        private const val PREFS_NAME = "pentagram_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
