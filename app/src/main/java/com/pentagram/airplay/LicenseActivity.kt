package com.pentagram.airplay

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Activity to display the GNU General Public License v3.
 */
class LicenseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        title = "License"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val licenseText = findViewById<TextView>(R.id.licenseText)

        // Load LICENSE file from assets
        try {
            val inputStream = assets.open("LICENSE")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.readText()
            reader.close()
            licenseText.text = text
        } catch (e: Exception) {
            // Fallback to showing GPL v3 notice
            licenseText.text = """
                GNU GENERAL PUBLIC LICENSE
                Version 3, 29 June 2007

                Copyright (C) 2025 Pentagram Contributors

                This program is free software: you can redistribute it and/or modify
                it under the terms of the GNU General Public License as published by
                the Free Software Foundation, either version 3 of the License, or
                (at your option) any later version.

                This program is distributed in the hope that it will be useful,
                but WITHOUT ANY WARRANTY; without even the implied warranty of
                MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
                GNU General Public License for more details.

                You should have received a copy of the GNU General Public License
                along with this program. If not, see <https://www.gnu.org/licenses/>.

                For the full license text, visit:
                https://www.gnu.org/licenses/gpl-3.0.html

                Error loading license file: ${e.message}
            """.trimIndent()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
