#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USERS="${USERS:-100}"
STORIES="${STORIES:-400}"
MAX_LIKES="${MAX_LIKES:-30}"
MAX_COMMENTS="${MAX_COMMENTS:-12}"
PASSWORD="${PASSWORD:-SkiStories123!}"
RESET="${RESET:-true}"

pushd "$ROOT_DIR" >/dev/null

if [[ -x "$ROOT_DIR/.venv/bin/python" ]]; then
  PYTHON_BIN="$ROOT_DIR/.venv/bin/python"
else
  PYTHON_BIN="python3"
fi

"$PYTHON_BIN" manage.py migrate

CMD=(
  "$PYTHON_BIN" manage.py seed_story_feed_data
  --users "$USERS"
  --stories "$STORIES"
  --max-likes "$MAX_LIKES"
  --max-comments "$MAX_COMMENTS"
  --password "$PASSWORD"
)

if [[ "$RESET" == "true" ]]; then
  CMD+=(--reset)
fi

"${CMD[@]}"

"$PYTHON_BIN" manage.py fetch_ski_news_rss --sample-on-failure --max-items 80

popd >/dev/null

echo "[OK] Demo stories data seeded."
