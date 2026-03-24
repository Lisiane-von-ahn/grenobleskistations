---
name: Migration Issue
about: Report a problem with Django migrations or CI/CD
title: "[MIGRATION] Brief description"
labels: ["migrations", "ci-cd"]
---

## 📋 Description
<!-- Describe the migration issue you encountered -->

## 🔍 Details

### Environment
- Python version: `python --version`
- Django version: `python manage.py --version`
- Database: PostgreSQL / SQLite / Other
- OS: macOS / Linux / Windows

### Steps to Reproduce
<!-- How can we reproduce the issue? -->

1. 
2. 
3. 

### Expected Behavior
<!-- What should happen? -->

### Actual Behavior
<!-- What's actually happening? -->

### Error Message
<!-- Include the full error output -->
```
[Paste error here]
```

### Current Database State
```bash
# Run these commands and share output
python manage.py showmigrations
python manage.py showmigrations --verbose
```

## 📸 Evidence

- [ ] CI/CD workflow failed - link to run: _____
- [ ] Local migration failed - error log attached
- [ ] Migration conflicts after merge

### Workflow Logs
<!-- If CI/CD failed, link to the GitHub Actions run -->
https://github.com/...

## 🛠️ What I've Tried
- [ ] `python manage.py makemigrations --check`
- [ ] `python manage.py migrate --noinput`
- [ ] `python manage.py migrate --plan`
- [ ] Rolled back migrations: `python manage.py migrate api zero`
- [ ] Reinstalled dependencies: `pip install -r requirements.txt`
- [ ] Checked migration syntax: `python -m py_compile api/migrations/*.py`

## 📝 Suggested Fix
<!-- If you have an idea how to fix this -->

## ✅ Checklist
- [ ] I've read MIGRATIONS_CI_CD.md
- [ ] I've run `python manage.py check`
- [ ] I've checked for uncommitted migration files: `git status`
- [ ] I've tried local migration: `python manage.py migrate --noinput`
- [ ] I've shared relevant error logs/screenshots

---

**Need help?** See [MIGRATIONS_CI_CD.md](../MIGRATIONS_CI_CD.md) for troubleshooting guide.
