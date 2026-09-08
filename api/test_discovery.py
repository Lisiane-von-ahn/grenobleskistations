from datetime import timedelta
from decimal import Decimal
from io import BytesIO, StringIO
from unittest.mock import patch

from django.core.cache import cache
from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from api.discovery import ski_assessment
from api.models import GrenoblePlace, SkiStation, SkiStationCamera, StationLiveStatus
from api.serializers import SkiStationSerializer


class DiscoveryTests(TestCase):
    def setUp(self):
        self.station = SkiStation.objects.create(name='Chamrousse', latitude=45.1, longitude=5.8)
        self.client = APIClient()

    def test_seed_is_offline_repeatable_and_preserves_editorial_changes(self):
        with patch('urllib.request.urlopen', side_effect=AssertionError('No network during seed')):
            call_command('seed_discovery', stdout=StringIO())
            place = GrenoblePlace.objects.get(slug='bastille')
            place.description_en = 'Editor content'
            place.save()
            call_command('seed_discovery', stdout=StringIO())
        self.assertEqual(GrenoblePlace.objects.count(), 3)
        self.assertEqual(SkiStationCamera.objects.count(), 1)
        self.assertEqual(GrenoblePlace.objects.get(slug='bastille').description_en, 'Editor content')
        self.station.refresh_from_db()
        for obj in [self.station, *GrenoblePlace.objects.all()]:
            Image.open(BytesIO(bytes(obj.image))).verify()
            self.assertIn('commons.wikimedia.org', obj.photo_source_url)
            self.assertTrue(obj.photo_credit)

    def test_places_are_public_read_only_and_images_are_serialized(self):
        call_command('seed_discovery', stdout=StringIO())
        response = self.client.get('/api/grenoble-places/')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 3)
        self.assertTrue(response.data[0]['image'])
        self.assertEqual(self.client.post('/api/grenoble-places/', {}).status_code, 405)
        self.assertEqual(SkiStationSerializer(self.station).data['ski_assessment'], 'unknown')

    def test_assessment_requires_fresh_complete_operator_data(self):
        status = StationLiveStatus(ski_station=self.station, temperature_c=Decimal('-2'),
                                   snow_depth_cm=70, pistes_open=8, lifts_open=3)
        self.assertEqual(ski_assessment(status), 'check_bulletin')
        status.pistes_open = 0
        self.assertEqual(ski_assessment(status), 'closed')
        status.observed_at = timezone.now() - timedelta(hours=7)
        self.assertEqual(ski_assessment(status), 'stale')
        status.observed_at = timezone.now() + timedelta(hours=1)
        self.assertEqual(ski_assessment(status), 'stale')
        status.observed_at = timezone.now()
        status.pistes_open = 8
        status.temperature_c = Decimal('8')
        self.assertEqual(ski_assessment(status), 'unfavourable')
        status.snow_depth_cm = None
        self.assertEqual(ski_assessment(status), 'unknown')

    def test_weather_is_cached_and_does_not_update_operator_report(self):
        cache.clear()
        payload = {'temperature_c': 4, 'observed_at': '2026-09-08T10:00:00+00:00'}
        with patch('api.views._fetch_weather_summary', return_value=payload) as fetch:
            first = self.client.get('/api/station-weather/')
            second = self.client.get('/api/station-weather/')
        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.data[0]['temperature_c'], 4)
        fetch.assert_called_once()
        self.assertFalse(StationLiveStatus.objects.exists())
