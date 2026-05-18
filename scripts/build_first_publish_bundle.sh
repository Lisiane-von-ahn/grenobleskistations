#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${ANDROID_DIR:-$ROOT_DIR/grenobleski_android_native}"
KEYSTORE_ENV_FILE="${KEYSTORE_ENV_FILE:-$ANDROID_DIR/signing/keystore.local.env}"
MIN_TARGET_SDK="${MIN_TARGET_SDK:-35}"
MAX_AAB_BYTES="${MAX_AAB_BYTES:-209715200}" # 200 MiB
BUILD_NUMBER="${BUILD_NUMBER:-$(date +%s)}"
VERSION_NAME="${VERSION_NAME:-1.${BUILD_NUMBER}.0}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] Missing command: $cmd"
    exit 1
  fi
}

require_cmd sed
require_cmd awk
require_cmd stat
require_cmd sha256sum

if [[ ! -d "$ANDROID_DIR" ]]; then
  echo "[ERROR] Android dir not found: $ANDROID_DIR"
  exit 1
fi

if [[ ! -f "$KEYSTORE_ENV_FILE" ]]; then
  echo "[ERROR] Keystore env file not found: $KEYSTORE_ENV_FILE"
  exit 1
fi

# shellcheck disable=SC1090
source "$KEYSTORE_ENV_FILE"

: "${ANDROID_SIGNING_KEYSTORE_PATH:?Missing ANDROID_SIGNING_KEYSTORE_PATH in $KEYSTORE_ENV_FILE}"
: "${ANDROID_SIGNING_STORE_PASS:?Missing ANDROID_SIGNING_STORE_PASS in $KEYSTORE_ENV_FILE}"
: "${ANDROID_SIGNING_KEY_ALIAS:?Missing ANDROID_SIGNING_KEY_ALIAS in $KEYSTORE_ENV_FILE}"
: "${ANDROID_SIGNING_KEY_PASS:?Missing ANDROID_SIGNING_KEY_PASS in $KEYSTORE_ENV_FILE}"

if [[ "$ANDROID_SIGNING_KEYSTORE_PATH" = /* ]]; then
  KEYSTORE_PATH_ABS="$ANDROID_SIGNING_KEYSTORE_PATH"
else
  KEYSTORE_PATH_ABS="$ROOT_DIR/$ANDROID_SIGNING_KEYSTORE_PATH"
fi

if [[ ! -f "$KEYSTORE_PATH_ABS" ]]; then
  echo "[ERROR] Keystore file not found: $KEYSTORE_PATH_ABS"
  exit 1
fi

GRADLE_FILE="$ANDROID_DIR/app/build.gradle.kts"
if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "[ERROR] Gradle file not found: $GRADLE_FILE"
  exit 1
fi

TARGET_SDK="$(sed -n 's/^\s*targetSdk\s*=\s*\([0-9]\+\).*/\1/p' "$GRADLE_FILE" | head -n1)"
if [[ -z "$TARGET_SDK" ]]; then
  echo "[ERROR] Could not parse targetSdk from $GRADLE_FILE"
  exit 1
fi

if (( TARGET_SDK < MIN_TARGET_SDK )); then
  echo "[ERROR] targetSdk=$TARGET_SDK is below required minimum ($MIN_TARGET_SDK)."
  exit 1
fi

echo "[INFO] targetSdk check OK: $TARGET_SDK"
echo "[INFO] Building signed release AAB..."

pushd "$ANDROID_DIR" >/dev/null
chmod +x ./gradlew
KEYSTORE_PATH="$KEYSTORE_PATH_ABS" \
KEY_STORE_PASSWORD="$ANDROID_SIGNING_STORE_PASS" \
KEY_ALIAS="$ANDROID_SIGNING_KEY_ALIAS" \
KEY_PASSWORD="$ANDROID_SIGNING_KEY_PASS" \
BUILD_NUMBER="$BUILD_NUMBER" \
VERSION_NAME="$VERSION_NAME" \
./gradlew clean bundleRelease --no-daemon
popd >/dev/null

AAB_PATH="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"
if [[ ! -f "$AAB_PATH" ]]; then
  echo "[ERROR] AAB not found at expected path: $AAB_PATH"
  exit 1
fi

AAB_SIZE_BYTES="$(stat -c%s "$AAB_PATH")"
AAB_SIZE_MIB="$(awk -v s="$AAB_SIZE_BYTES" 'BEGIN { printf "%.2f", s/1024/1024 }')"

if (( AAB_SIZE_BYTES > MAX_AAB_BYTES )); then
  echo "[ERROR] AAB too large: ${AAB_SIZE_MIB} MiB (${AAB_SIZE_BYTES} bytes)"
  echo "        Max allowed by this check: ${MAX_AAB_BYTES} bytes"
  exit 1
fi

echo "[INFO] AAB size check OK: ${AAB_SIZE_MIB} MiB (${AAB_SIZE_BYTES} bytes)"
echo "[INFO] SHA256: $(sha256sum "$AAB_PATH" | awk '{print $1}')"

echo "[OK] First-publish bundle is ready"
echo "     File: $AAB_PATH"
echo "     VersionName: $VERSION_NAME"
echo "     VersionCode(BUILD_NUMBER): $BUILD_NUMBER"
