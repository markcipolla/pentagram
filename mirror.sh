#!/bin/bash
#
# mirror.sh - Start or stop AirPlay screen mirroring to a named device
#
# Usage:
#   ./mirror.sh "Living Room TV"
#   ./mirror.sh --stop
#   ./mirror.sh --list
#
# Requires: Accessibility permissions for Terminal/iTerm in
#   System Settings > Privacy & Security > Accessibility

set -euo pipefail

usage() {
  echo "Usage: $0 <device-name> | --stop | --list"
  echo ""
  echo "  <device-name>  Name of the AirPlay device to mirror to"
  echo "  --stop          Stop current mirroring session"
  echo "  --list          List available AirPlay devices"
  exit 1
}

# Discover AirPlay devices via DNS-SD (returns lines of "name|deviceid")
discover_devices() {
  local timeout="${1:-3}"
  dns-sd -B _airplay._tcp local. &
  local pid=$!
  sleep "$timeout"
  kill "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null
}

# Resolve a device name to its AirPlay deviceid
resolve_device_id() {
  local device_name="$1"
  local output
  output=$(dns-sd -L "$device_name" _airplay._tcp local. &
    local pid=$!
    sleep 3
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
  )
  # Extract deviceid from TXT record
  echo "$output" | grep -oE 'deviceid=[0-9A-Fa-f:]+' | head -1 | cut -d= -f2
}

[[ $# -lt 1 ]] && usage

if [[ "$1" == "--list" ]]; then
  echo "Scanning for AirPlay devices..."
  discover_devices 3 2>&1 | grep "Add" | awk -F'   ' '{print $NF}' | sed 's/^ *//' | sort -u
  exit 0
fi

if [[ "$1" == "--stop" ]]; then
  osascript <<'APPLESCRIPT'
tell application "System Events"
  tell application process "ControlCenter"
    -- Open Control Centre
    click (first menu bar item of menu bar 1 whose description is "Control Centre")
    delay 1.0

    -- Click Screen Mirroring
    repeat with e in (entire contents of window 1)
      try
        if value of attribute "AXIdentifier" of e is "controlcenter-screen-mirroring" then
          click e
          exit repeat
        end if
      end try
    end repeat

    delay 1.5

    -- Click any active (checked) device checkbox to disconnect
    try
      set sa to scroll area 1 of group 1 of window 1
      set deviceList to group 1 of sa
      set cbs to checkboxes of deviceList
      repeat with cb in cbs
        if value of cb is 1 then
          click cb
          return "Mirroring stopped."
        end if
      end repeat
      -- No active device found, close panel
      key code 53
      return "No active mirroring session found."
    on error
      key code 53
      return "No active mirroring session found."
    end try
  end tell
end tell
APPLESCRIPT
  exit 0
fi

DEVICE_NAME="$1"

# Step 1: Resolve device name to its AirPlay deviceid via DNS-SD
echo "Resolving '$DEVICE_NAME'..."
TMPFILE=$(mktemp)
trap "rm -f '$TMPFILE'" EXIT
dns-sd -L "$DEVICE_NAME" _airplay._tcp local. > "$TMPFILE" 2>&1 &
RESOLVE_PID=$!
sleep 3
kill "$RESOLVE_PID" 2>/dev/null
wait "$RESOLVE_PID" 2>/dev/null || true

DEVICE_ID=$(grep -oE 'deviceid=[0-9A-Fa-f:]+' "$TMPFILE" | head -1 | cut -d= -f2)

if [[ -z "$DEVICE_ID" ]]; then
  echo "Error: Could not find AirPlay device '$DEVICE_NAME'"
  echo ""
  echo "Available devices:"
  discover_devices 3 2>&1 | grep "Add" | awk -F'   ' '{print "  " $NF}' | sed 's/^  */  /' | sort -u
  exit 1
fi

echo "Found device '$DEVICE_NAME' (id: $DEVICE_ID)"
echo "Starting screen mirroring..."

# Step 2: Use the deviceid to construct the expected AXIdentifier and click it
TARGET_ID="screen-mirroring-device-AirPlay:${DEVICE_ID}"

RESULT=$(osascript -e "
on run argv
  set targetId to item 1 of argv

  tell application \"System Events\"
    tell application process \"ControlCenter\"
      -- Open Control Centre
      try
        click (first menu bar item of menu bar 1 whose description is \"Control Centre\")
      on error
        return \"ERROR: Could not open Control Centre. Check Accessibility permissions.\"
      end try
      delay 1.0

      -- Click Screen Mirroring checkbox
      try
        set w to window 1
      on error
        return \"ERROR: Control Centre window did not open.\"
      end try

      set smFound to false
      set topGroup to group 1 of w
      repeat with e in (checkboxes of topGroup)
        try
          if value of attribute \"AXIdentifier\" of e is \"controlcenter-screen-mirroring\" then
            click e
            set smFound to true
            exit repeat
          end if
        end try
      end repeat

      if not smFound then
        key code 53
        return \"ERROR: Could not find Screen Mirroring control in Control Centre.\"
      end if

      delay 1.5

      -- Navigate to device list and click matching device
      try
        set w2 to window 1
      on error
        return \"ERROR: Screen Mirroring panel closed before device could be selected. Try again.\"
      end try

      try
        set sa to scroll area 1 of group 1 of w2
        set deviceList to group 1 of sa
        set cbs to checkboxes of deviceList
      on error
        key code 53
        return \"ERROR: No devices found in Screen Mirroring panel.\"
      end try

      if (count of cbs) is 0 then
        key code 53
        return \"ERROR: No AirPlay devices available.\"
      end if

      repeat with cb in cbs
        try
          set ident to value of attribute \"AXIdentifier\" of cb as text
          if ident is targetId then
            click cb
            return \"OK\"
          end if
        end try
      end repeat

      -- Fallback: if only one device, click it
      if (count of cbs) is 1 then
        click checkbox 1 of deviceList
        return \"OK\"
      end if

      key code 53
      return \"ERROR: Device not found in mirroring panel. \" & (count of cbs) & \" device(s) available but none matched.\"
    end tell
  end tell
end run
" "$TARGET_ID" 2>&1)

if [[ "$RESULT" == "OK" ]]; then
  echo "Mirroring to '$DEVICE_NAME'"
elif [[ "$RESULT" == ERROR:* ]]; then
  echo "${RESULT#ERROR: }" >&2
  exit 1
else
  echo "$RESULT" >&2
  exit 1
fi
