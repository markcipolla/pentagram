package com.pentagram.airplay.service

import android.util.Log

/**
 * JNI wrapper for native AirPlay cryptography
 * Uses OpenSSL directly via C code for 100% compatibility with UxPlay
 */
class AirPlayCryptoNative {

    companion object {
        private const val TAG = "AirPlayCryptoNative"

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
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Test function to verify JNI is working
     */
    external fun testJNI(): String

    /**
     * Get native library version
     */
    external fun getVersion(): String
}
