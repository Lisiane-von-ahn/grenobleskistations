from datetime import timedelta
from decimal import Decimal
from io import BytesIO, StringIO
from unittest.mock import patch

from django.core.cache import cache
from django.core.management import call_command
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from api.discovery import ski_assessment
from api.models import AppUsageEvent, CrowdStatusUpdate, GrenoblePlace, PisteConditionReport, SkiStation, SkiStationCamera, StationLiveStatus
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
        SkiStationCamera.objects.create(**camera_fields, name='Caméra inactive', is_active=False)
        detail_url = reverse('ski_station_detail', args=[self.station.pk])

        self.assertContains(self.client.get('/'), f'{detail_url}#cameras')
        detail = self.client.get(detail_url)
        self.assertContains(detail, 'id="cameras"')
        self.assertContains(detail, 'https://example.com/preview.jpg')
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
