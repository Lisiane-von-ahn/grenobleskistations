#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${ANDROID_DIR:-$ROOT_DIR/grenobleski_android_native}"
PACKAGE_NAME="${GOOGLE_PLAY_PACKAGE_NAME:-fr.grenobleski.nativeapp}"
TRACK="${GOOGLE_PLAY_TRACK:-internal}"
RELEASE_STATUS="${GOOGLE_PLAY_RELEASE_STATUS:-completed}"
ROLLOUT_PERCENTAGE="${GOOGLE_PLAY_ROLLOUT_PERCENTAGE:-100}"
SERVICE_ACCOUNT_JSON="${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:-}"
AAB_PATH="${GOOGLE_PLAY_AAB_PATH:-}"
DRY_RUN="${GOOGLE_PLAY_DRY_RUN:-false}"
VALIDATE_ONLY="${GOOGLE_PLAY_VALIDATE_ONLY:-false}"
FIRST_DEPLOY="${GOOGLE_PLAY_FIRST_DEPLOY:-false}"
ACK_MANUAL_COMPLIANCE="${GOOGLE_PLAY_ACK_MANUAL_COMPLIANCE:-false}"
MIN_TARGET_SDK="${GOOGLE_PLAY_MIN_TARGET_SDK:-34}"
BUILD_NUMBER="${GOOGLE_PLAY_BUILD_NUMBER:-$(date +%s)}"
VERSION_NAME="${GOOGLE_PLAY_VERSION_NAME:-1.${BUILD_NUMBER}.0}"

DEFAULT_LANGUAGE="${GOOGLE_PLAY_DEFAULT_LANGUAGE:-en-US}"
LISTING_TITLE="${GOOGLE_PLAY_LISTING_TITLE:-}"
SHORT_DESCRIPTION="${GOOGLE_PLAY_SHORT_DESCRIPTION:-}"
FULL_DESCRIPTION="${GOOGLE_PLAY_FULL_DESCRIPTION:-}"
PRIVACY_POLICY_URL="${GOOGLE_PLAY_PRIVACY_POLICY_URL:-https://www.grenobleski.fr/privacy}"
CHANGES_NOT_SENT_FOR_REVIEW="${GOOGLE_PLAY_CHANGES_NOT_SENT_FOR_REVIEW:-true}"

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] Missing command: $cmd"
    exit 1
  fi
}

validate_bool() {
  local value="${1,,}"
  [[ "$value" == "true" || "$value" == "false" ]]
}

verify_target_sdk_guard() {
  local min_required="$1"
  local gradle_file="$ANDROID_DIR/app/build.gradle.kts"

  if ! [[ "$min_required" =~ ^[0-9]+$ ]]; then
    echo "[ERROR] GOOGLE_PLAY_MIN_TARGET_SDK must be an integer (got '$min_required')."
    exit 1
  fi

  if [[ ! -f "$gradle_file" ]]; then
    echo "[WARN] Could not find $gradle_file. Skipping targetSdk guard."
    return
  fi

  local current_target
  current_target="$(sed -n 's/^\s*targetSdk\s*=\s*\([0-9]\+\).*/\1/p' "$gradle_file" | head -n1)"

  if [[ -z "$current_target" ]]; then
    echo "[WARN] Could not parse targetSdk from $gradle_file."
    return
  fi

  if (( current_target < min_required )); then
    echo "[ERROR] targetSdk=$current_target is below required minimum ($min_required)."
    echo "        Update grenobleski_android_native/app/build.gradle.kts before publishing."
    exit 1
  fi

  echo "[INFO] targetSdk guard passed ($current_target >= $min_required)."
}

print_config() {
  echo "[INFO] Google Play deploy configuration"
  echo "       Package:   $PACKAGE_NAME"
  echo "       Track:     $TRACK"
  echo "       Status:    $RELEASE_STATUS"
  echo "       Rollout:   $ROLLOUT_PERCENTAGE%"
  echo "       BuildNum:  $BUILD_NUMBER"
  echo "       Version:   $VERSION_NAME"
  echo "       First:     $FIRST_DEPLOY"
  echo "       DryRun:    $DRY_RUN"
  echo "       Validate:  $VALIDATE_ONLY"
}

if ! validate_bool "$DRY_RUN" || ! validate_bool "$VALIDATE_ONLY" || ! validate_bool "$FIRST_DEPLOY" || ! validate_bool "$ACK_MANUAL_COMPLIANCE" || ! validate_bool "$CHANGES_NOT_SENT_FOR_REVIEW"; then
  echo "[ERROR] Boolean env vars must be true or false."
  exit 1
fi

print_config

require_cmd python3
require_cmd java

if [[ "$VALIDATE_ONLY" == "true" ]]; then
  require_cmd "${ANDROID_DIR}/gradlew"

  missing_vars=()
  missing_files=()

  if [[ -z "$SERVICE_ACCOUNT_JSON" ]]; then
    missing_vars+=("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
  elif [[ ! -f "$SERVICE_ACCOUNT_JSON" ]]; then
    missing_files+=("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=$SERVICE_ACCOUNT_JSON")
  fi

  if [[ -z "$AAB_PATH" ]]; then
    [[ -z "${ANDROID_SIGNING_KEYSTORE_PATH:-}" ]] && missing_vars+=("ANDROID_SIGNING_KEYSTORE_PATH")
    [[ -z "${ANDROID_SIGNING_STORE_PASS:-}" ]] && missing_vars+=("ANDROID_SIGNING_STORE_PASS")
    [[ -z "${ANDROID_SIGNING_KEY_ALIAS:-}" ]] && missing_vars+=("ANDROID_SIGNING_KEY_ALIAS")
    [[ -z "${ANDROID_SIGNING_KEY_PASS:-}" ]] && missing_vars+=("ANDROID_SIGNING_KEY_PASS")

    if [[ -n "${ANDROID_SIGNING_KEYSTORE_PATH:-}" && ! -f "${ANDROID_SIGNING_KEYSTORE_PATH}" ]]; then
      missing_files+=("ANDROID_SIGNING_KEYSTORE_PATH=${ANDROID_SIGNING_KEYSTORE_PATH}")
    fi
  elif [[ ! -f "$AAB_PATH" ]]; then
    missing_files+=("GOOGLE_PLAY_AAB_PATH=$AAB_PATH")
  fi

  if (( ${#missing_vars[@]} > 0 )); then
    echo "[ERROR] Missing required vars:"
    for v in "${missing_vars[@]}"; do
      echo "  - $v"
    done
  fi

  if (( ${#missing_files[@]} > 0 )); then
    echo "[ERROR] Missing file paths:"
    for f in "${missing_files[@]}"; do
      echo "  - $f"
    done
  fi

  if (( ${#missing_vars[@]} > 0 || ${#missing_files[@]} > 0 )); then
    echo "[VALIDATE] FAILED"
    exit 2
  fi

  verify_target_sdk_guard "$MIN_TARGET_SDK"
  echo "[VALIDATE] OK"
  exit 0
fi

if [[ "$DRY_RUN" != "true" ]]; then
  if [[ -z "$SERVICE_ACCOUNT_JSON" || ! -f "$SERVICE_ACCOUNT_JSON" ]]; then
    echo "[ERROR] GOOGLE_PLAY_SERVICE_ACCOUNT_JSON must point to an existing JSON file."
    exit 1
  fi
fi

if [[ -z "$AAB_PATH" ]]; then
  require_cmd "${ANDROID_DIR}/gradlew"

  if [[ "$DRY_RUN" != "true" ]]; then
    : "${ANDROID_SIGNING_KEYSTORE_PATH:?ANDROID_SIGNING_KEYSTORE_PATH is required when GOOGLE_PLAY_AAB_PATH is not provided}"
    : "${ANDROID_SIGNING_STORE_PASS:?ANDROID_SIGNING_STORE_PASS is required when GOOGLE_PLAY_AAB_PATH is not provided}"
    : "${ANDROID_SIGNING_KEY_ALIAS:?ANDROID_SIGNING_KEY_ALIAS is required when GOOGLE_PLAY_AAB_PATH is not provided}"
    : "${ANDROID_SIGNING_KEY_PASS:?ANDROID_SIGNING_KEY_PASS is required when GOOGLE_PLAY_AAB_PATH is not provided}"

    if [[ ! -f "$ANDROID_SIGNING_KEYSTORE_PATH" ]]; then
      echo "[ERROR] Keystore file not found: $ANDROID_SIGNING_KEYSTORE_PATH"
      exit 1
    fi
  fi

  echo "[INFO] Building release AAB with Gradle..."
  pushd "$ANDROID_DIR" >/dev/null
  KEYSTORE_PATH="${ANDROID_SIGNING_KEYSTORE_PATH:-}" \
  KEY_STORE_PASSWORD="${ANDROID_SIGNING_STORE_PASS:-}" \
  KEY_ALIAS="${ANDROID_SIGNING_KEY_ALIAS:-}" \
  KEY_PASSWORD="${ANDROID_SIGNING_KEY_PASS:-}" \
  BUILD_NUMBER="$BUILD_NUMBER" \
  VERSION_NAME="$VERSION_NAME" \
  ./gradlew bundleRelease --no-daemon
  popd >/dev/null

  AAB_PATH="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"
fi

if [[ ! -f "$AAB_PATH" ]]; then
  echo "[ERROR] AAB not found: $AAB_PATH"
  exit 1
fi

verify_target_sdk_guard "$MIN_TARGET_SDK"

ARGS=(
  --service-account "$SERVICE_ACCOUNT_JSON"
  --package-name "$PACKAGE_NAME"
  --track "$TRACK"
  --release-status "$RELEASE_STATUS"
  --rollout-percentage "$ROLLOUT_PERCENTAGE"
  --aab "$AAB_PATH"
  --default-language "$DEFAULT_LANGUAGE"
)

if [[ -n "$LISTING_TITLE" ]]; then
  ARGS+=(--listing-title "$LISTING_TITLE")
fi
if [[ -n "$SHORT_DESCRIPTION" ]]; then
  ARGS+=(--short-description "$SHORT_DESCRIPTION")
fi
if [[ -n "$FULL_DESCRIPTION" ]]; then
  ARGS+=(--full-description "$FULL_DESCRIPTION")
fi
if [[ -n "$PRIVACY_POLICY_URL" ]]; then
  ARGS+=(--privacy-policy-url "$PRIVACY_POLICY_URL")
fi
if [[ "$FIRST_DEPLOY" == "true" ]]; then
  ARGS+=(--first-deploy)
fi
if [[ "$ACK_MANUAL_COMPLIANCE" == "true" ]]; then
  ARGS+=(--ack-manual-compliance)
fi
if [[ "$CHANGES_NOT_SENT_FOR_REVIEW" == "true" ]]; then
  ARGS+=(--changes-not-sent-for-review)
fi

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY RUN] Upload command prepared:"
  printf 'python3 %q' "$ROOT_DIR/scripts/google_play_upload.py"
  for arg in "${ARGS[@]}"; do
    printf ' %q' "$arg"
  done
  printf '\n'
  exit 0
fi

python3 "$ROOT_DIR/scripts/google_play_upload.py" "${ARGS[@]}"

echo "[OK] Google Play deployment finished."
