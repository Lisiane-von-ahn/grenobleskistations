#!/usr/bin/env bash
set -euo pipefail

# Production rollout helper for GrenobleSki API + BeeWare-facing endpoints.
# Run this on the production server inside the project directory.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -f docker-compose.yml ]]; then
  echo "ERROR: docker-compose.yml not found in ${ROOT_DIR}" >&2
  exit 1
fi

COMPOSE_FILES=("-f" "docker-compose.yml")
if [[ -f docker-compose.letsencrypt.yml && "${USE_INTERNAL_TRAEFIK:-false}" == "true" ]]; then
  COMPOSE_FILES+=("-f" "docker-compose.letsencrypt.yml")
fi

echo "==> Pulling latest code"
git pull --ff-only

echo "==> Building and starting web stack"
docker compose "${COMPOSE_FILES[@]}" up -d --build web

echo "==> Applying migrations"
docker compose "${COMPOSE_FILES[@]}" run --rm --no-deps --entrypoint "" web python manage.py migrate --noinput

echo "==> Ensuring bootstrap admin"
docker compose "${COMPOSE_FILES[@]}" run --rm --no-deps --entrypoint "" web python manage.py ensure_bootstrap_admin

echo "==> Restarting web container"
docker compose "${COMPOSE_FILES[@]}" restart web

echo "==> Checking local OpenAPI from container"
OPENAPI_JSON="$(curl -fsS http://127.0.0.1:8081/swagger/?format=openapi)"
python3 - <<'PY' <<<"${OPENAPI_JSON}"
import json
import sys

required = {
    "/auth/login/",
    "/auth/register/",
    "/auth/google-login/",
    "/auth/me/",
    "/auth/logout/",
    "/messages/",
    "/skistories/",
    "/skipartnerposts/",
    "/instructorservices/",
}

spec = json.loads(sys.stdin.read())
paths = set(spec.get("paths", {}).keys())
missing = sorted(required - paths)

if missing:
    print("Missing required paths in local OpenAPI:")
    for p in missing:
        print(" -", p)
    raise SystemExit(1)

print("Local OpenAPI check OK.")
PY

echo "==> Checking public OpenAPI (www.grenobleski.fr)"
PUBLIC_OPENAPI="$(curl -fsS https://www.grenobleski.fr/swagger/?format=openapi)"
python3 - <<'PY' <<<"${PUBLIC_OPENAPI}"
import json
import sys

required = {
    "/auth/login/",
    "/auth/register/",
    "/auth/google-login/",
    "/auth/me/",
    "/auth/logout/",
    "/messages/",
    "/skistories/",
    "/skipartnerposts/",
    "/instructorservices/",
}

spec = json.loads(sys.stdin.read())
paths = set(spec.get("paths", {}).keys())
missing = sorted(required - paths)

if missing:
    print("Public OpenAPI is not yet updated. Missing:")
    for p in missing:
        print(" -", p)
    raise SystemExit(1)

print("Public OpenAPI check OK.")
PY

echo "==> Rollout completed successfully"
