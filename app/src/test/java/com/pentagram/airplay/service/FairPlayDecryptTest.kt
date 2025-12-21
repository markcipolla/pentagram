package com.pentagram.airplay.service

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for FairPlayDecrypt
 * Tests the PlayFair decryption algorithm used for AirPlay key exchange
 */
class FairPlayDecryptTest {

    private val fairPlayDecrypt = FairPlayDecrypt()

    @Test
    fun testDecryptRequiresMinimum72Bytes() {
        val message3 = ByteArray(16)
        val shortCipherText = ByteArray(71) // Too short

        try {
            fairPlayDecrypt.decrypt(message3, shortCipherText)
            fail("Expected IllegalArgumentException for short ciphertext")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("72 bytes") == true)
        }
    }

    @Test
    fun testDecryptReturns16Bytes() {
        val message3 = ByteArray(16)
        val cipherText = ByteArray(72)

        val result = fairPlayDecrypt.decrypt(message3, cipherText)

        assertEquals(16, result.size)
    }

    @Test
    fun testDecryptDeterministic() {
        // Same input should produce same output
        val message3 = ByteArray(16) { it.toByte() }
        val cipherText = ByteArray(72) { (it * 2).toByte() }

        val result1 = fairPlayDecrypt.decrypt(message3, cipherText)
        val result2 = fairPlayDecrypt.decrypt(message3, cipherText)

        assertArrayEquals(result1, result2)
    }

    @Test
    fun testDecryptDifferentInputsDifferentOutputs() {
        val message3a = ByteArray(16) { 0x00.toByte() }
        val message3b = ByteArray(16) { 0xFF.toByte() }
        val cipherText = ByteArray(72) { it.toByte() }

        val result1 = fairPlayDecrypt.decrypt(message3a, cipherText)
        val result2 = fairPlayDecrypt.decrypt(message3b, cipherText)

        assertFalse(result1.contentEquals(result2))
    }

    @Test
    fun testDecryptWithLongerCipherText() {
        // Should work with ciphertext longer than 72 bytes
        val message3 = ByteArray(16)
        val cipherText = ByteArray(128) { it.toByte() }

        val result = fairPlayDecrypt.decrypt(message3, cipherText)

        assertEquals(16, result.size)
    }

    @Test
    fun testDecryptWithKnownTestVector() {
        // Test with a known input pattern to verify algorithm consistency
        val message3 = ByteArray(16) { i ->
            (0x10 + i).toByte()
        }
        val cipherText = ByteArray(72) { i ->
            when {
                i < 16 -> 0xAA.toByte()  // First 16 bytes (unused in chunks)
                i < 56 -> (0x10 + (i - 16)).toByte()  // chunk1: bytes 16-55
                else -> (0x20 + (i - 56)).toByte()    // chunk2: bytes 56-71
            }
        }

        val result = fairPlayDecrypt.decrypt(message3, cipherText)

        // Verify we get a 16-byte result
        assertEquals(16, result.size)
        // Verify it's not all zeros (algorithm did something)
        assertFalse(result.all { it == 0.toByte() })
    }
}
