package com.pentagram.airplay

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Activity to display Privacy Policy or Terms of Service.
 * Pass EXTRA_SHOW_TERMS = true to show Terms of Service instead of Privacy Policy.
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val showTerms = intent.getBooleanExtra(EXTRA_SHOW_TERMS, false)

        // Set title
        title = if (showTerms) {
            getString(R.string.terms_of_service_title)
        } else {
            getString(R.string.privacy_policy_title)
        }

        // Load content
        val contentText = findViewById<TextView>(R.id.contentText)
        val rawResource = if (showTerms) R.raw.terms_of_service else R.raw.privacy_policy

        try {
            val inputStream = resources.openRawResource(rawResource)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()
            contentText.text = content
        } catch (e: Exception) {
            contentText.text = if (showTerms) {
                getDefaultTermsOfService()
            } else {
                getDefaultPrivacyPolicy()
            }
        }
    }

    private fun getDefaultPrivacyPolicy(): String = """
PRIVACY POLICY

Last updated: January 2025

Pentagram ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how we handle information when you use our Pentagram AirPlay Receiver application.

INFORMATION WE COLLECT

Pentagram does not collect, store, or transmit any personal information. The app operates entirely on your local device and network.

NETWORK COMMUNICATION

The app communicates only within your local WiFi network to receive AirPlay streams from your Apple devices. No data is sent to external servers or third parties.

DATA STORAGE

- No user data is stored on external servers
- Preferences (such as theme settings) are stored locally on your device
- No analytics or tracking data is collected

THIRD-PARTY SERVICES

Pentagram does not use any third-party analytics, advertising, or tracking services.

CHILDREN'S PRIVACY

Our app does not collect any personal information from anyone, including children under 13 years of age.

CHANGES TO THIS POLICY

We may update this Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page.

CONTACT US

If you have any questions about this Privacy Policy, please contact us through our GitHub repository:
https://github.com/markcipolla/pentagram

OPEN SOURCE

Pentagram is open source software licensed under the GNU General Public License v3. You can review the source code to verify our privacy practices.
    """.trimIndent()

    private fun getDefaultTermsOfService(): String = """
TERMS OF SERVICE

Last updated: January 2025

Please read these Terms of Service ("Terms") carefully before using the Pentagram AirPlay Receiver application.

ACCEPTANCE OF TERMS

By downloading, installing, or using Pentagram, you agree to be bound by these Terms. If you do not agree to these Terms, do not use the application.

LICENSE

Pentagram is free software licensed under the GNU General Public License version 3 (GPL v3). You are free to:
- Use the software for any purpose
- Study how the software works and modify it
- Redistribute copies of the software
- Distribute copies of your modified versions

The full license text is available at: https://www.gnu.org/licenses/gpl-3.0.html

DISCLAIMER OF WARRANTIES

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.

LIMITATION OF LIABILITY

IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

AIRPLAY AND TRADEMARKS

AirPlay is a trademark of Apple Inc. Pentagram is not affiliated with, endorsed by, or sponsored by Apple Inc.

ACCEPTABLE USE

You agree to use Pentagram only for lawful purposes and in accordance with these Terms. You agree not to:
- Use the app to infringe on others' intellectual property rights
- Attempt to reverse engineer or modify the app in ways that violate the GPL v3 license
- Use the app in any way that could damage, disable, or impair the app

CHANGES TO TERMS

We reserve the right to modify these Terms at any time. We will notify users of any material changes by updating the "Last updated" date.

CONTACT US

If you have any questions about these Terms, please contact us through our GitHub repository:
https://github.com/markcipolla/pentagram
    """.trimIndent()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_SHOW_TERMS = "show_terms"
    }
}
