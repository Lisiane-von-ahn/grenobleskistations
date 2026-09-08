import json
from pathlib import Path
from django.core.management.base import BaseCommand
from django.db import transaction
from api.models import GrenoblePlace, SkiStation, SkiStationCamera

DATA = Path(__file__).resolve().parents[2] / 'seed_data'


class Command(BaseCommand):
    help = 'Seed Grenoble walks and attributed real photos from bundled assets (no network).'

    @transaction.atomic
    def handle(self, *args, **options):
        photos = {p['name']: p for p in json.loads((DATA / 'photos.json').read_text())}
        def image_fields(photo):
            return {
                'image': (DATA / 'photos' / photo['file']).read_bytes(),
                'photo_credit': f"{photo['credit']} · {photo['license']} · resized",
                'photo_source_url': photo['source'],
            }
        for place in json.loads((DATA / 'places.json').read_text()):
            slug = place.pop('slug')
            obj, created = GrenoblePlace.objects.get_or_create(slug=slug, defaults={**place, **image_fields(photos[slug])})
            if not obj.image:
                for key, value in image_fields(photos[slug]).items():
                    setattr(obj, key, value)
                obj.save(update_fields=['image', 'photo_credit', 'photo_source_url'])
        count = 0
        for station in SkiStation.objects.all():
            if station.name in photos and not station.photo_source_url:
                for key, value in image_fields(photos[station.name]).items():
                    setattr(station, key, value)
                station.save(update_fields=['image', 'photo_credit', 'photo_source_url'])
                count += 1
        for camera in json.loads((DATA / 'cameras.json').read_text()):
            for station in SkiStation.objects.filter(name=camera['station']):
                SkiStationCamera.objects.update_or_create(
                    ski_station=station, name=camera['name'],
                    defaults={'camera_url': camera['url'], 'camera_type': 'embedded',
                              'description': camera['label']},
                )
        self.stdout.write(self.style.SUCCESS(f'Discovery seeded: 3 places, {count} station photos updated.'))
