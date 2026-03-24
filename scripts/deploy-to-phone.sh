#!/usr/bin/env bash
#
# GrenobleSki Android - USB Device Testing Setup
# Deploy app to physical phone via USB for testing
#

set -e

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║  GrenobleSki Android - USB Device Testing              ║"
echo "║  Deploy directly to your phone for testing             ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"

PROJECT_ROOT=$(pwd)
ANDROID_DIR="$PROJECT_ROOT/grenobleski_android_native"

# Step 1: Check prerequisites
echo -e "${YELLOW}Step 1: Checking prerequisites...${NC}"

if [ ! -f "$ANDROID_DIR/app/build.gradle.kts" ]; then
    echo -e "${RED}❌ Android project not found${NC}"
    echo "   Make sure you're in the project root directory"
    exit 1
fi
echo -e "${GREEN}✓ Android project found${NC}"

if ! command -v adb &> /dev/null; then
    echo -e "${RED}❌ ADB (Android Debug Bridge) not found${NC}"
    echo ""
    echo "Install Android SDK Tools:"
    echo "  • Download from: https://developer.android.com/studio"
    echo "  • Or install with: brew install android-sdk (macOS)"
    echo "  • Add to PATH: export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools\""
    exit 1
fi
echo -e "${GREEN}✓ ADB installed: $(adb version | head -1)${NC}"

if ! command -v gradle &> /dev/null && [ ! -f "$ANDROID_DIR/gradlew" ]; then
    echo -e "${RED}❌ Gradle not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle available${NC}"

# Step 2: USB connection check
echo ""
echo -e "${YELLOW}Step 2: Checking USB connection...${NC}"
echo ""
echo "Please connect your Android phone via USB cable"
echo "Make sure to:"
echo "  1. Enable Developer Mode:"
echo "     • Android 12+: Settings → About Phone → tap Build Number 7 times"
echo "  2. Enable USB Debugging:"
echo "     • Settings → Developer options → USB Debugging = ON"
echo "  3. Trust this computer (check 'Always allow from this computer')"
echo ""
echo "Press ENTER when your phone is connected and USB Debugging is enabled..."
read -r

# List connected devices
ADB_DEVICES=$(adb devices | grep -v "^$" | tail -n +2 | grep -v "List of attached")
if [ -z "$ADB_DEVICES" ]; then
    echo -e "${RED}❌ No devices found${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  • USB cable: Try a different cable or USB port"
    echo "  • USB Debugging: Go to Settings → Developer options → USB Debugging"
    echo "  • Trust: Click 'Allow' on your phone if prompted"
    echo "  • Restart: Unplug and replug the cable"
    echo "  • Check: adb devices"
    exit 1
fi

ADB_DEVICE=$(echo "$ADB_DEVICES" | head -1 | awk '{print $1}')
echo -e "${GREEN}✓ Device detected: $ADB_DEVICE${NC}"

# Step 3: Build APK
echo ""
echo -e "${YELLOW}Step 3: Building APK...${NC}"
cd "$ANDROID_DIR"

if ./gradlew assembleDebug --no-daemon 2>&1 | tail -20; then
    echo -e "${GREEN}✓ APK built successfully${NC}"
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

# Step 4: Find APK path
APK_PATH=$(find . -name "app-debug.apk" -type f | head -1)
if [ -z "$APK_PATH" ]; then
    echo -e "${RED}❌ Debug APK not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ APK found: $APK_PATH${NC}"

# Step 5: Install to device
echo ""
echo -e "${YELLOW}Step 4: Installing to device ($ADB_DEVICE)...${NC}"
echo "This may take 10-30 seconds..."

if adb install -r "$APK_PATH"; then
    echo -e "${GREEN}✓ App installed successfully!${NC}"
else
    echo -e "${RED}❌ Installation failed${NC}"
    echo ""
    echo "Common issues:"
    echo "  • Not enough space: Uninstall old version first"
    echo "  • Device timeout: Check USB connection"
    echo "  • Permission denied: Re-enable USB Debugging"
    exit 1
fi

# Step 6: Launch app
echo ""
echo -e "${YELLOW}Step 5: Launching app...${NC}"

# Package name from build.gradle.kts
PACKAGE_NAME="fr.grenobleski.nativeapp"
MAIN_ACTIVITY="MainActivity"

if adb shell am start -n "$PACKAGE_NAME/$PACKAGE_NAME.$MAIN_ACTIVITY"; then
    echo -e "${GREEN}✓ App launched!${NC}"
else
    echo -e "${YELLOW}⚠ Could not auto-launch app${NC}"
    echo "  Open it manually: Find 'GrenobleSki' in your app drawer"
fi

# Step 7: View logs
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗"
echo "║  ✅ App installed and running!                            ║"
echo "╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "📱 Testing Tips:"
echo ""
echo "1. View real-time logs:"
echo -e "   ${BLUE}adb logcat | grep grenobleski${NC}"
echo ""
echo "2. Uninstall app:"
echo -e "   ${BLUE}adb uninstall fr.grenobleski.nativeapp${NC}"
echo ""
echo "3. Clear app data (for fresh testing):"
echo -e "   ${BLUE}adb shell pm clear fr.grenobleski.nativeapp${NC}"
echo ""
echo "4. View installed version:"
echo -e "   ${BLUE}adb shell dumpsys package fr.grenobleski.nativeapp | grep versionName${NC}"
echo ""
echo "5. Rebuild and reinstall (quick workflow):"
echo -e "   ${BLUE}./gradlew installDebug${NC}"
echo ""
echo "Trouble?"
echo "  • Connection lost? Reconnect USB and rerun this script"
echo "  • App crashed? Check logs: adb logcat"
echo "  • Changes not showing? Do clean rebuild:"
echo -e "    ${BLUE}./gradlew clean assembleDebug${NC}"
