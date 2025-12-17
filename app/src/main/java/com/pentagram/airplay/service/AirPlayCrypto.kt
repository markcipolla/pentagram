package com.pentagram.airplay.service

import android.util.Log
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Sign
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles Curve25519/Ed25519 cryptography for AirPlay pairing
 * NOW USING LIBSODIUM - Native C crypto library compatible with OpenSSL!
 */
class AirPlayCrypto {

    companion object {
        private const val TAG = "AirPlayCrypto"
    }

    private val sodium = LazySodiumAndroid(SodiumAndroid())

    // X25519 keypair (32 bytes each)
    private var x25519PrivateKey: ByteArray? = null
    private var x25519PublicKey: ByteArray? = null

    // Ed25519 keypair (64 bytes seed, 32 bytes public)
    // These can be set as persistent keys from external storage
    private var ed25519Seed: ByteArray? = null
    private var ed25519PublicKey: ByteArray? = null

    // Shared secret after key exchange
    private var sharedSecret: ByteArray? = null

    /**
     * Set persistent Ed25519 keypair (loaded from storage)
     * This makes the server have a stable identity like UxPlay
     */
    fun setPersistentEd25519Key(seed: ByteArray, publicKey: ByteArray) {
        ed25519Seed = seed
        ed25519PublicKey = publicKey
        Log.d(TAG, "Persistent Ed25519 key loaded")
        Log.d(TAG, "    Public key: ${publicKey.take(8).joinToString(" ") { "%02X".format(it) }}...")
    }

    /**
     * Generate a new X25519 (Curve25519) keypair for key exchange
     * Uses Libsodium's native C implementation (compatible with OpenSSL!)
     *
     * Note: Only generates X25519 keys. Ed25519 keys should be persistent and set via setPersistentEd25519Key()
     */
    fun generateKeyPair() {
        Log.d(TAG, "Generating X25519 keypair with Libsodium (native C crypto)...")

        // Generate X25519 keypair using Libsodium
        // Note: Libsodium uses Box crypto which is X25519 + XSalsa20 + Poly1305
        // We just need the X25519 keys
        val x25519KeyPair = sodium.cryptoBoxKeypair()
        x25519PublicKey = x25519KeyPair.publicKey.asBytes
        x25519PrivateKey = x25519KeyPair.secretKey.asBytes

        Log.d(TAG, "X25519 keypair generated (Libsodium)")
        Log.d(TAG, "    Public key: ${x25519PublicKey!!.take(8).joinToString(" ") { "%02X".format(it) }}...")

        // Ed25519 keys are persistent and should already be set via setPersistentEd25519Key()
        if (ed25519Seed == null || ed25519PublicKey == null) {
            Log.w(TAG, "WARNING: Ed25519 keys not set! Generating temporary keys (should use persistent keys)")
            val ed25519KeyPair = sodium.cryptoSignKeypair()
            ed25519PublicKey = ed25519KeyPair.publicKey.asBytes
            ed25519Seed = ed25519KeyPair.secretKey.asBytes
        }
    }

    /**
     * Get our public key as bytes (32 bytes)
     */
    fun getPublicKeyBytes(): ByteArray {
        if (x25519PublicKey == null) {
            generateKeyPair()
        }
        return x25519PublicKey!!
    }

    /**
     * Get Ed25519 public key (for pair-setup response)
     * The client needs this to verify signatures in pair-verify
     */
    fun getEd25519PublicKey(): ByteArray {
        if (ed25519PublicKey == null) {
            throw IllegalStateException("Ed25519 public key not initialized")
        }
        return ed25519PublicKey!!
    }

    /**
     * Perform X25519 key exchange with the client's public key
     * Uses Libsodium's crypto_scalarmult (native X25519 ECDH)
     */
    fun performKeyExchange(clientPublicKeyBytes: ByteArray): Boolean {
        if (x25519PrivateKey == null) {
            Log.e(TAG, "Private key not initialized")
            return false
        }

        try {
            Log.d(TAG, "Performing X25519 key exchange (Libsodium)...")
            Log.d(TAG, "    Client public key: ${clientPublicKeyBytes.take(8).joinToString(" ") { "%02X".format(it) }}...")

            // Libsodium's crypto_scalarmult performs X25519 ECDH
            // This is the EXACT same operation as OpenSSL's EVP_PKEY_derive for X25519
            sharedSecret = ByteArray(Box.BEFORENMBYTES)  // 32 bytes

            val result = sodium.cryptoScalarMult(
                sharedSecret,
                x25519PrivateKey,
                clientPublicKeyBytes
            )

            if (!result) {
                Log.e(TAG, "X25519 key exchange failed!")
                return false
            }

            val hexSecret = sharedSecret!!.joinToString(" ") { "%02X".format(it) }
            Log.d(TAG, "Shared secret computed (Libsodium): ${hexSecret.substring(0, 47)}...")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Key exchange failed: ${e.message}", e)
            return false
        }
    }

    /**
     * Sign verification message for pair-verify step
     * Uses Libsodium's Ed25519 (native C implementation, compatible with OpenSSL!)
     */
    fun signVerificationMessage(
        ourPublicKey: ByteArray,
        clientPublicKey: ByteArray
    ): ByteArray? {
        if (ed25519Seed == null) {
            Log.e(TAG, "Signing key not initialized")
            return null
        }

        try {
            // Build the message to sign: our_x25519_pub + client_x25519_pub
            val message = ourPublicKey + clientPublicKey

            Log.d(TAG, "Signing with Libsodium Ed25519 (native C crypto)")
            Log.d(TAG, "    Message = ourKey(${ourPublicKey.size}) + clientKey(${clientPublicKey.size})")
            Log.d(TAG, "    FULL MESSAGE: ${message.joinToString(" ") { "%02X".format(it) }}")

            // Libsodium's crypto_sign_detached (Native API - works with raw bytes!)
            // This is EXACTLY the same as OpenSSL's EVP_DigestSign for Ed25519
            val signature = ByteArray(Sign.BYTES)  // 64 bytes

            val success = sodium.cryptoSignDetached(
                signature,
                message,
                message.size.toLong(),
                ed25519Seed
            )

            if (!success) {
                Log.e(TAG, "Ed25519 signature failed!")
                return null
            }

            Log.d(TAG, "Verification signature created (${signature.size} bytes)")
            Log.d(TAG, "    FULL SIGNATURE: ${signature.joinToString(" ") { "%02X".format(it) }}")

            return signature
        } catch (e: Exception) {
            Log.e(TAG, "Signature creation failed: ${e.message}", e)
            return null
        }
    }

    /**
     * Verify Ed25519 signature from client
     * Uses Libsodium's native verification
     */
    fun verifySignature(message: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        try {
            Log.d(TAG, "Verifying Ed25519 signature (Libsodium)")

            // Use Native API - works with raw bytes!
            val result = sodium.cryptoSignVerifyDetached(
                signature,
                message,
                message.size,
                publicKeyBytes
            )

            Log.d(TAG, "Signature verification: ${if (result) "SUCCESS" else "FAILED"}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error: ${e.message}", e)
            return false
        }
    }

    /**
     * Derive encryption key from shared secret using SHA-512
     * This matches the RPiPlay/UxPlay implementation:
     * key = SHA512(salt + shared_secret)[0:keylen]
     *
     * Uses Conscrypt/OpenSSL for SHA-512 (same as UxPlay!)
     */
    fun deriveKey(salt: String, keylen: Int): ByteArray {
        if (sharedSecret == null) {
            throw IllegalStateException("Shared secret not established")
        }

        // Concatenate salt string + shared secret bytes
        val saltBytes = salt.toByteArray(Charsets.UTF_8)
        val combined = saltBytes + sharedSecret!!

        // Hash with SHA-512 (using Conscrypt/OpenSSL provider)
        val digest = java.security.MessageDigest.getInstance("SHA-512")
        val hash = digest.digest(combined)

        // Return first keylen bytes
        return hash.copyOf(keylen)
    }

    /**
     * Encrypt data using AES-128-CTR
     * Uses Conscrypt/OpenSSL (same as UxPlay!)
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        Log.d(TAG, "AES-CTR Encryption (OpenSSL via Conscrypt):")
        Log.d(TAG, "    Input (${plaintext.size} bytes): ${plaintext.joinToString(" ") { "%02X".format(it) }}")
        Log.d(TAG, "    Key (16 bytes): ${key.copyOf(16).joinToString(" ") { "%02X".format(it) }}")
        Log.d(TAG, "    IV (16 bytes): ${iv.copyOf(16).joinToString(" ") { "%02X".format(it) }}")

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key.copyOf(16), "AES")
        val ivSpec = IvParameterSpec(iv.copyOf(16))

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val result = cipher.doFinal(plaintext)

        Log.d(TAG, "    Output (${result.size} bytes): ${result.joinToString(" ") { "%02X".format(it) }}")

        return result
    }

    /**
     * Decrypt data using AES-128-CTR
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key.copyOf(16), "AES")
        val ivSpec = IvParameterSpec(iv.copyOf(16))

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Get shared secret (for debugging/testing)
     */
    fun getSharedSecret(): ByteArray? {
        return sharedSecret
    }

    /**
     * Reset crypto state for new pairing session
     */
    fun reset() {
        x25519PrivateKey = null
        x25519PublicKey = null
        ed25519Seed = null
        ed25519PublicKey = null
        sharedSecret = null
        Log.d(TAG, "Crypto state reset")
    }
}
