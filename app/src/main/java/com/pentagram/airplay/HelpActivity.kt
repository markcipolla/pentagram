package com.pentagram.airplay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Help activity displaying feature guide and troubleshooting information.
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // Set title and enable back button
        title = getString(R.string.help_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
