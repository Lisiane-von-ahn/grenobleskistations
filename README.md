# Ski Station Management API

## Overview

The Ski Station Management API is a Django-based application designed to manage ski stations, bus lines, service stores, and ski circuits. The API provides an easy-to-use interface for managing these resources, and comes with full documentation powered by Swagger UI. This project aims to make it easier for ski station operators, bus lines, and service providers to interact with data related to ski resorts.

## Project Architecture

This project is built with **Django** and uses **Django Rest Framework (DRF)** for the API. It is organized into several core components:

- **Models**: Represent the core entities such as `SkiStation`, `BusLine`, `ServiceStore`, and `SkiCircuit`.
- **Views/Serializers**: Expose API endpoints to interact with the models and handle data formatting and validation.
- **Swagger Documentation**: Powered by `drf-yasg`, it automatically generates beautiful and interactive API documentation.
- **Database**: Uses PostgreSQL for persistence, and can be easily switched to SQLite for development.

## Key Features

- **CRUD Operations**: Create, Read, Update, and Delete operations for managing ski stations, bus lines, service stores, and ski circuits.
- **Swagger UI**: Auto-generated interactive API documentation for easy testing and exploration of the API.
- **Authentication**: Support for token-based authentication (via Django Rest Framework).

## Requirements

- Python 3.8 or higher
- Django 3.x or higher
- Django REST Framework
- PostgreSQL (for production) or SQLite (for development)

## Installation

### 1. Clone the repository:

```bash
git clone https://github.com/yourusername/ski-station-api.git
cd ski-station-api
pip install -r requirements.txt
python manage.py migrate
python manage.py createsuperuser
python manage.py runserver
```

## Déploiement Docker Compose (PostgreSQL + HTTPS)

Le projet inclut un déploiement prêt pour serveur Linux avec:

- `docker-compose.yml` (Django + PostgreSQL)
- `docker-compose.letsencrypt.yml` (Traefik + certificats Let's Encrypt)
- `deploy/install.sh` (installation Docker + génération `.env` + `docker compose up`)
- `.github/workflows/deploy.yml` (déploiement SSH via GitHub Actions)

### Secrets GitHub Actions requis

- `SSH_HOST`
- `SSH_USER`
- `SSH_PRIVATE_KEY`
- `DB_HOST`
- `DB_USER`
- `DB_PASSWORD`
- `DB_NAME` (ex: `grenobleski`)
- `DB_PORT` (ex: `5432`)
- `DATABASE_URL` (optionnel, prioritaire si défini)
- `WEATHER_API_KEY` (optionnel)
- `BOOTSTRAP_ADMIN_USERNAME` (optionnel, défaut: `admin`)
- `BOOTSTRAP_ADMIN_PASSWORD` (optionnel, défaut: `admin`)
- `BOOTSTRAP_ADMIN_EMAIL` (optionnel, défaut: `admin@grenobleski.local`)

### Set automatique des secrets GitHub

1. Créez un fichier local non versionné:

```bash
cp .github/secrets/github-actions.secrets.example .github/secrets/github-actions.secrets
```

2. Renseignez les valeurs dans `.github/secrets/github-actions.secrets`.

3. Poussez automatiquement les secrets vers GitHub (nécessite `gh auth login`):

```bash
chmod +x deploy/set-github-secrets.sh
./deploy/set-github-secrets.sh .github/secrets/github-actions.secrets your-org/your-repo
```

### Exécution manuelle sur serveur

```bash
chmod +x deploy/install.sh
./deploy/install.sh HOST USER PASSWORD WEATHER_API_KEY DB_NAME DB_PORT DATABASE_URL BOOTSTRAP_ADMIN_USERNAME BOOTSTRAP_ADMIN_PASSWORD BOOTSTRAP_ADMIN_EMAIL
```

Un superuser bootstrap est créé une seule fois si absent. Il est marqué pour forcer le changement de mot de passe au premier login.

Le seed (`load_ski_stations.py`) est exécuté explicitement après le `docker compose up` par `deploy/install.sh`.
Il s'applique à la base configurée via les secrets (`DATABASE_URL` ou `DB_*`).

Le domaine configuré par défaut dans le script est:

- `grenobleski.fr`
- `www.grenobleski.fr`

### Traefik déjà présent sur le serveur (audela)

Si un Traefik existe déjà sur le serveur (ports 80/443 occupés), gardez ce projet en mode externe (`USE_INTERNAL_TRAEFIK=false`) et utilisez le snippet suivant côté Traefik existant:

- [deploy/traefik-dynamic-grenobleski.yml](deploy/traefik-dynamic-grenobleski.yml)

Ce snippet route:

- `grenobleski.fr` -> `http://127.0.0.1:8081`
- `www.grenobleski.fr` -> `http://127.0.0.1:8081`

Vérifiez que le `certResolver` (`letsencrypt`) correspond bien au nom utilisé dans votre Traefik existant.
Si vous voyez encore un `404 page not found` Traefik, le plus fréquent est que le fichier dynamique n'est pas chargé par le provider file (mauvais dossier ou pas de reload).

## Project Architecture

The architecture of the project follows the standard **Model-View-Controller (MVC)** pattern. Here's a breakdown of each component:

### 1. **Models (M)**
- **SkiStation**: Represents a ski resort with fields such as name, latitude, longitude, and capacity.
- **BusLine**: Represents a bus line that connects a ski station to various points, with fields such as bus number, departure and arrival stops, and travel time.
- **ServiceStore**: Represents a service store located at a ski station, with details like type, opening hours, and location.
- **SkiCircuit**: Represents a ski circuit at a station, with fields like difficulty level and number of pistes.

### 2. **Views (V)**
- **Django REST Framework Views**: The views in this project expose API endpoints to perform CRUD operations on the models. These views handle requests from the client and return appropriate responses.

### 3. **Controllers (C)**
- **Serializers**: The serializers are responsible for converting model instances to JSON format and vice versa. They validate data and handle the conversion between Python objects and JSON data.
- **API URLs**: The URL routing is configured to map requests to views, making the API accessible for external consumers.

### 4. **Swagger UI (API Documentation)**
- **drf-yasg**: The project uses `drf-yasg` to automatically generate interactive API documentation. This documentation helps both developers and users understand how to interact with the API.

### 5. **Database**
- The project uses **PostgreSQL** in production and can be switched to **SQLite** for development. The models are mapped to database tables for persistent storage.

### 6. **Authentication**
- Token-based authentication is supported, allowing users to authenticate and interact with protected API endpoints.

## Key Features

- **CRUD Operations**: Manage ski stations, bus lines, service stores, and ski circuits using simple API endpoints.
- **Interactive API Documentation**: API documentation generated using **Swagger UI**.
- **Authentication**: Token-based authentication for secure API access.
- **PostgreSQL Integration**: Uses PostgreSQL as the default database for data persistence.





