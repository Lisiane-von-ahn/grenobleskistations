import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone

from api.models import AccommodationPlace


OVERPASS_URLS = (
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass-api.de/api/interpreter',
)


class Command(BaseCommand):
    help = 'Refresh the daily local accommodation cache from OpenStreetMap/Overpass.'

    def handle(self, *args, **options):
        query = '''[out:json][timeout:90];
        nwr["tourism"~"^(hotel|guest_house|hostel|apartment|chalet|camp_site)$"]["name"](around:75000,45.1885,5.7245);
        out center tags;'''
        elements = None
        last_error = None
        for endpoint in OVERPASS_URLS:
            outgoing = Request(endpoint, data=urlencode({'data': query}).encode(), headers={
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'User-Agent': 'GrenobleSki/1.0 (https://www.grenobleski.fr)',
            })
            try:
                with urlopen(outgoing, timeout=100) as response:
                    elements = json.loads(response.read().decode()).get('elements', [])
                break
            except (HTTPError, URLError, TimeoutError, ValueError) as exc:
                last_error = exc
        if elements is None:
            raise CommandError(f'Overpass accommodation refresh failed: {last_error}')

        seen = set()
        now = timezone.now()
        with transaction.atomic():
            for element in elements:
                tags = element.get('tags') or {}
                coordinates = element.get('center') or element
                if not tags.get('name') or coordinates.get('lat') is None or coordinates.get('lon') is None:
                    continue
                key = (element.get('type', ''), element.get('id'))
                images = []
                if tags.get('image', '').startswith('http'):
                    images.append(tags['image'])
                if tags.get('wikimedia_commons', '').startswith('File:'):
                    filename = tags['wikimedia_commons'][5:].replace(' ', '_')
                    images.append(f'https://commons.wikimedia.org/wiki/Special:Redirect/file/{filename}')
                address = ' '.join(filter(None, [tags.get('addr:housenumber'), tags.get('addr:street')]))
                stars = str(tags.get('stars', '')).strip()
                AccommodationPlace.objects.update_or_create(osm_type=key[0], osm_id=key[1], defaults={
                    'name': tags['name'][:180], 'accommodation_type': tags.get('tourism', '')[:40],
                    'latitude': coordinates['lat'], 'longitude': coordinates['lon'],
                    'address': address[:300], 'city': tags.get('addr:city', '')[:120],
                    'website_url': (tags.get('website') or tags.get('contact:website') or '')[:700],
                    'image_urls': images[:5], 'stars': int(stars) if stars.isdigit() and int(stars) <= 5 else None,
                    'source_updated_at': now,
                })
                seen.add(key)
            if seen:
                AccommodationPlace.objects.exclude(source_updated_at=now).delete()
        self.stdout.write(self.style.SUCCESS(f'Cached {len(seen)} real accommodations from OpenStreetMap.'))
