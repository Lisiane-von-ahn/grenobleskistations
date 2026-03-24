# Django Migrations CI/CD Guide

## Overview

This project includes comprehensive GitHub Actions workflows to ensure Django migrations are properly created, tested, and validated before deployment.

## Workflows

### 1. **Migrations Validation** (`.github/workflows/migrations.yml`)

Runs on every push and pull request that touches:
- `api/models.py` - Model definitions
- `api/migrations/` - Migration files
- `skistation_project/` - Settings and apps
- `requirements.txt` - Dependencies

**What it checks:**

✅ **Syntax Validation**
- All migration files have valid Python syntax
- No import errors or typos

✅ **Model Changes Detection**
- Detects if models were changed but migrations weren't created
- Fails with clear error message if migrations are missing

✅ **Database Testing**
- Spins up PostgreSQL
- Tests migrations on a fresh database
- Verifies migrations can be rolled back
- Re-applies migrations to ensure idempotency

✅ **Migration Dependencies**
- Checks for circular dependencies
- Validates migration chain integrity
- Ensures proper dependency ordering

✅ **Django System Checks**
- Validates Django configuration
- Checks database connections
- Verifies app readiness

### 2. **CI Pipeline** (`.github/workflows/ci.yml`)

Runs on every push and pull request for the entire codebase.

**Enhanced migration checks:**
- Blocks PRs with unmigrated model changes
- Requires all migration files to be committed
- Provides helpful error messages and fix instructions

## Local Development

### Quick Start

1. **Create a new model change:**
   ```bash
   # Edit api/models.py
   vim api/models.py
   ```

2. **Generate migrations:**
   ```bash
   python manage.py makemigrations
   ```

3. **Apply migrations locally:**
   ```bash
   python manage.py migrate
   ```

4. **Commit migration files:**
   ```bash
   git add api/migrations/
   git commit -m "Add new fields to UserProfile model"
   ```

### Useful Migration Commands

```bash
# Show migration status
python manage.py showmigrations

# Show detailed migration graph
python manage.py showmigrations --verbose

# Check if migrations need to be created
python manage.py makemigrations --check

# Dry-run migrations (see what would happen)
python manage.py migrate --plan

# Rollback to a specific migration
python manage.py migrate api 0005_previous_migration

# Rollback all migrations for an app
python manage.py migrate api zero

# Show all migrations for a specific app
python manage.py showmigrations api
```

### Setting Up Pre-commit Hooks

Prevent committing unmigrated changes:

```bash
# Copy the pre-commit hook
cp .githooks/pre-commit .git/hooks/pre-commit

# Make it executable
chmod +x .git/hooks/pre-commit
```

Now every commit will automatically check for unmigrated model changes!

## Common Issues & Solutions

### ❌ "Unmigrated model changes detected"

**Cause:** You modified `models.py` but didn't create migrations.

**Fix:**
```bash
python manage.py makemigrations
git add api/migrations/
git commit -m "Add migrations for model changes"
```

### ❌ "Migration files are uncommitted"

**Cause:** Migration files exist but aren't staged for commit.

**Fix:**
```bash
python manage.py makemigrations
git add api/migrations/
git commit
```

### ❌ "Failed to apply migrations"

**Cause:** Migration has a syntax error or references missing models.

**Fix:**
1. Review the migration file: `cat api/migrations/XXXX_name.py`
2. Check for typos in model names
3. Ensure all model imports are present
4. Test locally: `python manage.py migrate`

### ❌ "Circular dependencies in migrations"

**Cause:** Two migrations depend on each other.

**Fix:**
1. Review dependency chains
2. Delete and regenerate: `rm api/migrations/XXXX_name.py`
3. Create fresh migration: `python manage.py makemigrations`

### ⚠️  "Django system checks failed"

**Cause:** Configuration or app loading issue.

**Fix:**
```bash
python manage.py check
# Review error messages and fix issues
```

## GitHub Actions Workflow Details

### Workflow Events

Migrations workflow triggers on:
- **Push** to any branch (if migrations/models changed)
- **Pull requests** (if migrations/models changed)

This ensures CI/CD checks run on:
- Feature branches before PR creation
- PRs before merge to main/develop

### Workflow Environment

- **Python:** 3.10
- **Database:** PostgreSQL 15
- **OS:** Ubuntu latest
- **Timeout:** 15 minutes per job

### Workflow Outputs

#### Success ✅
```
✅ All migration validation checks passed!

Summary:
  • All migration files have valid syntax
  • Migrations apply cleanly to fresh database
  • Migrations can be rolled back and reapplied
  • Django system checks passed
  • No circular dependencies detected
```

#### Failure ❌
```
❌ Migration validation failed!

Common issues:
  • Missing migration files (run: python manage.py makemigrations)
  • Circular dependencies in migrations
  • Migration syntax errors
  • Failed to rollback/reapply migrations

💡 To fix locally:
  python manage.py makemigrations --check --dry-run
  python manage.py migrate --noinput
```

## Migration Best Practices

### ✅ DO

- ✅ Create migrations for every model change
- ✅ Commit migration files to git immediately
- ✅ Use meaningful migration names: `python manage.py makemigrations --name add_user_profile_fields`
- ✅ Test migrations on a fresh database locally
- ✅ Keep migrations small and focused
- ✅ Use `--dry-run` to preview changes: `python manage.py migrate --plan`
- ✅ Review migration files before committing

### ❌ DON'T

- ❌ Manually edit migration files (unless you know what you're doing)
- ❌ Skip migrations and push directly to production
- ❌ Delete migration files once pushed to shared repos
- ❌ Squash migrations without coordination with team
- ❌ Create migrations for every small change (batch small changes)
- ❌ Ignore migration errors in PR reviews

## Deployment

### Before Deployment

1. **Ensure all migrations are created:**
   ```bash
   python manage.py makemigrations --check
   ```

2. **Verify migrations pass CI:**
   - Check GitHub Actions status
   - All workflows must pass green ✅

3. **Test migrations locally:**
   ```bash
   python manage.py migrate --plan
   python manage.py migrate
   ```

### During Deployment

The deployment pipeline (`.github/workflows/deploy.yml`) automatically:
1. Checks out latest code
2. Verifies migrations are committed
3. Applies migrations to production database
4. Verifies migration success
5. Rolls back if anything fails

**Deployment order:**
```bash
1. python manage.py migrate --noinput  # Apply all pending migrations
2. Restart Django application
3. Verify health checks pass
```

## Advanced Topics

### Squashing Migrations

If you have too many migrations:

```bash
python manage.py squashmigrations api 0001 0015 -n squashed_combined

# This creates a single migration from 0001 to 0015
# Then deploy and delete old migrations
```

### Renaming Migrations

If you need to rename a migration:

```bash
# Only do this if migration hasn't been applied to production!
git mv api/migrations/0015_old_name.py api/migrations/0015_new_name.py

# Update references in dependency files:
grep -r "0015_old_name" api/migrations/
```

### Reversible Migrations

Always use reversible SQL operations:

```python
# Good - reversible
migrations.RunSQL(
    sql='ALTER TABLE api_user ADD COLUMN age INT;',
    reverse_sql='ALTER TABLE api_user DROP COLUMN age;'
)

# Bad - not reversible
migrations.RunSQL(
    sql='ALTER TABLE api_user ADD COLUMN age INT;',
    reverse_sql=migrations.RunSQL.noop
)
```

## Support & Troubleshooting

### Getting Help

1. **Check the CI/CD logs:**
   - Go to repo → Actions → Latest failed workflow
   - Click on "Migrations Validation" job
   - Review error messages and logs

2. **Run local validation:**
   ```bash
   python manage.py makemigrations --check --dry-run
   python manage.py migrate --plan
   ```

3. **Contact the team:**
   - Create issue with workflow logs
   - Include model changes and errors

### Useful Links

- [Django Migrations Docs](https://docs.djangoproject.com/en/4.2/topics/migrations/)
- [GitHub Actions Docs](https://docs.github.com/en/actions)
- [PostgreSQL Docs](https://www.postgresql.org/docs/)

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-24 | Initial migration CI/CD setup |
| 1.1 | 2026-03-24 | Added migrations.yml workflow |
| 1.2 | 2026-03-24 | Enhanced CI pipeline with better error messages |

---

**Last Updated:** March 24, 2026  
**Maintainer:** DevOps Team
