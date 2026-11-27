import json
from datetime import datetime, timedelta, timezone as utc_timezone
from decimal import Decimal
from email.message import Message
from io import BytesIO, StringIO
from unittest.mock import patch

from django.core.cache import cache
from django.core.management import call_command
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.test import TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from api.discovery import estimate_ski_crowd, ski_assessment
from api.models import AppUsageEvent, BusLine, CrowdStatusUpdate, GrenoblePlace, PisteConditionReport, SkiStation, SkiStationCamera, StationLiveStatus
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

    def test_public_navigation_is_compact_and_android_download_is_stable(self):
        response = self.client.get('/')
        self.assertEqual(response.status_code, 200)
        html = response.content.decode()
        self.assertIn('id="site-main-nav"', html)
        self.assertEqual(html.count('class="nav-dropdown"'), 2)
        self.assertIn('id="mobile-app"', html)
        self.assertIn('https://play.google.com/store/apps/details?id=fr.grenobleski.nativeapp', html)
        self.assertIn('https://api.qrserver.com/v1/create-qr-code/', html)
        self.assertNotIn('>Confidentialité</a></li>', html)

    def test_account_actions_stay_available_inside_grouped_menu(self):
        user = get_user_model().objects.create_user(username='menu-user', password='menu-password')
        self.client.force_login(user)
        response = self.client.get('/')
        html = response.content.decode()
        self.assertIn('href="/profile/"', html)
        self.assertIn('href="/messages/"', html)
        self.assertIn('method="post" action="/logout/"', html)

    def test_account_workspace_contains_shortcuts_and_protects_deletion(self):
        user = get_user_model().objects.create_user(username='member', password='member-password')
        self.client.force_login(user)
        profile = self.client.get(reverse('profile'))
        self.assertContains(profile, reverse('my_carpool_reservations'))
        self.assertContains(profile, reverse('account_change_password'))
        self.assertContains(profile, 'id="workspace-title"')
        self.assertEqual(self.client.get(reverse('delete_account')).status_code, 405)
        self.assertTrue(get_user_model().objects.filter(pk=user.pk).exists())

    def test_google_sign_in_keeps_the_return_destination(self):
        response = self.client.get(reverse('account_login'), {'next': reverse('profile')})
        self.assertContains(response, 'images/google-g.png')
        self.assertContains(response, f'{reverse("google_login")}?next={reverse("profile")}')

    def test_webcams_are_linked_from_home_and_inactive_cameras_are_hidden(self):
        camera_fields = {'ski_station': self.station, 'camera_url': 'https://example.com/camera'}
        SkiStationCamera.objects.create(
            **camera_fields, name='Sommet', thumbnail_url='https://example.com/preview.jpg',
        )
        SkiStationCamera.objects.create(
            **camera_fields, name='Versant', thumbnail_url='https://example.com/second-preview.jpg',
        )
        SkiStationCamera.objects.create(**camera_fields, name='Caméra inactive', is_active=False)
        detail_url = reverse('ski_station_detail', args=[self.station.pk])

        self.assertContains(self.client.get('/'), f'{detail_url}#cameras')
        detail = self.client.get(detail_url)
        self.assertContains(detail, 'id="cameras"')
        self.assertContains(detail, 'https://example.com/preview.jpg')
        self.assertContains(detail, 'https://example.com/second-preview.jpg')
        self.assertContains(detail, 'loading="eager"')
        self.assertContains(detail, 'loading="lazy"')
        self.assertNotContains(detail, 'Caméra inactive')

    def test_home_loads_the_first_supported_camera_and_can_switch(self):
        live = SkiStationCamera.objects.create(
            ski_station=self.station, name='La Croix', camera_type='embedded',
            camera_url='https://www.skaping.com/chamrousse/la-croix',
        )
        other = SkiStationCamera.objects.create(
            ski_station=self.station, name='Other', camera_type='embedded',
            camera_url='https://www.skaping.com.example.com/chamrousse/la-croix',
        )
        home = self.client.get('/')
        self.assertEqual(home.context['selected_webcam']['camera'], live)
        self.assertContains(home, f'<iframe src="{live.camera_url}"', html=False)
        self.assertContains(home, 'loading="eager"')

        selected = self.client.get('/', {'webcam': other.pk})
        self.assertEqual(selected.context['selected_webcam']['camera'], other)
        self.assertNotContains(selected, f'<iframe src="{other.camera_url}"')

    def test_crowd_estimate_uses_local_hours_and_weekends_without_reports(self):
        saturday = datetime(2026, 12, 12, 9, tzinfo=utc_timezone.utc)
        friday = saturday - timedelta(days=1)
        weekend = estimate_ski_crowd(None, now=saturday)
        weekday = estimate_ski_crowd(None, now=friday)
        self.assertEqual([(slot['level'], slot['current']) for slot in weekend['slots']],
                         [('normal', False), ('busy', True), ('normal', False)])
        self.assertEqual([slot['level'] for slot in weekday['slots']], ['quiet', 'normal', 'quiet'])

        with patch('skistation_project.views.timezone.now', return_value=saturday):
            response = self.client.get(reverse('ski_station_detail', args=[self.station.pk]))
        self.assertEqual(response.context['crowd_total'], 0)
        self.assertContains(response, 'class="crowd-estimate-busy"')
        self.assertContains(response, 'aria-current="time"')

    def test_crowd_estimate_respects_fresh_closure_and_season(self):
        winter = datetime(2026, 12, 12, 9, tzinfo=utc_timezone.utc)
        summer = datetime(2026, 7, 18, 9, tzinfo=utc_timezone.utc)
        self.assertEqual(estimate_ski_crowd(None, now=summer)['state'], 'out_of_season')
        status = StationLiveStatus(ski_station=self.station, observed_at=winter, pistes_open=0)
        self.assertEqual(estimate_ski_crowd(status, now=winter)['state'], 'closed')
        self.assertEqual(estimate_ski_crowd(status, now=winter + timedelta(hours=7))['state'], 'estimate')

    def test_crowd_uses_visitor_updates_in_the_selected_time_window(self):
        visitor = get_user_model().objects.create_user(username='crowd-visitor')
        CrowdStatusUpdate.objects.create(ski_station=self.station, user=visitor, crowd_level='busy')
        previous = CrowdStatusUpdate.objects.create(ski_station=self.station, user=visitor, crowd_level='quiet')
        CrowdStatusUpdate.objects.filter(pk=previous.pk).update(created_at=timezone.now() - timedelta(hours=5))
        PisteConditionReport.objects.create(ski_station=self.station, user=visitor, piste_rating=5)
        url = reverse('ski_station_detail', args=[self.station.pk])

        short = self.client.get(url, {'trend': '3h'})
        self.assertContains(short, 'id="affluence"')
        self.assertEqual({row['key']: row['count'] for row in short.context['crowd_rows']},
                         {'quiet': 0, 'normal': 0, 'busy': 1})
        self.assertEqual(self.client.get(url, {'trend': '24h'}).context['crowd_total'], 2)

    def test_station_search_filters_by_name_and_active_webcam(self):
        SkiStation.objects.create(name='Chamrousse annexe', latitude=45.1, longitude=5.8)
        SkiStationCamera.objects.create(
            ski_station=self.station, name='Sommet', camera_type='snapshot',
            camera_url='https://example.com/camera.jpg',
        )
        response = self.client.get(reverse('ski_station_search'), {'q': 'chamrousse', 'webcams': '1', 'distance': 'abc'})
        self.assertEqual(list(response.context['ski_stations']), [self.station])

    @override_settings(GOOGLE_ROUTES_API_KEY='')
    def test_route_page_embeds_bus_fallback_without_provider(self):
        BusLine.objects.create(ski_station=self.station, bus_number='N93', departure_stop='Grenoble',
                               arrival_stop='Le Recoin', travel_time='60 min')
        url = reverse('ski_station_route', args=[self.station.pk])
        page = self.client.get(url, {'embedded': '1'})
        self.assertContains(page, 'N93')
        self.assertContains(page, 'Le Recoin')
        self.assertNotContains(page, 'id="site-main-nav"')
        self.assertIn('no-store', page['Cache-Control'])

    @override_settings(GOOGLE_ROUTES_API_KEY='test-key')
    def test_route_calculation_is_on_demand_and_displays_attributed_steps(self):
        url = reverse('ski_station_route', args=[self.station.pk])
        raw = BytesIO(b'{"routes":[{"localizedValues":{"duration":{"text":"48 min"}},'
                      b'"legs":[{"steps":[{"navigationInstruction":{"instructions":"Follow D111"}}]}]}]}')
        with patch('skistation_project.station_routes.urlopen', return_value=raw) as provider:
            self.assertEqual(self.client.get(url).status_code, 200)
            self.assertEqual(self.client.post(url, {'mode': 'bad'}).status_code, 400)
            provider.assert_not_called()
            response = self.client.post(url, {'mode': 'DRIVE', 'origin': 'Vizille, France'})
        self.assertContains(response, '48 min')
        self.assertContains(response, 'Follow D111')
        self.assertContains(response, 'translate="no">Google Maps')
        self.assertNotContains(response, 'test-key')
        self.assertIn(b'"travelMode": "DRIVE"', provider.call_args.args[0].data)
        self.assertIn(b'Vizille, France', provider.call_args.args[0].data)

    @override_settings(GOOGLE_ROUTES_API_KEY='test-key')
    def test_map_view_draws_route_without_rendering_steps_or_exposing_the_key(self):
        url = reverse('ski_station_route', args=[self.station.pk]) + '?embedded=1'
        raw = BytesIO(json.dumps({'routes': [{
            'localizedValues': {'duration': {'text': '48 min'}},
            'polyline': {'encodedPolyline': 'abc?def'},
            'legs': [{'steps': [{'navigationInstruction': {'instructions': 'Follow D111'}}]}],
        }]}).encode())
        image = BytesIO()
        Image.new('RGB', (4, 4)).save(image, format='PNG')
        image.seek(0)
        image.headers = Message()
        image.headers['Content-Type'] = 'image/png'
        with patch('skistation_project.station_routes.urlopen', side_effect=[raw, image]) as provider:
            response = self.client.post(url, {'presentation': 'map', 'mode': 'DRIVE'})
        self.assertContains(response, 'data:image/png;base64,')
        self.assertContains(response, '48 min')
        self.assertNotContains(response, 'Follow D111')
        self.assertNotContains(response, 'test-key')
        self.assertNotIn('routes.legs.steps', provider.call_args_list[0].args[0].get_header('X-goog-fieldmask'))
        self.assertIn('enc%3Aabc%3Fdef', provider.call_args_list[1].args[0])

    @override_settings(GOOGLE_ROUTES_API_KEY='test-key')
    def test_transit_steps_highlight_the_second_boarding_as_a_connection(self):
        url = reverse('ski_station_route', args=[self.station.pk])
        steps = [
            {'transitDetails': {'transitLine': {'nameShort': 'A', 'vehicle': {'type': 'TRAM'}}}},
            {'travelMode': 'WALK', 'navigationInstruction': {'instructions': 'Walk to the bus stop'}},
            {'transitDetails': {'transitLine': {'nameShort': 'T65', 'vehicle': {'type': 'BUS'}}}},
        ]
        raw = BytesIO(json.dumps({'routes': [{'legs': [{'steps': steps}]}]}).encode())
        with patch('skistation_project.station_routes.urlopen', return_value=raw):
            response = self.client.post(url, {'mode': 'TRANSIT'})
        self.assertContains(response, 'fa-train-tram')
        self.assertContains(response, 'Walk to the bus stop')
        self.assertContains(response, 'Correspondance', count=1)
        self.assertContains(response, 'T65')
        self.assertNotContains(response, 'data:image/png;base64,')

    def test_app_station_feed_includes_measured_zero_snow_and_crowd(self):
        StationLiveStatus.objects.create(
            ski_station=self.station, temperature_c=0, snow_depth_cm=0,
            observed_at=timezone.now() - timedelta(hours=8),
        )
        user = get_user_model().objects.create_user(username='recent-reporter')
        CrowdStatusUpdate.objects.create(ski_station=self.station, user=user, crowd_level='normal')
        response = self.client.get('/api/skistations/?image=url')
        row = (response.data.get('results', []) if isinstance(response.data, dict) else response.data)[0]
        self.assertEqual(row['snow_depth_cm'], 0)
        self.assertEqual(row['snow_depth']['source'], 'operator')
        self.assertEqual(row['crowd_estimate']['reported_level'], 'normal')
        self.assertNotIn('image', row)

    @override_settings(OPEN_METEO_API_KEY='test-key')
    def test_snow_depth_fallback_batches_stations_and_preserves_zero(self):
        SkiStation.objects.create(name='Les 7 Laux', latitude=45.24, longitude=5.97)
        now = timezone.now()
        model_values = [
            {'current_units': {'snow_depth': 'm'},
             'current': {'snow_depth': depth, 'time': now.replace(tzinfo=None).isoformat()}}
            for depth in (0.25, 0.0)
        ]
        cache.clear()
        with patch('api.snow_depth._model_readings', return_value=model_values) as model:
            for _ in range(2):
                response = self.client.get('/api/skistations/?image=url')
                self.assertEqual(response.status_code, 200)
                rows = response.data.get('results', []) if isinstance(response.data, dict) else response.data
                self.assertCountEqual([row['snow_depth_cm'] for row in rows], [25, 0])
                self.assertTrue(all(row['snow_depth']['source'] == 'open_meteo' for row in rows))
        model.assert_called_once()
        self.assertEqual(len(model.call_args.args[0]), 2)

    def test_station_photo_is_cached_separately_from_html(self):
        image = BytesIO()
        Image.new('RGB', (4, 4)).save(image, format='PNG')
        self.station.image = image.getvalue()
        self.station.save(update_fields=['image'])
        photo_url = reverse('ski_station_photo', args=[self.station.pk])

        homepage = self.client.get('/')
        self.assertContains(homepage, photo_url)
        self.assertNotContains(homepage, 'data:image/jpeg;base64,')
        photo = self.client.get(photo_url)
        self.assertEqual(photo['Content-Type'], 'image/png')
        self.assertIn('max-age=3600', photo['Cache-Control'])
        self.assertEqual(photo.content, image.getvalue())
        resized = self.client.get(photo_url, {'width': 480})
        self.assertEqual(resized['Content-Type'], 'image/webp')
        self.assertEqual(Image.open(BytesIO(resized.content)).size, (4, 4))
        self.assertFalse(AppUsageEvent.objects.filter(path=photo_url).exists())

    def test_admin_traffic_dashboard_counts_web_views_and_requires_permission(self):
        self.client.get('/?test=private-value', HTTP_USER_AGENT='Android Mobile')
        self.client.get('/', HTTP_USER_AGENT='Desktop')
        event = AppUsageEvent.objects.get(feature_name='home', platform=AppUsageEvent.PLATFORM_MOBILE)
        self.assertEqual(event.path, '/')
        self.assertEqual(event.metadata, {})

        staff = get_user_model().objects.create_user(username='traffic-staff', is_staff=True)
        self.client.force_login(staff)
        dashboard_url = reverse('admin:app-usage-dashboard')
        self.assertEqual(self.client.get(dashboard_url).status_code, 403)
        self.assertNotContains(self.client.get(reverse('admin:index')), dashboard_url)
        staff.user_permissions.add(Permission.objects.get(codename='view_appusageevent'))

        self.assertContains(self.client.get(reverse('admin:index')), dashboard_url)
        dashboard = self.client.get(dashboard_url)
        self.assertEqual(dashboard.status_code, 200)
        self.assertEqual(dashboard.context['page_views_count'], 2)
        self.assertEqual(dashboard.context['page_views_24h'], 2)
        self.assertEqual(dashboard.context['mobile_page_views'], 1)
        self.assertEqual(dashboard.context['daily_page_views'][-1]['total'], 2)
