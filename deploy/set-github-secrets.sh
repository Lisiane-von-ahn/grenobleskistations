#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./deploy/set-github-secrets.sh [secrets_file] [repo]
#
# Example:
#   ./deploy/set-github-secrets.sh .github/secrets/github-actions.secrets your-org/grenobleskistations

SECRETS_FILE="${1:-.github/secrets/github-actions.secrets}"
REPO="${2:-}"

need_cmd() { command -v "$1" >/dev/null 2>&1; }

if ! need_cmd gh; then
  echo "❌ GitHub CLI (gh) not found. Install it first: https://cli.github.com/"
  exit 1
fi

if [[ ! -f "${SECRETS_FILE}" ]]; then
  echo "❌ Secrets file not found: ${SECRETS_FILE}"
  echo "   Copy .github/secrets/github-actions.secrets.example to ${SECRETS_FILE} and fill values."
  exit 1
fi

if [[ -n "${REPO}" ]]; then
  GH_REPO_ARGS=(--repo "${REPO}")
else
  GH_REPO_ARGS=()
fi

echo "🔐 Setting GitHub Actions secrets from ${SECRETS_FILE}"

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" ]] && continue
  [[ "$line" =~ ^[[:space:]]*# ]] && continue

  if [[ "$line" != *=* ]]; then
    echo "⚠️ Skipping invalid line (no '='): $line"
    continue
  fi

  key="${line%%=*}"
  value="${line#*=}"

  key="$(echo "$key" | xargs)"

  if [[ -z "$key" ]]; then
    continue
  fi

  printf '%s' "$value" | gh secret set "$key" "${GH_REPO_ARGS[@]}"
  echo "✅ $key"
done < "$SECRETS_FILE"

echo "🎉 Done. Secrets have been uploaded."
