# GrenobleSki release checklist

For model changes, generate and commit migrations, run `python3 manage.py check`, `python3 manage.py makemigrations --check --dry-run`, and relevant tests. Keep `.github/workflows/ci.yml`'s latest-migration assertion aligned with the newest API migration; deployment also applies migrations explicitly.

For Android releases, create a strictly newer signed AAB using the configured keystore and record its version. Do not publish the bundle to Google Play unless explicitly requested.
