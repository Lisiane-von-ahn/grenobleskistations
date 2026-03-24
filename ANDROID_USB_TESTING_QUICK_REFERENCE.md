# Android USB Testing - Quick Cheat Sheet

## 🚀 Setup (One-time)

```bash
# 1. Install Android SDK Tools
#    See: ANDROID_USB_TESTING.md → Prerequisites

# 2. Enable Developer Mode on phone (Android 12+)
#    Settings → About Phone → Build Number (tap 7x)

# 3. Enable USB Debugging
#    Settings → Developer options → USB Debugging = ON

# 4. Connect phone via USB cable
# 5. Accept trust prompt on phone ("Always allow from this computer")
```

## ⚡ One-Command Deploy

```bash
# Automated: Builds, installs, and launches app
bash scripts/deploy-to-phone.sh
```

## 🔧 Manual Methods

```bash
# Method 1: Gradle (1 command)
cd grenobleski_android_native
./gradlew installDebug

# Method 2: Step-by-step (full control)
# Step 1: Build APK
./gradlew assembleDebug

# Step 2: Install to phone
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Step 3: Launch app
adb shell am start -n fr.grenobleski.nativeapp/fr.grenobleski.nativeapp.MainActivity

# Step 4: View logs
adb logcat | grep grenobleski
```

## 🔍 Debugging

```bash
# Check phone connection
adb devices

# View real-time logs
adb logcat | grep grenobleski

# View all logs with errors
adb logcat *:E

# Clear app data (fresh install)
adb uninstall fr.grenobleski.nativeapp

# Get device info
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

## 📱 Common Tasks

| Task | Command |
|------|---------|
| **Check if connected** | `adb devices` |
| **Install app** | `./gradlew installDebug` |
| **View logs** | `adb logcat \| grep grenobleski` |
| **Uninstall** | `adb uninstall fr.grenobleski.nativeapp` |
| **Clear data** | `adb shell pm clear fr.grenobleski.nativeapp` |
| **See version** | `adb shell dumpsys package fr.grenobleski.nativeapp \| grep versionName` |
| **Open shell** | `adb shell` |
| **Restart daemon** | `adb kill-server && adb devices` |

## 🔴 Troubleshooting

| Issue | Solution |
|-------|----------|
| **No devices** | Check cable, enable USB debugging, accept trust prompt |
| **Build fails** | Run `./gradlew clean assembleDebug` |
| **Installation fails** | Uninstall first: `adb uninstall fr.grenobleski.nativeapp` |
| **App crashes** | Check logs: `adb logcat \| grep grenobleski` |
| **Not enough space** | Clear data: `adb shell pm clear fr.grenobleski.nativeapp` |

## 💾 Clean Fresh Install

```bash
# Complete reset
adb uninstall fr.grenobleski.nativeapp
cd grenobleski_android_native
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear fr.grenobleski.nativeapp
```

## 📋 Daily Workflow

```bash
# After code changes:
cd grenobleski_android_native
./gradlew installDebug          # Build and install
adb logcat | grep grenobleski   # Watch logs while testing
```

---

**See ANDROID_USB_TESTING.md for full guide**
