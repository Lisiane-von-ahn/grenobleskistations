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
}

SERVICE_SEED_BY_STATION = {
    'Chamrousse': [
        {'name': 'Office de Tourisme de Chamrousse', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Avenue Henry Duhamel, 38410 Chamrousse'},
        {'name': 'ESF Chamrousse 1750', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Recoin, 38410 Chamrousse', 'website_url': 'https://www.esfchamrousse.com'},
        {'name': 'Intersport Chamrousse 1750', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:00', 'address': 'Recoin, 38410 Chamrousse', 'website_url': 'https://www.intersport-rent.fr'},
        {'name': 'Secours des pistes Chamrousse', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38410 Chamrousse'},
        {'name': 'Brasserie La Recombaz', 'type': 'Restaurant', 'opening_hours': '09:00-17:30', 'address': 'Recoin, 38410 Chamrousse', 'source_note': 'Restaurant de montagne face aux pistes (à vérifier avant déplacement).'},
        {'name': 'Restaurant Le 1650', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Roche Béranger, 38410 Chamrousse', 'source_note': 'Restauration rapide et plats chauds en station (à vérifier avant déplacement).'},
    ],
    'Les 7 Laux': [
        {'name': 'Office de Tourisme Les 7 Laux', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Prapoutel, 38190 Les Adrets'},
        {'name': 'ESF Les 7 Laux', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Prapoutel, 38190 Les Adrets', 'website_url': 'https://www.esf-les7laux.com'},
        {'name': 'Skimium Les 7 Laux', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:00', 'address': 'Prapoutel centre, 38190 Les Adrets', 'website_url': 'https://www.skimium.fr'},
        {'name': 'Secours des pistes Les 7 Laux', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Prapoutel, 38190 Les Adrets'},
        {'name': 'Restaurant Le Cairn', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Prapoutel, 38190 Les Adrets', 'source_note': 'Brasserie traditionnelle en pied de piste (à vérifier avant déplacement).'},
        {'name': 'Auberge du Pré Bayard', 'type': 'Restaurant', 'opening_hours': '12:00-14:30', 'address': 'Prapoutel, 38190 Les Adrets', 'source_note': 'Cuisine savoyarde authentique (à vérifier avant déplacement).'},
    ],
    "Alpe d'Huez": [
        {'name': "Office de Tourisme Alpe d'Huez", 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:30', 'address': 'Place Paganon, 38750 Huez'},
        {'name': "ESF Alpe d'Huez", 'type': 'École de ski', 'opening_hours': '08:45-18:00', 'address': 'Avenue des Jeux, 38750 Huez', 'website_url': 'https://www.esf-alpedhuez.com'},
        {'name': "Sport 2000 Alpe d'Huez", 'type': 'Magasin / Location', 'opening_hours': '08:30-19:30', 'address': 'Avenue des Jeux, 38750 Huez', 'website_url': 'https://www.sport2000.fr'},
        {'name': "Secours des pistes Alpe d'Huez", 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38750 Huez'},
        {'name': "La Folie Douce Alpe d'Huez", 'type': 'Restaurant', 'opening_hours': '11:00-17:00', 'address': 'Piste du Signal, 38750 Huez', 'website_url': 'https://www.lafoliedouce.com', 'source_note': 'Bar-restaurant mythique en altitude, ambiance festive (à vérifier avant déplacement).'},
        {'name': 'Brasserie Le Perce-Neige', 'type': 'Restaurant', 'opening_hours': '12:00-22:00', 'address': 'Avenue des Jeux, 38750 Huez', 'source_note': 'Cuisine montagnarde et pizzas en station (à vérifier avant déplacement).'},
        {'name': 'Restaurant Au Chamois d\'Or', 'type': 'Restaurant', 'opening_hours': '12:00-14:30 / 19:00-21:30', 'address': 'Rond-Point des Pistes, 38750 Huez', 'source_note': 'Restaurant gastronomique vue panoramique sur les pistes (à vérifier avant déplacement).'},
    ],
    'Villard-de-Lans / Corrençon': [
        {'name': 'Office de Tourisme Villard-de-Lans', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Place Mure Ravaud, 38250 Villard-de-Lans'},
        {'name': 'ESF Villard-de-Lans / Corrençon', 'type': 'École de ski', 'opening_hours': '08:45-17:30', 'address': 'Côte 2000, 38250 Villard-de-Lans', 'website_url': 'https://www.esf-villarddelans.com'},
        {'name': 'Skimium Villard-Côte 2000', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Côte 2000, 38250 Villard-de-Lans', 'website_url': 'https://www.skimium.fr'},
        {'name': 'Secours des pistes Villard-Corrençon', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Côte 2000, 38250 Villard-de-Lans'},
        {'name': 'Le Balcon de Villard', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Côte 2000, 38250 Villard-de-Lans', 'source_note': 'Restaurant d\'altitude avec vue sur le Vercors (à vérifier avant déplacement).'},
        {'name': 'Restaurant La Marmite', 'type': 'Restaurant', 'opening_hours': '12:00-14:30', 'address': 'Centre village, 38250 Villard-de-Lans', 'source_note': 'Spécialités fromagères et fondues savoyardes (à vérifier avant déplacement).'},
    ],
    'Autrans-Méaudre en Vercors': [
        {'name': 'Office de Tourisme Autrans-Méaudre', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Le Village, 38880 Autrans-Méaudre en Vercors'},
        {'name': 'ESF Autrans', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Autrans Centre Nordique, 38880 Autrans-Méaudre en Vercors', 'website_url': 'https://www.esf-autrans.com'},
        {'name': 'Sport 2000 Autrans', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre village, 38880 Autrans-Méaudre en Vercors', 'website_url': 'https://www.sport2000.fr'},
        {'name': 'Secours des pistes Autrans-Méaudre', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Autrans / Méaudre, 38880 Autrans-Méaudre en Vercors'},
        {'name': "La Ferme d'Autrans", 'type': 'Restaurant', 'opening_hours': '12:00-14:30', 'address': 'Route du Col de la Croix Perrin, 38880 Autrans-Méaudre en Vercors', 'source_note': 'Cuisine du terroir dans une ferme rénovée (à vérifier avant déplacement).'},
        {'name': 'Restaurant Les Alpins', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Front de neige, 38880 Autrans-Méaudre en Vercors', 'source_note': 'Restauration en pied de piste, plats chauds et crêpes (à vérifier avant déplacement).'},
    ],
    "Le Collet d'Allevard": [
        {'name': 'Office de Tourisme Allevard-les-Bains', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': '3 Place de la Résistance, 38580 Allevard'},
        {'name': "ESF Le Collet d'Allevard", 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': "Front de neige, 38580 Le Collet d'Allevard"},
        {'name': 'Skimium Le Collet', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': "Front de neige, 38580 Le Collet d'Allevard", 'website_url': 'https://www.skimium.fr'},
        {'name': 'Secours des pistes Le Collet', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': "Poste de secours, 38580 Le Collet d'Allevard"},
        {'name': 'Restaurant Les Alpages du Collet', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': "Front de neige, 38580 Le Collet d'Allevard", 'source_note': 'Brasserie en station, raclette et plats montagnards (à vérifier avant déplacement).'},
        {'name': 'Buvette du Collet', 'type': 'Restaurant', 'opening_hours': '09:30-15:30', 'address': "Sommet du domaine, 38580 Le Collet d'Allevard", 'source_note': 'Buvette avec vue panoramique sur Belledonne (à vérifier avant déplacement).'},
    ],
    'Les 2 Alpes': [
        {'name': 'Office de Tourisme Les 2 Alpes', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:30', 'address': 'Place des 2 Alpes, 38860 Les Deux Alpes'},
        {'name': 'ESF Les 2 Alpes', 'type': 'École de ski', 'opening_hours': '08:30-18:00', 'address': 'Centre station, 38860 Les Deux Alpes', 'website_url': 'https://www.esf-les2alpes.com'},
        {'name': 'Intersport Les 2 Alpes', 'type': 'Magasin / Location', 'opening_hours': '08:30-19:30', 'address': 'Avenue de la Muzelle, 38860 Les Deux Alpes', 'website_url': 'https://www.intersport-rent.fr'},
        {'name': 'Secours des pistes Les 2 Alpes', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38860 Les Deux Alpes'},
        {'name': 'La Spaghetteria', 'type': 'Restaurant', 'opening_hours': '12:00-14:30 / 19:00-22:00', 'address': 'Avenue de la Muzelle, 38860 Les Deux Alpes', 'source_note': 'Restaurant italien incontournable des 2 Alpes (à vérifier avant déplacement).'},
        {'name': 'Restaurant La Patate', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Piste de la Fée, 38860 Les Deux Alpes', 'source_note': 'Restaurant d\'altitude au cœur du domaine, vue sur glacier (à vérifier avant déplacement).'},
        {'name': 'Le Panoramic 3200', 'type': 'Restaurant', 'opening_hours': '10:00-16:00', 'address': 'Glacier des 2 Alpes, 38860 Les Deux Alpes', 'source_note': 'Restaurant à 3200m d\'altitude sur le glacier, buffets et plats chauds (à vérifier avant déplacement).'},
    ],
    'La Grave - La Meije': [
        {'name': 'Office de Tourisme La Grave', 'type': 'Information', 'opening_hours': '09:00-12:00 / 14:00-17:30', 'address': 'Le Bourg, 05320 La Grave'},
        {'name': 'École de Ski Oxygène La Grave', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Téléphérique, 05320 La Grave', 'website_url': 'https://www.oxygene-ski.com'},
        {'name': 'Location Ski La Grave', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Le Village, 05320 La Grave'},
        {'name': 'Secours des pistes La Meije', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Téléphérique, 05320 La Grave'},
        {'name': 'Restaurant Les Glaciers de La Meije', 'type': 'Restaurant', 'opening_hours': '12:00-14:30', 'address': 'Le Bourg, 05320 La Grave', 'source_note': 'Cuisine traditionnelle hautes-alpes face à La Meije (à vérifier avant déplacement).'},
        {'name': "Cafétéria du Téléphérique de La Grave", 'type': 'Restaurant', 'opening_hours': '10:00-15:30', 'address': 'Téléphérique, 05320 La Grave', 'source_note': 'Restauration rapide au pied du téléphérique (à vérifier avant déplacement).'},
    ],
    'Oz 3300': [
        {'name': 'Office de Tourisme Oz 3300', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Station Oz 3300, 38114 Oz-en-Oisans'},
        {'name': 'ESF Oz 3300', 'type': 'École de ski', 'opening_hours': '09:00-17:30', 'address': 'Front de neige, 38114 Oz-en-Oisans'},
        {'name': 'Skiset Oz 3300', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Galerie commerçante, 38114 Oz-en-Oisans', 'website_url': 'https://www.skiset.com'},
        {'name': 'Secours des pistes Oz', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38114 Oz-en-Oisans'},
        {'name': 'Restaurant Le Belvédère Oz 3300', 'type': 'Restaurant', 'opening_hours': '09:00-16:30', 'address': 'Front de neige, 38114 Oz-en-Oisans', 'source_note': 'Restaurant panoramique avec vue sur les Grandes Rousses (à vérifier avant déplacement).'},
        {'name': "L'Igloo Oz en Oisans", 'type': 'Restaurant', 'opening_hours': '09:30-17:00', 'address': 'Galerie commerçante, 38114 Oz-en-Oisans', 'source_note': 'Brasserie familiale au cœur de la station (à vérifier avant déplacement).'},
    ],
    'Vaujany': [
        {'name': 'Office de Tourisme de Vaujany', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Maison de Vaujany, 38114 Vaujany'},
        {'name': 'ESF Vaujany', 'type': 'École de ski', 'opening_hours': '09:00-17:30', 'address': 'Place de la Fare, 38114 Vaujany'},
        {'name': 'Skimium Vaujany', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre station, 38114 Vaujany', 'website_url': 'https://www.skimium.fr'},
        {'name': 'Secours des pistes Vaujany', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38114 Vaujany'},
        {'name': 'Restaurant La Fare', 'type': 'Restaurant', 'opening_hours': '12:00-14:30 / 19:00-21:00', 'address': 'Place de la Fare, 38114 Vaujany', 'source_note': 'Restaurant du village, spécialités locales et raclette (à vérifier avant déplacement).'},
        {'name': 'Café-Restaurant du Centre Vaujany', 'type': 'Restaurant', 'opening_hours': '09:00-18:00', 'address': 'Centre village, 38114 Vaujany', 'source_note': 'Brasserie conviviale en station (à vérifier avant déplacement).'},
    ],
    'Auris-en-Oisans': [
        {'name': 'Office de Tourisme Auris-en-Oisans', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-18:00', 'address': 'Auris Station, 38142 Auris'},
        {'name': 'ESF Auris-en-Oisans', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Front de neige, 38142 Auris'},
        {'name': 'Skiset Auris', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:30', 'address': 'Centre station, 38142 Auris', 'website_url': 'https://www.skiset.com'},
        {'name': 'Secours des pistes Auris', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38142 Auris'},
        {'name': 'Restaurant L\'Avalanche Auris', 'type': 'Restaurant', 'opening_hours': '09:00-16:30', 'address': 'Front de neige, 38142 Auris', 'source_note': 'Restauration ski-in ski-out face aux pistes (à vérifier avant déplacement).'},
        {'name': 'Le Soleil Auris-en-Oisans', 'type': 'Restaurant', 'opening_hours': '12:00-14:30', 'address': 'Route de la station, 38142 Auris', 'source_note': 'Cave à fondue et tartiflettes savoyardes (à vérifier avant déplacement).'},
    ],
    'Le Sappey-en-Chartreuse': [
        {'name': 'Mairie / Info Le Sappey-en-Chartreuse', 'type': 'Information', 'opening_hours': '09:00-12:00 / 14:00-17:00', 'address': 'Le Bourg, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'ESF Le Sappey', 'type': 'École de ski', 'opening_hours': '09:00-16:30', 'address': 'Domaine du Sappey, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'Location Le Sappey Ski', 'type': 'Magasin / Location', 'opening_hours': '08:30-17:30', 'address': 'Centre village, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'Secours des pistes Le Sappey', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Domaine du Sappey, 38700 Le Sappey-en-Chartreuse'},
        {'name': 'Auberge Le Sappey-en-Chartreuse', 'type': 'Restaurant', 'opening_hours': '12:00-14:00 / 19:00-21:00', 'address': 'Le Bourg, 38700 Le Sappey-en-Chartreuse', 'source_note': 'Auberge avec cuisine du terroir chartreusain (à vérifier avant déplacement).'},
    ],
    'Saint-Pierre-de-Chartreuse': [
        {'name': 'Office de Tourisme Coeur de Chartreuse', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'ESF Saint-Pierre-de-Chartreuse', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'Le Planolet, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'Sport 2000 Saint-Pierre', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Plan de Ville, 38380 Saint-Pierre-de-Chartreuse', 'website_url': 'https://www.sport2000.fr'},
        {'name': 'Secours des pistes Saint-Pierre', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Le Planolet, 38380 Saint-Pierre-de-Chartreuse'},
        {'name': 'Restaurant La Chaumière Saint-Pierre', 'type': 'Restaurant', 'opening_hours': '12:00-14:30 / 19:00-21:30', 'address': 'Le Bourg, 38380 Saint-Pierre-de-Chartreuse', 'source_note': 'Cuisine régionale et grenobloise (à vérifier avant déplacement).'},
        {'name': 'Le Planolet Brasserie', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Le Planolet, 38380 Saint-Pierre-de-Chartreuse', 'source_note': 'Brasserie en pied de piste, sandwichs et plats du jour (à vérifier avant déplacement).'},
    ],
    'Lans-en-Vercors': [
        {'name': 'Office de Tourisme de Lans-en-Vercors', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38250 Lans-en-Vercors'},
        {'name': 'ESF Lans-en-Vercors', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': "Domaine de l'Aigle, 38250 Lans-en-Vercors"},
        {'name': 'Location Ski Lans', 'type': 'Magasin / Location', 'opening_hours': '08:30-17:30', 'address': 'Centre village, 38250 Lans-en-Vercors'},
        {'name': 'Secours des pistes Lans-en-Vercors', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': "Domaine de l'Aigle, 38250 Lans-en-Vercors"},
        {'name': 'Restaurant du Grand Veymont', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Domaine de l\'Aigle, 38250 Lans-en-Vercors', 'source_note': 'Restaurant en altitude face au Grand Veymont (à vérifier avant déplacement).'},
        {'name': 'Crêperie Vercors Lans', 'type': 'Restaurant', 'opening_hours': '11:30-15:00', 'address': 'Centre village, 38250 Lans-en-Vercors', 'source_note': 'Crêperies et galettes locales (à vérifier avant déplacement).'},
    ],
    'Gresse-en-Vercors': [
        {'name': 'Office de Tourisme de Gresse-en-Vercors', 'type': 'Information', 'opening_hours': '09:00-12:30 / 14:00-17:30', 'address': 'Place de la Mairie, 38650 Gresse-en-Vercors'},
        {'name': 'ESF Gresse-en-Vercors', 'type': 'École de ski', 'opening_hours': '09:00-17:00', 'address': 'La Station, 38650 Gresse-en-Vercors'},
        {'name': 'Skimium Gresse', 'type': 'Magasin / Location', 'opening_hours': '08:30-18:00', 'address': 'Front de neige, 38650 Gresse-en-Vercors', 'website_url': 'https://www.skimium.fr'},
        {'name': 'Secours des pistes Gresse-en-Vercors', 'type': 'Secours', 'opening_hours': 'Pendant ouverture du domaine', 'address': 'Poste de secours, 38650 Gresse-en-Vercors'},
        {'name': 'Restaurant Les Pistes Gresse', 'type': 'Restaurant', 'opening_hours': '09:00-17:00', 'address': 'Front de neige, 38650 Gresse-en-Vercors', 'source_note': 'Self-service et plats chauds en pied de piste (à vérifier avant déplacement).'},
        {'name': 'Auberge du Vercors Gresse', 'type': 'Restaurant', 'opening_hours': '12:00-14:00 / 19:00-21:00', 'address': 'Le Village, 38650 Gresse-en-Vercors', 'source_note': 'Cuisine du Vercors, fondues et diots (à vérifier avant déplacement).'},
    ],
}

STATION_OFFICIAL_CONTACTS = {
    'Chamrousse': {'website_url': 'https://www.chamrousse.com', 'phone': '+33 4 76 89 92 65'},
    'Les 7 Laux': {'website_url': 'https://www.les7laux.com', 'phone': '+33 4 76 08 17 86'},
    "Alpe d'Huez": {'website_url': 'https://www.alpedhuez.com', 'phone': '+33 4 76 11 44 44'},
    'Villard-de-Lans / Corrençon': {'website_url': 'https://www.villarddelans-correnconenvercors.com', 'phone': '+33 4 76 95 10 38'},
    'Autrans-Méaudre en Vercors': {'website_url': 'https://www.autrans-meaudre.fr', 'phone': '+33 4 76 95 30 70'},
    "Le Collet d'Allevard": {'website_url': 'https://www.lecollet.com', 'phone': '+33 4 76 45 01 88'},
    'Les 2 Alpes': {'website_url': 'https://www.les2alpes.com', 'phone': '+33 4 76 79 22 00'},
    'La Grave - La Meije': {'website_url': 'https://www.lagrave-lameije.com', 'phone': '+33 4 76 79 90 05'},
    'Oz 3300': {'website_url': 'https://www.oz3300.com', 'phone': '+33 4 76 80 78 01'},
    'Vaujany': {'website_url': 'https://www.vaujany.com', 'phone': '+33 4 76 80 72 37'},
    'Auris-en-Oisans': {'website_url': '', 'phone': '+33 4 76 80 13 52'},
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
    return [enrich_service_data(service, station_name) for service in specific_services]

BUS_LINES_SEED = [
    {
        'station_name': 'Chamrousse',
        'bus_number': 'N93',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Chamrousse - Roche Béranger',
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '1h10',
        'route_points': 'Gare Routière → Grand\'Place → Uriage → Chamrousse',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': 'Les 7 Laux',
        'bus_number': 'N94',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Prapoutel - Les 7 Laux',
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '1h05',
        'route_points': 'Gare Routière → Crolles → Brignoud → Prapoutel',
        'departure_latitude': 45.191210,
        'departure_longitude': 5.714260,
    },
    {
        'station_name': "Alpe d'Huez",
        'bus_number': 'T76',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Alpe d\'Huez Station',
        'frequency': 'Consulter l\'horaire officiel',
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
        'station_name': "Le Collet d'Allevard",
        'bus_number': 'N97',
        'departure_stop': 'Brignoud Gare',
        'arrival_stop': 'Le Collet d\'Allevard Front de neige',
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '50 min',
        'route_points': 'Brignoud → Allevard → Le Collet',
        'departure_latitude': 45.259167,
        'departure_longitude': 5.902222,
    },
    {
        'station_name': 'Les 2 Alpes',
        'bus_number': 'T73',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Les 2 Alpes Office de tourisme',
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '1h45',
        'route_points': 'Grenoble → Vizille → Bourg-d\'Oisans → Les 2 Alpes',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Vaujany',
        'bus_number': 'T71',
        'departure_stop': 'Grenoble Gare SNCF',
        'arrival_stop': 'Vaujany Office de tourisme',
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '1h30',
        'route_points': 'Grenoble → Allemond → Vaujany',
        'departure_latitude': 45.191887,
        'departure_longitude': 5.714684,
    },
    {
        'station_name': 'Saint-Pierre-de-Chartreuse',
        'bus_number': 'T40',
        'departure_stop': 'Grenoble Gare Routière',
        'arrival_stop': 'Saint-Pierre-de-Chartreuse Plan de Ville',
        'frequency': 'Consulter l\'horaire officiel',
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
        'frequency': 'Consulter l\'horaire officiel',
        'travel_time': '45 min',
        'route_points': 'Grenoble → Seyssins → Lans-en-Vercors',
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
    fallback_path = Path(__file__).resolve().parent / 'static' / 'images' / 'ski.png'
    if fallback_path.exists():
        return fallback_path.read_bytes()
    return None


@transaction.atomic
def seed_ski_stations():
    created_count = 0
    updated_count = 0

    station_by_name = {}
    for station in STATIONS_DATA:
        image_bytes = get_image_bytes(STATION_IMAGE_MAP.get(station['name'], 'ski.png'))

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
        service_names = {service['name'] for service in services}
        ServiceStore.objects.filter(ski_station=station).exclude(name__in=service_names).delete()
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

    active_bus_numbers_by_station = {}
    for item in BUS_LINES_SEED:
        station = station_by_name.get(item['station_name'])
        if not station:
            continue
        active_bus_numbers_by_station.setdefault(station.id, set()).add(item['bus_number'])
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

    for station in station_by_name.values():
        active_bus_numbers = active_bus_numbers_by_station.get(station.id, set())
        BusLine.objects.filter(ski_station=station).exclude(bus_number__in=active_bus_numbers).delete()

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
