package com.pentagram.airplay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity to display legal information and GPL v3 license notices.
 *
 * This activity is required for GPL v3 compliance (Section 5d) which states
 * that interactive user interfaces must display Appropriate Legal Notices.
 *
 * Copyright (C) 2025 Pentagram Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
class LegalInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_info)

        // Set title
        title = "About"

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set app version
        val versionText = findViewById<TextView>(R.id.appVersionText)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            versionText.text = "Version $versionName"
        } catch (e: Exception) {
            versionText.text = "Version Unknown"
        }

        // Set up click listeners
        findViewById<LinearLayout>(R.id.licenseItem).setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }

        // Third-party software click listeners
        findViewById<LinearLayout>(R.id.rpiplayItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FD-/RPiPlay"))
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.uxplayItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FDH2/UxPlay"))
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.playfairItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FDH2/UxPlay"))
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.llhttpItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nodejs/llhttp"))
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.privacyPolicyItem).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.reportIssuesItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/markcipolla/pentagram/issues"))
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.sourceCodeItem).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/markcipolla/pentagram"))
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
