package com.pentagram.airplay.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.pentagram.airplay.crypto.MirrorBufferDecryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Receives and decodes H.264 video stream from AirPlay client
 *
 * AirPlay uses TCP for video streaming (not RTP):
 * - Stream type 110 = Video
 * - Each packet has 4-byte length header (LITTLE-ENDIAN!)
 * - Followed by H.264 NAL units
 * - SPS/PPS headers sent first
 */
class VideoStreamReceiver(
    private val isScreenMirroring: Boolean,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val encryptionKey: ByteArray? = null,
    private val streamConnectionID: Long = 0,
    private val onDisconnected: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "VideoStreamReceiver"

        // H.264 NAL unit types
        private const val NAL_SLICE = 1
        private const val NAL_IDR = 5
        private const val NAL_SEI = 6
        private const val NAL_SPS = 7
        private const val NAL_PPS = 8
        private const val NAL_AUD = 9
    }

    private var surface: Surface? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var mediaCodec: MediaCodec? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = false

    // SPS and PPS data (needed for MediaCodec initialization)
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null
    private var codecInitialized = false

    // Native UxPlay mirror_buffer decryptor
    private var nativeDecryptor: MirrorBufferDecryptor? = null
    private var baseEncryptionKey: ByteArray? = null  // Keep for initialization

    /**
     * Set or update the surface for video rendering
     * Can be called after initialization when surface becomes available
     */
    fun setSurface(newSurface: Surface?) {
        Log.w(TAG, "═══════════════════════════════════════════════")
        Log.w(TAG, "setSurface() called!")
        Log.w(TAG, "  New surface: ${if (newSurface != null) "AVAILABLE ✅" else "NULL ❌"}")
        Log.w(TAG, "  Codec initialized: $codecInitialized")
        Log.w(TAG, "  Has SPS/PPS: ${spsData != null}/${ppsData != null}")
        Log.w(TAG, "═══════════════════════════════════════════════")

        surface = newSurface
        Log.w(TAG, "Surface updated: ${if (newSurface != null) "available" else "null"}")

        // If codec is already initialized, we need to reinitialize with the new surface
        if (codecInitialized && spsData != null && ppsData != null) {
            Log.w(TAG, "🔄 Codec was already initialized - REINITIALIZING with new surface...")
            try {
                mediaCodec?.stop()
                Log.w(TAG, "  → MediaCodec stopped")
                mediaCodec?.release()
                Log.w(TAG, "  → MediaCodec released")
                codecInitialized = false
                Log.w(TAG, "  → Calling initializeMediaCodec() with surface...")
                initializeMediaCodec()
                Log.w(TAG, "  ✅ MediaCodec reinitialized successfully with surface!")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error reinitializing MediaCodec", e)
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "⚠️  Codec NOT yet initialized (will use surface when it initializes)")
            Log.w(TAG, "     codecInitialized=$codecInitialized, has SPS=${spsData != null}, has PPS=${ppsData != null}")
        }
    }

    fun start(port: Int): Boolean {
        return try {
            Log.i(TAG, "Starting VideoStreamReceiver on port $port...")
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true  // Allow immediate port reuse
            serverSocket!!.bind(java.net.InetSocketAddress(port))
            isRunning = true

            // Initialize native decryptor with base key
            if (encryptionKey != null) {
                baseEncryptionKey = encryptionKey
                try {
                    nativeDecryptor = MirrorBufferDecryptor(encryptionKey)
                    // Initialize AES with streamConnectionID
                    nativeDecryptor!!.initAes(streamConnectionID)
                    Log.i(TAG, "✅ Native mirror_buffer decryptor initialized (key: ${encryptionKey.size} bytes, streamID: $streamConnectionID)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize native decryptor", e)
                    nativeDecryptor = null
                }
            } else {
                Log.w(TAG, "⚠️  No encryption key provided - video packets will not be decrypted")
            }

            Log.i(TAG, "═══════════════════════════════════════════════")
            Log.i(TAG, "Video Stream Receiver Started")
            Log.i(TAG, "═══════════════════════════════════════════════")
            Log.i(TAG, "Port: $port")
            Log.i(TAG, "Resolution: ${width}x${height}")
            Log.i(TAG, "Mode: ${if (isScreenMirroring) "Mirror" else "Extended Display"}")
            Log.i(TAG, "Encryption: ${if (nativeDecryptor != null) "ENABLED (Native UxPlay)" else "DISABLED"}")
            Log.i(TAG, "═══════════════════════════════════════════════")

            // Start accepting connections in background
            Log.i(TAG, "Launching acceptConnection coroutine...")
            scope.launch {
                try {
                    Log.i(TAG, "acceptConnection coroutine started")
                    acceptConnection()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in acceptConnection coroutine", e)
                }
            }
            Log.i(TAG, "VideoStreamReceiver start() completed successfully")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video receiver", e)
            false
        }
    }

    private suspend fun acceptConnection() {
        try {
            Log.i(TAG, "Waiting for video stream connection...")
            clientSocket = serverSocket?.accept()

            if (clientSocket != null) {
                Log.i(TAG, "✅ Video client connected: ${clientSocket!!.remoteSocketAddress}")
                receiveVideoStream(clientSocket!!.getInputStream())
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error accepting video connection", e)
            }
        }
    }

    private suspend fun receiveVideoStream(inputStream: InputStream) {
        val buffer = ByteArray(1024 * 128) // 128KB buffer
        var packetCount = 0

        try {
            // AirPlay video stream protocol:
            // Each packet consists of:
            // 1. 128-byte header (unencrypted)
            // 2. Variable-length payload (encrypted for video data)

            val header = ByteArray(128)

            while (isRunning && scope.isActive) {
                // Read 128-byte header
                var headerRead = 0
                while (headerRead < 128) {
                    val bytesRead = inputStream.read(header, headerRead, 128 - headerRead)
                    if (bytesRead == -1) {
                        if (headerRead == 0) {
                            Log.i(TAG, "Stream closed by client (clean EOF)")
                        } else {
                            Log.w(TAG, "Stream closed mid-header: got $headerRead/128 bytes")
                        }
                        break
                    }
                    headerRead += bytesRead
                }

                if (headerRead != 128) {
                    break
                }

                // Parse payload size from header bytes 0-3 (LITTLE-ENDIAN!)
                // UxPlay uses byteutils_get_int() which reads little-endian on most systems
                val payloadSize = ((header[3].toInt() and 0xFF) shl 24) or
                                  ((header[2].toInt() and 0xFF) shl 16) or
                                  ((header[1].toInt() and 0xFF) shl 8) or
                                  (header[0].toInt() and 0xFF)

                // Parse packet type from header bytes 4-5
                val packetType = header[4].toInt() and 0xFF
                val packetSubtype = header[5].toInt() and 0xFF

                if (payloadSize <= 0 || payloadSize > buffer.size) {
                    Log.w(TAG, "Invalid payload size: $payloadSize")
                    break
                }

                // Read payload data
                var totalRead = 0
                while (totalRead < payloadSize) {
                    val read = inputStream.read(buffer, totalRead, payloadSize - totalRead)
                    if (read < 0) {
                        Log.w(TAG, "Stream ended while reading payload: got $totalRead/$payloadSize bytes")
                        break
                    }
                    totalRead += read
                }

                if (totalRead != payloadSize) {
                    Log.w(TAG, "Incomplete payload: expected $payloadSize, got $totalRead")
                    break
                }

                // Decrypt payload if needed
                // Type 0x00 = encrypted video data
                // Type 0x01 = unencrypted SPS/PPS configuration (AVCC format)
                // Type 0x02/0x05 = unencrypted keepalive/reports
                val decryptedData = if (packetType == 0x00 && nativeDecryptor != null) {
                    // Encrypted video data - decrypt with native UxPlay implementation
                    try {
                        val inputData = buffer.copyOf(payloadSize)
                        nativeDecryptor!!.decrypt(inputData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption failed", e)
                        buffer.copyOf(payloadSize)
                    }
                } else {
                    // Unencrypted packet (SPS/PPS or keepalive)
                    buffer.copyOf(payloadSize)
                }

                // Process the packet based on type
                when (packetType) {
                    0x01 -> {
                        // Type 0x01 = unencrypted SPS/PPS in AVCC format
                        processAVCCConfigPacket(decryptedData, decryptedData.size)
                    }
                    0x00 -> {
                        // Type 0x00 = encrypted video data (H.264 NAL units)
                        processH264Packet(decryptedData, decryptedData.size)
                    }
                    0x05, 0x02 -> {
                        // Type 0x05 = statistics/feedback, Type 0x02 = keepalive
                        // Silently ignore
                    }
                    else -> {
                        Log.w(TAG, "Unknown packet type: 0x${"%02X".format(packetType)}")
                    }
                }
                packetCount++

                if (packetCount == 1 || packetCount % 100 == 0) {
                    Log.i(TAG, "Received $packetCount video packets")
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error receiving video stream", e)
            }
        } finally {
            Log.i(TAG, "Video stream ended. Total packets: $packetCount")

            // Notify listener that stream has disconnected
            if (isRunning) {
                Log.i(TAG, "Notifying disconnection callback...")
                onDisconnected?.invoke()
            }
        }
    }

    private fun processAVCCConfigPacket(data: ByteArray, length: Int) {
        // Parse AVCC format configuration packet (SPS/PPS)
        // Format: https://wiki.multimedia.cx/index.php/MPEG-4_Part_15
        //
        // Byte 0: version (always 1)
        // Byte 1: profile
        // Byte 2: profile compatibility
        // Byte 3: level
        // Byte 4: 6 bits reserved (111111) + 2 bits NAL length size minus 1
        // Byte 5: 3 bits reserved (111) + 5 bits number of SPS
        // Then for each SPS:
        //   2 bytes: SPS length
        //   N bytes: SPS data
        // Then:
        //   1 byte: number of PPS
        // Then for each PPS:
        //   2 bytes: PPS length
        //   N bytes: PPS data

        try {
            if (length < 7) {
                Log.e(TAG, "AVCC packet too short: $length bytes")
                return
            }

            val version = data[0].toInt() and 0xFF
            if (version != 1) {
                Log.w(TAG, "Unknown AVCC version: $version")
            }

            // Number of SPS NAL units (lower 5 bits of byte 5)
            val numSPS = data[5].toInt() and 0x1F
            Log.i(TAG, "AVCC: $numSPS SPS NAL unit(s)")

            var offset = 6
            var newSpsData: ByteArray? = null
            var newPpsData: ByteArray? = null

            // Parse SPS NAL units
            for (i in 0 until numSPS) {
                if (offset + 2 > length) {
                    Log.e(TAG, "AVCC: truncated SPS length at offset $offset")
                    return
                }

                val spsLength = ((data[offset].toInt() and 0xFF) shl 8) or
                               (data[offset + 1].toInt() and 0xFF)
                offset += 2

                if (offset + spsLength > length) {
                    Log.e(TAG, "AVCC: truncated SPS data at offset $offset (need $spsLength bytes)")
                    return
                }

                // Extract SPS NAL unit
                val sps = data.copyOfRange(offset, offset + spsLength)
                newSpsData = sps
                Log.i(TAG, "✅ Extracted SPS: $spsLength bytes")

                offset += spsLength
            }

            // Number of PPS NAL units
            if (offset >= length) {
                Log.e(TAG, "AVCC: no PPS data")
                return
            }

            val numPPS = data[offset].toInt() and 0xFF
            offset++
            Log.i(TAG, "AVCC: $numPPS PPS NAL unit(s)")

            // Parse PPS NAL units
            for (i in 0 until numPPS) {
                if (offset + 2 > length) {
                    Log.e(TAG, "AVCC: truncated PPS length at offset $offset")
                    return
                }

                val ppsLength = ((data[offset].toInt() and 0xFF) shl 8) or
                               (data[offset + 1].toInt() and 0xFF)
                offset += 2

                if (offset + ppsLength > length) {
                    Log.e(TAG, "AVCC: truncated PPS data at offset $offset (need $ppsLength bytes)")
                    return
                }

                // Extract PPS NAL unit
                val pps = data.copyOfRange(offset, offset + ppsLength)
                newPpsData = pps
                Log.i(TAG, "✅ Extracted PPS: $ppsLength bytes")

                offset += ppsLength
            }

            // Check if SPS/PPS has changed (indicates resolution change)
            if (codecInitialized && newSpsData != null && newPpsData != null) {
                val spsChanged = !newSpsData.contentEquals(spsData)
                val ppsChanged = !newPpsData.contentEquals(ppsData)

                if (spsChanged || ppsChanged) {
                    Log.w(TAG, "🔄 SPS/PPS changed - resolution change detected! Reinitializing codec...")
                    Log.w(TAG, "   SPS changed: $spsChanged")
                    Log.w(TAG, "   PPS changed: $ppsChanged")

                    // Stop and release the old codec
                    try {
                        mediaCodec?.stop()
                        mediaCodec?.release()
                        mediaCodec = null
                        codecInitialized = false
                        frameCount = 0
                        Log.w(TAG, "   → Old codec released")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing old codec", e)
                    }
                }
            }

            // Update SPS/PPS data
            spsData = newSpsData
            ppsData = newPpsData

            // Try to initialize codec now that we have SPS and PPS
            tryInitializeCodec()

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AVCC config packet", e)
        }
    }

    private fun processH264Packet(data: ByteArray, length: Int) {
        // Encrypted video packets use length-prefixed NAL units (4-byte big-endian length)
        // This is the format after AES decryption
        // Format: [4-byte big-endian length][NAL unit data]... repeat

        var offset = 0
        var nalCount = 0
        while (offset < length - 4) {
            // Read 4-byte big-endian NAL unit length
            val nalLength = ((data[offset].toInt() and 0xFF) shl 24) or
                           ((data[offset + 1].toInt() and 0xFF) shl 16) or
                           ((data[offset + 2].toInt() and 0xFF) shl 8) or
                           (data[offset + 3].toInt() and 0xFF)

            if (nalLength <= 0 || nalLength > length || offset + 4 + nalLength > length) {
                // Invalid NAL length
                Log.w(TAG, "Invalid NAL length: $nalLength at offset $offset (packet size: $length)")
                break
            }

            // NAL unit data starts after the 4-byte length
            val nalDataOffset = offset + 4

            // Get NAL unit type from first byte
            val nalHeader = data[nalDataOffset].toInt() and 0xFF
            val nalType = nalHeader and 0x1F

            // Check forbidden_zero_bit (first bit must be 0)
            if ((nalHeader and 0x80) != 0) {
                Log.w(TAG, "Invalid NAL unit: forbidden_zero_bit is 1")
                break
            }

            // Process this NAL unit
            processNALUnit(nalType, data, nalDataOffset, nalLength)

            // Move to next NAL unit
            offset = nalDataOffset + nalLength
            nalCount++
        }

        if (nalCount == 0) {
            Log.w(TAG, "NO NAL units found in $length bytes")
        }
    }

    private var frameCount = 0

    private fun processNALUnit(nalType: Int, data: ByteArray, offset: Int, length: Int) {
        when (nalType) {
            NAL_SPS -> {
                val newSpsData = data.copyOfRange(offset, offset + length)

                // Check if SPS has changed (indicates resolution change)
                if (codecInitialized && spsData != null && !newSpsData.contentEquals(spsData)) {
                    Log.w(TAG, "🔄 SPS changed - resolution change detected! Reinitializing codec...")

                    // Stop and release the old codec
                    try {
                        mediaCodec?.stop()
                        mediaCodec?.release()
                        mediaCodec = null
                        codecInitialized = false
                        frameCount = 0
                        Log.w(TAG, "   → Old codec released")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing old codec", e)
                    }
                }

                spsData = newSpsData
                Log.i(TAG, "Received SPS (Sequence Parameter Set): $length bytes")
                tryInitializeCodec()
            }
            NAL_PPS -> {
                val newPpsData = data.copyOfRange(offset, offset + length)

                // Check if PPS has changed (indicates resolution change)
                if (codecInitialized && ppsData != null && !newPpsData.contentEquals(ppsData)) {
                    Log.w(TAG, "🔄 PPS changed - resolution change detected! Reinitializing codec...")

                    // Stop and release the old codec
                    try {
                        mediaCodec?.stop()
                        mediaCodec?.release()
                        mediaCodec = null
                        codecInitialized = false
                        frameCount = 0
                        Log.w(TAG, "   → Old codec released")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing old codec", e)
                    }
                }

                ppsData = newPpsData
                Log.i(TAG, "Received PPS (Picture Parameter Set): $length bytes")
                tryInitializeCodec()
            }
            NAL_IDR, NAL_SLICE -> {
                // Video frame data
                if (codecInitialized) {
                    frameCount++
                    if (frameCount <= 5 || frameCount % 30 == 0) {
                        Log.i(TAG, "Decoding frame #$frameCount (NAL type: ${if (nalType == NAL_IDR) "IDR" else "SLICE"}, length: $length bytes)")
                    }
                    decodeFrame(data, offset, length, nalType == NAL_IDR)
                } else {
                    Log.w(TAG, "Received frame before codec initialized (NAL type: $nalType)")
                }
            }
            NAL_SEI -> {
                if (frameCount < 3) {
                    Log.d(TAG, "Received SEI (Supplemental Enhancement Information)")
                }
            }
            NAL_AUD -> {
                // Access Unit Delimiter - can ignore
            }
            else -> {
                if (frameCount < 10) {
                    Log.d(TAG, "Received NAL unit type: $nalType, length: $length")
                }
            }
        }
    }

    private fun tryInitializeCodec() {
        if (spsData != null && ppsData != null && !codecInitialized) {
            initializeMediaCodec()
        }
    }

    private fun initializeMediaCodec() {
        try {
            Log.i(TAG, "Initializing MediaCodec for H.264...")

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

            // Add SPS and PPS to format
            val csd0 = ByteBuffer.allocate(spsData!!.size + ppsData!!.size + 8)
            // Add SPS with start code
            csd0.put(byteArrayOf(0, 0, 0, 1))
            csd0.put(spsData!!)
            // Add PPS with start code
            csd0.put(byteArrayOf(0, 0, 0, 1))
            csd0.put(ppsData!!)
            csd0.flip()

            format.setByteBuffer("csd-0", csd0)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024) // 1MB

            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

            if (surface != null) {
                mediaCodec!!.configure(format, surface, null, 0)
            } else {
                Log.w(TAG, "No surface provided - decoder will run without output")
                mediaCodec!!.configure(format, null, null, 0)
            }

            mediaCodec!!.start()
            codecInitialized = true

            Log.i(TAG, "✅ MediaCodec initialized successfully!")
            Log.i(TAG, "   Codec: H.264 (AVC)")
            Log.i(TAG, "   Resolution: ${width}x${height}")
            Log.i(TAG, "   SPS size: ${spsData!!.size} bytes")
            Log.i(TAG, "   PPS size: ${ppsData!!.size} bytes")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaCodec", e)
            codecInitialized = false
        }
    }

    private fun decodeFrame(data: ByteArray, offset: Int, length: Int, isKeyFrame: Boolean) {
        try {
            val codec = mediaCodec ?: return

            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(10000) // 10ms timeout
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()

                    // Add start code before NAL unit
                    inputBuffer.put(byteArrayOf(0, 0, 0, 1))
                    inputBuffer.put(data, offset, length)

                    val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    codec.queueInputBuffer(inputBufferIndex, 0, length + 4, 0, flags)
                }
            }

            // Release output buffers
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)

            while (outputBufferIndex >= 0) {
                // Render to surface (if provided)
                codec.releaseOutputBuffer(outputBufferIndex, true)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame", e)
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping video stream receiver...")
        isRunning = false

        try {
            nativeDecryptor?.destroy()
            nativeDecryptor = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying native decryptor", e)
        }

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaCodec", e)
        }

        try {
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing client socket", e)
        }

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }

        scope.cancel()
        codecInitialized = false
        spsData = null
        ppsData = null

        Log.i(TAG, "Video stream receiver stopped")
    }
}
