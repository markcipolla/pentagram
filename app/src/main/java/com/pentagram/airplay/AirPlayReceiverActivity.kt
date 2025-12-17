package com.pentagram.airplay

import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pentagram.airplay.service.VideoStreamReceiver

class AirPlayReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AirPlayReceiverActivity"

        // Registry to communicate surface to VideoStreamReceiver
        private var currentSessionUUID: String? = null
        private var currentVideoReceiver: VideoStreamReceiver? = null

        fun registerVideoReceiver(sessionUUID: String, receiver: VideoStreamReceiver) {
            currentSessionUUID = sessionUUID
            currentVideoReceiver = receiver
        }

        fun unregisterVideoReceiver() {
            currentSessionUUID = null
            currentVideoReceiver = null
        }

        // Keep track of the current instance to update video dimensions
        private var currentInstance: AirPlayReceiverActivity? = null

        fun updateVideoDimensions(width: Int, height: Int) {
            currentInstance?.updateVideoSize(width, height)
        }
    }

    private lateinit var surfaceView: AspectRatioSurfaceView
    private lateinit var connectionStatus: TextView
    private var sessionUUID: String = ""
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.w(TAG, "🚀🚀🚀 AirPlayReceiverActivity.onCreate() called! 🚀🚀🚀")

            // Lock to current orientation to prevent rotation during playback
            val currentOrientation = resources.configuration.orientation
            requestedOrientation = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            Log.w(TAG, "Locked orientation to: ${if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}")

            // Make fullscreen (hide status bar and navigation bar)
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            // Keep screen on during playback
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            setContentView(R.layout.activity_airplay_receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
            return
        }

        surfaceView = findViewById(R.id.surfaceView)
        connectionStatus = findViewById(R.id.connectionStatus)

        // Get extended display mode information from intent
        val isScreenMirroring = intent.getBooleanExtra("isScreenMirroring", true)
        sessionUUID = intent.getStringExtra("sessionUUID") ?: ""
        val deviceName = intent.getStringExtra("deviceName") ?: "Unknown Device"
        val deviceModel = intent.getStringExtra("deviceModel") ?: ""

        // Register this instance for dimension updates
        currentInstance = this

        // Get video dimensions from intent (passed from AirPlayServer)
        videoWidth = intent.getIntExtra("videoWidth", 0)
        videoHeight = intent.getIntExtra("videoHeight", 0)

        Log.w(TAG, "Video dimensions from intent: ${videoWidth}x${videoHeight}")

        // Set surface holder size based on video dimensions
        if (videoWidth > 0 && videoHeight > 0) {
            updateSurfaceSize()
        }

        Log.w(TAG, "Activity session UUID: $sessionUUID")
        Log.w(TAG, "Registry session UUID: $currentSessionUUID")
        Log.w(TAG, "Registry has receiver: ${currentVideoReceiver != null}")

        // Set up surface holder callback to provide surface to VideoStreamReceiver
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.w(TAG, "🎬🎬🎬 Surface created! 🎬🎬🎬")
                Log.w(TAG, "  Session match: ${currentSessionUUID == sessionUUID}")
                Log.w(TAG, "  Receiver exists: ${currentVideoReceiver != null}")

                // Provide surface to the VideoStreamReceiver if it matches our session
                if (currentSessionUUID == sessionUUID && currentVideoReceiver != null) {
                    Log.w(TAG, "  → Calling setSurface() on VideoStreamReceiver...")
                    currentVideoReceiver?.setSurface(holder.surface)
                    Log.i(TAG, "Surface connected to VideoStreamReceiver")

                    // Hide the status text now that video is connected
                    runOnUiThread {
                        connectionStatus.visibility = android.view.View.GONE
                    }
                } else {
                    Log.e(TAG, "  ❌ Cannot connect surface - session mismatch or no receiver!")
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.i(TAG, "Surface changed: ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.i(TAG, "Surface destroyed")
            }
        })

        // Update UI to show display mode
        val displayMode = if (isScreenMirroring) {
            "Screen Mirroring"
        } else {
            "Extended Display"
        }

        val deviceInfo = if (deviceModel.isNotEmpty()) {
            "$deviceName ($deviceModel)"
        } else {
            deviceName
        }

        connectionStatus.text = """
            $displayMode Active
            Connected to: $deviceInfo
        """.trimIndent()

        // Log the session information
        Log.i(TAG, "═══════════════════════════════════════════════")
        Log.i(TAG, "Display Mode: $displayMode")
        Log.i(TAG, "Device: $deviceInfo")
        Log.i(TAG, "Session: $sessionUUID")
        Log.i(TAG, "═══════════════════════════════════════════════")

        // Set up touch event handling
        surfaceView.setOnTouchListener { view, event ->
            handleTouchEvent(event)
            true
        }
    }

    private fun handleTouchEvent(event: android.view.MotionEvent): Boolean {
        // Get touch coordinates relative to the video
        val x = event.x
        val y = event.y

        // Get the SurfaceView dimensions
        val viewWidth = surfaceView.width.toFloat()
        val viewHeight = surfaceView.height.toFloat()

        // Convert to normalized coordinates (0.0 to 1.0)
        val normalizedX = x / viewWidth
        val normalizedY = y / viewHeight

        // Convert to video coordinates
        val videoX = (normalizedX * videoWidth).toInt()
        val videoY = (normalizedY * videoHeight).toInt()

        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "Touch DOWN at ($videoX, $videoY)")
                // TODO: Send touch event to Mac via AirPlay protocol
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                Log.d(TAG, "Touch MOVE at ($videoX, $videoY)")
                // TODO: Send touch move event to Mac
            }
            android.view.MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Touch UP at ($videoX, $videoY)")
                // TODO: Send touch up event to Mac
            }
        }

        return true
    }

    fun updateConnectionStatus(connected: Boolean) {
        runOnUiThread {
            if (connected) {
                connectionStatus.text = getString(R.string.connected)
                connectionStatus.alpha = 0.5f
            } else {
                connectionStatus.text = getString(R.string.waiting_for_connection)
                connectionStatus.alpha = 1.0f
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val newOrientation = if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

        Log.w(TAG, "Configuration changed - new orientation: $newOrientation")

        // Update surface size for new orientation
        if (videoWidth > 0 && videoHeight > 0) {
            updateSurfaceSize()
        }
    }

    private fun updateSurfaceSize() {
        Log.w(TAG, "updateSurfaceSize: Setting surface to ${videoWidth}x${videoHeight}")

        // Set the surface size to match video dimensions
        surfaceView.holder.setFixedSize(videoWidth, videoHeight)
        surfaceView.setVideoSize(videoWidth, videoHeight)

        Log.w(TAG, "  ✅ Surface configured")
    }

    fun updateVideoSize(newWidth: Int, newHeight: Int) {
        runOnUiThread {
            Log.w(TAG, "📐 Updating video size: ${videoWidth}x${videoHeight} → ${newWidth}x${newHeight}")
            videoWidth = newWidth
            videoHeight = newHeight
            updateSurfaceSize()

            // Reconnect surface to reinitialize MediaCodec with new dimensions
            if (currentSessionUUID == sessionUUID && currentVideoReceiver != null) {
                Log.w(TAG, "Reconnecting surface for new video dimensions...")
                currentVideoReceiver?.setSurface(surfaceView.holder.surface)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Clear instance reference
        if (currentInstance == this) {
            currentInstance = null
        }

        // Unregister video receiver if this is the current session
        if (currentSessionUUID == sessionUUID) {
            unregisterVideoReceiver()
            Log.i(TAG, "VideoStreamReceiver unregistered")
        }
    }
}
