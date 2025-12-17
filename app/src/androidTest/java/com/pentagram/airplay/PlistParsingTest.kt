package com.pentagram.airplay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Test to verify binary plist parsing works for AirPlay stream setup
 */
@RunWith(AndroidJUnit4::class)
class PlistParsingTest {

    @Test
    fun testMirrorModePlist() {
        // Simulate a mirror mode plist (isScreenMirroringSession = true)
        val plistXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>isScreenMirroringSession</key>
                <true/>
                <key>deviceID</key>
                <string>A6:36:29:21:B3:1D</string>
                <key>model</key>
                <string>Mac15,6</string>
                <key>name</key>
                <string>Test MacBook Pro</string>
                <key>sessionUUID</key>
                <string>12345678-1234-1234-1234-123456789012</string>
            </dict>
            </plist>
        """.trimIndent()

        val plist = PropertyListParser.parse(plistXml.toByteArray()) as NSDictionary

        val isScreenMirroring = plist.get("isScreenMirroringSession")?.toJavaObject() as? Boolean
        val deviceName = plist.get("name")?.toJavaObject() as? String
        val model = plist.get("model")?.toJavaObject() as? String

        assertEquals(true, isScreenMirroring)
        assertEquals("Test MacBook Pro", deviceName)
        assertEquals("Mac15,6", model)

        println("✅ Mirror mode test passed!")
        println("   isScreenMirroring: $isScreenMirroring")
        println("   Device: $deviceName ($model)")
    }

    @Test
    fun testExtendedDisplayPlist() {
        // Simulate extended display mode plist (isScreenMirroringSession = false)
        val plistXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>isScreenMirroringSession</key>
                <false/>
                <key>deviceID</key>
                <string>A6:36:29:21:B3:1D</string>
                <key>model</key>
                <string>Mac15,6</string>
                <key>name</key>
                <string>Test MacBook Pro</string>
                <key>sessionUUID</key>
                <string>12345678-1234-1234-1234-123456789012</string>
            </dict>
            </plist>
        """.trimIndent()

        val plist = PropertyListParser.parse(plistXml.toByteArray()) as NSDictionary

        val isScreenMirroring = plist.get("isScreenMirroringSession")?.toJavaObject() as? Boolean
        val deviceName = plist.get("name")?.toJavaObject() as? String
        val model = plist.get("model")?.toJavaObject() as? String

        assertEquals(false, isScreenMirroring)
        assertEquals("Test MacBook Pro", deviceName)
        assertEquals("Mac15,6", model)

        println("✅ Extended display mode test passed!")
        println("   isScreenMirroring: $isScreenMirroring")
        println("   Device: $deviceName ($model)")
    }
}
