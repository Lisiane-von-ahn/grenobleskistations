import json
from datetime import timedelta
from io import BytesIO
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from .models import SkiStation, StationAlert, StationLiveStatus, UserProfile


class PlanningTests(TestCase):
    def setUp(self):
        self.station = SkiStation.objects.create(name='Chamrousse', latitude='45.1', longitude='5.9')
        self.user = get_user_model().objects.create_user(username='skier', password='password')
        self.profile, _ = UserProfile.objects.get_or_create(user=self.user)
        self.client = APIClient()

    def test_favorites_require_authentication_and_are_isolated(self):
        self.assertIn(self.client.post('/api/station-favorites/', {}).status_code, (401, 403))
        self.client.force_authenticate(self.user)
        response = self.client.post('/api/station-favorites/', {'station_id': self.station.pk, 'enabled': True}, format='json')
        self.assertEqual(response.data['station_ids'], [self.station.pk])
        self.assertEqual(self.client.post('/api/station-favorites/', {'station_id': self.station.pk, 'enabled': 'false'}, format='json').status_code, 400)
        other = get_user_model().objects.create_user(username='other')
        self.client.force_authenticate(other)
        self.assertEqual(self.client.get('/api/station-favorites/').data['station_ids'], [])

    def test_bulletins_generate_alerts_for_followers_and_retries_do_not_duplicate(self):
        self.profile.favorite_stations.add(self.station)
        live = StationLiveStatus.objects.create(
            ski_station=self.station, pistes_open=2, lifts_open=1, snow_fall_24h_cm=10,
            operational_notice='Wind at the summit',
        )
        self.assertEqual(StationAlert.objects.filter(user=self.user).count(), 3)
        live.save()
        self.assertEqual(StationAlert.objects.filter(user=self.user).count(), 3)
        self.client.force_authenticate(self.user)
        response = self.client.get('/api/station-alerts/')
        self.assertEqual(len(response.data), 3)
        ids = [row['id'] for row in response.data]
        self.client.post('/api/station-alerts/', {'ids': ids}, format='json')
        self.assertFalse(StationAlert.objects.filter(user=self.user, read_at__isnull=True).exists())

    def test_stale_bulletins_do_not_generate_alerts(self):
        self.profile.favorite_stations.add(self.station)
        StationLiveStatus.objects.create(
            ski_station=self.station, pistes_open=2, lifts_open=1,
            observed_at=timezone.now() - timedelta(days=2),
        )
        self.assertFalse(StationAlert.objects.exists())

    @override_settings(GOOGLE_ROUTES_API_KEY='key')
    def test_return_journey_reverses_endpoints_and_passes_departure(self):
        departure = (timezone.now() + timedelta(days=1)).strftime('%Y-%m-%dT%H:%M')
        with patch('skistation_project.station_routes.urlopen', return_value=BytesIO(b'{"routes":[]}')) as provider:
            response = self.client.post(reverse('ski_station_route', args=[self.station.pk]), {
                'origin': 'Grenoble, France', 'mode': 'TRANSIT', 'direction': 'return', 'departure_time': departure,
            })
        self.assertEqual(response.status_code, 200)
        payload = json.loads(provider.call_args.args[0].data)
        self.assertEqual(payload['destination'], {'address': 'Grenoble, France'})
        self.assertIn('location', payload['origin'])
        self.assertIn('departureTime', payload)

    def test_comparison_and_offline_pages_render(self):
        self.assertContains(self.client.get('/compare/'), self.station.name)
        self.assertContains(self.client.get('/offline/'), 'offline-preview')
        self.assertEqual(self.client.get('/route-offline-worker.js')['Content-Type'], 'application/javascript')
