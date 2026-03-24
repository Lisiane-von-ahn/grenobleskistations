#!/usr/bin/env bash
# Django Migrations Quick Reference Card
# Copy this file contents for quick access: cat docs/MIGRATIONS_QUICK_REFERENCE.sh

# ═══════════════════════════════════════════════════════════════════════════
# 🚀 QUICK START - Essential Commands Only
# ═══════════════════════════════════════════════════════════════════════════

# After editing models (api/models.py):
python manage.py makemigrations          # 1. Create migration files
python manage.py migrate                 # 2. Apply to local database
git add api/migrations/                  # 3. Stage migration files
git commit -m "Add migration for X"      # 4. Commit

# ═══════════════════════════════════════════════════════════════════════════
# ✅ PRE-COMMIT CHECKS (GitHub Actions runs these automatically)
# ═══════════════════════════════════════════════════════════════════════════

# Verify no model changes are unmigrated:
python manage.py makemigrations --check --noinput

# Apply all migrations:
python manage.py migrate --noinput

# Check if database is up to date:
python manage.py migrate --check --noinput

# Validate Django configuration:
python manage.py check

# ═══════════════════════════════════════════════════════════════════════════
# 📊 STATUS & DIAGNOSTICS
# ═══════════════════════════════════════════════════════════════════════════

# Show all migrations and their status:
python manage.py showmigrations

# Show detailed migration graph:
python manage.py showmigrations --verbose

# See what migrations will be applied (dry-run):
python manage.py migrate --plan

# Show a specific app's migrations:
python manage.py showmigrations api

# ═══════════════════════════════════════════════════════════════════════════
# 🔧 TROUBLESHOOTING
# ═══════════════════════════════════════════════════════════════════════════

# Revert migrations (roll back to a specific point):
python manage.py migrate api 0010_previous_migration  # Go back to specific
python manage.py migrate api zero                     # Undo all

# Create named migrations (clearer):
python manage.py makemigrations --name add_profile_picture

# Check for unmigrated changes (without creating migration):
python manage.py makemigrations --check --dry-run

# Test migration on fresh database:
python manage.py migrate --noinput

# ═══════════════════════════════════════════════════════════════════════════
# 🐛 WHEN THINGS GO WRONG
# ═══════════════════════════════════════════════════════════════════════════

# Error: "Unmigrated model changes detected"
# → Run: python manage.py makemigrations

# Error: "Migration files are uncommitted"  
# → Run: git add api/migrations/ && git commit

# Error: "Failed to apply migrations"
# → Check: cat api/migrations/XXXX_name.py
# → Look for: syntax errors, missing imports, wrong model names

# Error: "Circular dependencies"
# → Run: python manage.py showmigrations --verbose
# → Find circular references and regenerate affected migrations

# ═══════════════════════════════════════════════════════════════════════════
# 📋 COMMON WORKFLOWS
# ═══════════════════════════════════════════════════════════════════════════

# Add a new field to a model:
# 1. Edit api/models.py (add field)
# 2. python manage.py makemigrations
# 3. python manage.py migrate
# 4. git add api/migrations/ && git commit

# Rename a field:
# 1. Keep old field in code temporarily
# 2. Add new field in models.py
# 3. python manage.py makemigrations
# 4. Edit migration: copy data from old to new
# 5. Add RemoveField operation in migration
# 6. python manage.py migrate
# 7. git add api/migrations/ && git commit

# Delete a field:
# 1. Remove from models.py
# 2. python manage.py makemigrations
# 3. python manage.py migrate
# 4. git add api/migrations/ && git commit

# ═══════════════════════════════════════════════════════════════════════════
# 🔐 BEST PRACTICES CHECKLIST
# ═══════════════════════════════════════════════════════════════════════════

# Before committing:
# ✅ python manage.py makemigrations --check  (no unmigrated changes)
# ✅ python manage.py migrate                 (apply all migrations)
# ✅ git status                               (see what changed)
# ✅ Review migration file: cat api/migrations/XXXX_name.py
# ✅ Test: python manage.py migrate --plan
# ✅ git add api/migrations/
# ✅ git commit -m "Add migrations for X"

# Before pushing to GitHub:
# ✅ All migrations committed to git
# ✅ No uncommitted .py files in api/migrations/
# ✅ Local: python manage.py check passes
# ✅ Local: python manage.py migrate works
# ✅ GitHub Actions: All workflows pass ✓

# ═══════════════════════════════════════════════════════════════════════════
# 🚨 ABSOLUTE DON'Ts
# ═══════════════════════════════════════════════════════════════════════════

# ❌ DON'T edit migration files after they're committed
# ❌ DON'T skip migrations and push directly to production
# ❌ DON'T delete migration files once pushed to shared repo
# ❌ DON'T manually edit the database schema (always use migrations)
# ❌ DON'T forget to commit migration files (they go with your models)
# ❌ DON'T create migrations on one branch then work on another (rebase/merge cleanly)

# ═══════════════════════════════════════════════════════════════════════════
# 📞 NEED HELP?
# ═══════════════════════════════════════════════════════════════════════════

# Full documentation:
# cat MIGRATIONS_CI_CD.md

# GitHub Actions logs:
# Go to: https://github.com/grenobleski/grenobleskistations/actions
# Click on failed workflow → Click job → See detailed logs

# Django docs:
# https://docs.djangoproject.com/en/4.2/topics/migrations/

# Report issue:
# Use issue template: https://github.com/grenobleski/grenobleskistations/issues/new?template=migration-issue.md

# ═══════════════════════════════════════════════════════════════════════════
