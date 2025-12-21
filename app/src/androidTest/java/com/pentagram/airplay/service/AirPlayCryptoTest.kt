package com.pentagram.airplay.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for AirPlayCrypto
 * Tests X25519/Ed25519 key generation, exchange, and signing
 */
@RunWith(AndroidJUnit4::class)
class AirPlayCryptoTest {

    private lateinit var crypto: AirPlayCrypto
    private lateinit var clientCrypto: AirPlayCrypto

    @Before
    fun setUp() {
        crypto = AirPlayCrypto()
        clientCrypto = AirPlayCrypto()
    }

    @Test
    fun testGenerateKeyPairCreatesKeys() {
        crypto.generateKeyPair()

        val publicKey = crypto.getPublicKeyBytes()

        assertNotNull(publicKey)
        assertEquals(32, publicKey.size)
        assertFalse(publicKey.all { it == 0.toByte() })
    }

    @Test
    fun testGetPublicKeyBytesAutoGenerates() {
        // Should auto-generate if not already done
        val publicKey = crypto.getPublicKeyBytes()

        assertNotNull(publicKey)
        assertEquals(32, publicKey.size)
    }

    @Test
    fun testKeyExchangeProducesSharedSecret() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        val serverPubKey = crypto.getPublicKeyBytes()
        val clientPubKey = clientCrypto.getPublicKeyBytes()

        // Perform key exchange on both sides
        val serverResult = crypto.performKeyExchange(clientPubKey)
        val clientResult = clientCrypto.performKeyExchange(serverPubKey)

        assertTrue(serverResult)
        assertTrue(clientResult)

        // Both should have derived the same shared secret
        val serverSecret = crypto.getSharedSecret()
        val clientSecret = clientCrypto.getSharedSecret()

        assertNotNull(serverSecret)
        assertNotNull(clientSecret)
        assertArrayEquals(serverSecret, clientSecret)
    }

    @Test
    fun testDeriveKeyProducesConsistentResults() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        val key1 = crypto.deriveKey("TestSalt", 16)
        val key2 = crypto.deriveKey("TestSalt", 16)

        assertArrayEquals(key1, key2)
    }

    @Test
    fun testDeriveKeyDifferentSaltsDifferentKeys() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        val key1 = crypto.deriveKey("Salt1", 16)
        val key2 = crypto.deriveKey("Salt2", 16)

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun testDeriveKeyDifferentLengths() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        val key16 = crypto.deriveKey("TestSalt", 16)
        val key32 = crypto.deriveKey("TestSalt", 32)

        assertEquals(16, key16.size)
        assertEquals(32, key32.size)
        // First 16 bytes should match
        assertArrayEquals(key16, key32.copyOf(16))
    }

    @Test
    fun testEncryptDecryptRoundTrip() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        val key = crypto.deriveKey("EncryptionKey", 16)
        val iv = ByteArray(16) { it.toByte() }
        val plaintext = "Hello, AirPlay!".toByteArray()

        val ciphertext = crypto.encrypt(plaintext, key, iv)
        val decrypted = crypto.decrypt(ciphertext, key, iv)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testEncryptProducesDifferentOutput() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        val key = crypto.deriveKey("EncryptionKey", 16)
        val iv = ByteArray(16) { it.toByte() }
        val plaintext = "Hello, AirPlay!".toByteArray()

        val ciphertext = crypto.encrypt(plaintext, key, iv)

        assertFalse(plaintext.contentEquals(ciphertext))
    }

    @Test
    fun testSetPersistentEd25519Key() {
        val seed = ByteArray(64) { it.toByte() }
        val publicKey = ByteArray(32) { (it + 100).toByte() }

        crypto.setPersistentEd25519Key(seed, publicKey)

        val retrievedPublicKey = crypto.getEd25519PublicKey()
        assertArrayEquals(publicKey, retrievedPublicKey)
    }

    @Test
    fun testSignAndVerify() {
        // Create two crypto instances to simulate server and client
        val serverCrypto = AirPlayCrypto()
        val clientCrypto = AirPlayCrypto()

        serverCrypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        val serverPubKey = serverCrypto.getPublicKeyBytes()
        val clientPubKey = clientCrypto.getPublicKeyBytes()

        // Server signs the verification message
        val signature = serverCrypto.signVerificationMessage(serverPubKey, clientPubKey)

        assertNotNull(signature)
        assertEquals(64, signature!!.size)

        // Client should be able to verify (using server's Ed25519 public key)
        val message = serverPubKey + clientPubKey
        val serverEd25519PubKey = serverCrypto.getEd25519PublicKey()

        val verified = clientCrypto.verifySignature(message, signature, serverEd25519PubKey)
        assertTrue(verified)
    }

    @Test
    fun testVerifySignatureFailsWithWrongMessage() {
        val serverCrypto = AirPlayCrypto()
        val clientCrypto = AirPlayCrypto()

        serverCrypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        val serverPubKey = serverCrypto.getPublicKeyBytes()
        val clientPubKey = clientCrypto.getPublicKeyBytes()

        val signature = serverCrypto.signVerificationMessage(serverPubKey, clientPubKey)

        // Try to verify with wrong message
        val wrongMessage = clientPubKey + serverPubKey  // Reversed order
        val serverEd25519PubKey = serverCrypto.getEd25519PublicKey()

        val verified = clientCrypto.verifySignature(wrongMessage, signature!!, serverEd25519PubKey)
        assertFalse(verified)
    }

    @Test
    fun testReset() {
        crypto.generateKeyPair()
        clientCrypto.generateKeyPair()

        crypto.performKeyExchange(clientCrypto.getPublicKeyBytes())

        assertNotNull(crypto.getSharedSecret())

        crypto.reset()

        assertNull(crypto.getSharedSecret())
    }
}
