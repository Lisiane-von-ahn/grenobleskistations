# Android USB Testing Guide - GrenobleSki

## Quick Start (30 seconds)

```bash
# 1. Connect phone via USB
# 2. Enable USB Debugging on phone (Settings → Developer options)
# 3. Run:
bash scripts/deploy-to-phone.sh
```

The script will build, install, and launch the app on your phone automatically! 🎉

---

## Prerequisites

### On Your Computer

#### 1. Android SDK Tools (ADB)

**Option A: Download SDK Tools**
- Go to [Android Developer](https://developer.android.com/studio#downloads)
- Download "Command line tools only"
- Extract to: `~/Android/sdk` (or your preferred location)
- Add to PATH: `export PATH="$PATH:$ANDROID_HOME/platform-tools"`

**Option B: Homebrew (macOS)**
```bash
brew install android-sdk
```

**Option C: Android Studio**
- Download [Android Studio](https://developer.android.com/studio)
- Tools → SDK Manager → "SDK Tools" tab
- Install: "Android SDK Platform-Tools"

**Verify Installation:**
```bash
adb --version
# Should output: Android Debug Bridge version
```

#### 2. USB Cable (Important!)

- Use **original USB cable** or high-quality cable
- **USB Type-C** recommended (most modern Android phones)
- Test the cable works with data transfer (not just charging)

---

### On Your Phone (Android 12+)

#### Step 1: Enable Developer Mode

1. Go to **Settings**
2. Tap **About Phone**
3. Scroll down, find **Build Number**
4. **Tap 7 times rapidly** (you'll see "You're a developer!")
5. Go back, you should now see **Developer options**

#### Step 2: Enable USB Debugging

1. Go to **Settings** → **Developer options**
2. Find **USB Debugging** toggle
3. Turn it **ON** ✓
4. (Optional) Turn on **Install via USB** for better compatibility

#### Step 3: Trust This Computer

1. Plug in USB cable
2. You'll see a dialog: **"Allow USB debugging?"**
3. ✅ Check **"Always allow from this computer"**
4. Tap **Allow**

---

## Deployment Methods

### Method 1: Automated Script (Recommended)

```bash
# Easiest way - handles everything
bash scripts/deploy-to-phone.sh
```

This script:
- ✅ Checks prerequisites
- ✅ Waits for USB device
- ✅ Builds debug APK
- ✅ Installs to phone
- ✅ Launches the app
- ✅ Shows real-time logs

### Method 2: Automatic Gradle (One-liner)

```bash
cd grenobleski_android_native
./gradlew installDebug
```

Gradle automatically:
- Finds connected device
- Builds APK
- Installs it
- **Does NOT launch** (you need to open manually)

### Method 3: Manual Steps

```bash
# Step 1: Build debug APK
cd grenobleski_android_native
./gradlew assembleDebug

# Step 2: Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Step 3: Launch app
adb shell am start -n fr.grenobleski.nativeapp/fr.grenobleski.nativeapp.MainActivity

# Step 4: View logs
adb logcat | grep -i grenobleski
```

---

## Troubleshooting

### ❌ "No devices found"

**Check 1: Phone connection**
```bash
# List connected devices
adb devices

# Should show:
# List of attached devices
# ABC123XYZ45     device
```

**If no device shows:**

1. **Try different USB port** (front ports if using hub)
2. **Try different USB cable** (data cable, not charge-only)
3. **Pull USB, wait 5 seconds, replug**
4. **Check phone for permission dialog:**
   - Unplug cable
   - Check if notification appeared
   - Tap "Allow" or accept the prompt
   - Replug cable

5. **Restart devices:**
   ```bash
   # Restart ADB server
   adb kill-server
   adb devices  # This restarts daemon
   ```

6. **Driver issue (Windows only):**
   - [Download phone drivers](https://developer.android.com/studio/run/oem-usb)
   - Or use generic Google drivers

### ❌ "Build failed"

**Check 1: Java version**
```bash
java -version
# Should be Java 17+
```

**Check 2: Build issues**
```bash
# Clean and rebuild
cd grenobleski_android_native
./gradlew clean assembleDebug
```

**Check 3: Dependencies**
```bash
# Update Gradle
./gradlew wrapper --gradle-version latest
```

### ❌ "Installation failed: INSTALL_FAILED_INVALID_APK"

**Cause:** APK is corrupted or version issue

**Fix:**
```bash
# Uninstall old version first
adb uninstall fr.grenobleski.nativeapp

# Then try install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### ❌ "Installation failed: INSTALL_FAILED_INSUFFICIENT_STORAGE"

**Cause:** Not enough space on phone

**Fix:**
1. Delete some apps or files on phone
2. Clear cache: `adb shell pm clear fr.grenobleski.nativeapp`
3. Retry

### ❌ "Installation failed: INSTALL_FAILED_UPDATE_INCOMPATIBLE"

**Cause:** Version downgrade (release vs debug)

**Fix:**
```bash
# Remove existing app
adb uninstall fr.grenobleski.nativeapp

# Try install again
adb install app/build/outputs/apk/debug/app-debug.apk
```

### ❌ App crashes immediately after launch

**View crash logs:**
```bash
# See all logs
adb logcat

# Filter for app logs only
adb logcat | grep grenobleski

# See crashes and errors
adb logcat *:E | grep grenobleski
```

**Common crashes:**
- **Network error:** Check if API server is running
- **Database error:** Try clearing app data: `adb shell pm clear fr.grenobleski.nativeapp`
- **Permission denied:** Check app permissions in Settings

---

## Common Workflows

### Daily Testing Loop

```bash
# After making code changes:

# 1. Rebuild and install (fastest)
cd grenobleski_android_native
./gradlew installDebug

# 2. Phone will auto-update, watch logs
adb logcat | grep grenobleski

# 3. Test features on your phone

# 4. Make code changes, repeat step 1
```

### Clean Fresh Install

```bash
# Complete reset for fresh testing
adb uninstall fr.grenobleski.nativeapp      # Remove old
./gradlew clean assembleDebug               # Fresh build
adb install app/build/outputs/apk/debug/app-debug.apk  # Clean install
adb shell pm clear fr.grenobleski.nativeapp # Clear data
```

### Monitor Real-time Logs

```bash
# Terminal 1: Watch logs live
adb logcat -s grenobleski

# Terminal 2: During testing, see crashes/errors appear instantly
adb logcat | grep grenobleski
```

---

## Advanced Options

### Install Specific APK to Device

```bash
adb install /path/to/app-debug.apk
```

### Uninstall App

```bash
adb uninstall fr.grenobleski.nativeapp
```

### Clear All App Data (Fresh State)

```bash
adb shell pm clear fr.grenobleski.nativeapp
```

### View Device Info

```bash
# Device model and Android version
adb shell getprop ro.build.version.release
adb shell getprop ro.product.model
adb shell getprop ro.product.manufacturer
```

### Enable Full Logcat Output

```bash
# See everything (verbose)
adb logcat

# See only errors and warnings
adb logcat *:W

# See app logs with timestamps
adb logcat -v threadtime | grep grenobleski

# Save logs to file
adb logcat > logcat.txt &
# (Run your tests)
kill %1  # Stop logging
```

### Remote Shell Access

```bash
# Open shell on device
adb shell

# Inside shell, useful commands:
pm list packages                              # List all apps
pm dump fr.grenobleski.nativeapp              # App info
getprop ro.build.version.release              # Android version
ls -la /data/data/fr.grenobleski.nativeapp/   # App data dir
exit  # Exit shell
```

---

## USB Debugging Tips

### Keep USB Debugging Safe

✅ **DO:**
- Trust only your own computer
- Revoke USB debugging when done on public networks
- Use USB cables you own

❌ **DON'T:**
- Accept "Always allow" on public computers
- Share USB credentials
- Leave USB debugging on unnecessarily

**Revoke USB Debugging:**
```bash
# If you need to revoke all connections:
# On phone: Settings → Developer options → Revoke USB debugging authorizations
```

### Multi-device Testing

```bash
# If you have multiple devices connected:
adb devices -l  # List all with details

# Install on all connected
for device in $(adb devices | grep device | awk '{print $1}'); do
    adb -s $device install -r app/build/outputs/apk/debug/app-debug.apk
done

# Or target specific device
adb -s ABC123XYZ45 install -r app-debug.apk
```

### Wireless Debugging (Advanced)

```bash
# Enable wireless ADB (same network)
adb tcpip 5555
adb connect YOUR_PHONE_IP:5555

# Verify
adb devices

# Back to USB
adb usb
```

---

## Architecture Overview

```
┌─────────────────────────────────────┐
│  Your Computer                      │
│  ├─ Android SDK (ADB)              │
│  ├─ Gradle / Android Studio        │
│  └─ GrenobleSki Source Code        │
└──────────────┬──────────────────────┘
               │
               │ USB Cable
               │ (Data Transfer)
               ↓
┌──────────────────────────────────────┐
│  Your Android Phone                  │
│  ├─ USB Debugging Enabled           │
│  ├─ GrenobleSki App (Debug)         │
│  ├─ App Logs                        │
│  └─ Device Data & Storage           │
└──────────────────────────────────────┘
```

---

## Performance Tips

- **Faster builds:** Use `--no-daemon` flag
- **Faster installs:** Use `adb install -r` (reinstall without uninstall)
- **Faster development:** Use `./gradlew installDebug` repeatedly
- **Monitor battery:** Check app's battery usage in Settings

---

## Support & Resources

| Resource | Link |
|----------|------|
| Android Debug Bridge | https://developer.android.com/studio/command-line/adb |
| USB Debugging Setup | https://developer.android.com/studio/debug/debug-with-device |
| ADB Commands | https://developer.android.com/studio/command-line/adb |
| Android Emulator | https://developer.android.com/studio/run/emulator-commandline |

---

**Need Help?**

1. Check logcat: `adb logcat | grep grenobleski`
2. Run the setup diagnostic: `bash scripts/deploy-to-phone.sh`
3. Check [Common Issues](#troubleshooting) section above
4. Review Android official docs (links above)

**Happy testing!** 🎉
