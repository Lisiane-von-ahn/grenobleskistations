#!/usr/bin/env bash
#
# GrenobleSki Android Deployment Quick Setup
# This script helps you set up Play Store deployment
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
echo "║  GrenobleSki Android → Google Play Store Setup         ║"
echo "║  Follow these steps to enable automated deployment     ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Step 1: Verify Android project
echo -e "${YELLOW}Step 1: Verifying Android project...${NC}"
if [ ! -f "grenobleski_android_native/app/build.gradle.kts" ]; then
    echo -e "${RED}❌ Error: Android project not found${NC}"
    echo "Make sure you're in the project root directory"
    exit 1
fi
echo -e "${GREEN}✓ Android project found${NC}"

# Step 2: Check for keystore
echo ""
echo -e "${YELLOW}Step 2: Checking signing keystore...${NC}"
if [ -f "grenobleski_android_native/signing/release.keystore" ]; then
    echo -e "${GREEN}✓ Keystore found${NC}"
    echo "  Location: grenobleski_android_native/signing/release.keystore"
else
    echo -e "${RED}❌ Keystore not found${NC}"
    echo "  Expected: grenobleski_android_native/signing/release.keystore"
    exit 1
fi

# Step 3: Check GitHub secrets
echo ""
echo -e "${YELLOW}Step 3: Checking GitHub secrets...${NC}"
echo "You need to add these secrets to GitHub:"
echo -e "  ${BLUE}ANDROID_KEYSTORE_BASE64${NC} - Base64 encoded keystore"
echo -e "  ${BLUE}ANDROID_STORE_PASSWORD${NC} - Keystore password"
echo -e "  ${BLUE}ANDROID_KEY_ALIAS${NC} - Key alias"
echo -e "  ${BLUE}ANDROID_KEY_PASSWORD${NC} - Key password (often same as store)"
echo -e "  ${BLUE}PLAY_STORE_SERVICE_ACCOUNT_JSON${NC} - Service account JSON"

# Step 4: Help encode keystore
echo ""
echo -e "${YELLOW}Step 4: Encode keystore for GitHub${NC}"
echo "To copy keystore to GitHub Secrets, run:"
echo ""
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    echo -e "${BLUE}  base64 < grenobleski_android_native/signing/release.keystore | pbcopy${NC}"
    echo "  (Value is copied to clipboard automatically)"
else
    # Linux
    echo -e "${BLUE}  base64 grenobleski_android_native/signing/release.keystore${NC}"
    echo "  (Copy the output manually)"
fi

echo ""
echo "Then:"
echo "  1. Go to GitHub Repo → Settings → Secrets and variables → Actions"
echo "  2. Click 'New repository secret'"
echo "  3. Name: ANDROID_KEYSTORE_BASE64"
echo "  4. Paste the value"
echo "  5. Click 'Add secret'"

# Step 5: Service account
echo ""
echo -e "${YELLOW}Step 5: Set up Google Play Store Service Account${NC}"
echo ""
echo "Follow these steps (one time only):"
echo ""
echo "A. Create Google Cloud Project:"
echo "   1. Go to https://console.cloud.google.com"
echo "   2. Create project: 'GrenobleSki Android CI/CD'"
echo "   3. Enable Google Play Android Developer API"
echo ""
echo "B. Create Service Account:"
echo "   1. Go to https://console.cloud.google.com/iam-admin/serviceaccounts"
echo "   2. Create Service Account: 'github-actions-deploy'"
echo "   3. Grant role: 'Service Account User'"
echo ""
echo "C. Grant Play Store Permissions:"
echo "   1. Go to Google Play Console"
echo "   2. Settings → Users and permissions"
echo "   3. Invite the service account email"
echo "   4. Grant 'Manage releases' permission"
echo ""
echo "D. Create JSON Key:"
echo "   1. Cloud Console → Service Accounts"
echo "   2. Select 'github-actions-deploy'"
echo "   3. Keys → Add Key → Create new Key → JSON"
echo "   4. Download the JSON file"
echo ""
echo "E. Add the downloaded JSON key to GitHub:"
echo ""
echo "   Then add the file's raw JSON content to GitHub Secrets:"
echo "   - Name: PLAY_STORE_SERVICE_ACCOUNT_JSON"
echo "   - Value: [paste the complete JSON content]"

# Step 6: Verify app in Play Console
echo ""
echo -e "${YELLOW}Step 6: Verify Play Store Setup${NC}"
echo "Make sure you have:"
echo "  ✓ App created in Google Play Console"
echo "  ✓ Package name: fr.grenobleski.nativeapp"
echo "  ✓ Internal Testing track created"
echo "  ✓ Service account invited with 'Manage releases' permission"

# Summary
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗"
echo "║  Deployment Setup Checklist                              ║"
echo "╚════════════════════════════════════════════════════════╝${NC}"

CHECKLIST=(
    "Android project compiles (run: ./gradlew build)"
    "Keystore file exists: grenobleski_android_native/signing/release.keystore"
    "Created Google Cloud Project"
    "Created Service Account: github-actions-deploy"
    "Service Account has Play Store permission: Manage releases"
    "Downloaded Service Account JSON key"
    "GitHub Secret: ANDROID_KEYSTORE_BASE64"
    "GitHub Secret: ANDROID_STORE_PASSWORD"
    "GitHub Secret: ANDROID_KEY_ALIAS"
    "GitHub Secret: ANDROID_KEY_PASSWORD"
    "GitHub Secret: PLAY_STORE_SERVICE_ACCOUNT_JSON"
    "App exists in Google Play Console (fr.grenobleski.nativeapp)"
    "Internal Testing track created in Play Console"
)

for i in "${!CHECKLIST[@]}"; do
    echo "  [ ] $((i+1)). ${CHECKLIST[$i]}"
done

echo ""
echo -e "${YELLOW}After completing the checklist:${NC}"
echo ""
echo "  1. Push to main branch:"
echo -e "     ${BLUE}git push origin main${NC}"
echo ""
echo "  2. Check GitHub Actions:"
echo -e "     ${BLUE}Go to Actions tab and monitor the deployment${NC}"
echo ""
echo "  3. Verify in Play Console:"
echo -e "     ${BLUE}GrenobleSki → Testing → Internal testing${NC}"
echo""
echo -e "${GREEN}Ready to deploy! 🚀${NC}"
echo ""
echo "For detailed help, see: ANDROID_DEPLOYMENT.md"
