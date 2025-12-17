#!/usr/bin/osascript

-- AppleScript to trigger AirPlay screen mirroring on macOS
-- This script automates opening Control Center and selecting the AirPlay device

on run argv
    -- Get device name from argument, default to searching for pentagram symbol
    if (count of argv) > 0 then
        set deviceName to item 1 of argv
    else
        set deviceName to "⛧"
    end if

    tell application "System Events"
        -- Open Control Center
        tell process "ControlCenter"
            -- Click on the Control Center icon in menu bar
            try
                click menu bar item "Control Center" of menu bar 1
                delay 0.5
            on error
                display notification "Could not open Control Center" with title "AirPlay Automation"
                return "Error: Could not open Control Center"
            end try

            -- Look for Screen Mirroring button
            try
                -- In macOS Sequoia and later, Screen Mirroring is in Control Center
                click button "Screen Mirroring" of window "Control Center"
                delay 1
            on error
                display notification "Could not find Screen Mirroring button" with title "AirPlay Automation"
                return "Error: Could not find Screen Mirroring button"
            end try

            -- Look for the device with the pentagram symbol or specified name
            try
                -- Try to find and click the device
                repeat with i from 1 to 10
                    try
                        if button i of window "Screen Mirroring" exists then
                            set btnTitle to title of button i of window "Screen Mirroring"
                            if btnTitle contains deviceName then
                                click button i of window "Screen Mirroring"
                                display notification "Connecting to " & btnTitle with title "AirPlay Automation"
                                return "Success: Clicked " & btnTitle
                            end if
                        end if
                    end try
                end repeat

                display notification "Device not found: " & deviceName with title "AirPlay Automation"
                return "Error: Device not found"

            on error errMsg
                display notification "Error selecting device: " & errMsg with title "AirPlay Automation"
                return "Error: " & errMsg
            end try
        end tell
    end tell
end run
