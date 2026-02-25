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

if [[ "${RUN_SEED_ON_STARTUP:-true}" == "true" ]]; then
    SHOULD_SEED=$(python - <<'PY'
import os
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'skistation_project.settings')
import django
django.setup()
from api.models import SkiStation
print("yes" if SkiStation.objects.count() == 0 else "no")
PY
)

    if [[ "${SHOULD_SEED}" == "yes" ]]; then
        echo "Running initial seed..."
        python /app/load_ski_stations.py
    else
        echo "Seed skipped (data already present)."
    fi
fi

python manage.py collectstatic --noinput

exec "$@"
