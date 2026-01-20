# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Pentagram is an Android AirPlay receiver that allows streaming video from macOS and iOS devices to Android. It implements the AirPlay RTSP protocol with FairPlay encryption support using native C/C++ cryptography.

## Build and Development Commands

### Building and Deployment
```bash
# Build and deploy to connected Android device
./deploy.sh

# Build debug APK only
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

### Testing
```bash
# Quick test with manual screen mirroring trigger
./test-loop.sh

# Full automated test (recommended for development)
./full-test-loop.sh --auto-trigger --duration 60 --keep-logs

# Watch logs only (no deployment)
./watch-logs.sh --clear
```

### Logging
```bash
# View real-time filtered logs
./logs.sh

# Enhanced log viewer with filtering
./watch-logs.sh --filter "ERROR"

# Direct logcat access
adb logcat -s Pentagram:* AirPlay:* VideoStream:* FairPlay:*
```

### Running Tests
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Run specific test
./gradlew test --tests "com.pentagram.airplay.service.FairPlayDecryptTest"
```

## Architecture

### Core Flow
1. **AirPlayService** (foreground service) manages mDNS registration and server lifecycle
2. **AirPlayServer** implements RTSP protocol, handles pairing, encryption setup, stream management
3. Mac/iOS connects via RTSP to port 7000
4. Ed25519/Curve25519 key exchange for encrypted pairing
5. **VideoStreamReceiver** receives encrypted H.264 packets over TCP
6. Native C code (from RPiPlay) performs AES-CTR decryption
7. MediaCodec hardware decoder renders to Surface
8. **AirPlayReceiverActivity** displays full-screen video with proper aspect ratio

### Key Components

**Service Layer:**
- `AirPlayService.kt` - Foreground service managing mDNS and server lifecycle
- `AirPlayServer.kt` - RTSP server, pairing, session management
- `VideoStreamReceiver.kt` - TCP video stream reception, decryption, MediaCodec decoding

**Crypto Layer:**
- `AirPlayCrypto.kt` - Ed25519/Curve25519 pairing and key exchange
- `FairPlay.kt` - FairPlay protocol for video encryption key decryption
- `FairPlayDecrypt.kt` - FairPlay decryption tables and key derivation
- `MirrorBufferDecryptor.kt` - JNI wrapper for native AES-CTR decryption

**UI Layer:**
- `MainActivity.kt` - Entry point, start/stop AirPlay service
- `AirPlayReceiverActivity.kt` - Full-screen video display
- `AspectRatioSurfaceView.kt` - Custom SurfaceView maintaining video aspect ratio

**Native Layer (app/src/main/jni/):**
- `mirror_buffer.c` - AES-CTR video decryption (from RPiPlay)
- `crypto.c` - Core cryptographic operations
- `fairplay_*.c` - FairPlay cipher implementations
- `*_jni.c` - JNI bridges to Kotlin code

### Critical Implementation Details

**Video Stream Format:**
- AirPlay uses TCP for video (not RTP)
- Each packet has 4-byte LITTLE-ENDIAN length header
- Stream type 110 = Video
- H.264 NAL units: SPS/PPS sent first, then IDR/P-frames
- Resolution changes detected by SPS/PPS changes, triggers MediaCodec reinitialization

**Encryption:**
- Pairing: Ed25519 signatures + Curve25519 ECDH key exchange
- Video keys: FairPlay-decrypted, then hashed with ECDH shared secret using SHA-512
- Video stream: AES-CTR decryption using native code (mirror_buffer format from RPiPlay)
- Key derivation: `SHA-512(fairplayKey[16] + ecdhSecret[32])[0:16]`

**Native Crypto:**
- All performance-critical crypto operations in C/C++
- Uses BoringSSL/OpenSSL APIs via Conscrypt
- JNI bindings in `*_jni.c` files
- Native builds use CMake (see `app/src/main/jni/CMakeLists.txt`)

**Resolution Handling:**
- Activity orientation locked on connection start (prevents aspect ratio issues)
- MediaCodec automatically reinitialized when SPS/PPS changes detected
- AspectRatioSurfaceView handles dynamic surface resizing

## Technology Stack

- **Kotlin** - Primary language
- **Android NDK/CMake** - Native C/C++ builds
- **MediaCodec** - Hardware H.264 decoding
- **NsdManager** - mDNS service discovery
- **Conscrypt** - OpenSSL-based crypto provider
- **LazySodium** - libsodium bindings (Ed25519, Curve25519)
- **dd-plist** - Apple Property List parsing

## Common Development Patterns

### Typical Workflow
1. Make code changes
2. Run `./full-test-loop.sh --auto-trigger --keep-logs`
3. Review logs for errors/warnings
4. Verify video streaming works
5. Iterate

### Modifying Native Code
1. Edit files in `app/src/main/jni/`
2. CMake rebuilds automatically during `./gradlew assembleDebug`
3. Changes require app reinstall (native libs embedded in APK)

### Debugging Crypto Issues
- Check FairPlay initialization in logs
- Verify ECDH shared secret exists (pairing mode)
- Monitor `FairPlay:*` and `MirrorBuffer:*` log tags
- Native crashes: check `adb logcat | grep -i "fatal\|crash"`

### Testing on macOS
```bash
# Auto-trigger screen mirroring (requires accessibility permissions)
osascript trigger-airplay.scpt "Device Name"

# Manual: Control Center → Screen Mirroring → Select "⛧ [Device Name]"
```

## Build Configuration

- **Namespace:** `com.pentagram.airplay`
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35
- **Compile SDK:** 35
- **NDK Version:** 25.2.9519653
- **CMake Version:** 3.22.1
- **Kotlin:** 1.9.20
- **Java:** 1.8 (source/target compatibility)

### Release Builds
Release builds require `keystore.properties` file (not in git):
```properties
storeFile=path/to/keystore.jks
storePassword=***
keyAlias=***
keyPassword=***
```

See `keystore.properties.template` for format.

## Known Limitations

- Orientation locks when AirPlay starts (prevents aspect ratio issues during streaming)
- Audio streaming not implemented (video only)
- Requires same WiFi network (mDNS requirement)

## Attribution

This project incorporates GPL v3 licensed code from:
- **RPiPlay** - Mirror buffer decryption, packet handling
- **UxPlay** - Protocol insights, RTSP handling, extended display support

See NOTICE and LICENSE files for details.
