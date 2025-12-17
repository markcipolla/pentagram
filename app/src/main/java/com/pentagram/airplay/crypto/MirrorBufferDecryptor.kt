package com.pentagram.airplay.crypto

import android.util.Log

/**
 * JNI wrapper for UxPlay's mirror_buffer video decryption
 *
 * This uses the native C implementation from UxPlay which handles:
 * - AES-CTR streaming cipher with proper block alignment
 * - Keystream buffer management across packet boundaries
 * - SHA-512 key derivation from streamConnectionID
 */
class MirrorBufferDecryptor(private val baseKey: ByteArray) {
    private var nativeHandle: Long = 0
    private var initialized = false

    companion object {
        private const val TAG = "MirrorBufferDecryptor"

        init {
            try {
                // Load Conscrypt's native library first to provide BoringSSL symbols
                System.loadLibrary("conscrypt_jni")
                Log.i(TAG, "Conscrypt native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Conscrypt not available, trying system crypto", e)
            }

            try {
                System.loadLibrary("airplay_crypto")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }
    }

    init {
        require(baseKey.size == 16) { "AES key must be 16 bytes" }
        nativeHandle = nativeInit(baseKey)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to initialize native mirror buffer")
        }
        Log.i(TAG, "Mirror buffer initialized")
    }

    /**
     * Initialize AES cipher with derived keys from streamConnectionID
     * Must be called before decrypt()
     */
    fun initAes(streamConnectionID: Long) {
        if (nativeHandle == 0L) {
            throw IllegalStateException("Native handle is invalid")
        }
        nativeInitAes(nativeHandle, streamConnectionID)
        initialized = true
        Log.i(TAG, "AES initialized for streamConnectionID: $streamConnectionID")
    }

    /**
     * Decrypt video packet data
     * Returns decrypted data (same length as input)
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        if (!initialized) {
            throw IllegalStateException("Must call initAes() before decrypt()")
        }
        if (nativeHandle == 0L) {
            throw IllegalStateException("Native handle is invalid")
        }
        return nativeDecrypt(nativeHandle, encryptedData)
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
            initialized = false
            Log.i(TAG, "Mirror buffer destroyed")
        }
    }

    protected fun finalize() {
        destroy()
    }

    // Native methods
    private external fun nativeInit(aeskey: ByteArray): Long
    private external fun nativeInitAes(handle: Long, streamConnectionID: Long)
    private external fun nativeDecrypt(handle: Long, input: ByteArray): ByteArray
    private external fun nativeDestroy(handle: Long)
}
