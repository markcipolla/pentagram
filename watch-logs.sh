#!/bin/bash

# Quick log viewer for Pentagram AirPlay receiver
# Monitors filtered logs without deploying or restarting

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

LOG_TAG="Pentagram|AirPlay|MainActivity|VideoStream|FairPlay|MirrorBuffer"

# Parse arguments
CLEAR_LOGS=false
FILTER=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --clear|-c)
            CLEAR_LOGS=true
            shift
            ;;
        --filter|-f)
            FILTER="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -c, --clear       Clear logs before watching"
            echo "  -f, --filter TEXT Additional grep filter"
            echo "  -h, --help        Show this help"
            echo ""
            echo "Examples:"
            echo "  $0                      # Watch all Pentagram logs"
            echo "  $0 --clear              # Clear then watch"
            echo "  $0 --filter \"ERROR\"     # Only show lines with ERROR"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${BLUE}════════════════════════════════════${NC}"
echo -e "${BLUE}Pentagram Log Viewer${NC}"
echo -e "${BLUE}════════════════════════════════════${NC}"
echo ""

# Clear logs if requested
if [ "$CLEAR_LOGS" = true ]; then
    echo -e "${YELLOW}Clearing logs...${NC}"
    adb logcat -c
    echo -e "${GREEN}✓ Logs cleared${NC}"
    echo ""
fi

echo -e "${GREEN}Monitoring logs...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""
echo -e "${BLUE}════════════════════════════════════${NC}"
echo ""

# Monitor logs
if [ -n "$FILTER" ]; then
    adb logcat -s "$LOG_TAG" | grep --line-buffered "$FILTER"
else
    adb logcat -s "$LOG_TAG"
fi
