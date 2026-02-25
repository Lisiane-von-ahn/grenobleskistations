#!/usr/bin/env bash
set -euo pipefail

cd /app

if [[ -n "${DATABASE_URL:-}" ]]; then
  echo "Waiting for database..."
  python - <<'PY'
import os
import time
import sys
from urllib.parse import urlparse
import psycopg2

db_url = os.getenv("DATABASE_URL")
if not db_url:
    sys.exit(0)

parsed = urlparse(db_url)
max_attempts = 30

for attempt in range(1, max_attempts + 1):
    try:
        conn = psycopg2.connect(
            dbname=parsed.path.lstrip('/'),
            user=parsed.username,
            password=parsed.password,
            host=parsed.hostname,
            port=parsed.port or 5432,
            connect_timeout=3,
        )
        conn.close()
        print("Database ready")
        break
    except Exception as exc:
        if attempt == max_attempts:
            print(f"Database unavailable after {max_attempts} attempts: {exc}", file=sys.stderr)
            sys.exit(1)
        time.sleep(2)
PY
fi

python manage.py migrate --noinput
python manage.py collectstatic --noinput

exec "$@"
