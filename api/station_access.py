"""Official winter lift status and ski pass references shared by web and Android."""

from datetime import date
from zoneinfo import ZoneInfo

from django.utils import timezone

from .discovery import ski_assessment


OZ_VAUJANY_PASS = 'https://www.oz-vaujany.com/hiver/forfait-sejour-oz-vaujany/'
ALPE_D_HUEZ_PASS = 'https://skipass.alpedhuez.com/hiver/forfait/forfait-ski/'

STATION_ACCESS = {
    'Chamrousse': {
        'opening_url': 'https://www.chamrousse.com/ouverture-piste-remontee-alpin-nordique.html',
        'schedule_url': 'https://www.chamrousse.com/remontees-mecaniques-1.html',
        'next_opening': date(2026, 12, 5),
        'purchase_url': 'https://ski.chamrousse.com/',
        'prices_url': 'https://www.chamrousse.com/medias/images/prestataires/133857-e2627_grille_alpin_light.pdf',
        'adult_day_price': '45,50 €',
    },
    'Les 7 Laux': {
        'opening_url': 'https://www.les7laux.com/hiver/ski-alpin-nordique-autres-glisses/ski/infos-pistes/',
        'next_opening': date(2026, 11, 28),
        'purchase_url': 'https://skipass.les7laux.com/',
        'prices_url': 'https://www.les7laux.com/hiver/ski-alpin-nordique-autres-glisses/ski/forfaits-tarifs/',
        'adult_day_price': '42–45 €',
    },
    "Alpe d'Huez": {
        'opening_url': 'https://explore.alpedhuez.com/fr/carte',
        'next_opening': date(2026, 12, 5),
        'purchase_url': ALPE_D_HUEZ_PASS,
        'prices_url': ALPE_D_HUEZ_PASS,
        'adult_day_price': '68 €',
        'price_high_season': True,
    },
    'Villard-de-Lans / Corrençon': {
        'opening_url': 'https://www.villarddelans-correnconenvercors.com/live/info-pistes/',
        'purchase_url': 'https://www.villardcorrencon.com/fr/',
        'prices_url': 'https://www.villardcorrencon.com/fr/GRILLETARIFAIREHIVER2627',
    },
    'Autrans-Méaudre en Vercors': {
        'opening_url': 'https://station.autrans-meaudre.fr/fr/nos-activites/ski-alpin/',
        'prices_url': 'https://media.station.autrans-meaudre.fr/filer_public/3c/cc/3ccc6ea6-17b7-4242-8b22-044e7bc16794/tarifs_hiver_alpin_26-27-v2.pdf',
        'purchase_url': 'https://station.autrans-meaudre.fr/fr/billetterie-forfaits/',
        'adult_day_price': '24,20 €',
    },
    "Le Collet d'Allevard": {
        'opening_url': 'https://www.lecollet.com/hiver/skier/infos-pistes/',
        'prices_url': 'https://skipass.lecollet.com/fr/tarifs-hiver',
        'purchase_url': 'https://skipass.lecollet.com/',
        'adult_day_price': '25–31 €',
    },
    'Les 2 Alpes': {
        'opening_url': 'https://www.les2alpes.com/hiver/live/etat-des-pistes-en-live/',
        'next_opening': date(2026, 11, 28),
        'schedule_url': 'https://www.skipass-2alpes.com/fr/tarifs',
        'purchase_url': 'https://www.skipass-2alpes.com/fr/forfaits/ski',
        'prices_url': 'https://www.skipass-2alpes.com/fr/tarifs',
    },
    'La Grave - La Meije': {
        'opening_url': 'https://la-grave.com/hiver-tarifs-horaires/',
        'prices_url': 'https://la-grave.com/hiver-tarifs-horaires/',
        'purchase_url': 'https://la-grave.com/tous-nos-forfaits-en-ligne/',
        'next_opening': date(2026, 12, 19),
        'adult_day_price': '68 €',
    },
    'Oz 3300': {
        'prices_url': OZ_VAUJANY_PASS,
        'purchase_url': OZ_VAUJANY_PASS,
        'adult_day_price': '49,50 €',
    },
    'Vaujany': {
        'opening_url': 'https://explore.vaujany.com/fr/carte',
        'prices_url': OZ_VAUJANY_PASS,
        'purchase_url': OZ_VAUJANY_PASS,
        'adult_day_price': '49,50 €',
    },
    'Auris-en-Oisans': {
        'purchase_url': 'https://www.auris-en-oisans.fr/preparer/achetez-vos-forfaits/',
    },
    'Le Sappey-en-Chartreuse': {
        'prices_url': 'https://www.chartreuse-tourisme.com/offres/domaine-alpin-du-sappey-en-chartreuse-le-sappey-en-chartreuse-fr-2717108/',
    },
    'Saint-Pierre-de-Chartreuse': {
        'prices_url': 'https://www.chartreuse-tourisme.com/preparer-son-sejour/sejours/idees-sejours/st-pierre-chartreuse-ski-famille/',
    },
    'Lans-en-Vercors': {
        'prices_url': 'https://skipass.lansenvercors.com/fr/FAQ',
        'purchase_url': 'https://skipass.lansenvercors.com/',
    },
    'Gresse-en-Vercors': {
        'opening_url': 'https://gresse-en-vercors.fr/rdsgv/',
        'prices_url': 'https://gresse-en-vercors.fr/tarifs/',
        'schedule_url': 'https://gresse-en-vercors.fr/tarifs/',
        'next_opening': date(2026, 12, 19),
    },
}


def station_access(station, *, now=None):
    now = now or timezone.now()
    today = now.astimezone(ZoneInfo('Europe/Paris')).date()
    access = STATION_ACCESS.get(station.name, {}).copy()
    live = getattr(station, 'live_status', None)
    assessment = ski_assessment(live, now=now)
    status = 'unknown'
    if assessment == 'closed':
        status = 'closed'
    elif live and assessment != 'stale' and (live.pistes_open or 0) > 0 and (live.lifts_open or 0) > 0:
        status = 'open'

    access['status'] = status
    if status != 'unknown':
        access['observed_at'] = live.observed_at
        if live.source_url:
            access['opening_url'] = live.source_url

    opening = access.get('next_opening')
    if status == 'open' or not opening or opening < today:
        access.pop('next_opening', None)
        access.pop('schedule_url', None)
    if access.get('adult_day_price') and date(2026, 11, 1) <= today <= date(2027, 4, 30):
        access['price_season'] = '2026–2027'
    else:
        access.pop('adult_day_price', None)
        access.pop('price_high_season', None)
    return access
