package com.pentagram.airplay.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Instrumented tests for FairPlay native library
 * Tests the FairPlay handshake and decryption functionality
 */
@RunWith(AndroidJUnit4::class)
class FairPlayTest {

    private lateinit var fairPlay: FairPlay

    @Before
    fun setUp() {
        fairPlay = FairPlay()
    }

    @After
    fun tearDown() {
        fairPlay.destroy()
    }

    @Test
    fun testInitializationSucceeds() {
        val result = fairPlay.init()
        assertTrue("FairPlay should initialize successfully", result)
    }

    @Test
    fun testDoubleInitializationSucceeds() {
        val result1 = fairPlay.init()
        val result2 = fairPlay.init()

        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun testSetupRequires16Bytes() {
        fairPlay.init()

        // Too short
        val shortRequest = ByteArray(15)
        val shortResult = fairPlay.setup(shortRequest)
        assertNull("Setup with 15 bytes should fail", shortResult)

        // Too long
        val longRequest = ByteArray(17)
        val longResult = fairPlay.setup(longRequest)
        assertNull("Setup with 17 bytes should fail", longResult)
    }

    @Test
    fun testSetupWithValidInput() {
        fairPlay.init()

        val request = ByteArray(16) { it.toByte() }
        val response = fairPlay.setup(request)

        assertNotNull("Setup should return response", response)
        assertEquals("Setup response should be 142 bytes", 142, response?.size ?: 0)
    }

    @Test
    fun testSetupWithoutInitFails() {
        // Don't call init()
        val request = ByteArray(16)
        val response = fairPlay.setup(request)

        assertNull("Setup without init should fail", response)
    }

    @Test
    fun testHandshakeRequires164Bytes() {
        fairPlay.init()

        // First do setup
        val setupRequest = ByteArray(16) { it.toByte() }
        fairPlay.setup(setupRequest)

        // Too short handshake
        val shortRequest = ByteArray(163)
        val shortResult = fairPlay.handshake(shortRequest)
        assertNull("Handshake with 163 bytes should fail", shortResult)

        // Too long handshake
        val longRequest = ByteArray(165)
        val longResult = fairPlay.handshake(longRequest)
        assertNull("Handshake with 165 bytes should fail", longResult)
    }

    @Test
    fun testDecryptRequires72Bytes() {
        fairPlay.init()

        // Too short
        val shortKey = ByteArray(71)
        val shortResult = fairPlay.decrypt(shortKey)
        assertNull("Decrypt with 71 bytes should fail", shortResult)

        // Too long should still work (uses first 72 bytes)
        val longKey = ByteArray(100)
        val longResult = fairPlay.decrypt(longKey)
        // May or may not succeed depending on state
    }

    @Test
    fun testDecryptWithoutSetupHandshake() {
        fairPlay.init()

        // Try decrypt without proper setup/handshake
        val encryptedKey = ByteArray(72)
        val result = fairPlay.decrypt(encryptedKey)

        // Should return something (using default state) or null
        // The exact behavior depends on the native implementation
    }

    @Test
    fun testDestroyAndReinit() {
        fairPlay.init()
        fairPlay.destroy()

        // Should be able to reinit after destroy
        val result = fairPlay.init()
        assertTrue("Should reinitialize after destroy", result)
    }
}
