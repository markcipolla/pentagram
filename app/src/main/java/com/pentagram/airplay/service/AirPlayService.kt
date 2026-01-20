package com.pentagram.airplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pentagram.airplay.MainActivity
import com.pentagram.airplay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.Security

class AirPlayService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var airplayServer: AirPlayServer? = null

    // Persistent Ed25519 keypair (for server identity like UxPlay)
    private var persistentEd25519Seed: ByteArray? = null
    private var persistentEd25519PublicKey: ByteArray? = null

    companion object {
        private const val TAG = "AirPlayService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "airplay_service_channel"
        // Service name will be set dynamically with device name
        private const val SERVICE_TYPE = "_airplay._tcp"
        private const val AIRPLAY_PORT = 7000

        // SharedPreferences for persistent keys
        private const val PREFS_NAME = "airplay_keys"
        private const val KEY_ED25519_SEED = "ed25519_seed"
        private const val KEY_ED25519_PUBLIC = "ed25519_public"

        init {
            // Install Conscrypt as the primary security provider
            // This uses OpenSSL (same as RPiPlay) instead of BouncyCastle
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
                Log.d(TAG, "Conscrypt (OpenSSL) provider installed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install Conscrypt provider: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AirPlay Service created")

        // Load or generate persistent Ed25519 keypair
        loadOrGeneratePersistentKeys()
    }

    /**
     * Load persistent Ed25519 keypair from storage, or generate and save new one
     * This makes the server have a stable identity across restarts (like UxPlay)
     */
    private fun loadOrGeneratePersistentKeys() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSeed = prefs.getString(KEY_ED25519_SEED, null)
        val savedPublicKey = prefs.getString(KEY_ED25519_PUBLIC, null)

        if (savedSeed != null && savedPublicKey != null) {
            // Load existing keys
            try {
                persistentEd25519Seed = hexStringToByteArray(savedSeed)
                persistentEd25519PublicKey = hexStringToByteArray(savedPublicKey)
                Log.i(TAG, "Loaded persistent Ed25519 keys from storage")
                Log.d(TAG, "    Public key: ${persistentEd25519PublicKey!!.take(8).joinToString(" ") { "%02X".format(it) }}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load keys, generating new ones: ${e.message}")
                generateAndSavePersistentKeys(prefs)
            }
        } else {
            // Generate new keys and save
            Log.i(TAG, "No persistent keys found, generating new Ed25519 keypair")
            generateAndSavePersistentKeys(prefs)
        }
    }

    private fun generateAndSavePersistentKeys(prefs: android.content.SharedPreferences) {
        val sodium = com.goterl.lazysodium.LazySodiumAndroid(com.goterl.lazysodium.SodiumAndroid())
        val keyPair = sodium.cryptoSignKeypair()

        persistentEd25519Seed = keyPair.secretKey.asBytes
        persistentEd25519PublicKey = keyPair.publicKey.asBytes

        Log.i(TAG, "Generated Ed25519 keypair:")
        Log.i(TAG, "    Secret key size: ${persistentEd25519Seed!!.size} bytes")
        Log.i(TAG, "    Public key size: ${persistentEd25519PublicKey!!.size} bytes")

        // Save to SharedPreferences as hex strings
        prefs.edit().apply {
            putString(KEY_ED25519_SEED, byteArrayToHexString(persistentEd25519Seed!!))
            putString(KEY_ED25519_PUBLIC, byteArrayToHexString(persistentEd25519PublicKey!!))
            apply()
        }

        Log.i(TAG, "Generated and saved new persistent Ed25519 keypair")
        Log.d(TAG, "    Public key: ${persistentEd25519PublicKey!!.take(8).joinToString(" ") { "%02X".format(it) }}...")
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AirPlay Service started")

        // Start as foreground service with media playback type (for video streaming)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        // Start AirPlay server
        serviceScope.launch {
            startAirPlayServer()
        }

        // Register mDNS service
        registerService()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AirPlay Service destroyed")

        unregisterService()
        airplayServer?.stop()
        serviceJob.cancel()
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AirPlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when AirPlay receiver is running"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerService() {
        nsdManager = (getSystemService(Context.NSD_SERVICE) as NsdManager)

        // Get the device name and add pentagram prefix
        val deviceName = android.os.Build.MODEL ?: "Unknown Device"
        val serviceName = "⛧ $deviceName"

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
            port = AIRPLAY_PORT

            // Add TXT records for AirPlay
            setAttribute("deviceid", getMacAddress())
            // Use older features without PIN requirement
            setAttribute("features", "0x5A7FFFF7,0x1E")  // Older features without strict auth
            setAttribute("srcvers", "220.68")
            setAttribute("flags", "0x4")  // Just audio support, no strict screen mirroring flag
            setAttribute("vv", "2")
            setAttribute("model", "AppleTV3,2")
            setAttribute("pw", "false")  // No password required
            setAttribute("pi", getMacAddress())
            setAttribute("protovers", "1.1")

            // DON'T advertise pk to avoid PIN requirement
            // macOS 15.6 requires PIN when pk is present
            Log.i(TAG, "mDNS: NOT advertising pk to avoid PIN pairing requirement")
            Log.i(TAG, "mDNS: Service name: $serviceName")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service", e)
        }
    }

    private fun unregisterService() {
        try {
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister service", e)
        }
    }

    private fun startAirPlayServer() {
        try {
            airplayServer = AirPlayServer(AIRPLAY_PORT, this, persistentEd25519Seed, persistentEd25519PublicKey)
            airplayServer?.start()
            Log.d(TAG, "AirPlay server started on port $AIRPLAY_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AirPlay server", e)
        }
    }

    private fun getMacAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val mac = networkInterface.hardwareAddress
                    if (mac != null) {
                        val sb = StringBuilder()
                        for (i in mac.indices) {
                            sb.append(String.format("%02X", mac[i]))
                            if (i < mac.size - 1) {
                                sb.append(":")
                            }
                        }
                        return sb.toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MAC address", e)
        }
        return "00:00:00:00:00:00"
    }

    private fun generatePublicKey(): String {
        // Simplified - in production, generate a proper Ed25519 public key
        return "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
    }
}
