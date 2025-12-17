package com.pentagram.airplay.service

import android.util.Log

/**
 * FairPlay wrapper using native C implementation from UxPlay
 * Handles decryption of the encrypted AES key (ekey) from SETUP requests
 */
class FairPlay {

    companion object {
        private const val TAG = "FairPlay"

        init {
            try {
                // Load Conscrypt's native library first to provide BoringSSL symbols
                System.loadLibrary("conscrypt_jni")
                Log.d(TAG, "Conscrypt native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Conscrypt not available, trying system crypto", e)
            }

            try {
                System.loadLibrary("airplay_crypto")
                Log.d(TAG, "Loaded airplay_crypto native library")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }
    }

    // Native methods
    private external fun nativeInit(): Int
    private external fun nativeSetup(request: ByteArray): ByteArray?
    private external fun nativeHandshake(request: ByteArray): ByteArray?
    private external fun nativeDecrypt(encryptedKey: ByteArray): ByteArray?
    private external fun nativeDestroy()

    private var initialized = false

    /**
     * Initialize FairPlay
     */
    fun init(): Boolean {
        if (initialized) {
            return true
        }

        val result = nativeInit()
        if (result == 0) {
            initialized = true
            Log.i(TAG, "FairPlay initialized")
            return true
        }

        Log.e(TAG, "FairPlay initialization failed")
        return false
    }

    /**
     * Handle fp-setup phase 1
     * Input: 16 bytes
     * Output: 142 bytes
     */
    fun setup(request: ByteArray): ByteArray? {
        if (!initialized) {
            Log.e(TAG, "FairPlay not initialized")
            return null
        }

        if (request.size != 16) {
            Log.e(TAG, "Invalid setup request size: ${request.size} (expected 16)")
            return null
        }

        val response = nativeSetup(request)
        if (response != null) {
            Log.i(TAG, "FairPlay setup successful (${response.size} bytes)")
        } else {
            Log.e(TAG, "FairPlay setup failed")
        }

        return response
    }

    /**
     * Handle fp-setup phase 2 (handshake)
     * Input: 164 bytes
     * Output: 32 bytes
     */
    fun handshake(request: ByteArray): ByteArray? {
        if (!initialized) {
            Log.e(TAG, "FairPlay not initialized")
            return null
        }

        if (request.size != 164) {
            Log.e(TAG, "Invalid handshake request size: ${request.size} (expected 164)")
            return null
        }

        val response = nativeHandshake(request)
        if (response != null) {
            Log.i(TAG, "FairPlay handshake successful (${response.size} bytes)")
        } else {
            Log.e(TAG, "FairPlay handshake failed")
        }

        return response
    }

    /**
     * Decrypt the encrypted AES key (ekey) from SETUP request
     * Input: 72-byte RSA-encrypted key
     * Output: 16-byte AES key (plaintext)
     */
    fun decryptKey(encryptedKey: ByteArray): ByteArray? {
        if (!initialized) {
            Log.e(TAG, "FairPlay not initialized")
            return null
        }

        if (encryptedKey.size != 72) {
            Log.e(TAG, "Invalid ekey size: ${encryptedKey.size} (expected 72)")
            return null
        }

        Log.d(TAG, "Decrypting 72-byte ekey to 16-byte AES key...")
        val aesKey = nativeDecrypt(encryptedKey)

        if (aesKey != null) {
            Log.i(TAG, "✅ FairPlay decrypt successful! Got 16-byte AES key")
            Log.d(TAG, "    AES key: ${aesKey.take(8).joinToString(" ") { "%02X".format(it) }}...")
        } else {
            Log.e(TAG, "❌ FairPlay decrypt failed")
        }

        return aesKey
    }

    /**
     * Cleanup
     */
    fun destroy() {
        if (initialized) {
            nativeDestroy()
            initialized = false
            Log.i(TAG, "FairPlay destroyed")
        }
    }
}
