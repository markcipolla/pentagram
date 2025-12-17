#!/bin/bash

# Automated testing loop for Pentagram AirPlay receiver
# This script deploys the app, starts the service, monitors logs,
# and can trigger screen mirroring tests

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PACKAGE_NAME="com.pentagram.airplay"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"
LOG_TAG="Pentagram|AirPlay|MainActivity|VideoStream|FairPlay|MirrorBuffer"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Pentagram AirPlay Test Loop${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Check if device is connected
echo -e "${YELLOW}[1/7] Checking for connected Android device...${NC}"
DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l | tr -d ' ')
if [ "$DEVICE_COUNT" -eq "0" ]; then
    echo -e "${RED}Error: No Android device connected${NC}"
    echo "Please connect a device and enable USB debugging"
    exit 1
fi
DEVICE_NAME=$(adb shell getprop ro.product.model)
echo -e "${GREEN}✓ Connected to: $DEVICE_NAME${NC}"
echo ""

# Step 2: Build and deploy
echo -e "${YELLOW}[2/7] Building and deploying app...${NC}"
./deploy.sh
echo -e "${GREEN}✓ App deployed successfully${NC}"
echo ""

# Step 3: Clear logs
echo -e "${YELLOW}[3/7] Clearing logcat buffer...${NC}"
adb logcat -c
echo -e "${GREEN}✓ Logs cleared${NC}"
echo ""

# Step 4: Launch app
echo -e "${YELLOW}[4/7] Launching Pentagram app...${NC}"
adb shell am start -n $MAIN_ACTIVITY
sleep 2
echo -e "${GREEN}✓ App launched${NC}"
echo ""

# Step 5: Start AirPlay service
echo -e "${YELLOW}[5/7] Starting AirPlay service...${NC}"
# The app auto-starts the service, but we can verify it's running
sleep 3
SERVICE_RUNNING=$(adb shell dumpsys activity services | grep "AirPlayService" | wc -l | tr -d ' ')
if [ "$SERVICE_RUNNING" -gt "0" ]; then
    echo -e "${GREEN}✓ AirPlay service is running${NC}"
else
    echo -e "${RED}⚠ Warning: AirPlay service may not be running${NC}"
fi
echo ""

# Step 6: Get device info for screen mirroring
echo -e "${YELLOW}[6/7] Getting device information...${NC}"
DEVICE_IP=$(adb shell ip addr show wlan0 | grep "inet " | awk '{print $2}' | cut -d/ -f1)
DEVICE_MODEL=$(adb shell getprop ro.product.model)
echo -e "${GREEN}Device Name: ⛧ $DEVICE_MODEL${NC}"
echo -e "${GREEN}Device IP: $DEVICE_IP${NC}"
echo -e "${GREEN}AirPlay Port: 7000${NC}"
echo ""

# Step 7: Instructions and log monitoring
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Ready for Screen Mirroring Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Manual Steps:${NC}"
echo "1. On macOS: Open Control Center"
echo "2. Click 'Screen Mirroring'"
echo -e "3. Select '${GREEN}⛧ $DEVICE_MODEL${NC}'"
echo "4. Enter PIN if prompted (will appear in logs below)"
echo ""
echo -e "${YELLOW}Automated Steps:${NC}"
echo "- Logs are being monitored in real-time"
echo "- Press Ctrl+C to stop monitoring"
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Log Output (filtered):${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Monitor logs with filtering
adb logcat -s "$LOG_TAG"
