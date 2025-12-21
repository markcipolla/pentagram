package com.pentagram.airplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.pentagram.airplay.service.AirPlayService
import com.pentagram.airplay.service.AirPlayCryptoNative

class MainActivity : AppCompatActivity() {

    private lateinit var toggleServiceButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var pinText: TextView
    private lateinit var pinLabelText: TextView
    private lateinit var instructionsText: TextView
    private lateinit var wizardImage: ImageView

    private var isServiceRunning = false
    private var connectionState = ConnectionState.DISCONNECTED
    private var connectedDeviceName: String? = null

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        PAIRING,
        STREAMING
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

        // Static reference to current activity for PIN display
        private var currentActivity: MainActivity? = null

        fun displayPin(pin: String) {
            currentActivity?.runOnUiThread {
                currentActivity?.showPin(pin)
            }
        }

        fun hidePin() {
            currentActivity?.runOnUiThread {
                currentActivity?.clearPin()
            }
        }

        fun updateConnectionState(state: ConnectionState, deviceName: String? = null) {
            currentActivity?.runOnUiThread {
                currentActivity?.setConnectionState(state, deviceName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleServiceButton = findViewById(R.id.toggleServiceButton)
        statusText = findViewById(R.id.statusText)
        deviceNameText = findViewById(R.id.deviceNameText)
        pinText = findViewById(R.id.pinText)
        pinLabelText = findViewById(R.id.pinLabelText)
        instructionsText = findViewById(R.id.instructionsText)
        wizardImage = findViewById(R.id.wizardImage)

        // Load the wizard GIF with Glide
        Glide.with(this)
            .asGif()
            .load(R.drawable.wizard_pentagram)
            .into(wizardImage)

        // Register this activity for PIN display
        currentActivity = this

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        toggleServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopAirPlayService()
            } else {
                startAirPlayService()
            }
        }

        // Test JNI
        testJNI()

        // Auto-start AirPlay service for testing
        startAirPlayService()

        updateUI()
    }

    private fun testJNI() {
        try {
            val nativeCrypto = AirPlayCryptoNative()
            val version = nativeCrypto.getVersion()
            val testResult = nativeCrypto.testJNI()
            Log.i("MainActivity", "JNI Version: $version")
            Log.i("MainActivity", "JNI Test: $testResult")
        } catch (e: Exception) {
            Log.e("MainActivity", "JNI test failed: ${e.message}", e)
        }
    }

    private fun startAirPlayService() {
        val intent = Intent(this, AirPlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        updateUI()
    }

    private fun stopAirPlayService() {
        val intent = Intent(this, AirPlayService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()
    }

    private fun setConnectionState(state: ConnectionState, deviceName: String? = null) {
        connectionState = state
        connectedDeviceName = deviceName
        updateUI()
    }

    private fun updateUI() {
        val deviceName = android.os.Build.MODEL ?: "Unknown Device"

        if (!isServiceRunning) {
            toggleServiceButton.text = getString(R.string.start_service)
            toggleServiceButton.isEnabled = true
            statusText.text = getString(R.string.service_stopped)
            deviceNameText.text = ""
            instructionsText.text = "Start the service to begin advertising as an AirPlay receiver."
            return
        }

        // Service is running - show state based on connection
        when (connectionState) {
            ConnectionState.DISCONNECTED -> {
                toggleServiceButton.text = getString(R.string.stop_service)
                toggleServiceButton.isEnabled = true
                statusText.text = getString(R.string.service_running)
                deviceNameText.text = ""
                instructionsText.text = "Look for '⛧ $deviceName' in your macOS screen mirroring menu."
            }
            ConnectionState.CONNECTING -> {
                toggleServiceButton.text = "Connecting..."
                toggleServiceButton.isEnabled = false
                statusText.text = "Device connecting"
                val client = connectedDeviceName ?: "Unknown"
                deviceNameText.text = "From: $client"
                instructionsText.text = "Establishing connection..."
            }
            ConnectionState.PAIRING -> {
                toggleServiceButton.text = "Pairing..."
                toggleServiceButton.isEnabled = false
                statusText.text = "Pairing in progress"
                val client = connectedDeviceName ?: "Unknown"
                deviceNameText.text = "From: $client"
                instructionsText.text = "Verifying device..."
            }
            ConnectionState.STREAMING -> {
                toggleServiceButton.text = getString(R.string.stop_service)
                toggleServiceButton.isEnabled = true
                statusText.text = "Streaming"
                val client = connectedDeviceName ?: "Unknown"
                deviceNameText.text = "Connected to: $client"
                instructionsText.text = "Screen mirroring active"
            }
        }
    }

    private fun showPin(pin: String) {
        pinText.text = pin
        pinText.visibility = android.view.View.VISIBLE
        pinLabelText.visibility = android.view.View.VISIBLE
    }

    private fun clearPin() {
        pinText.text = ""
        pinText.visibility = android.view.View.GONE
        pinLabelText.visibility = android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentActivity == this) {
            currentActivity = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Permission handled
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                startActivity(Intent(this, LegalInfoActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
