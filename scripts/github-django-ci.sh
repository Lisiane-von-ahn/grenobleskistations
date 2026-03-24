#!/usr/bin/env bash

set -euo pipefail

COMMAND="${1:-}"

if [[ -z "$COMMAND" ]]; then
    echo "Usage: bash scripts/github-django-ci.sh <command>"
    echo "Commands: install-system-deps, install-python-deps, check-unmigrated, wait-postgres, recreate-db, migrate, seed, verify-migrations, check-project"
    exit 1
fi

case "$COMMAND" in
    install-system-deps)
        sudo apt-get update
        sudo apt-get install -y postgresql-client libpq-dev
        ;;
    install-python-deps)
        python -m pip install --upgrade pip
        pip install -r requirements.txt
        ;;
    check-unmigrated)
        python manage.py makemigrations --check --dry-run --noinput || {
            echo "❌ ERROR: Model changes detected without migration files"
            echo "Run locally: python manage.py makemigrations"
            echo "Then commit the migration files"
            exit 1
        }
        ;;
    wait-postgres)
        until pg_isready -h localhost -U postgres; do
            echo "Waiting for PostgreSQL..."
            sleep 1
        done
        ;;
    recreate-db)
        PGPASSWORD=postgres psql -h localhost -U postgres -c "DROP DATABASE IF EXISTS grenobleski_test;" || true
        PGPASSWORD=postgres psql -h localhost -U postgres -c "CREATE DATABASE grenobleski_test;"
        ;;
    migrate)
        python manage.py migrate --noinput || {
            echo "❌ ERROR: Failed to apply migrations"
            exit 1
        }
        ;;
    seed)
        python load_ski_stations.py || {
            echo "❌ ERROR: Failed to seed ski stations data"
            exit 1
        }
        ;;
    verify-migrations)
        python manage.py migrate --check --noinput || {
            echo "❌ ERROR: Database migrations incomplete"
            exit 1
        }
        ;;
    check-project)
        python manage.py check || {
            echo "❌ ERROR: Django system checks failed"
            exit 1
        }
        ;;
    *)
        echo "Unknown command: $COMMAND"
        exit 1
        ;;
esac