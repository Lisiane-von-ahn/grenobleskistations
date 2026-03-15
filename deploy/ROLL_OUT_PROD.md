# Production Rollout (BeeWare + API)

Ce guide deploie les nouveaux endpoints API utilises par l'app BeeWare, puis verifie la publication dans Swagger du domaine.

## 1) Connexion SSH

Depuis ta machine locale:

```bash
ssh root@82.165.153.80
```

Si echec SSH:

- Verifier IP/port/firewall
- Verifier cle SSH chargee (`ssh-add -l`)
- Tester en verbose: `ssh -vvv root@82.165.153.80`

## 2) Aller dans le projet sur le serveur

```bash
cd /root/grenobleskistations
```

## 3) Lancer le rollout automatise

```bash
./deploy/rollout_beeware_api_prod.sh
```

Ce script fait:

- `git pull --ff-only`
- `docker compose up -d --build web`
- `python manage.py migrate`
- `python manage.py ensure_bootstrap_admin`
- redemarrage web
- verification OpenAPI locale (`127.0.0.1:8081/swagger/?format=openapi`)
- verification OpenAPI publique (`https://www.grenobleski.fr/swagger/?format=openapi`)

## 4) Verification manuelle rapide

```bash
curl -fsS https://www.grenobleski.fr/swagger/?format=openapi | python3 -m json.tool | head -n 40
```

Endpoints attendus dans `paths`:

- `/auth/login/`
- `/auth/register/`
- `/auth/google-login/`
- `/auth/me/`
- `/auth/logout/`
- `/messages/`
- `/skistories/`
- `/skipartnerposts/`
- `/instructorservices/`

## 5) Smoke test auth API

```bash
curl -fsS -X POST https://www.grenobleski.fr/api/auth/login/ \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@grenobleski.local","password":"admin"}'
```

Remarque: adapter ce compte a tes identifiants de prod.
