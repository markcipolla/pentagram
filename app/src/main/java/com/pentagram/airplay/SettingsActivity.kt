package com.pentagram.airplay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/**
 * Settings activity providing theme toggle, help, and legal information access.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var themeRadioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferencesManager = PreferencesManager(this)

        // Set up ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings)

        setupThemeSelection()
        setupClickListeners()
        setupVersionInfo()
    }

    private fun setupThemeSelection() {
        themeRadioGroup = findViewById(R.id.themeRadioGroup)

        // Set current selection based on saved preference
        when (preferencesManager.themeMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                themeRadioGroup.check(R.id.radioSystem)
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                themeRadioGroup.check(R.id.radioLight)
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                themeRadioGroup.check(R.id.radioDark)
            }
        }

        // Listen for changes
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            preferencesManager.themeMode = newMode
        }
    }

    private fun setupClickListeners() {
        // Help
        findViewById<LinearLayout>(R.id.helpRow).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        // Send Feedback (GitHub Issues)
        findViewById<LinearLayout>(R.id.feedbackRow).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL))
            startActivity(intent)
        }

        // Privacy Policy
        findViewById<LinearLayout>(R.id.privacyPolicyRow).setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            intent.putExtra(PrivacyPolicyActivity.EXTRA_SHOW_TERMS, false)
            startActivity(intent)
        }

        // Terms of Service
        findViewById<LinearLayout>(R.id.termsRow).setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            intent.putExtra(PrivacyPolicyActivity.EXTRA_SHOW_TERMS, true)
            startActivity(intent)
        }

        // Legal Info (GPL)
        findViewById<LinearLayout>(R.id.legalInfoRow).setOnClickListener {
            startActivity(Intent(this, LegalInfoActivity::class.java))
        }
    }

    private fun setupVersionInfo() {
        val versionText = findViewById<TextView>(R.id.versionText)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = getString(R.string.version, packageInfo.versionName)
        } catch (e: Exception) {
            versionText.text = getString(R.string.version, "Unknown")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val GITHUB_ISSUES_URL = "https://github.com/markcipolla/pentagram/issues"
    }
}
