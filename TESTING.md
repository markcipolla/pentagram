# Pentagram Testing Guide

This guide explains how to use the automated testing scripts to close the feedback loop during development.

## Available Test Scripts

### 1. `test-loop.sh` - Basic Test Loop

Simple script that deploys and monitors logs. Requires manual screen mirroring trigger.

**Usage:**
```bash
./test-loop.sh
```

**What it does:**
1. ✓ Checks for connected Android device
2. ✓ Builds and deploys the app
3. ✓ Clears logcat buffer
4. ✓ Launches the app
5. ✓ Verifies AirPlay service is running
6. ✓ Displays device information
7. ⚠ Shows manual instructions for screen mirroring
8. ✓ Monitors filtered logs in real-time

**When to use:** Quick testing where you want to manually control screen mirroring

---

### 2. `full-test-loop.sh` - Automated Test Loop

Advanced script with optional automatic screen mirroring trigger.

**Usage:**
```bash
# Basic usage (manual trigger)
./full-test-loop.sh

# Auto-trigger screen mirroring
./full-test-loop.sh --auto-trigger

# Monitor for 60 seconds
./full-test-loop.sh --duration 60

# Save logs to file
./full-test-loop.sh --keep-logs

# Combine options
./full-test-loop.sh --auto-trigger --duration 60 --keep-logs
```

**Options:**
- `--auto-trigger` - Automatically trigger screen mirroring using AppleScript
- `--duration N` - Monitor logs for N seconds (default: 30)
- `--keep-logs` - Save logs to timestamped file
- `--help` - Show help message

**What it does:**
1. ✓ Checks for connected Android device
2. ✓ Builds and deploys the app
3. ✓ Clears logcat buffer
4. ✓ Launches the app
5. ✓ Verifies AirPlay service is running
6. ✓ Displays device information
7. ✓ Optionally auto-triggers screen mirroring (if `--auto-trigger`)
8. ✓ Monitors filtered logs for specified duration

**When to use:** Development workflow where you want minimal manual intervention

---

### 3. `trigger-airplay.scpt` - Screen Mirroring Automation

AppleScript to automate macOS screen mirroring selection.

**Usage:**
```bash
# Trigger with default device name (searches for ⛧)
osascript trigger-airplay.scpt

# Trigger with specific device name
osascript trigger-airplay.scpt "Pixel 8"
```

**What it does:**
1. Opens Control Center
2. Clicks "Screen Mirroring" button
3. Searches for device containing the specified name
4. Clicks the device to initiate connection
5. Shows notification on success/failure

**Requirements:**
- macOS Monterey or later
- Accessibility permissions for Terminal/IDE
- Control Center with Screen Mirroring available

**Note:** The first time you run this, macOS will prompt you to grant accessibility permissions.

---

## Quick Start

### For Development Testing (Recommended)

```bash
# One-command full automated test
./full-test-loop.sh --auto-trigger --duration 60 --keep-logs
```

This will:
- Deploy the latest build
- Launch the app
- Automatically trigger screen mirroring
- Monitor logs for 60 seconds
- Save logs to a file for review

### For Manual Testing

```bash
# Basic test with manual control
./test-loop.sh
```

Then manually:
1. Open Control Center on macOS
2. Click Screen Mirroring
3. Select your device
4. Watch logs appear in real-time

---

## Typical Development Workflow

1. **Make code changes**
   ```bash
   # Edit files in Android Studio or your editor
   ```

2. **Run automated test**
   ```bash
   ./full-test-loop.sh --auto-trigger --keep-logs
   ```

3. **Review results**
   - Watch real-time logs
   - Check for errors or warnings
   - Verify video streaming works

4. **Iterate**
   - Fix any issues
   - Repeat steps 1-3

---

## Log Filtering

The scripts monitor these log tags:
- `Pentagram` - Main app logs
- `AirPlay` - AirPlay server logs
- `MainActivity` - UI activity logs
- `VideoStream` - Video streaming logs
- `FairPlay` - Encryption/decryption logs
- `MirrorBuffer` - Native buffer processing logs

You can customize the filter by editing `LOG_TAG` in the script.

---

## Troubleshooting

### Device Not Found

**Problem:** "No Android device connected"

**Solutions:**
- Connect device via USB
- Enable USB debugging in Developer Options
- Run `adb devices` to verify connection
- Try `adb kill-server && adb start-server`

### AppleScript Permissions Denied

**Problem:** "Not authorized to send Apple events"

**Solutions:**
1. Open System Preferences → Security & Privacy → Privacy
2. Select "Accessibility" from left panel
3. Add Terminal (or your IDE) to the list
4. Check the box to enable
5. Restart Terminal/IDE

### Screen Mirroring Not Found

**Problem:** AppleScript can't find Screen Mirroring button

**Solutions:**
- Ensure Control Center is accessible in macOS
- Check that Screen Mirroring is enabled in System Preferences
- Verify devices are on same WiFi network
- Try running manually first to verify setup

### Service Not Starting

**Problem:** "AirPlay service may not be running"

**Solutions:**
- Check app permissions (notifications, network)
- Review logcat for service startup errors
- Ensure foreground service permission is granted
- Try manually starting service in the app

---

## Advanced Usage

### Save Logs with Custom Filename

```bash
./full-test-loop.sh --keep-logs
# Creates: pentagram-test-YYYYMMDD-HHMMSS.log
```

### Monitor Specific Log Tags

Edit `LOG_TAG` in the script:
```bash
LOG_TAG="YourCustomTag|AnotherTag"
```

### Continuous Testing Loop

```bash
while true; do
    ./full-test-loop.sh --auto-trigger --duration 30
    echo "Test completed. Waiting 5 seconds..."
    sleep 5
done
```

### Test Multiple Devices

```bash
# List all connected devices
adb devices -l

# Target specific device
export ANDROID_SERIAL=<device-serial>
./full-test-loop.sh
```

---

## Script Architecture

```
┌─────────────────────────┐
│   full-test-loop.sh     │
│  (Main orchestrator)    │
└───────────┬─────────────┘
            │
            ├─► deploy.sh
            │   (Build & install app)
            │
            ├─► trigger-airplay.scpt
            │   (Auto-trigger mirroring)
            │
            └─► adb logcat
                (Monitor logs)
```

---

## Tips for Effective Testing

1. **Use `--keep-logs`** to build a history of test runs
2. **Start with manual testing** (`test-loop.sh`) to understand the flow
3. **Use `--auto-trigger`** once your setup is stable
4. **Adjust `--duration`** based on your test needs
5. **Monitor logs carefully** for early error detection
6. **Test on same WiFi network** for best results

---

## Example Output

```
╔════════════════════════════════════════╗
║  Pentagram AirPlay Full Test Loop     ║
╚════════════════════════════════════════╝

[1/8] Checking for connected Android device...
✓ Connected to: Pixel 8

[2/8] Building and deploying app...
✓ App deployed successfully

[3/8] Clearing logcat buffer...
✓ Logs cleared

[4/8] Launching Pentagram app...
✓ App launched

[5/8] Verifying AirPlay service...
✓ AirPlay service is running

[6/8] Getting device information...
╔════════════════════════════════════════╗
║ Device Information                     ║
╠════════════════════════════════════════╣
║ Name:  ⛧ Pixel 8
║ IP:    192.168.1.100
║ Port:  7000
╚════════════════════════════════════════╝

[7/8] Triggering screen mirroring automatically...
✓ Screen mirroring triggered

[8/8] Monitoring logs...
╔════════════════════════════════════════╗
║ Log Output (filtered)                  ║
╠════════════════════════════════════════╣
║ Duration: 30s (Ctrl+C to stop)
╚════════════════════════════════════════╝

... logs appear here ...
```

---

## Contributing

If you improve these scripts, please:
1. Test on multiple macOS versions
2. Update this documentation
3. Submit a pull request
