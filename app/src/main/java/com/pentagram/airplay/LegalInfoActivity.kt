package com.pentagram.airplay

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

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

        // Create a simple scrollable TextView to display legal info
        val textView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 12f
            setTextIsSelectable(true)
        }

        setContentView(textView)

        // Load legal info from raw resource
        try {
            val inputStream = resources.openRawResource(R.raw.legal_info)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val legalText = reader.readText()
            reader.close()

            textView.text = legalText
        } catch (e: Exception) {
            textView.text = """
                PENTAGRAM - LEGAL INFORMATION

                Copyright (C) 2025 Pentagram Contributors

                This program is free software licensed under the GNU General Public License v3.

                Source code available at:
                https://github.com/markcipolla/pentagram

                For full license text, visit:
                https://www.gnu.org/licenses/gpl-3.0.html

                Error loading detailed legal information: ${e.message}
            """.trimIndent()
        }

        // Set title
        title = "Legal Information"

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
