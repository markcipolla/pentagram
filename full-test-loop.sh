#!/bin/bash

# Full automated testing loop for Pentagram AirPlay receiver
# This script deploys, starts the service, triggers screen mirroring,
# and monitors logs for the complete feedback loop

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
PACKAGE_NAME="com.pentagram.airplay"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"
LOG_TAG="Pentagram|AirPlay|MainActivity|VideoStream|FairPlay|MirrorBuffer"
LOG_FILE="pentagram-test-$(date +%Y%m%d-%H%M%S).log"

# Parse command line arguments
AUTO_TRIGGER=false
DURATION=30
KEEP_LOGS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --auto-trigger)
            AUTO_TRIGGER=true
            shift
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --keep-logs)
            KEEP_LOGS=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --auto-trigger    Automatically trigger screen mirroring (requires AppleScript)"
            echo "  --duration N      Monitor logs for N seconds (default: 30)"
            echo "  --keep-logs       Save logs to file"
            echo "  --help            Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${MAGENTA}╔════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║  Pentagram AirPlay Full Test Loop     ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Check if device is connected
echo -e "${YELLOW}[1/8] Checking for connected Android device...${NC}"
DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l | tr -d ' ')
if [ "$DEVICE_COUNT" -eq "0" ]; then
    echo -e "${RED}✗ Error: No Android device connected${NC}"
    echo "  Please connect a device and enable USB debugging"
    exit 1
fi
DEVICE_NAME=$(adb shell getprop ro.product.model | tr -d '\r')
echo -e "${GREEN}✓ Connected to: $DEVICE_NAME${NC}"
echo ""

# Step 2: Build and deploy
echo -e "${YELLOW}[2/8] Building and deploying app...${NC}"
if ./deploy.sh > /dev/null 2>&1; then
    echo -e "${GREEN}✓ App deployed successfully${NC}"
else
    echo -e "${RED}✗ Deployment failed${NC}"
    exit 1
fi
echo ""

# Step 3: Clear logs
echo -e "${YELLOW}[3/8] Clearing logcat buffer...${NC}"
adb logcat -c
echo -e "${GREEN}✓ Logs cleared${NC}"
echo ""

# Step 4: Launch app
echo -e "${YELLOW}[4/8] Launching Pentagram app...${NC}"
adb shell am start -n $MAIN_ACTIVITY > /dev/null 2>&1
sleep 2
echo -e "${GREEN}✓ App launched${NC}"
echo ""

# Step 5: Start AirPlay service (verify)
echo -e "${YELLOW}[5/8] Verifying AirPlay service...${NC}"
sleep 3
SERVICE_RUNNING=$(adb shell dumpsys activity services | grep "AirPlayService" | wc -l | tr -d ' ')
if [ "$SERVICE_RUNNING" -gt "0" ]; then
    echo -e "${GREEN}✓ AirPlay service is running${NC}"
else
    echo -e "${RED}⚠ Warning: AirPlay service may not be running${NC}"
fi
echo ""

# Step 6: Get device info
echo -e "${YELLOW}[6/8] Getting device information...${NC}"
DEVICE_IP=$(adb shell ip addr show wlan0 | grep "inet " | awk '{print $2}' | cut -d/ -f1 | tr -d '\r')
DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
AIRPLAY_NAME="⛧ $DEVICE_MODEL"

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║ Device Information                     ║${NC}"
echo -e "${BLUE}╠════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║${NC} Name:  ${GREEN}$AIRPLAY_NAME${NC}"
echo -e "${BLUE}║${NC} IP:    ${GREEN}$DEVICE_IP${NC}"
echo -e "${BLUE}║${NC} Port:  ${GREEN}7000${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Step 7: Trigger screen mirroring (if auto mode)
if [ "$AUTO_TRIGGER" = true ]; then
    echo -e "${YELLOW}[7/8] Triggering screen mirroring automatically...${NC}"
    if [ -f "./trigger-airplay.scpt" ]; then
        RESULT=$(osascript trigger-airplay.scpt "$DEVICE_MODEL" 2>&1)
        if [[ $RESULT == *"Success"* ]]; then
            echo -e "${GREEN}✓ Screen mirroring triggered${NC}"
        else
            echo -e "${RED}⚠ Failed to trigger: $RESULT${NC}"
            echo -e "${YELLOW}  Please manually select the device in Control Center${NC}"
        fi
    else
        echo -e "${RED}⚠ AppleScript not found${NC}"
        echo -e "${YELLOW}  Please manually select the device in Control Center${NC}"
    fi
else
    echo -e "${YELLOW}[7/8] Manual screen mirroring required...${NC}"
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║ Please connect manually:               ║${NC}"
    echo -e "${BLUE}╠════════════════════════════════════════╣${NC}"
    echo -e "${BLUE}║${NC} 1. Open Control Center on macOS"
    echo -e "${BLUE}║${NC} 2. Click 'Screen Mirroring'"
    echo -e "${BLUE}║${NC} 3. Select '${GREEN}$AIRPLAY_NAME${NC}'"
    echo -e "${BLUE}║${NC} 4. Enter PIN if prompted"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
fi
echo ""

# Step 8: Monitor logs
echo -e "${YELLOW}[8/8] Monitoring logs...${NC}"
echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║ Log Output (filtered)                  ║${NC}"
echo -e "${BLUE}╠════════════════════════════════════════╣${NC}"
if [ "$KEEP_LOGS" = true ]; then
    echo -e "${BLUE}║${NC} Saving to: ${GREEN}$LOG_FILE${NC}"
fi
echo -e "${BLUE}║${NC} Duration: ${GREEN}${DURATION}s${NC} (Ctrl+C to stop)"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Start log monitoring
if [ "$KEEP_LOGS" = true ]; then
    # Save to file and display
    timeout "$DURATION" adb logcat -s "$LOG_TAG" 2>&1 | tee "$LOG_FILE" &
    LOG_PID=$!
else
    # Just display
    timeout "$DURATION" adb logcat -s "$LOG_TAG" &
    LOG_PID=$!
fi

# Handle Ctrl+C gracefully
trap "kill $LOG_PID 2>/dev/null; echo ''; echo -e '${YELLOW}Test stopped${NC}'; exit 0" INT

# Wait for log monitoring to finish
wait $LOG_PID 2>/dev/null

echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║ Test completed                         ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"

if [ "$KEEP_LOGS" = true ]; then
    echo -e "${BLUE}Logs saved to: $LOG_FILE${NC}"
fi
