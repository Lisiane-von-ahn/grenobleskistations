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

DEBUG=False
ALLOWED_HOSTS=${APP_HOSTNAME},${APP_HOSTNAME_WWW},localhost,127.0.0.1,[::1]
DJANGO_SECRET_KEY=${secret_key}
WEATHER_API_KEY=${WEATHER_API_KEY_ARG}
RUN_SEED_ON_STARTUP=false

DATABASE_URL=${database_url_effective}
BOOTSTRAP_ADMIN_USERNAME=${BOOTSTRAP_ADMIN_USERNAME_ARG}
BOOTSTRAP_ADMIN_PASSWORD=${BOOTSTRAP_ADMIN_PASSWORD_ARG}
BOOTSTRAP_ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL_ARG}

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
    if docker compose "${compose_files[@]}" exec -T web python manage.py migrate --noinput && \
       docker compose "${compose_files[@]}" exec -T web python manage.py ensure_bootstrap_admin; then
      migrate_ok=true
      break
    fi
    echo "Migrate attempt ${i}/20 failed, retrying in 5s..."
    sleep 5
  done

  if [[ "${migrate_ok}" != "true" ]]; then
    echo "❌ Migrate failed after retries"
    docker compose "${compose_files[@]}" logs --tail=200 web || true
    exit 1
  fi

  echo "🌱 Running seed in configured PostgreSQL database"
  local seed_ok=false
  for i in $(seq 1 20); do
    if docker compose "${compose_files[@]}" exec -T web python /app/load_ski_stations.py; then
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
