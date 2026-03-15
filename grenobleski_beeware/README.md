# GrenobleSki BeeWare App

Application BeeWare (Toga) moderne pour GrenobleSki avec:

- Authentification email/mot de passe via API Django
- Connexion Google via page web officielle GrenobleSki
- Creation de compte et mot de passe oublie via pages web GrenobleSki
- Interface FR/EN
- Reutilisation du logo existant GrenobleSki
- Icône mobile derivee du logo GrenobleSki
- Modules principaux: stations, bus, services, marketplace, stories, ski partners, instructeurs, messages, profil
- Detection dynamique des endpoints via `https://www.grenobleski.fr/swagger/?format=openapi`

## Structure

- `src/grenobleski_mobile/app.py`: UI principale moderne
- `src/grenobleski_mobile/api_client.py`: client HTTP vers API Django
- `src/grenobleski_mobile/i18n.py`: traductions FR/EN
- `src/grenobleski_mobile/resources/logo.png`: logo reutilise du site

## Prerequis

- Python 3.10+
- BeeWare / Briefcase

Installation locale:

```bash
cd grenobleski_beeware
python -m venv .venv
source .venv/bin/activate
pip install -U pip briefcase
pip install -e .
```

## Lancer en mode dev

```bash
cd grenobleski_beeware
briefcase dev
```

Par defaut, l'app pointe vers `http://127.0.0.1:8000/api`.
Par defaut, l'app pointe vers `https://www.grenobleski.fr/api`.

Pour un serveur different:

```bash
export GRENOBLESKI_API_URL="https://grenobleski.fr/api"
briefcase dev
```

L'app lit automatiquement la spec Swagger du domaine cible et active uniquement les sections disponibles.

## Build mobile/desktop

Exemples:

```bash
briefcase create android
briefcase build android
briefcase run android
```

```bash
briefcase create ios
briefcase build ios
briefcase run ios
```

## Authentification

- Connexion native dans l'app: email + mot de passe.
- Google Sign-In: ouverture de la page web officielle `accounts/google/login/`.
- Creation de compte: ouverture de `accounts/signup/`.
- Mot de passe oublie: ouverture de `password/reset/`.
