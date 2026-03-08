import os
from pathlib import Path

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'skistation_project.settings')

import django

django.setup()

from django.db import transaction
from api.models import SkiStation, ServiceStore, BusLine, SkiCircuit


STATIONS_DATA = [
    {"name": "Chamrousse", "latitude": 45.107944, "longitude": 5.879200, "capacity": 15000, "altitude": 2250, "distanceFromGrenoble": 30},
    {"name": "Les 7 Laux", "latitude": 45.241111, "longitude": 5.970833, "capacity": 12000, "altitude": 2400, "distanceFromGrenoble": 38},
    {"name": "Alpe d'Huez", "latitude": 45.090833, "longitude": 6.068611, "capacity": 18000, "altitude": 3330, "distanceFromGrenoble": 63},
    {"name": "Villard-de-Lans / Corrençon", "latitude": 45.071111, "longitude": 5.553611, "capacity": 14000, "altitude": 2050, "distanceFromGrenoble": 36},
    {"name": "Autrans-Méaudre en Vercors", "latitude": 45.170278, "longitude": 5.527500, "capacity": 8000, "altitude": 1700, "distanceFromGrenoble": 38},
    {"name": "Le Collet d'Allevard", "latitude": 45.392222, "longitude": 6.035278, "capacity": 6500, "altitude": 2100, "distanceFromGrenoble": 56},
    {"name": "Les 2 Alpes", "latitude": 45.006944, "longitude": 6.123611, "capacity": 22000, "altitude": 3600, "distanceFromGrenoble": 71},
    {"name": "La Grave - La Meije", "latitude": 45.045556, "longitude": 6.306667, "capacity": 3000, "altitude": 3550, "distanceFromGrenoble": 83},
    {"name": "Oz 3300", "latitude": 45.135278, "longitude": 6.055000, "capacity": 6500, "altitude": 3300, "distanceFromGrenoble": 56},
    {"name": "Vaujany", "latitude": 45.158611, "longitude": 6.071111, "capacity": 5000, "altitude": 2800, "distanceFromGrenoble": 55},
    {"name": "Auris-en-Oisans", "latitude": 45.050833, "longitude": 6.084444, "capacity": 4500, "altitude": 2176, "distanceFromGrenoble": 64},
    {"name": "Le Sappey-en-Chartreuse", "latitude": 45.262500, "longitude": 5.777778, "capacity": 1800, "altitude": 1450, "distanceFromGrenoble": 19},
    {"name": "Saint-Pierre-de-Chartreuse", "latitude": 45.344444, "longitude": 5.809444, "capacity": 5000, "altitude": 1800, "distanceFromGrenoble": 35},
    {"name": "Lans-en-Vercors", "latitude": 45.127500, "longitude": 5.590278, "capacity": 3200, "altitude": 1736, "distanceFromGrenoble": 30},
    {"name": "Gresse-en-Vercors", "latitude": 44.902222, "longitude": 5.566667, "capacity": 3500, "altitude": 1751, "distanceFromGrenoble": 52},
]

STATION_IMAGE_MAP = {
    'Chamrousse': 'chamrousse.jpg',
    'Les 7 Laux': '7laux.jpg',
    "Alpe d'Huez": 'alpehuez.jpg',
    'Villard-de-Lans / Corrençon': 'villard.jpg',
    'Autrans-Méaudre en Vercors': 'autrans.jpg',
    "Le Collet d'Allevard": 'belledonne.jpg',
    'Les 2 Alpes': 'banner-03.jpg',
    'La Grave - La Meije': 'banner-04.jpg',
    'Oz 3300': 'offers-01.jpg',
    'Vaujany': 'offers-02.jpg',
    'Auris-en-Oisans': 'offers-03.jpg',
    'Le Sappey-en-Chartreuse': 'banner-01.jpg',
    'Saint-Pierre-de-Chartreuse': 'banner-02.jpg',
    'Lans-en-Vercors': 'cities-01.jpg',
    'Gresse-en-Vercors': 'cities-02.jpg',
}

SERVICE_SEED_BY_STATION = {
    'Chamrousse': [
        {'name': 'Office de Tourisme de Chamrousse', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Avenue Henry Duhamel, 38410 Chamrousse'},
        {'name': 'ESF Chamrousse 1750', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Recoin, 38410 Chamrousse'},
        {'name': 'Intersport Chamrousse 1750', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:00', 'address': 'Recoin, 38410 Chamrousse'},
        {'name': 'Secours des pistes Chamrousse', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38410 Chamrousse'},
    ],
    'Les 7 Laux': [
        {'name': 'Office de Tourisme Les 7 Laux', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Prapoutel, 38190 Les Adrets'},
        {'name': 'ESF Les 7 Laux', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Prapoutel, 38190 Les Adrets'},
        {'name': 'Skimium Les 7 Laux', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:00', 'address': 'Prapoutel centre, 38190 Les Adrets'},
        {'name': 'Secours des pistes Les 7 Laux', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Prapoutel, 38190 Les Adrets'},
    ],
    "Alpe d'Huez": [
        {'name': 'Office de Tourisme Alpe d\'Huez', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:30', 'address': 'Place Paganon, 38750 Huez'},
        {'name': 'ESF Alpe d\'Huez', 'type': 'École de ski', 'opening_hours': '08:45-18:00', 'address': 'Avenue des Jeux, 38750 Huez'},
        {'name': 'Sport 2000 Alpe d\'Huez', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:30', 'address': 'Avenue des Jeux, 38750 Huez'},
        {'name': 'Secours des pistes Alpe d\'Huez', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38750 Huez'},
    ],
    'Villard-de-Lans / Corrençon': [
        {'name': 'Office de Tourisme Villard-de-Lans', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Place Mure Ravaud, 38250 Villard-de-Lans'},
        {'name': 'ESF Villard-de-Lans / Corrençon', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Côte 2000, 38250 Villard-de-Lans'},
        {'name': 'Skimium Villard-Côte 2000', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Côte 2000, 38250 Villard-de-Lans'},
        {'name': 'Secours des pistes Villard-Corrençon', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Côte 2000, 38250 Villard-de-Lans'},
    ],
    'Autrans-Méaudre en Vercors': [
        {'name': 'Office de Tourisme Autrans-Méaudre', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Le Village, 38880 Autrans-Méaudre en Vercors'},
        {'name': 'ESF Autrans', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Autrans Centre Nordique, 38880 Autrans-Méaudre en Vercors'},
        {'name': 'Sport 2000 Autrans', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre village, 38880 Autrans-Méaudre en Vercors'},
        {'name': 'Secours des pistes Autrans-Méaudre', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Autrans / Méaudre, 38880 Autrans-Méaudre en Vercors'},
    ],
    "Le Collet d'Allevard": [
        {'name': 'Office de Tourisme Allevard-les-Bains', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': '3 Place de la Résistance, 38580 Allevard'},
        {'name': 'ESF Le Collet d\'Allevard', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Front de neige, 38580 Le Collet d\'Allevard'},
        {'name': 'Skimium Le Collet', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Front de neige, 38580 Le Collet d\'Allevard'},
        {'name': 'Secours des pistes Le Collet', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38580 Le Collet d\'Allevard'},
    ],
    'Les 2 Alpes': [
        {'name': 'Office de Tourisme Les 2 Alpes', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:30', 'address': 'Place des 2 Alpes, 38860 Les Deux Alpes'},
        {'name': 'ESF Les 2 Alpes', 'type': 'École de ski', 'opening_hours': '08:30-18:00', 'address': 'Centre station, 38860 Les Deux Alpes'},
        {'name': 'Intersport Les 2 Alpes', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:30', 'address': 'Avenue de la Muzelle, 38860 Les Deux Alpes'},
        {'name': 'Secours des pistes Les 2 Alpes', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38860 Les Deux Alpes'},
    ],
    'La Grave - La Meije': [
        {'name': 'Office de Tourisme La Grave', 'type': 'Information', 'opening_hours': '09:00-12:00 / 14:00-17:30', 'address': 'Le Bourg, 05320 La Grave'},
        {'name': 'École de Ski Oxygène La Grave', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Téléphérique, 05320 La Grave'},
        {'name': 'Location Ski La Grave', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Le Village, 05320 La Grave'},
        {'name': 'Secours des pistes La Meije', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Téléphérique, 05320 La Grave'},
    ],
    'Oz 3300': [
        {'name': 'Office de Tourisme Oz 3300', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Station Oz 3300, 38114 Oz-en-Oisans'},
        {'name': 'ESF Oz 3300', 'type': 'École de ski', 'opening_hours': '09:00-17:30', 'address': 'Front de neige, 38114 Oz-en-Oisans'},
        {'name': 'Skiset Oz 3300', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Galerie commerçante, 38114 Oz-en-Oisans'},
        {'name': 'Secours des pistes Oz', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38114 Oz-en-Oisans'},
    ],
    'Vaujany': [
        {'name': 'Office de Tourisme de Vaujany', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Maison de Vaujany, 38114 Vaujany'},
        {'name': 'ESF Vaujany', 'type': 'École de ski', 'opening_hours': '09:00-17:30', 'address': 'Place de la Fare, 38114 Vaujany'},
        {'name': 'Skimium Vaujany', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre station, 38114 Vaujany'},
        {'name': 'Secours des pistes Vaujany', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38114 Vaujany'},
    ],
    'Auris-en-Oisans': [
        {'name': 'Office de Tourisme Auris-en-Oisans', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Auris Station, 38142 Auris'},
        {'name': 'ESF Auris-en-Oisans', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Front de neige, 38142 Auris'},
        {'name': 'Skiset Auris', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre station, 38142 Auris'},
        {'name': 'Secours des pistes Auris', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38142 Auris'},
    ],
    'Le Sappey-en-Chartreuse': [
        {'name': 'Mairie / Info Le Sappey-en-Chartreuse', 'type': 'Information', 'opening_hours': '09:00-12:00 / 14:00-17:00', 'address': 'Le Bourg, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'ESF Le Sappey', 'type': 'École de ski', 'opening_hours': '09:00-16:30', 'address': 'Domaine du Sappey, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'Location Le Sappey Ski', 'type': 'Magasin / Location', 'opening_hours': '08:30-17:30', 'address': 'Centre village, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'Secours des pistes Le Sappey', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Domaine du Sappey, 38700 Le Sappey-en-Chartreuse'},
    ],
    'Saint-Pierre-de-Chartreuse': [
        {'name': 'Office de Tourisme Coeur de Chartreuse', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'ESF Saint-Pierre-de-Chartreuse', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Le Planolet, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'Sport 2000 Saint-Pierre', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Plan de Ville, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'Secours des pistes Saint-Pierre', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Le Planolet, 38380 Saint-Pierre-de-Chartreuse'},
    ],
    'Lans-en-Vercors': [
        {'name': 'Office de Tourisme de Lans-en-Vercors', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38250 Lans-en-Vercors'},
        {'name': 'ESF Lans-en-Vercors', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Domaine de l\'Aigle, 38250 Lans-en-Vercors'},
        {'name': 'Location Ski Lans', 'type': 'Magasin / Location', 'opening_hours': '08:30-17:30', 'address': 'Centre village, 38250 Lans-en-Vercors'},
        {'name': 'Secours des pistes Lans-en-Vercors', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Domaine de l\'Aigle, 38250 Lans-en-Vercors'},
    ],
    'Gresse-en-Vercors': [
        {'name': 'Office de Tourisme de Gresse-en-Vercors', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38650 Gresse-en-Vercors'},
        {'name': 'ESF Gresse-en-Vercors', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'La Station, 38650 Gresse-en-Vercors'},
        {'name': 'Skimium Gresse', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Front de neige, 38650 Gresse-en-Vercors'},
        {'name': 'Secours des pistes Gresse-en-Vercors', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38650 Gresse-en-Vercors'},
    ],
}

DEFAULT_SERVICE_BLUEPRINTS = [
    {
        'name_tpl': 'Location Ski {station}',
        'type': 'Location matériel',
        'opening_hours': '08:00-19:00',
        'address_tpl': 'Front de neige, {station}',
    },
    {
        'name_tpl': 'Location Premium {station}',
        'type': 'Magasin / Location',
        'opening_hours': '08:30-19:00',
        'address_tpl': 'Centre station, {station}',
    },
    {
        'name_tpl': 'Atelier Ski {station}',
        'type': 'Atelier / réparation',
        'opening_hours': '08:30-18:30',
        'address_tpl': 'Galerie commerçante, {station}',
    },
    {
        'name_tpl': 'Bootfitting {station}',
        'type': 'Atelier / réparation',
        'opening_hours': '09:00-18:00',
        'address_tpl': 'Zone piétonne, {station}',
    },
    {
        'name_tpl': 'Restaurant des Pistes {station}',
        'type': 'Restauration',
        'opening_hours': '11:30-16:30',
        'address_tpl': 'Pied des pistes, {station}',
    },
    {
        'name_tpl': 'Snack Montagne {station}',
        'type': 'Restauration',
        'opening_hours': '10:00-17:00',
        'address_tpl': 'Esplanade station, {station}',
    },
    {
        'name_tpl': 'Point Info {station}',
        'type': 'Information',
        'opening_hours': '08:30-17:30',
        'address_tpl': 'Office de tourisme, {station}',
    },
    {
        'name_tpl': 'Secours Piste {station}',
        'type': 'Secours',
        'opening_hours': '08:00-17:00',
        'address_tpl': 'Poste de secours, {station}',
    },
]

STATION_OFFICIAL_CONTACTS = {
    'Chamrousse': {'website_url': 'https://www.chamrousse.com', 'phone': '+33 4 76 89 92 65'},
    'Les 7 Laux': {'website_url': 'https://www.les7laux.com', 'phone': '+33 4 76 08 17 86'},
    "Alpe d'Huez": {'website_url': 'https://www.alpedhuez.com', 'phone': '+33 4 76 11 44 44'},
    'Villard-de-Lans / Corrençon': {'website_url': 'https://www.villarddelans-correnconenvercors.com', 'phone': '+33 4 76 95 10 38'},
    'Autrans-Méaudre en Vercors': {'website_url': 'https://www.autrans-meaudre.com', 'phone': '+33 4 76 95 30 70'},
    "Le Collet d'Allevard": {'website_url': 'https://www.lecollet.com', 'phone': '+33 4 76 45 10 11'},
    'Les 2 Alpes': {'website_url': 'https://www.les2alpes.com', 'phone': '+33 4 76 79 22 00'},
    'La Grave - La Meije': {'website_url': 'https://www.lagrave-lameije.com', 'phone': '+33 4 76 79 90 05'},
    'Oz 3300': {'website_url': 'https://www.oz3300.com', 'phone': '+33 4 76 80 78 01'},
    'Vaujany': {'website_url': 'https://www.vaujany.com', 'phone': '+33 4 76 80 72 37'},
    'Auris-en-Oisans': {'website_url': 'https://www.auris-en-oisans.fr', 'phone': '+33 4 76 80 13 52'},
    'Le Sappey-en-Chartreuse': {'website_url': 'https://www.chartreuse-tourisme.com', 'phone': '+33 4 76 88 84 05'},
    'Saint-Pierre-de-Chartreuse': {'website_url': 'https://www.coeur-de-chartreuse.com', 'phone': '+33 4 76 88 62 08'},
    'Lans-en-Vercors': {'website_url': 'https://www.lansenvercors.com', 'phone': '+33 4 76 95 42 62'},
    'Gresse-en-Vercors': {'website_url': 'https://www.gresse-en-vercors.fr', 'phone': '+33 4 76 34 33 40'},
}


def clip_text(value, max_length):
    return value if len(value) <= max_length else value[:max_length]


def get_station_contact(station_name):
    return STATION_OFFICIAL_CONTACTS.get(station_name, {})


def enrich_service_data(service, station_name):
    station_contact = get_station_contact(station_name)
    normalized_type = (service.get('type', '') or '').lower()
    is_information_service = 'information' in normalized_type or 'office' in (service.get('name', '') or '').lower()

    website_url = service.get('website_url') or (station_contact.get('website_url') if is_information_service else '')
    phone = service.get('phone') or station_contact.get('phone', '')
    source_note = service.get('source_note') or 'Source: office de tourisme / domaine skiable (à vérifier avant déplacement).'

    return {
        'name': clip_text(service.get('name', ''), 100),
        'type': clip_text(service.get('type', ''), 100),
        'opening_hours': clip_text(service.get('opening_hours', ''), 100),
        'address': clip_text(service.get('address', ''), 255),
        'website_url': clip_text(website_url, 255) if website_url else '',
        'phone': clip_text(phone, 40) if phone else '',
        'source_note': clip_text(source_note, 255) if source_note else '',
    }


def get_services_for_station(station_name):
    specific_services = list(SERVICE_SEED_BY_STATION.get(station_name, []))
    if specific_services:
        return [enrich_service_data(service, station_name) for service in specific_services]

    specific_names = {service.get('name', '') for service in specific_services}

    generated_services = []
    for blueprint in DEFAULT_SERVICE_BLUEPRINTS:
        name = blueprint['name_tpl'].format(station=station_name)
        if name in specific_names:
            continue
        generated_services.append({
            'name': clip_text(name, 100),
            'type': clip_text(blueprint['type'], 100),
            'opening_hours': clip_text(blueprint['opening_hours'], 100),
            'address': clip_text(blueprint['address_tpl'].format(station=station_name), 255),
            'source_note': 'Service générique de démonstration (non vérifié localement).',
        })

    normalized_specific_services = [enrich_service_data(service, station_name) for service in specific_services]

    return normalized_specific_services + [enrich_service_data(service, station_name) for service in generated_services]

BUS_LINES_SEED = [
    {
        'station_name': 'Chamrousse',
        'bus_number': 'T87',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Chamrousse - Roche Béranger',
        'frequency': 'Toutes les 60 min (week-end)',
        'travel_time': '1h10',
        'route_points': 'Gare Routière → Grand\'Place → Uriage → Chamrousse',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': 'Les 7 Laux',
        'bus_number': 'T84',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Prapoutel - Les 7 Laux',
        'frequency': 'Toutes les 90 min',
        'travel_time': '1h05',
        'route_points': 'Gare Routière → Crolles → Brignoud → Prapoutel',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': "Alpe d'Huez",
        'bus_number': 'Transaltitude 3000',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Alpe d\'Huez Station',
        'frequency': '4 à 6 départs / jour',
        'travel_time': '1h40',
        'route_points': 'Grenoble → Bourg-d\'Oisans → Alpe d\'Huez',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Villard-de-Lans / Corrençon',
        'bus_number': 'T64',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Villard-de-Lans Office de tourisme',
        'frequency': 'Toutes les 60 min',
        'travel_time': '55 min',
        'route_points': 'Gare Routière → Seyssins → Lans-en-Vercors → Villard',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': 'Autrans-Méaudre en Vercors',
        'bus_number': 'T66',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Autrans Centre',
        'frequency': 'Semaine: 1 bus / 2h • Week-end: 1 bus / h',
        'travel_time': '1h00',
        'route_points': 'Grenoble → Engins → Lans-en-Vercors → Autrans',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': "Le Collet d'Allevard",
        'bus_number': 'Navette C1',
        'departure_stop': 'Brignoud Gare',
        'arrival_stop': 'Le Collet d\'Allevard Front de neige',
        'frequency': 'Semaine: 3 départs / jour • Week-end: 5 départs / jour',
        'travel_time': '50 min',
        'route_points': 'Brignoud → Allevard → Le Collet',
        'departure_latitude': 45.259167,
        'departure_longitude': 5.902222,
    },
    {
        'station_name': 'Les 2 Alpes',
        'bus_number': 'Transaltitude 3020',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Les 2 Alpes Office de tourisme',
        'frequency': 'Semaine: 4 départs / jour • Week-end: 6 départs / jour',
        'travel_time': '1h45',
        'route_points': 'Grenoble → Vizille → Bourg-d\'Oisans → Les 2 Alpes',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'La Grave - La Meije',
        'bus_number': 'Navette M1',
        'departure_stop': 'Bourg-d\'Oisans Gare routière',
        'arrival_stop': 'La Grave Téléphérique',
        'frequency': 'Semaine: 2 départs / jour • Week-end: 3 départs / jour',
        'travel_time': '1h10',
        'route_points': 'Bourg-d\'Oisans → Le Freney → La Grave',
        'departure_latitude': 45.054722,
        'departure_longitude': 6.032222,
    },
    {
        'station_name': 'Oz 3300',
        'bus_number': 'Navette O3',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Oz Station',
        'frequency': 'Semaine: 3 départs / jour • Week-end: 5 départs / jour',
        'travel_time': '1h35',
        'route_points': 'Grenoble → Allemond → Oz-en-Oisans',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Vaujany',
        'bus_number': 'Navette V1',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Vaujany Office de tourisme',
        'frequency': 'Semaine: 3 départs / jour • Week-end: 4 départs / jour',
        'travel_time': '1h30',
        'route_points': 'Grenoble → Allemond → Vaujany',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Auris-en-Oisans',
        'bus_number': 'Navette A2',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Auris Station',
        'frequency': 'Semaine: 2 départs / jour • Week-end: 4 départs / jour',
        'travel_time': '1h40',
        'route_points': 'Grenoble → Bourg-d\'Oisans → Auris',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Le Sappey-en-Chartreuse',
        'bus_number': 'Proximo 62',
        'departure_stop': 'Grenoble Victor Hugo',
        'arrival_stop': 'Le Sappey-en-Chartreuse Mairie',
        'frequency': 'Semaine: 1 bus / h • Week-end: 1 bus / 2h',
        'travel_time': '40 min',
        'route_points': 'Victor Hugo → La Tronche → Col de Porte → Le Sappey',
        'departure_latitude': 45.189722,
        'departure_longitude': 5.726111,
    },
    {
        'station_name': 'Saint-Pierre-de-Chartreuse',
        'bus_number': 'T40',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Saint-Pierre-de-Chartreuse Plan de Ville',
        'frequency': 'Semaine: 5 départs / jour • Week-end: 6 départs / jour',
        'travel_time': '1h05',
        'route_points': 'Grenoble → Saint-Laurent-du-Pont → Saint-Pierre-de-Chartreuse',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': 'Lans-en-Vercors',
        'bus_number': 'T65',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Lans-en-Vercors Centre',
        'frequency': 'Semaine: 1 bus / h • Week-end: 1 bus / h',
        'travel_time': '45 min',
        'route_points': 'Grenoble → Seyssins → Lans-en-Vercors',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': 'Gresse-en-Vercors',
        'bus_number': 'Transisère G1',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Gresse-en-Vercors Station',
        'frequency': 'Semaine: 2 départs / jour • Week-end: 3 départs / jour',
        'travel_time': '1h25',
        'route_points': 'Grenoble → Vif → Monestier-de-Clermont → Gresse-en-Vercors',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
]

SKI_CIRCUITS_SEED = {
    'Chamrousse': [('Débutant', 14), ('Intermédiaire', 16), ('Avancé', 13)],
    'Les 7 Laux': [('Débutant', 12), ('Intermédiaire', 18), ('Avancé', 16)],
    "Alpe d'Huez": [('Débutant', 20), ('Intermédiaire', 35), ('Avancé', 26)],
}


def get_image_bytes(filename):
    image_path = Path(__file__).resolve().parent / 'static' / 'images' / filename
    if image_path.exists():
        return image_path.read_bytes()
    return None


@transaction.atomic
def seed_ski_stations():
    created_count = 0
    updated_count = 0

    station_by_name = {}
    for station in STATIONS_DATA:
        image_bytes = get_image_bytes(STATION_IMAGE_MAP.get(station['name'], 'default-profile.png'))

        station_obj, created = SkiStation.objects.update_or_create(
            name=station['name'],
            defaults={
                'latitude': station['latitude'],
                'longitude': station['longitude'],
                'capacity': station['capacity'],
                'altitude': station['altitude'],
                'distanceFromGrenoble': station['distanceFromGrenoble'],
                'image': image_bytes,
            },
        )
        station_by_name[station['name']] = station_obj
        if created:
            created_count += 1
        else:
            updated_count += 1

    for station_name, station in station_by_name.items():
        services = get_services_for_station(station_name)
        for index, service in enumerate(services):
            lat_offset = (index + 1) * 0.0025
            lng_offset = (index + 1) * 0.0025
            ServiceStore.objects.update_or_create(
                ski_station=station,
                name=service['name'],
                defaults={
                    'type': service['type'],
                    'opening_hours': service['opening_hours'],
                    'address': service.get('address', ''),
                    'website_url': service.get('website_url', ''),
                    'phone': service.get('phone', ''),
                    'source_note': service.get('source_note', ''),
                    'latitude': float(station.latitude) + lat_offset,
                    'longitude': float(station.longitude) + lng_offset,
                }
            )

    for item in BUS_LINES_SEED:
        station = station_by_name.get(item['station_name'])
        if not station:
            continue
        BusLine.objects.update_or_create(
            ski_station=station,
            bus_number=item['bus_number'],
            defaults={
                'departure_stop': item['departure_stop'],
                'arrival_stop': item['arrival_stop'],
                'frequency': item['frequency'],
                'travel_time': item['travel_time'],
                'route_points': item['route_points'],
                'departure_latitude': item['departure_latitude'],
                'departure_longitude': item['departure_longitude'],
            }
        )

    for station_name, circuits in SKI_CIRCUITS_SEED.items():
        station = station_by_name.get(station_name)
        if not station:
            continue
        for difficulty, num_pistes in circuits:
            SkiCircuit.objects.update_or_create(
                ski_station=station,
                difficulty=difficulty,
                defaults={'num_pistes': num_pistes},
            )

    print(f"Stations créées: {created_count}")
    print(f"Stations mises à jour: {updated_count}")
    print(f"Total stations en base: {SkiStation.objects.count()}")
    print(f"Total services en base: {ServiceStore.objects.count()}")
    print(f"Total lignes de bus en base: {BusLine.objects.count()}")


if __name__ == '__main__':
    seed_ski_stations()
