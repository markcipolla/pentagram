#!/bin/bash

#
# Pentagram - Log Viewer
# View real-time logs from the Pentagram app
#

set -e

# Color codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

export PATH=$PATH:~/Library/Android/sdk/platform-tools

echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║      Pentagram - Live Log Viewer      ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Viewing logs for Pentagram app...${NC}"
echo "Press Ctrl+C to stop"
echo ""

# Clear logcat and start viewing logs
# Key components:
# - AirPlayService: mDNS service registration and lifecycle
# - AirPlayServer: RTSP protocol handling, pairing, stream setup
# - VideoStreamReceiver: H.264 video decoding and MediaCodec
# - AirPlayReceiverActivity: UI and surface management
# - AspectRatioSurfaceView: Video aspect ratio handling
adb logcat -c
adb logcat -s AirPlayService:* AirPlayServer:* MainActivity:* VideoStreamReceiver:* AirPlayReceiverActivity:* AspectRatioSurfaceView:* | while read line; do
    echo "$line"
done
