#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Docker deploy installer (used by GitHub Actions)
# =============================================================================
# Calling convention kept compatible with legacy deploy:
#   ./deploy/install.sh HOST USER PASSWORD WEATHER_API_KEY [DB_NAME] [DB_PORT] [DATABASE_URL] [BOOTSTRAP_ADMIN_USERNAME] [BOOTSTRAP_ADMIN_PASSWORD] [BOOTSTRAP_ADMIN_EMAIL]
#
# HOST/USER/PASSWORD/DB_* are DB inputs.
# If DATABASE_URL is provided, it takes precedence.
# If HOST is localhost/127.0.0.1, app uses host.docker.internal from container.
# By default, this script does NOT start an internal Traefik (shared-server safe).
# Set USE_INTERNAL_TRAEFIK=true to enable docker-compose.letsencrypt.yml.
# =============================================================================

DOMAIN="grenobleski.fr"
WWW_DOMAIN="www.grenobleski.fr"
EMAIL="admin@grenobleski.fr"

APP_DIR="/root/grenobleskistations"

DB_HOST_ARG="${1:-}"
DB_USER_ARG="${2:-}"
DB_PASSWORD_ARG="${3:-}"
WEATHER_API_KEY_ARG="${4:-}"
DB_NAME_ARG="${5:-grenobleski}"
DB_PORT_ARG="${6:-5432}"
DATABASE_URL_ARG="${7:-}"
BOOTSTRAP_ADMIN_USERNAME_ARG="${8:-admin}"
BOOTSTRAP_ADMIN_PASSWORD_ARG="${9:-admin}"
BOOTSTRAP_ADMIN_EMAIL_ARG="${10:-admin@grenobleski.local}"

if [[ -z "${DB_HOST_ARG}" || -z "${DB_USER_ARG}" || -z "${DB_PASSWORD_ARG}" ]]; then
  echo "❌ Missing required args. Usage: ./deploy/install.sh HOST USER PASSWORD WEATHER_API_KEY [DB_NAME] [DB_PORT] [DATABASE_URL] [BOOTSTRAP_ADMIN_USERNAME] [BOOTSTRAP_ADMIN_PASSWORD] [BOOTSTRAP_ADMIN_EMAIL]"
  exit 1
fi

APP_HOSTNAME="${DOMAIN}"
APP_HOSTNAME_WWW="${WWW_DOMAIN}"
USE_INTERNAL_TRAEFIK="${USE_INTERNAL_TRAEFIK:-false}"
GOOGLE_WEB_CLIENT_ID="${GOOGLE_WEB_CLIENT_ID:-}"
GOOGLE_WEB_CLIENT_SECRET="${GOOGLE_WEB_CLIENT_SECRET:-}"
GOOGLE_WEB_CLIENT_KEY="${GOOGLE_WEB_CLIENT_KEY:-}"
ENABLE_WEB_ADS="${ENABLE_WEB_ADS:-false}"
WEB_ADS_PROVIDER="${WEB_ADS_PROVIDER:-none}"
ADSENSE_CLIENT_ID="${ADSENSE_CLIENT_ID:-}"
ADSENSE_SLOT_FOOTER="${ADSENSE_SLOT_FOOTER:-}"
PROPELLERADS_SCRIPT_SRC="${PROPELLERADS_SCRIPT_SRC:-}"
PROPELLERADS_CONTAINER_ID="${PROPELLERADS_CONTAINER_ID:-}"
WEB_ADS_SCRIPT_SRC="${WEB_ADS_SCRIPT_SRC:-}"
WEB_ADS_CONTAINER_ID="${WEB_ADS_CONTAINER_ID:-}"
ADS_TXT_CONTENT="${ADS_TXT_CONTENT:-}"

need_cmd() { command -v "$1" >/dev/null 2>&1; }

rand_hex_64() {
  if need_cmd openssl; then
    openssl rand -hex 32
  else
    python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
  fi
}

install_docker_if_needed() {
  if need_cmd docker && docker compose version >/dev/null 2>&1; then
    echo "✅ Docker + docker compose already installed"
    return
  fi

  echo "🧩 Installing Docker Engine + docker compose plugin"
  apt-get update
  apt-get install -y ca-certificates curl gnupg lsb-release
  install -m 0755 -d /etc/apt/keyrings

  OS_ID="$(. /etc/os-release && echo "$ID")"
  OS_CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"

  curl -fsSL "https://download.docker.com/linux/${OS_ID}/gpg" | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg

  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${OS_ID} ${OS_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

  systemctl enable docker
  systemctl restart docker
  docker compose version
  echo "✅ Docker installed"
}

free_ports_80_443() {
  if systemctl list-unit-files | grep -q '^nginx\.service'; then
    echo "🧹 Stopping/disabling nginx (free 80/443)"
    systemctl stop nginx || true
    systemctl disable nginx || true
  fi

  if systemctl list-unit-files | grep -q '^grenobleski\.service'; then
    echo "🧹 Stopping/disabling legacy grenobleski service"
    systemctl stop grenobleski || true
    systemctl disable grenobleski || true
  fi
}

ensure_app_dir() {
  if [[ -d "${APP_DIR}" ]]; then
    return
  fi

  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local fallback_dir
  fallback_dir="$(cd "${script_dir}/.." && pwd)"

  if [[ -f "${fallback_dir}/docker-compose.yml" ]]; then
    APP_DIR="${fallback_dir}"
    return
  fi

  echo "❌ App directory not found: ${APP_DIR}"
  exit 1
}

ensure_env_file() {
  cd "${APP_DIR}"

  local env_file="${APP_DIR}/.env"
  local secret_key=""

  if [[ -f "${env_file}" ]]; then
    secret_key="$(grep -E '^DJANGO_SECRET_KEY=' "${env_file}" | tail -n1 | cut -d= -f2- || true)"
  fi
  if [[ -z "${secret_key}" ]]; then
    secret_key="$(rand_hex_64)"
  fi

  local db_host_effective="${DB_HOST_ARG}"
  if [[ "${DB_HOST_ARG}" == "localhost" || "${DB_HOST_ARG}" == "127.0.0.1" || "${DB_HOST_ARG}" == "::1" ]]; then
    db_host_effective="host.docker.internal"
  fi

  local database_url_effective="${DATABASE_URL_ARG}"
  if [[ -z "${database_url_effective}" ]]; then
    database_url_effective="postgresql://${DB_USER_ARG}:${DB_PASSWORD_ARG}@${db_host_effective}:${DB_PORT_ARG}/${DB_NAME_ARG}"
  fi

  echo "🔐 Writing .env for docker compose"
  umask 077
  cat > "${env_file}" <<EOF
APP_HOSTNAME=${APP_HOSTNAME}
APP_HOSTNAME_WWW=${APP_HOSTNAME_WWW}
LETSENCRYPT_EMAIL=${EMAIL}
DJANGO_SITE_DOMAIN=${APP_HOSTNAME_WWW}
DJANGO_SITE_NAME=GrenobleSki
SOCIAL_AUTH_GOOGLE_OAUTH2_REDIRECT_URL=https://${APP_HOSTNAME_WWW}/accounts/google/login/callback/
MOBILE_APP_AUTH_REDIRECT=grenobleski://auth

DEBUG=False
ALLOWED_HOSTS=${APP_HOSTNAME},${APP_HOSTNAME_WWW},localhost,127.0.0.1,[::1]
DJANGO_SECRET_KEY=${secret_key}
WEATHER_API_KEY=${WEATHER_API_KEY_ARG}
RUN_SEED_ON_STARTUP=false
RUN_MIGRATIONS_ON_STARTUP=false

DATABASE_URL=${database_url_effective}
BOOTSTRAP_ADMIN_USERNAME=${BOOTSTRAP_ADMIN_USERNAME_ARG}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD_ARG}
BOOTSTRAP_ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL_ARG}
GOOGLE_WEB_CLIENT_ID=${GOOGLE_WEB_CLIENT_ID}
GOOGLE_WEB_CLIENT_SECRET=${GOOGLE_WEB_CLIENT_SECRET}
GOOGLE_WEB_CLIENT_KEY=${GOOGLE_WEB_CLIENT_KEY}
ENABLE_WEB_ADS=${ENABLE_WEB_ADS}
WEB_ADS_PROVIDER=${WEB_ADS_PROVIDER}
ADSENSE_CLIENT_ID=${ADSENSE_CLIENT_ID}
ADSENSE_SLOT_FOOTER=${ADSENSE_SLOT_FOOTER}
PROPELLERADS_SCRIPT_SRC=${PROPELLERADS_SCRIPT_SRC}
PROPELLERADS_CONTAINER_ID=${PROPELLERADS_CONTAINER_ID}
WEB_ADS_SCRIPT_SRC=${WEB_ADS_SCRIPT_SRC}
WEB_ADS_CONTAINER_ID=${WEB_ADS_CONTAINER_ID}
ADS_TXT_CONTENT=${ADS_TXT_CONTENT}

POSTGRES_DB=${DB_NAME_ARG}
POSTGRES_USER=${DB_USER_ARG}
POSTGRES_PASSWORD=${DB_PASSWORD_ARG}
EOF
  chmod 600 "${env_file}"
}

run_compose() {
  cd "${APP_DIR}"

  if [[ ! -f docker-compose.yml ]]; then
    echo "❌ Missing docker-compose.yml in ${APP_DIR}"
    exit 1
  fi

  local compose_files=("-f" "docker-compose.yml")

  if [[ "${USE_INTERNAL_TRAEFIK}" == "true" ]]; then
    if [[ ! -f docker-compose.letsencrypt.yml ]]; then
      echo "❌ Missing docker-compose.letsencrypt.yml in ${APP_DIR}"
      exit 1
    fi
    compose_files+=("-f" "docker-compose.letsencrypt.yml")
  fi

  echo "🐳 Starting/updating stack"
  if ! docker compose "${compose_files[@]}" up -d --build; then
    echo "⚠️ compose up failed; trying clean restart"
    docker compose "${compose_files[@]}" down || true
    docker compose "${compose_files[@]}" up -d --build
  fi

  echo "✅ Stack running"
  docker compose ps

  echo "🗄️ Ensuring migrations are applied before seed"
  local migrate_ok=false
  for i in $(seq 1 20); do
    local migrate_step_ok=false
    if docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py migrate --noinput; then
      migrate_step_ok=true
    else
      echo "⚠️ Migrate command failed; checking whether migrations are already fully applied"
      if docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py migrate --check --noinput; then
        migrate_step_ok=true
      fi
    fi

    if [[ "${migrate_step_ok}" == "true" ]] && \
       docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py ensure_bootstrap_admin && \
       docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py ensure_google_social_app; then
      migrate_ok=true
      break
    fi
    echo "Migrate attempt ${i}/20 failed, retrying in 5s..."
    docker compose "${compose_files[@]}" logs --tail=80 web || true
    sleep 5
  done

  if [[ "${migrate_ok}" != "true" ]]; then
    echo "❌ Migrate failed after retries"
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "✅ Verifying no pending migrations remain"
  if ! docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py migrate --check --noinput; then
    echo "❌ Pending migrations detected after migrate"
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "🔍 Verifying migration files are in sync with models"
  if ! docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python manage.py makemigrations --check --dry-run; then
    echo "❌ Model changes detected without migration files"
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "🌱 Running seed in configured PostgreSQL database"
  local seed_ok=false
  for i in $(seq 1 20); do
    if docker compose "${compose_files[@]}" run --rm --no-deps --entrypoint "" web python /app/load_ski_stations.py; then
      seed_ok=true
      break
    fi
    echo "Seed attempt ${i}/20 failed, retrying in 5s..."
    sleep 5
  done

  if [[ "${seed_ok}" != "true" ]]; then
    echo "❌ Seed failed after retries"
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "✅ Seed completed"

  echo "⏳ Waiting for web container health"
  local web_container_id=""
  local health_ok=false
  local unhealthy_streak=0
  for i in $(seq 1 60); do
    web_container_id="$(docker compose "${compose_files[@]}" ps -q web 2>/dev/null || true)"
    if [[ -z "${web_container_id}" ]]; then
      echo "web container: missing"
      sleep 5
      continue
    fi

    local health_state
    health_state="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${web_container_id}" 2>/dev/null || echo "missing")"
    local container_state
    container_state="$(docker inspect --format='{{.State.Status}}' "${web_container_id}" 2>/dev/null || echo "missing")"
    local restart_count
    restart_count="$(docker inspect --format='{{.RestartCount}}' "${web_container_id}" 2>/dev/null || echo "0")"

    echo "web state: ${container_state}, health: ${health_state}, restarts: ${restart_count}"

    if [[ "${restart_count}" -ge 3 ]]; then
      echo "⚠️ Web container restart count is high (${restart_count}), continuing while health is checked."
    fi

    if [[ "${health_state}" == "unhealthy" ]]; then
      unhealthy_streak=$((unhealthy_streak + 1))
    else
      unhealthy_streak=0
    fi

    if [[ "${unhealthy_streak}" -ge 6 ]]; then
      echo "❌ Web container unhealthy for too long (${unhealthy_streak} consecutive checks)."
      docker compose "${compose_files[@]}" ps || true
      docker compose "${compose_files[@]}" logs --tail=200 web || true
      exit 1
    fi

    if [[ "${container_state}" == "running" ]] && [[ "${health_state}" == "healthy" || "${health_state}" == "none" ]]; then
      health_ok=true
      break
    fi

    sleep 5
  done

  if [[ "${health_ok}" != "true" || -z "${web_container_id}" ]]; then
    echo "❌ Web container did not become healthy in time"
    docker compose "${compose_files[@]}" ps || true
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "⏳ Verifying app response from inside web container (:8000)"
  local internal_ok=false
  for i in $(seq 1 40); do
    if docker exec "${web_container_id}" sh -lc "curl -fsS -H 'Host: ${APP_HOSTNAME}' http://127.0.0.1:8000/ >/dev/null"; then
      internal_ok=true
      echo "✅ App responds inside container"
      break
    fi
    sleep 3
  done

  if [[ "${internal_ok}" != "true" ]]; then
    echo "❌ App is not responding on internal HTTP endpoint"
    docker compose "${compose_files[@]}" ps || true
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "⏳ Checking app reachability on host port 8081 (best effort)"
  local host_ok=false
  for i in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:8081" >/dev/null || curl -fsS -H "Host: ${APP_HOSTNAME}" "http://127.0.0.1:8081" >/dev/null; then
      host_ok=true
      echo "✅ App is reachable on host port 8081"
      break
    fi
    sleep 2
  done

  if [[ "${host_ok}" != "true" ]]; then
    echo "⚠️ Host port 8081 not reachable yet, but container is healthy and serving internally."
    echo "ℹ️ Continuing deployment; verify reverse proxy routing if needed."
  fi

  echo
  if [[ "${USE_INTERNAL_TRAEFIK}" == "true" ]]; then
    echo "🌍 App: https://${APP_HOSTNAME}/ and https://${APP_HOSTNAME_WWW}/"
  else
    echo "🌍 App: http://SERVER_IP:8081 (mode Traefik externe)"
  fi
}

main() {
  ensure_app_dir
  install_docker_if_needed
  if [[ "${USE_INTERNAL_TRAEFIK}" == "true" ]]; then
    free_ports_80_443
  else
    echo "ℹ️ USE_INTERNAL_TRAEFIK=false: keep existing reverse proxy on ports 80/443"
  fi
  ensure_env_file
  run_compose
}

main "$@"
