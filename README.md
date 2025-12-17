# Pentagram ⛧

An Android AirPlay receiver that allows you to stream video from macOS and iOS devices to your Android device.

## Features

- **AirPlay Video Streaming**: Receive H.264 video streams from Apple devices
- **Screen Mirroring**: Mirror your Mac or iOS screen to Android
- **Extended Display Mode**: Use your Android device as a second display
- **Dynamic Resolution**: Automatically adapts when the sender changes resolution
- **Orientation Locking**: Maintains correct aspect ratio by locking to initial orientation
- **Zero Configuration**: Automatically advertises via mDNS/Bonjour
- **Encrypted Streaming**: Full FairPlay encryption support using native cryptography

## Requirements

- Android 8.0 (API 26) or higher
- macOS or iOS device on the same WiFi network
- Android NDK for building native components

## Quick Start

### Building and Deploying

```bash
# Build and deploy to connected Android device
./deploy.sh
```

### Viewing Logs

```bash
# View real-time logs from the app
./logs.sh

# Or use the enhanced log viewer
./watch-logs.sh

# Clear logs before watching
./watch-logs.sh --clear

# Filter for specific content
./watch-logs.sh --filter "ERROR"
```

### Using the App

1. Launch the Pentagram app on your Android device
2. Tap "Start AirPlay" to begin advertising
3. On macOS: Open Control Center → Screen Mirroring → Select "⛧ [Your Device Name]"
4. On iOS: Control Center → Screen Mirroring → Select "⛧ [Your Device Name]"

## Architecture

### Core Components

- **AirPlayService**: Foreground service that manages mDNS service registration and server lifecycle
- **AirPlayServer**: Implements the AirPlay RTSP protocol, including pairing, encryption setup, and stream management
- **VideoStreamReceiver**: Handles H.264 video stream reception, decryption, and MediaCodec decoding
- **AirPlayReceiverActivity**: Full-screen activity that displays the video stream
- **AspectRatioSurfaceView**: Custom SurfaceView that maintains proper video aspect ratio

### Native Components

The app includes native C/C++ libraries for performance-critical cryptographic operations:

- **FairPlay Decryption**: Native implementation of AES-CTR decryption for video streams
- **Playfair Cipher**: Historical cipher implementation (used for obfuscation)

## Technology Stack

### Android

- **Kotlin**: Primary development language
- **Android NDK**: Native cryptographic implementations
- **MediaCodec**: Hardware-accelerated H.264 video decoding
- **NsdManager**: mDNS service discovery and registration

### Cryptography

- **Conscrypt**: Modern TLS/SSL provider (OpenSSL-based)
- **LazySodium**: Kotlin bindings for libsodium (Ed25519, Curve25519)
- **dd-plist**: Apple Property List parsing

### Protocol Implementation

- **RTSP**: Real-Time Streaming Protocol for AirPlay communication
- **mDNS**: Service discovery via `_airplay._tcp`
- **H.264**: Video codec (AVC format)

## Acknowledgments

This project builds upon the excellent work of several open-source projects:

### Reference Implementations

- **[RPiPlay](https://github.com/FD-/RPiPlay)**: C implementation of AirPlay server for Raspberry Pi
  - Reference for AirPlay protocol implementation
  - Mirror buffer decryption logic
  - Packet structure and encryption handling

- **[UxPlay](https://github.com/FDH2/UxPlay)**: Unix AirPlay server
  - Additional protocol insights
  - Stream setup and RTSP handling
  - Extended display mode support

### Libraries and Dependencies

- **[Conscrypt](https://github.com/google/conscrypt)**: OpenSSL-based security provider for Android
- **[LazySodium](https://github.com/terl/lazysodium-android)**: Kotlin wrapper for libsodium
- **[libsodium](https://github.com/jedisct1/libsodium)**: Modern cryptography library (Ed25519, Curve25519)
- **[dd-plist](https://github.com/3breadt/dd-plist)**: Apple Property List parser for Java/Android
- **[Netty](https://netty.io/)**: Asynchronous networking framework

### Protocol Documentation

- Apple's AirPlay protocol (reverse-engineered by the community)
- [RAOP/AirTunes Protocol](https://nto.github.io/AirPlay.html)
- [AirPlay 2 Research](https://openairplay.github.io/airplay-spec/)

## Project Structure

```
pentagram/
├── app/src/main/
│   ├── java/com/pentagram/airplay/
│   │   ├── AirPlayReceiverActivity.kt    # Video display activity
│   │   ├── AspectRatioSurfaceView.kt     # Custom video surface
│   │   ├── MainActivity.kt               # App entry point
│   │   ├── crypto/
│   │   │   └── MirrorBufferDecryptor.kt  # JNI wrapper for decryption
│   │   └── service/
│   │       ├── AirPlayService.kt         # Foreground service
│   │       ├── AirPlayServer.kt          # RTSP server implementation
│   │       ├── AirPlayCrypto.kt          # Pairing and key exchange
│   │       ├── AirPlayCryptoNative.kt    # Native crypto JNI
│   │       ├── FairPlay.kt               # FairPlay protocol
│   │       ├── FairPlayDecrypt.kt        # FairPlay decryption tables
│   │       └── VideoStreamReceiver.kt    # Video stream handling
│   ├── jni/                              # Native C/C++ code
│   │   ├── mirror_buffer.c               # Video decryption (from RPiPlay)
│   │   └── playfair/                     # Playfair cipher implementation
│   └── res/                              # Android resources
├── deploy.sh                             # Build and deployment script
├── logs.sh                               # Log viewer script
└── README.md                             # This file
```

## Technical Details

### Video Pipeline

1. **Connection**: Mac/iOS initiates RTSP connection to port 7000
2. **Pairing**: Ed25519/Curve25519 key exchange for encrypted communication
3. **Stream Setup**: Negotiate video resolution, codec parameters
4. **Streaming**: Receive encrypted H.264 packets over TCP
5. **Decryption**: AES-CTR decryption using native code (from RPiPlay)
6. **Decoding**: MediaCodec hardware decoder renders to Surface
7. **Display**: AspectRatioSurfaceView maintains correct aspect ratio

### Resolution Handling

- Locks activity orientation on connection (prevents rotation issues)
- Detects SPS/PPS changes in stream (indicates resolution change)
- Reinitializes MediaCodec automatically when resolution changes
- Surface resizing handled by AspectRatioSurfaceView

### Encryption

- **Pairing**: Ed25519 signatures, Curve25519 ECDH key exchange
- **Video Stream**: AES-CTR with derived keys (mirror_buffer format)
- **Native Implementation**: Uses RPiPlay's proven decryption code

## Development

### Automated Testing

Pentagram includes automated testing scripts to close the feedback loop during development:

```bash
# Quick test with manual screen mirroring
./test-loop.sh

# Full automated test (deploys, triggers mirroring, monitors logs)
./full-test-loop.sh --auto-trigger --duration 60 --keep-logs

# Watch logs only (no deployment)
./watch-logs.sh --clear
```

See [TESTING.md](TESTING.md) for detailed testing documentation.

**Typical Development Workflow:**
1. Make code changes
2. Run `./full-test-loop.sh --auto-trigger --keep-logs`
3. Review logs and video streaming
4. Iterate

### Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd pentagram

# Build and install
./deploy.sh
```

### Debugging

```bash
# View filtered logs
./logs.sh

# Or view all logs
adb logcat
```

### Testing

1. Test with different resolutions on Mac (System Settings → Displays)
2. Test orientation locking (start in portrait vs landscape)
3. Test with both screen mirroring and extended display modes
4. Test with iOS devices (iPhone/iPad)

## Known Limitations

- Orientation is locked when AirPlay starts (by design, to prevent aspect ratio issues)
- Audio streaming not yet implemented
- Some macOS versions may require specific feature flags in mDNS advertisement

## Contributing

Contributions are welcome! Areas for improvement:

- Audio streaming support
- Better error handling and recovery
- Performance optimizations
- Support for additional AirPlay features

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0).

This means you are free to:
- Use this software for any purpose
- Study how it works and modify it
- Share copies with others
- Share your modifications with others

Under the following terms:
- Any modified versions must also be licensed under GPL v3
- You must make source code available when distributing
- You must preserve copyright and license notices

See the [LICENSE](LICENSE) file for the full license text.

### Third-Party Components

This project incorporates code from the following GPL v3 and MIT licensed projects:

- **[UxPlay](https://github.com/FDH2/UxPlay)** (GPL v3) - AirPlay server implementation
- **[RPiPlay](https://github.com/FD-/RPiPlay)** (GPL v3) - Mirror buffer decryption
- **playfair** (GPL v3) - Part of UxPlay
- **llhttp** (MIT) - HTTP parser, part of UxPlay

See the [NOTICE](NOTICE) file for detailed attribution and license information.

### About GPL v3

This project is free and open source software. "Free" refers to freedom, not price. You have the freedom to run, study, share, and modify this software. When you distribute copies or modifications, you must pass on these same freedoms to others by also licensing your version under GPL v3.

For more information about GPL v3 and your rights, visit: https://www.gnu.org/licenses/gpl-3.0.html

## Building from Source

### Prerequisites

- **Android Studio** (Arctic Fox or newer)
- **Android SDK** (API 26 or higher)
- **Android NDK** (for native components)
- **Java Development Kit** (JDK 11 or newer)
- **Git** (for cloning the repository)

### Build Instructions

1. **Clone the Repository**
   ```bash
   git clone https://github.com/markcipolla/pentagram.git
   cd pentagram
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned `pentagram` directory
   - Click "Open"

3. **Install NDK** (if not already installed)
   - In Android Studio: Tools → SDK Manager
   - Go to the "SDK Tools" tab
   - Check "NDK (Side by side)"
   - Click "Apply" to install

4. **Build the Project**

   **Option A: Using the deployment script**
   ```bash
   ./deploy.sh
   ```
   This will build and install the app on a connected Android device.

   **Option B: Using Android Studio**
   - Connect your Android device via USB (with USB debugging enabled)
   - Click the "Run" button (green triangle) in Android Studio
   - Select your device from the list

   **Option C: Using Gradle command line**
   ```bash
   # Build debug APK
   ./gradlew assembleDebug

   # Install to connected device
   ./gradlew installDebug

   # Build and install
   ./gradlew installDebug
   ```

5. **Build Output**
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `app/build/outputs/apk/release/app-release.apk`

### Installation Instructions

#### Installing on Android Device

**Method 1: Direct Installation via USB**
```bash
# Using deployment script
./deploy.sh

# Or using adb directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Method 2: Manual Installation**
1. Copy the APK file to your Android device
2. Open the APK file on your device
3. Grant permission to install from unknown sources if prompted
4. Tap "Install"

**Method 3: Android Studio**
1. Connect device via USB with USB debugging enabled
2. Click Run button in Android Studio
3. Select your device

### Enabling USB Debugging on Android

1. Go to Settings → About Phone
2. Tap "Build Number" 7 times to enable Developer Options
3. Go to Settings → Developer Options
4. Enable "USB Debugging"

### Verifying Installation

After installation:
1. Launch the Pentagram app
2. Tap "Start AirPlay"
3. Check that the service starts successfully
4. The device should appear in AirPlay menu on Mac/iOS devices

### Troubleshooting Build Issues

**NDK Not Found**
- Install NDK via Android Studio: Tools → SDK Manager → SDK Tools → NDK
- Or set `ANDROID_NDK_HOME` environment variable

**Gradle Build Failed**
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug
```

**Device Not Detected**
```bash
# Check device connection
adb devices

# Restart adb server if needed
adb kill-server
adb start-server
```

### Building Release Version

To build a release version for distribution:

1. **Create a Signing Key** (first time only)
   ```bash
   keytool -genkey -v -keystore pentagram-release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias pentagram-key
   ```

2. **Configure Signing** in `app/build.gradle.kts`

3. **Build Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

Note: For GPL v3 compliance, when distributing the app, you must also provide access to the complete source code and build instructions.

## Security Note

This is a research and development project. The AirPlay protocol implementation is based on reverse-engineering and community documentation. Use at your own risk on trusted networks only.
