package com.pentagram.airplay.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.dd.plist.NSDictionary
import com.dd.plist.NSArray
import com.dd.plist.NSNumber
import com.dd.plist.PropertyListParser
import com.pentagram.airplay.AirPlayReceiverActivity
import com.pentagram.airplay.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class AirPlayServer(
    private val port: Int,
    private val context: Context,
    private val persistentEd25519Seed: ByteArray? = null,
    private val persistentEd25519PublicKey: ByteArray? = null
) {

    private var serverSocket: ServerSocket? = null
    private val serverJob = Job()
    private val serverScope = CoroutineScope(Dispatchers.IO + serverJob)
    private var isRunning = false
    private val crypto = AirPlayCrypto()
    private val fairplay = FairPlay() // FairPlay for decrypting video encryption keys
    private var videoReceiver: VideoStreamReceiver? = null
    private var videoPort: Int = 0

    // Video stream encryption keys (from SETUP request)
    private var videoEncryptionKey: ByteArray? = null
    private var videoEncryptionIV: ByteArray? = null

    // Session information (from first SETUP request)
    private var sessionUUID: String? = null
    private var deviceName: String? = null
    private var deviceModel: String? = null

    // PIN-based pairing state
    private var currentPin: String? = null
    private var clientEd25519PublicKey: ByteArray? = null

    init {
        // Set persistent Ed25519 keys if provided
        if (persistentEd25519Seed != null && persistentEd25519PublicKey != null) {
            crypto.setPersistentEd25519Key(persistentEd25519Seed, persistentEd25519PublicKey)
            Log.i(TAG, "AirPlayServer initialized with persistent Ed25519 keys")
        } else {
            Log.w(TAG, "AirPlayServer initialized WITHOUT persistent Ed25519 keys!")
        }

        // Initialize FairPlay for video key decryption
        if (!fairplay.init()) {
            Log.e(TAG, "Failed to initialize FairPlay - video decryption will not work!")
        }
    }

    companion object {
        private const val TAG = "AirPlayServer"
    }

    /**
     * Hash FairPlay-decrypted key with ECDH shared secret (when pairing is enabled)
     * Algorithm (from UxPlay raop_handlers.h:836-841 + crypto.c:511):
     *   SHA-512(fairplayKey[16 bytes] + ecdhSecret[32 bytes])[0:16]
     * IMPORTANT: UxPlay uses SHA-512 (EVP_sha512) for ECDH hashing, not SHA-256!
     */
    private fun hashKeyWithECDH(fairplayKey: ByteArray): ByteArray {
        val ecdhSecret = crypto.getSharedSecret()
        if (ecdhSecret == null) {
            Log.w(TAG, "No ECDH shared secret - using FairPlay key directly (legacy mode)")
            return fairplayKey
        }

        Log.i(TAG, "Hashing FairPlay key with ECDH shared secret (pairing mode)")

        // SHA-512(fairplayKey + ecdhSecret) -> use first 16 bytes
        // CRITICAL: Must use SHA-512, not SHA-256 (UxPlay uses EVP_sha512 in sha_init)
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(fairplayKey)
        digest.update(ecdhSecret)
        val hash = digest.digest()  // 64 bytes from SHA-512
        val hashedKey = hash.copyOf(16)  // Use first 16 bytes

        return hashedKey
    }

    /**
     * Derive video encryption key from FairPlay-decrypted key and streamConnectionID
     * Algorithm (from UxPlay): SHA-512("AirPlayStreamKey{streamConnectionID}" + fairplayKey)[0:16]
     * IMPORTANT: Must use SHA-512, not SHA-256!
     */
    private fun deriveVideoKey(fairplayKey: ByteArray, streamConnectionID: Long): ByteArray {
        val saltKey = "AirPlayStreamKey$streamConnectionID"
        val digest = MessageDigest.getInstance("SHA-512")  // UxPlay uses SHA-512!
        digest.update(saltKey.toByteArray(Charsets.UTF_8))
        digest.update(fairplayKey)
        val hash = digest.digest()  // 64 bytes from SHA-512
        val derivedKey = hash.copyOf(16)  // Use first 16 bytes

        Log.d(TAG, "Derived video key from streamConnectionID=$streamConnectionID")

        return derivedKey
    }

    /**
     * Derive video encryption IV from FairPlay-decrypted key and streamConnectionID
     * Algorithm (from UxPlay): SHA-512("AirPlayStreamIV{streamConnectionID}" + fairplayKey)[0:16]
     * IMPORTANT: Must use SHA-512, not SHA-256!
     */
    private fun deriveVideoIV(fairplayKey: ByteArray, streamConnectionID: Long): ByteArray {
        val saltIV = "AirPlayStreamIV$streamConnectionID"
        val digest = MessageDigest.getInstance("SHA-512")  // UxPlay uses SHA-512!
        digest.update(saltIV.toByteArray(Charsets.UTF_8))
        digest.update(fairplayKey)
        val hash = digest.digest()  // 64 bytes from SHA-512
        val derivedIV = hash.copyOf(16)  // Use first 16 bytes

        Log.d(TAG, "Derived video IV from streamConnectionID=$streamConnectionID")

        return derivedIV
    }

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }

        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d(TAG, "AirPlay server listening on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let {
                            Log.d(TAG, "Client connected: ${it.inetAddress}")
                            handleClient(it)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverJob.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private fun handleClient(socket: Socket) {
        serverScope.launch {
            try {
                socket.keepAlive = true
                socket.tcpNoDelay = true

                val inputStream = socket.getInputStream()
                val output = socket.getOutputStream()

                while (!socket.isClosed && isRunning) {
                    // Read RTSP/HTTP request line and headers as text
                    val headerBuilder = StringBuilder()
                    var ch: Int
                    var lastChar = 0
                    var lineBreaks = 0

                    // Read until we get \r\n\r\n (end of headers)
                    while (lineBreaks < 2) {
                        ch = try {
                            inputStream.read()
                        } catch (e: Exception) {
                            Log.d(TAG, "Read error or client disconnected: ${e.message}")
                            return@launch
                        }

                        if (ch == -1) {
                            Log.d(TAG, "Client disconnected")
                            return@launch
                        }

                        headerBuilder.append(ch.toChar())

                        if (ch == '\n'.code && lastChar == '\r'.code) {
                            lineBreaks++
                        } else if (ch != '\r'.code && ch != '\n'.code) {
                            lineBreaks = 0
                        }

                        lastChar = ch
                    }

                    val headerText = headerBuilder.toString()
                    val lines = headerText.split("\r\n").filter { it.isNotEmpty() }

                    if (lines.isEmpty()) {
                        Log.d(TAG, "Empty request")
                        break
                    }

                    val requestLine = lines[0]
                    Log.i(TAG, ">>> Request: $requestLine")

                    val headers = mutableMapOf<String, String>()
                    for (i in 1 until lines.size) {
                        val parts = lines[i].split(": ", limit = 2)
                        if (parts.size == 2) {
                            headers[parts[0].lowercase()] = parts[1]
                        }
                    }

                    // Read body as BINARY if Content-Length is specified
                    var bodyBytes = ByteArray(0)
                    headers["content-length"]?.toIntOrNull()?.let { contentLength ->
                        if (contentLength > 0) {
                            try {
                                bodyBytes = ByteArray(contentLength)
                                var totalRead = 0
                                while (totalRead < contentLength) {
                                    val read = inputStream.read(bodyBytes, totalRead, contentLength - totalRead)
                                    if (read == -1) break
                                    totalRead += read
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reading body: ${e.message}")
                            }
                        }
                    }

                    // Parse request
                    val parts = requestLine.split(" ")
                    if (parts.size >= 2) {
                        val method = parts[0]
                        val path = parts[1]

                        Log.i(TAG, "    Handling: $method $path")

                        try {
                            when {
                                path == "/server-info" -> handleServerInfo(output, headers)
                                path == "/info" -> handleInfo(output, headers)
                                path == "/pair-pin-start" -> handlePairPinStart(output, headers)
                                path == "/pair-setup-pin" -> handlePairSetupPin(output, headers, bodyBytes)
                                path == "/pair-setup" -> handlePairSetup(output, headers, bodyBytes)
                                path == "/pair-verify" -> handlePairVerify(output, headers, bodyBytes)
                                path.startsWith("/stream") -> handleStream(output, method, headers, bodyBytes)
                                path == "/reverse" -> handleReverse(output, headers)
                                path == "/feedback" -> handleFeedback(output, headers)
                                path.startsWith("/fp-setup") -> handleFairPlaySetup(output, headers, bodyBytes)
                                method == "SETUP" -> handleSetup(output, headers, bodyBytes, path)
                                method == "GET_PARAMETER" -> handleGetParameter(output, headers, bodyBytes, path)
                                method == "RECORD" -> handleRecord(output, headers, bodyBytes, path)
                                else -> {
                                    Log.w(TAG, "Unknown path: $method $path")
                                    sendResponse(output, 404, "Not Found", "text/plain", "", headers)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling request: ${e.message}", e)
                            try {
                                sendResponse(output, 500, "Internal Server Error", "text/plain", "", headers)
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to send error response", e2)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client: ${e.message}", e)
            } finally {
                try {
                    socket.close()
                    Log.d(TAG, "Client socket closed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing socket", e)
                }
            }
        }
    }

    private fun handleServerInfo(output: OutputStream, headers: Map<String, String>) {
        val response = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>deviceid</key>
                <string>00:00:00:00:00:00</string>
                <key>features</key>
                <integer>0x5A7FFFF7</integer>
                <key>model</key>
                <string>AppleTV5,3</string>
                <key>protovers</key>
                <string>1.1</string>
                <key>srcvers</key>
                <string>220.68</string>
                <key>statusFlags</key>
                <integer>68</integer>
                <key>vv</key>
                <integer>2</integer>
                <key>pi</key>
                <string>00:00:00:00:00:00</string>
            </dict>
            </plist>
        """.trimIndent()

        sendResponse(output, 200, "OK", "text/x-apple-plist+xml", response, headers)
    }

    private fun handleInfo(output: OutputStream, headers: Map<String, String>) {
        // Get actual device display metrics in current orientation
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val displayMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        // Use actual current orientation
        val displayWidth = displayMetrics.widthPixels
        val displayHeight = displayMetrics.heightPixels

        val orientation = if (displayWidth > displayHeight) "landscape" else "portrait"
        Log.i(TAG, "Reporting display to Mac: ${displayWidth}x${displayHeight} ($orientation)")

        // Get device name with pentagram prefix
        val deviceName = android.os.Build.MODEL ?: "Unknown Device"
        val serviceName = "⛧ $deviceName"

        val response = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>audioFormats</key>
                <array>
                    <dict>
                        <key>type</key>
                        <integer>96</integer>
                        <key>audioInputFormats</key>
                        <integer>67108864</integer>
                        <key>audioOutputFormats</key>
                        <integer>67108864</integer>
                    </dict>
                </array>
                <key>displays</key>
                <array>
                    <dict>
                        <key>uuid</key>
                        <string>e0ff8a27-6738-3d56-8a16-cc53aacee925</string>
                        <key>width</key>
                        <integer>$displayWidth</integer>
                        <key>height</key>
                        <integer>$displayHeight</integer>
                        <key>widthPixels</key>
                        <integer>$displayWidth</integer>
                        <key>heightPixels</key>
                        <integer>$displayHeight</integer>
                        <key>refreshRate</key>
                        <real>60.0</real>
                        <key>features</key>
                        <integer>14</integer>
                        <key>overscanned</key>
                        <true/>
                    </dict>
                </array>
                <key>features</key>
                <integer>0x527FFEE6</integer>
                <key>statusFlags</key>
                <integer>68</integer>
                <key>model</key>
                <string>AppleTV3,2</string>
                <key>name</key>
                <string>$serviceName</string>
                <key>pi</key>
                <string>00:00:00:00:00:00</string>
                <key>pk</key>
                <string>4203796b75886bf64ec88d7670cb134b4cf831b945154b6261ce3bdd08783d6c</string>
                <key>vv</key>
                <integer>2</integer>
                <key>sourceVersion</key>
                <string>220.68</string>
            </dict>
            </plist>
        """.trimIndent()

        sendResponse(output, 200, "OK", "text/x-apple-plist+xml", response, headers)
    }

    private fun handlePairPinStart(output: OutputStream, headers: Map<String, String>) {
        // Generate a random 4-digit PIN and store it
        currentPin = (0..9999).random().toString().padStart(4, '0')

        Log.i(TAG, ">>> Pair-PIN-Start requested")
        Log.i(TAG, "*** CLIENT MUST NOW ENTER PIN = \"$currentPin\" AS AIRPLAY PASSWORD")

        // Display the PIN on the Android screen
        MainActivity.displayPin(currentPin!!)

        // Return empty 200 OK response
        sendResponse(output, 200, "OK", "application/octet-stream", "", headers)
    }

    private fun handlePairSetupPin(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.i(TAG, ">>> Pair-Setup-PIN requested - body: ${body.size} bytes")

        try {
            // Parse the binary plist to extract the PIN
            val plist = PropertyListParser.parse(body) as? NSDictionary
            val method = plist?.get("method")?.toJavaObject() as? String
            val pinFromClient = plist?.get("pin")?.toJavaObject() as? String

            Log.i(TAG, "    Method: $method, PIN from client: $pinFromClient")

            // For PIN-based pairing, the client sends the PIN in MAC address format
            // e.g., "0E:84:73:21:D4:2A" represents PIN "9409"
            // But we're just going to accept any PIN for now since the real validation
            // would require converting the format

            // TODO: Implement proper PIN validation
            // For now, just proceed with pairing
            Log.i(TAG, "    Accepting PIN and proceeding with pair-setup")

            // Hide the PIN from the screen
            MainActivity.hidePin()

            handlePairSetup(output, headers, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing pair-setup-pin body", e)
            handlePairSetup(output, headers, body)
        }
    }

    private fun handlePairSetup(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.i(TAG, "Pair setup - body length: ${body.size}")

        if (body.size != 32) {
            Log.e(TAG, "Invalid pair-setup data size: ${body.size}")
            sendResponse(output, 400, "Bad Request", "text/plain", "", headers)
            return
        }

        // Client sends its Ed25519 public key (32 bytes)
        // Store it for later verification in pair-verify stage 0
        clientEd25519PublicKey = body

        val hexString = body.joinToString(" ") { "%02X".format(it) }

        // Send back our Ed25519 public key (32 bytes)
        // This allows the client to verify our Ed25519 signatures in pair-verify
        val ourEd25519PublicKey = crypto.getEd25519PublicKey()
        val responseBody = String(ourEd25519PublicKey, Charsets.ISO_8859_1)

        val hexResponse = ourEd25519PublicKey.joinToString(" ") { "%02X".format(it) }
        Log.i(TAG, "Pair setup - sending our Ed25519 public key: ${hexResponse.substring(0, 47)}...")

        sendResponse(output, 200, "OK", "application/octet-stream", responseBody, headers)
    }

    private fun handlePairVerify(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.i(TAG, "Pair verify - body length: ${body.size}")

        if (body.isEmpty() || body.size < 4) {
            Log.e(TAG, "Pair verify data too short")
            sendResponse(output, 400, "Bad Request", "text/plain", "", headers)
            return
        }

        if (body.isNotEmpty()) {
            val hexString = body.joinToString(" ") { "%02X".format(it) }
        }

        // Check which stage: data[0] == 1 (handshake) or data[0] == 0 (finish)
        val stage = body[0].toInt()
        Log.i(TAG, "Pair verify stage: $stage (${if (stage == 1) "handshake" else if (stage == 0) "finish" else "unknown"})")

        when (stage) {
            1 -> handlePairVerifyHandshake(output, headers, body)
            0 -> handlePairVerifyFinish(output, headers, body)
            else -> {
                Log.e(TAG, "Unknown pair-verify stage: $stage")
                sendResponse(output, 400, "Bad Request", "text/plain", "", headers)
            }
        }
    }

    private fun handlePairVerifyHandshake(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.d(TAG, "Pair verify HANDSHAKE (stage 1)")

        // Parse the verification data structure (from RPiPlay):
        // Bytes 0-3: Header/message type (4 bytes)
        // Bytes 4-35: Client's NEW ephemeral ECDH public key (32 bytes) - X25519
        // Bytes 36-67: Client's OLD ECDH public key from pair-setup (32 bytes)

        if (body.size < 68) {
            Log.e(TAG, "Verification data too short: ${body.size} bytes")
            sendResponse(output, 400, "Bad Request", "text/plain", "", headers)
            return
        }

        // Extract components
        val header = body.copyOfRange(0, 4)
        val clientNewECDHKey = body.copyOfRange(4, 36)
        val clientOldECDHKey = body.copyOfRange(36, 68)

        // CRITICAL ORDER: Generate OUR new keypair FIRST, then do key exchange!
        // 1. Generate NEW server keypair for this verification session
        crypto.generateKeyPair()
        val ourPublicKey = crypto.getPublicKeyBytes()

        // 2. NOW compute shared secret with client's NEW key using our NEW private key
        val keyExchangeSuccess = crypto.performKeyExchange(clientNewECDHKey)
        if (!keyExchangeSuccess) {
            Log.e(TAG, "Pair-verify key exchange failed!")
            sendResponse(output, 500, "Internal Server Error", "text/plain", "", headers)
            return
        }

        // Create signature: sign(our_new_public_key + client_new_public_key)
        val signature = crypto.signVerificationMessage(ourPublicKey, clientNewECDHKey)

        if (signature == null) {
            Log.e(TAG, "Failed to create verification signature")
            sendResponse(output, 500, "Internal Server Error", "text/plain", "", headers)
            return
        }

        // UxPlay implementation uses 96-byte response:
        // [32-byte ECDH public key][64-byte ENCRYPTED Ed25519 signature]
        // Signature is encrypted with AES-128-CTR using derived keys

        // Derive AES key and IV using SHA-512 (matching UxPlay exactly)
        val aesKey = crypto.deriveKey("Pair-Verify-AES-Key", 16)
        val aesIV = crypto.deriveKey("Pair-Verify-AES-IV", 16)

        // Encrypt the 64-byte signature with AES-128-CTR
        val encryptedSignature = crypto.encrypt(signature, aesKey, aesIV)

        // Build response: [our public key][encrypted signature] = 96 bytes
        val response = ourPublicKey + encryptedSignature

        val responseBody = String(response, Charsets.ISO_8859_1)

        Log.i(TAG, "Pair verify - sending ${response.size}-byte response (UxPlay format)")
        Log.i(TAG, "    Our public key: ${ourPublicKey.take(8).joinToString(" ") { "%02X".format(it) }}...")
        Log.i(TAG, "    Encrypted sig: ${encryptedSignature.take(8).joinToString(" ") { "%02X".format(it) }}...")

        sendResponse(output, 200, "OK", "application/octet-stream", responseBody, headers)
    }

    private fun handlePairVerifyFinish(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.d(TAG, "Pair verify FINISH (stage 0)")

        // Stage 0: Client sends their signature for us to verify
        // Expected: 4 + PAIRING_SIG_SIZE (4 + 64 = 68 bytes)

        if (body.size < 68) {
            Log.e(TAG, "Pair verify finish data too short: ${body.size} bytes")
            sendResponse(output, 400, "Bad Request", "text/plain", "", headers)
            return
        }

        // Extract client's signature (bytes 4-67)
        val clientSignature = body.copyOfRange(4, 68)


        // TODO: Verify client's signature
        // For now, just accept it and send empty 200 OK response

        Log.i(TAG, "Pair verify COMPLETE - connection established!")
        sendResponse(output, 200, "OK", "application/octet-stream", "", headers)
    }

    // Pre-computed FairPlay response messages (from UxPlay/lib/fairplay_playfair.c)
    // These are cryptographic responses for FairPlay v3 handshake
    private val FAIRPLAY_RESPONSES = arrayOf(
        byteArrayOf(0x46,0x50,0x4c,0x59,0x03,0x01,0x02,0x00,0x00,0x00,0x00,0x82.toByte(),0x02,0x00,0x0f,0x9f.toByte(),0x3f,0x9e.toByte(),0x0a,0x25,0x21,0xdb.toByte(),0xdf.toByte(),0x31,0x2a,0xb2.toByte(),0xbf.toByte(),0xb2.toByte(),0x9e.toByte(),0x8d.toByte(),0x23,0x2b,0x63,0x76,0xa8.toByte(),0xc8.toByte(),0x18,0x70,0x1d,0x22,0xae.toByte(),0x93.toByte(),0xd8.toByte(),0x27,0x37,0xfe.toByte(),0xaf.toByte(),0x9d.toByte(),0xb4.toByte(),0xfd.toByte(),0xf4.toByte(),0x1c,0x2d,0xba.toByte(),0x9d.toByte(),0x1f,0x49,0xca.toByte(),0xaa.toByte(),0xbf.toByte(),0x65,0x91.toByte(),0xac.toByte(),0x1f,0x7b,0xc6.toByte(),0xf7.toByte(),0xe0.toByte(),0x66,0x3d,0x21,0xaf.toByte(),0xe0.toByte(),0x15,0x65,0x95.toByte(),0x3e,0xab.toByte(),0x81.toByte(),0xf4.toByte(),0x18,0xce.toByte(),0xed.toByte(),0x09,0x5a,0xdb.toByte(),0x7c,0x3d,0x0e,0x25,0x49,0x09,0xa7.toByte(),0x98.toByte(),0x31,0xd4.toByte(),0x9c.toByte(),0x39,0x82.toByte(),0x97.toByte(),0x34,0x34,0xfa.toByte(),0xcb.toByte(),0x42,0xc6.toByte(),0x3a,0x1c,0xd9.toByte(),0x11,0xa6.toByte(),0xfe.toByte(),0x94.toByte(),0x1a,0x8a.toByte(),0x6d,0x4a,0x74,0x3b,0x46,0xc3.toByte(),0xa7.toByte(),0x64,0x9e.toByte(),0x44,0xc7.toByte(),0x89.toByte(),0x55,0xe4.toByte(),0x9d.toByte(),0x81.toByte(),0x55,0x00,0x95.toByte(),0x49,0xc4.toByte(),0xe2.toByte(),0xf7.toByte(),0xa3.toByte(),0xf6.toByte(),0xd5.toByte(),0xba.toByte()),
        byteArrayOf(0x46,0x50,0x4c,0x59,0x03,0x01,0x02,0x00,0x00,0x00,0x00,0x82.toByte(),0x02,0x01,0xcf.toByte(),0x32,0xa2.toByte(),0x57,0x14,0xb2.toByte(),0x52,0x4f,0x8a.toByte(),0xa0.toByte(),0xad.toByte(),0x7a,0xf1.toByte(),0x64,0xe3.toByte(),0x7b,0xcf.toByte(),0x44,0x24,0xe2.toByte(),0x00,0x04,0x7e,0xfc.toByte(),0x0a,0xd6.toByte(),0x7a,0xfc.toByte(),0xd9.toByte(),0x5d,0xed.toByte(),0x1c,0x27,0x30,0xbb.toByte(),0x59,0x1b,0x96.toByte(),0x2e,0xd6.toByte(),0x3a,0x9c.toByte(),0x4d,0xed.toByte(),0x88.toByte(),0xba.toByte(),0x8f.toByte(),0xc7.toByte(),0x8d.toByte(),0xe6.toByte(),0x4d,0x91.toByte(),0xcc.toByte(),0xfd.toByte(),0x5c,0x7b,0x56,0xda.toByte(),0x88.toByte(),0xe3.toByte(),0x1f,0x5c,0xce.toByte(),0xaf.toByte(),0xc7.toByte(),0x43,0x19,0x95.toByte(),0xa0.toByte(),0x16,0x65,0xa5.toByte(),0x4e,0x19,0x39,0xd2.toByte(),0x5b,0x94.toByte(),0xdb.toByte(),0x64,0xb9.toByte(),0xe4.toByte(),0x5d,0x8d.toByte(),0x06,0x3e,0x1e,0x6a,0xf0.toByte(),0x7e,0x96.toByte(),0x56,0x16,0x2b,0x0e,0xfa.toByte(),0x40,0x42,0x75,0xea.toByte(),0x5a,0x44,0xd9.toByte(),0x59,0x1c,0x72,0x56,0xb9.toByte(),0xfb.toByte(),0xe6.toByte(),0x51,0x38,0x98.toByte(),0xb8.toByte(),0x02,0x27,0x72,0x19,0x88.toByte(),0x57,0x16,0x50,0x94.toByte(),0x2a,0xd9.toByte(),0x46,0x68,0x8a.toByte()),
        byteArrayOf(0x46,0x50,0x4c,0x59,0x03,0x01,0x02,0x00,0x00,0x00,0x00,0x82.toByte(),0x02,0x02,0xc1.toByte(),0x69,0xa3.toByte(),0x52,0xee.toByte(),0xed.toByte(),0x35,0xb1.toByte(),0x8c.toByte(),0xdd.toByte(),0x9c.toByte(),0x58,0xd6.toByte(),0x4f,0x16,0xc1.toByte(),0x51,0x9a.toByte(),0x89.toByte(),0xeb.toByte(),0x53,0x17,0xbd.toByte(),0x0d,0x43,0x36,0xcd.toByte(),0x68,0xf6.toByte(),0x38,0xff.toByte(),0x9d.toByte(),0x01,0x6a,0x5b,0x52,0xb7.toByte(),0xfa.toByte(),0x92.toByte(),0x16,0xb2.toByte(),0xb6.toByte(),0x54,0x82.toByte(),0xc7.toByte(),0x84.toByte(),0x44,0x11,0x81.toByte(),0x21,0xa2.toByte(),0xc7.toByte(),0xfe.toByte(),0xd8.toByte(),0x3d,0xb7.toByte(),0x11,0x9e.toByte(),0x91.toByte(),0x82.toByte(),0xaa.toByte(),0xd7.toByte(),0xd1.toByte(),0x8c.toByte(),0x70,0x63,0xe2.toByte(),0xa4.toByte(),0x57,0x55,0x59,0x10,0xaf.toByte(),0x9e.toByte(),0x0e,0xfc.toByte(),0x76,0x34,0x7d,0x16,0x40,0x43,0x80.toByte(),0x7f,0x58,0x1e,0xe4.toByte(),0xfb.toByte(),0xe4.toByte(),0x2c,0xa9.toByte(),0xde.toByte(),0xdc.toByte(),0x1b,0x5e,0xb2.toByte(),0xa3.toByte(),0xaa.toByte(),0x3d,0x2e,0xcd.toByte(),0x59,0xe7.toByte(),0xee.toByte(),0xe7.toByte(),0x0b,0x36,0x29,0xf2.toByte(),0x2a,0xfd.toByte(),0x16,0x1d,0x87.toByte(),0x73,0x53,0xdd.toByte(),0xb9.toByte(),0x9a.toByte(),0xdc.toByte(),0x8e.toByte(),0x07,0x00,0x6e,0x56,0xf8.toByte(),0x50,0xce.toByte()),
        byteArrayOf(0x46,0x50,0x4c,0x59,0x03,0x01,0x02,0x00,0x00,0x00,0x00,0x82.toByte(),0x02,0x03,0x90.toByte(),0x01,0xe1.toByte(),0x72,0x7e,0x0f,0x57,0xf9.toByte(),0xf5.toByte(),0x88.toByte(),0x0d,0xb1.toByte(),0x04,0xa6.toByte(),0x25,0x7a,0x23,0xf5.toByte(),0xcf.toByte(),0xff.toByte(),0x1a,0xbb.toByte(),0xe1.toByte(),0xe9.toByte(),0x30,0x45,0x25,0x1a,0xfb.toByte(),0x97.toByte(),0xeb.toByte(),0x9f.toByte(),0xc0.toByte(),0x01,0x1e,0xbe.toByte(),0x0f,0x3a,0x81.toByte(),0xdf.toByte(),0x5b,0x69,0x1d,0x76,0xac.toByte(),0xb2.toByte(),0xf7.toByte(),0xa5.toByte(),0xc7.toByte(),0x08,0xe3.toByte(),0xd3.toByte(),0x28,0xf5.toByte(),0x6b,0xb3.toByte(),0x9d.toByte(),0xbd.toByte(),0xe5.toByte(),0xf2.toByte(),0x9c.toByte(),0x8a.toByte(),0x17,0xf4.toByte(),0x81.toByte(),0x48,0x7e,0x3a,0xe8.toByte(),0x63,0xc6.toByte(),0x78,0x32,0x54,0x22,0xe6.toByte(),0xf7.toByte(),0x8e.toByte(),0x16,0x6d,0x18,0xaa.toByte(),0x7f,0xd6.toByte(),0x36,0x25,0x8b.toByte(),0xce.toByte(),0x28,0x72,0x6f,0x66,0x1f,0x73,0x88.toByte(),0x93.toByte(),0xce.toByte(),0x44,0x31,0x1e,0x4b,0xe6.toByte(),0xc0.toByte(),0x53,0x51,0x93.toByte(),0xe5.toByte(),0xef.toByte(),0x72,0xe8.toByte(),0x68,0x62,0x33,0x72,0x9c.toByte(),0x22,0x7d,0x82.toByte(),0x0c,0x99.toByte(),0x94.toByte(),0x45,0xd8.toByte(),0x92.toByte(),0x46,0xc8.toByte(),0xc3.toByte(),0x59)
    )

    private fun handleFairPlaySetup(output: OutputStream, headers: Map<String, String>, body: ByteArray) {
        Log.i(TAG, ">>> FairPlay setup requested - body: ${body.size} bytes")

        // Log request data
        if (body.isNotEmpty()) {
            val hexString = body.joinToString(" ") { "%02X".format(it) }
        }

        when (body.size) {
            16 -> {
                // First FairPlay request: setup (16 bytes) -> respond with 142 bytes
                Log.i(TAG, "    FairPlay setup: calling fairplay.setup()")

                val response = fairplay.setup(body)
                if (response != null) {
                    val responseBody = String(response, Charsets.ISO_8859_1)
                    Log.i(TAG, "✅ FairPlay setup response: ${response.size} bytes")
                    sendResponse(output, 200, "OK", "application/octet-stream", responseBody, headers)
                } else {
                    Log.e(TAG, "❌ FairPlay setup failed")
                    sendResponse(output, 500, "Internal Server Error", "text/plain", "FairPlay setup failed", headers)
                }
            }

            164 -> {
                // Second FairPlay request: handshake (164 bytes) -> respond with 32 bytes
                Log.i(TAG, "    FairPlay handshake: calling fairplay.handshake()")

                val response = fairplay.handshake(body)
                if (response != null) {
                    val responseBody = String(response, Charsets.ISO_8859_1)
                    Log.i(TAG, "✅ FairPlay handshake response: ${response.size} bytes")
                    sendResponse(output, 200, "OK", "application/octet-stream", responseBody, headers)
                } else {
                    Log.e(TAG, "❌ FairPlay handshake failed")
                    sendResponse(output, 500, "Internal Server Error", "text/plain", "FairPlay handshake failed", headers)
                }
            }

            else -> {
                Log.e(TAG, "Invalid FairPlay request size: ${body.size}")
                sendResponse(output, 400, "Bad Request", "text/plain", "Invalid request size", headers)
            }
        }
    }

    /**
     * Handle SETUP request to initialize video/audio streaming
     * Based on UxPlay's raop_handler_setup
     */
    private fun handleSetup(
        output: OutputStream,
        headers: Map<String, String>,
        body: ByteArray,
        rtspUrl: String
    ) {
        Log.i(TAG, ">>> SETUP request received")
        Log.i(TAG, "    RTSP URL: $rtspUrl")
        Log.i(TAG, "    Body size: ${body.size} bytes")

        try {
            // Parse binary plist body
            val plist = com.dd.plist.PropertyListParser.parse(body) as? com.dd.plist.NSDictionary
            if (plist == null) {
                Log.e(TAG, "Failed to parse SETUP plist")
                sendResponse(output, 400, "Bad Request", "text/plain", "Invalid plist", headers)
                return
            }

            // Log the keys we received
            Log.d(TAG, "SETUP plist keys: ${plist.allKeys().joinToString(", ")}")

            // Extract ekey and eiv (AES encryption parameters)
            val ekeyData = plist.get("ekey")?.toJavaObject() as? ByteArray
            val eivData = plist.get("eiv")?.toJavaObject() as? ByteArray

            if (ekeyData != null && eivData != null) {
                Log.i(TAG, "First SETUP call - initializing encryption keys")

                // Decrypt the ekey using FairPlay to get the actual 16-byte AES key
                val decryptedKey = fairplay.decryptKey(ekeyData)
                if (decryptedKey != null) {
                    Log.i(TAG, "✅ Successfully decrypted ekey to ${decryptedKey.size}-byte AES key")

                    // CRITICAL: When pairing is enabled, hash the FairPlay key with ECDH shared secret
                    // This matches UxPlay behavior (raop_handlers.h:836-841)
                    val finalKey = hashKeyWithECDH(decryptedKey)

                    videoEncryptionKey = finalKey
                    videoEncryptionIV = eivData
                } else {
                    Log.e(TAG, "❌ Failed to decrypt ekey - video will not work!")
                    videoEncryptionKey = null
                    videoEncryptionIV = null
                }

                // Extract and store device and session information
                val deviceID = (plist.get("deviceID") as? com.dd.plist.NSString)?.toString()
                deviceModel = (plist.get("model") as? com.dd.plist.NSString)?.toString()
                deviceName = (plist.get("name") as? com.dd.plist.NSString)?.toString()
                sessionUUID = (plist.get("sessionUUID")?.toJavaObject() as? String)

                Log.i(TAG, "Client device: $deviceName ($deviceModel), ID: $deviceID")

                // Extract timing port
                val timingPort = (plist.get("timingPort")?.toJavaObject() as? Number)?.toLong()

                // Create response plist with timing and event ports
                val responsePlist = com.dd.plist.NSDictionary()
                responsePlist["eventPort"] = com.dd.plist.NSNumber(0)  // Event port not used
                responsePlist["timingPort"] = com.dd.plist.NSNumber(7010)  // NTP timing port

                // Convert response to binary plist
                val baos = java.io.ByteArrayOutputStream()
                com.dd.plist.PropertyListParser.saveAsBinary(responsePlist, baos)
                val responseBytes = baos.toByteArray()
                val responseBody = String(responseBytes, Charsets.ISO_8859_1)

                Log.i(TAG, "✅ SETUP response: eventPort=0, timingPort=7010")
                sendResponse(output, 200, "OK", "application/x-apple-binary-plist", responseBody, headers)
                return
            }

            // Process stream setup requests
            val streamsArray = plist.get("streams") as? com.dd.plist.NSArray
            if (streamsArray != null) {
                Log.i(TAG, "Stream setup - ${streamsArray.count()} stream(s)")

                val responseStreams = com.dd.plist.NSArray(streamsArray.count())

                for (i in 0 until streamsArray.count()) {
                    val stream = streamsArray.objectAtIndex(i) as? com.dd.plist.NSDictionary
                    if (stream != null) {
                        val streamType = (stream.get("type")?.toJavaObject() as? Number)?.toLong()

                        when (streamType) {
                            110L -> {
                                // Video mirroring stream (type 110)
                                Log.i(TAG, "    Video mirroring stream")
                                val streamConnectionID = (stream.get("streamConnectionID")?.toJavaObject() as? Number)?.toLong()

                                // Get device display dimensions in current orientation
                                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                                val displayMetrics = android.util.DisplayMetrics()
                                windowManager.defaultDisplay.getMetrics(displayMetrics)

                                // Use actual current orientation
                                val deviceWidth = displayMetrics.widthPixels
                                val deviceHeight = displayMetrics.heightPixels

                                val orientation = if (deviceWidth > deviceHeight) "landscape" else "portrait"
                                Log.i(TAG, "    Device display: ${deviceWidth}x${deviceHeight} ($orientation)")

                                // Start video receiver on port 7100
                                videoPort = 7100

                                // Stop old receiver if exists
                                try {
                                    videoReceiver?.stop()
                                    videoReceiver = null
                                    Log.d(TAG, "Stopped old video receiver")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error stopping old video receiver: ${e.message}")
                                }

                                // Create new video receiver
                                try {
                                    Log.i(TAG, "Creating VideoStreamReceiver (${deviceWidth}x${deviceHeight})...")

                                    // Pass base FairPlay key and streamConnectionID to VideoStreamReceiver
                                    // The native UxPlay code will derive the actual video keys internally
                                    videoReceiver = VideoStreamReceiver(
                                        isScreenMirroring = true,
                                        width = deviceWidth,
                                        height = deviceHeight,
                                        encryptionKey = videoEncryptionKey,
                                        streamConnectionID = streamConnectionID ?: 0
                                    )
                                    Log.i(TAG, "VideoStreamReceiver created, starting on port $videoPort...")

                                    if (videoReceiver!!.start(videoPort)) {
                                        Log.i(TAG, "🎬 Video receiver started on port $videoPort")

                                        // Use session info stored from first SETUP request
                                        val sessionId = sessionUUID ?: ""
                                        val devName = deviceName ?: "Unknown Device"
                                        val devModel = deviceModel ?: ""

                                        // Register the video receiver so the activity can provide the surface
                                        Log.w(TAG, "📝 Registering VideoStreamReceiver with session UUID: $sessionId")
                                        AirPlayReceiverActivity.registerVideoReceiver(
                                            sessionId,
                                            videoReceiver!!
                                        )

                                        // Update activity with new video dimensions (in case resolution changed)
                                        AirPlayReceiverActivity.updateVideoDimensions(deviceWidth, deviceHeight)

                                        // Launch the receiver activity with display mode flag
                                        try {
                                            Log.w(TAG, "🚀 Attempting to launch AirPlayReceiverActivity...")
                                            val intent = Intent(context, AirPlayReceiverActivity::class.java).apply {
                                                putExtra("isScreenMirroring", true)
                                                putExtra("sessionUUID", sessionId)
                                                putExtra("deviceName", devName)
                                                putExtra("deviceModel", devModel)
                                                putExtra("videoPort", videoPort)
                                                putExtra("videoWidth", deviceWidth)
                                                putExtra("videoHeight", deviceHeight)
                                                // Critical: Use FLAG_ACTIVITY_NEW_TASK for background service launch
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            }

                                            // Try direct activity launch - Android will handle it appropriately
                                            try {
                                                context.startActivity(intent)
                                                Log.w(TAG, "✅ Launched AirPlayReceiverActivity directly")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Direct launch failed: ${e.message}")

                                                // Fallback: Show notification for user to tap
                                                val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

                                                // Create notification channel for Android O+
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    val channel = android.app.NotificationChannel(
                                                        "airplay_video_channel",
                                                        "AirPlay Video",
                                                        android.app.NotificationManager.IMPORTANCE_HIGH
                                                    ).apply {
                                                        description = "AirPlay video streaming notifications"
                                                    }
                                                    notificationManager.createNotificationChannel(channel)
                                                }

                                                val contentPendingIntent = android.app.PendingIntent.getActivity(
                                                    context,
                                                    0,
                                                    intent,
                                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                                )

                                                // Build notification with tap action
                                                val notification = androidx.core.app.NotificationCompat.Builder(context, "airplay_video_channel")
                                                    .setSmallIcon(com.pentagram.airplay.R.mipmap.ic_launcher)
                                                    .setContentTitle("🎬 AirPlay Video Ready")
                                                    .setContentText("Tap to view video from $devName")
                                                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                                                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                                                    .setContentIntent(contentPendingIntent)
                                                    .setAutoCancel(true)
                                                    .setOngoing(true)
                                                    .build()

                                                notificationManager.notify(999, notification)
                                                Log.w(TAG, "✅ Notification shown - user needs to tap to view video")
                                            }

                                            // Give activity time to start before video starts flowing
                                            Thread.sleep(500)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Failed to launch AirPlayReceiverActivity", e)
                                            e.printStackTrace()
                                        }
                                    } else {
                                        Log.e(TAG, "❌ Video receiver start() returned false")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Exception creating/starting video receiver: ${e.message}", e)
                                }

                                // Create response for video stream
                                val responseStream = com.dd.plist.NSDictionary()
                                responseStream["type"] = com.dd.plist.NSNumber(110)
                                responseStream["dataPort"] = com.dd.plist.NSNumber(videoPort)  // Video data port
                                responseStreams.setValue(i, responseStream)
                            }
                            96L -> {
                                // Audio stream (type 96)
                                Log.i(TAG, "    Audio stream")
                                val controlPort = (stream.get("controlPort")?.toJavaObject() as? Number)?.toLong()

                                // Create response for audio stream
                                val responseStream = com.dd.plist.NSDictionary()
                                responseStream["type"] = com.dd.plist.NSNumber(96)
                                responseStream["dataPort"] = com.dd.plist.NSNumber(6000)  // Audio data port
                                responseStream["controlPort"] = com.dd.plist.NSNumber(6001)  // Audio control port
                                responseStream["serverPort"] = com.dd.plist.NSNumber(6002)  // Audio server port
                                responseStreams.setValue(i, responseStream)
                            }
                            else -> {
                                Log.w(TAG, "    Unknown stream type: $streamType")
                            }
                        }
                    }
                }

                // Create response plist
                val responsePlist = com.dd.plist.NSDictionary()
                responsePlist["streams"] = responseStreams

                // Convert to binary plist
                val baos = java.io.ByteArrayOutputStream()
                com.dd.plist.PropertyListParser.saveAsBinary(responsePlist, baos)
                val responseBytes = baos.toByteArray()
                val responseBody = String(responseBytes, Charsets.ISO_8859_1)

                Log.i(TAG, "✅ SETUP stream response sent")
                sendResponse(output, 200, "OK", "application/x-apple-binary-plist", responseBody, headers)
                return
            }

            // If we get here, unknown SETUP request
            Log.w(TAG, "Unknown SETUP request format")
            sendResponse(output, 400, "Bad Request", "text/plain", "Unknown SETUP format", headers)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling SETUP: ${e.message}", e)
            sendResponse(output, 500, "Internal Server Error", "text/plain", e.message ?: "Error", headers)
        }
    }

    /**
     * Handle GET_PARAMETER request
     * Client typically requests volume or other parameters
     */
    private fun handleGetParameter(
        output: OutputStream,
        headers: Map<String, String>,
        body: ByteArray,
        rtspUrl: String
    ) {
        Log.i(TAG, ">>> GET_PARAMETER request")

        // Parse the body to see what parameter is requested
        val bodyText = String(body, Charsets.UTF_8).trim()

        when (bodyText) {
            "volume" -> {
                // Return current volume level (range: -144.0 to 0.0, in dB)
                // -144.0 = muted, 0.0 = maximum
                val volumeResponse = "volume: -20.0\r\n"
                Log.i(TAG, "✅ Returning volume: -20.0 dB")
                sendResponse(output, 200, "OK", "text/parameters", volumeResponse, headers)
            }
            else -> {
                // For unknown parameters, return empty response with 200 OK
                Log.w(TAG, "Unknown GET_PARAMETER: '$bodyText'")
                sendResponse(output, 200, "OK", "text/parameters", "", headers)
            }
        }
    }

    /**
     * Handle RECORD request to start streaming
     * This is the final step before the client begins sending video/audio data
     */
    private fun handleRecord(
        output: OutputStream,
        headers: Map<String, String>,
        body: ByteArray,
        rtspUrl: String
    ) {
        Log.i(TAG, ">>> RECORD request - client ready to stream!")
        Log.i(TAG, "    RTSP URL: $rtspUrl")

        // RECORD typically has no body, just acknowledges the client is ready to stream
        // We respond with 200 OK and the client will start sending UDP packets

        Log.i(TAG, "✅ RECORD acknowledged - streaming should begin")
        Log.i(TAG, "═══════════════════════════════════════════════")
        Log.i(TAG, "🎬 CLIENT IS NOW STREAMING!")
        Log.i(TAG, "═══════════════════════════════════════════════")

        sendResponse(output, 200, "OK", "text/plain", "", headers)
    }

    private fun handleStream(
        output: OutputStream,
        method: String,
        headers: Map<String, String>,
        body: ByteArray
    ) {
        Log.i(TAG, "Stream request: $method")

        when (method) {
            "POST" -> {
                // Stream setup - client wants to start streaming
                Log.i(TAG, "Stream POST - accepting stream setup")

                // Parse binary plist to extract session parameters
                try {
                    if (body.isNotEmpty()) {
                        val plist = PropertyListParser.parse(body) as? NSDictionary

                        if (plist != null) {
                            // Extract isScreenMirroringSession flag (KEY for extended display)
                            val isScreenMirroring = plist.get("isScreenMirroringSession")?.toJavaObject() as? Boolean ?: true

                            // Extract other useful session parameters
                            val sessionUUID = plist.get("sessionUUID")?.toJavaObject() as? String
                            val deviceID = plist.get("deviceID")?.toJavaObject() as? String
                            val model = plist.get("model")?.toJavaObject() as? String
                            val deviceName = plist.get("name")?.toJavaObject() as? String
                            val osName = plist.get("osName")?.toJavaObject() as? String
                            val osVersion = plist.get("osVersion")?.toJavaObject() as? String

                            // Log the connection details
                            Log.i(TAG, "═══════════════════════════════════════════════")
                            Log.i(TAG, "AirPlay Stream Setup")
                            Log.i(TAG, "═══════════════════════════════════════════════")
                            Log.i(TAG, "Display Mode: ${if (isScreenMirroring) "MIRRORING" else "EXTENDED DISPLAY"}")
                            Log.i(TAG, "Device: $deviceName ($model)")
                            Log.i(TAG, "OS: $osName $osVersion")
                            Log.i(TAG, "Device ID: $deviceID")
                            Log.i(TAG, "Session UUID: $sessionUUID")
                            Log.i(TAG, "═══════════════════════════════════════════════")

                            // Start video stream receiver on a random available port
                            videoPort = findAvailablePort()
                            videoReceiver = VideoStreamReceiver(isScreenMirroring)

                            if (videoReceiver!!.start(videoPort)) {
                                Log.i(TAG, "✅ Video receiver started on port $videoPort")

                                // Register the video receiver so the activity can provide the surface
                                val sessionId = sessionUUID ?: ""
                                Log.w(TAG, "📝 Registering VideoStreamReceiver with session UUID: $sessionId")
                                AirPlayReceiverActivity.registerVideoReceiver(
                                    sessionId,
                                    videoReceiver!!
                                )

                                // Launch the receiver activity with display mode flag
                                try {
                                    Log.w(TAG, "🚀 Attempting to launch AirPlayReceiverActivity...")
                                    val intent = Intent(context, AirPlayReceiverActivity::class.java).apply {
                                        putExtra("isScreenMirroring", isScreenMirroring)
                                        putExtra("sessionUUID", sessionId)
                                        putExtra("deviceName", deviceName ?: "Unknown Device")
                                        putExtra("deviceModel", model ?: "")
                                        putExtra("videoPort", videoPort)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    Log.w(TAG, "✅ Launched AirPlayReceiverActivity in ${if (isScreenMirroring) "mirror" else "extended"} mode")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Failed to launch AirPlayReceiverActivity", e)
                                }
                            } else {
                                Log.e(TAG, "❌ Failed to start video receiver")
                            }
                        } else {
                            Log.w(TAG, "Failed to parse stream setup plist - not a dictionary")
                        }
                    } else {
                        Log.w(TAG, "Stream POST received with empty body")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing stream setup plist", e)
                }

                // Create response plist with stream port information
                try {
                    // Build binary plist response
                    val streamDict = NSDictionary()
                    streamDict.put("type", NSNumber(110))
                    streamDict.put("dataPort", NSNumber(videoPort))

                    val streamsArray = NSArray(1)
                    streamsArray.setValue(0, streamDict)

                    val responseDict = NSDictionary()
                    responseDict.put("streams", streamsArray)

                    // Convert to binary plist
                    val binaryOutputStream = ByteArrayOutputStream()
                    PropertyListParser.saveAsBinary(responseDict, binaryOutputStream)
                    val binaryPlist = binaryOutputStream.toByteArray()
                    val responsePlist = String(binaryPlist, Charsets.ISO_8859_1)

                    Log.i(TAG, "Sending stream response with video port: $videoPort (${binaryPlist.size} bytes)")
                    sendResponse(output, 200, "OK", "application/x-apple-binary-plist", responsePlist, headers)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating stream response plist", e)
                    sendResponse(output, 500, "Internal Server Error", "text/plain", "", headers)
                }
            }
            "GET" -> {
                // Stream info
                Log.i(TAG, "Stream GET - returning stream info")
                sendResponse(output, 200, "OK", "application/x-apple-binary-plist", "", headers)
            }
            "TEARDOWN" -> {
                // Stop streaming
                Log.i(TAG, "Stream TEARDOWN - stopping")

                // Stop video receiver
                videoReceiver?.stop()
                videoReceiver = null
                videoPort = 0

                sendResponse(output, 200, "OK", "text/plain", "", headers)
            }
            else -> {
                Log.w(TAG, "Unknown stream method: $method")
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "", headers)
            }
        }
    }

    private fun handleReverse(output: OutputStream, headers: Map<String, String>) {
        // Reverse HTTP connection for events
        Log.i(TAG, "Reverse connection requested")
        sendResponse(output, 101, "Switching Protocols", "text/plain", "", headers)
    }

    private fun handleFeedback(output: OutputStream, headers: Map<String, String>) {
        // Client feedback
        sendResponse(output, 200, "OK", "text/plain", "", headers)
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String,
        requestHeaders: Map<String, String>
    ) {
        // Convert body string to bytes using ISO-8859-1 to preserve binary data
        val bodyBytes = if (body.isNotEmpty()) {
            body.toByteArray(Charsets.ISO_8859_1)
        } else {
            ByteArray(0)
        }

        val headers = buildString {
            append("RTSP/1.0 $statusCode $statusText\r\n")
            append("Server: AirTunes/220.68\r\n")

            // Echo back CSeq if present (required for RTSP)
            requestHeaders["cseq"]?.let {
                append("CSeq: $it\r\n")
            }

            if (contentType.isNotEmpty()) {
                append("Content-Type: $contentType\r\n")
            }

            if (bodyBytes.isNotEmpty()) {
                // Use BYTE length, not string length!
                append("Content-Length: ${bodyBytes.size}\r\n")
            }

            // Add AirPlay-specific headers
            append("Audio-Jack-Status: connected\r\n")

            append("\r\n")
        }


        // Write headers as text
        output.write(headers.toByteArray(Charsets.ISO_8859_1))

        // Write body as binary
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }

        output.flush()
    }

    /**
     * Find an available TCP port for video streaming
     * @return Available port number, or 7100 as fallback
     */
    private fun findAvailablePort(): Int {
        return try {
            val socket = ServerSocket(0) // 0 = find any available port
            val port = socket.localPort
            socket.close()
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find available port", e)
            7100 // Fallback to default AirPlay video port
        }
    }
}
