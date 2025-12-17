#!/bin/bash

#
# Pentagram - Build and Deploy Script
# Builds the Android APK and deploys it to a connected device
#

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default configuration
JAVA_HOME_DEFAULT="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ANDROID_HOME_DEFAULT="$HOME/Library/Android/sdk"
BUILD_TYPE="Debug"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --release)
            BUILD_TYPE="Release"
            shift
            ;;
        --java-home)
            JAVA_HOME_DEFAULT="$2"
            shift 2
            ;;
        --android-home)
            ANDROID_HOME_DEFAULT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --release              Build release APK instead of debug"
            echo "  --java-home PATH       Set custom JAVA_HOME path"
            echo "  --android-home PATH    Set custom ANDROID_HOME path"
            echo "  --help                 Show this help message"
            echo ""
            echo "Default JAVA_HOME: $JAVA_HOME_DEFAULT"
            echo "Default ANDROID_HOME: $ANDROID_HOME_DEFAULT"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set environment variables
export JAVA_HOME="$JAVA_HOME_DEFAULT"
export ANDROID_HOME="$ANDROID_HOME_DEFAULT"
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║   Pentagram - Build & Deploy Script   ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""

# Verify Java installation
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo -e "${RED}✗ Error: Java not found at $JAVA_HOME${NC}"
    echo "Please install Android Studio or set --java-home to a valid JDK"
    exit 1
fi

echo -e "${GREEN}✓${NC} Java found: $(basename "$JAVA_HOME")"

# Verify Android SDK
if [ ! -d "$ANDROID_HOME" ]; then
    echo -e "${RED}✗ Error: Android SDK not found at $ANDROID_HOME${NC}"
    echo "Please install Android SDK or set --android-home"
    exit 1
fi

echo -e "${GREEN}✓${NC} Android SDK found: $ANDROID_HOME"
echo ""

# Build the APK
echo -e "${YELLOW}Building ${BUILD_TYPE} APK...${NC}"
echo ""

if [ "$BUILD_TYPE" == "Release" ]; then
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}✗ Build failed: APK not found at $APK_PATH${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ Build successful!${NC}"
echo -e "  APK location: $APK_PATH"
echo ""

# Check for connected devices
echo -e "${YELLOW}Checking for connected devices...${NC}"
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${YELLOW}⚠ No devices connected${NC}"
    echo "APK built successfully but not deployed."
    echo ""
    echo "To install manually:"
    echo "  adb install -r $APK_PATH"
    exit 0
fi

echo -e "${GREEN}✓${NC} Found $DEVICE_COUNT connected device(s)"
echo ""

# Deploy to device
echo -e "${YELLOW}Deploying to device...${NC}"
adb install -r "$APK_PATH"

echo ""
echo -e "${GREEN}✓ Deployment successful!${NC}"
echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          Deployment Complete!          ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo "Next steps:"
echo "  1. Open 'Pentagram' app on your device"
echo "  2. Tap 'Start AirPlay'"
echo "  3. On macOS: Control Center → Screen Mirroring → Pentagram"
echo ""
