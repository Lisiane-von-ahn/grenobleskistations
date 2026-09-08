from datetime import timedelta
from email.utils import format_datetime
from io import BytesIO, StringIO
from unittest.mock import patch
from urllib.error import URLError

from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient
from api.models import CultureFeedSource, SkiNewsItem, SkiStation
from api.management.commands.fetch_culture_rss import parse_entries


class CultureFeedTests(TestCase):
    def setUp(self):
        CultureFeedSource.objects.update(is_active=False)
        self.source = CultureFeedSource.objects.create(name='Museum', url='https://museum.example/feed', topic='museum')
        self.date = format_datetime(timezone.now())

    def rss(self, link='https://museum.example/exhibition'):
        return f'''<rss version="2.0"><channel><item>
            <title>Art &amp; science</title><link>{link}</link>
            <description>&lt;p&gt;A family visit &amp;amp; workshop.&lt;/p&gt;</description>
            <pubDate>{self.date}</pubDate>
            <enclosure type="image/jpeg" url="https://museum.example/photo.jpg"/>
            </item></channel></rss>'''.encode()

    def test_import_preserves_source_date_image_and_long_links_without_duplicates(self):
        link = 'https://museum.example/' + 'exhibition-' * 25
        for _ in range(2):
            with patch('api.management.commands.fetch_culture_rss.urlopen', return_value=BytesIO(self.rss(link))):
                call_command('fetch_culture_rss', stdout=StringIO())
        self.assertEqual(SkiNewsItem.objects.count(), 1)
        item = SkiNewsItem.objects.get()
        self.assertEqual(item.category, 'culture')
        self.assertEqual(item.link, link)
        self.assertEqual(item.source_name, 'Museum')
        self.assertEqual(item.summary, 'A family visit & workshop.')
        self.assertTrue(item.image_url.endswith('photo.jpg'))
        self.source.refresh_from_db()
        self.assertIsNotNone(self.source.last_synced_at)
        self.assertFalse(self.source.last_error)

    def test_unavailable_or_invalid_feed_retains_previous_articles_and_sync_time(self):
        item = SkiNewsItem.objects.create(title='Existing', link='https://museum.example/old', category='culture', culture_source=self.source)
        for data in [b'<html>Not a feed</html>', b'<rss broken']:
            with patch('api.management.commands.fetch_culture_rss.urlopen', return_value=BytesIO(data)):
                call_command('fetch_culture_rss', stdout=StringIO(), stderr=StringIO())
            self.assertTrue(SkiNewsItem.objects.filter(pk=item.pk).exists())
            self.source.refresh_from_db()
            self.assertIsNone(self.source.last_synced_at)
            self.assertTrue(self.source.last_error)

    def test_atom_and_untrusted_links(self):
        raw = b'''<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Talk</title>
        <link href="/talk"/><updated>2026-09-08T10:00:00Z</updated><summary>Science</summary></entry>
        <entry><title>Bad</title><link href="javascript:alert(1)"/></entry></feed>'''
        rows = list(parse_entries(raw, self.source.url))
        self.assertEqual(rows[0]['link'], 'https://museum.example/talk')
        self.assertEqual(rows[0]['published_at'].year, 2026)
        self.assertEqual(rows[1]['link'], '')
        self.assertIsNone(rows[1]['published_at'])

    def test_culture_api_is_separate_and_hidden_sources_are_not_shown(self):
        culture = SkiNewsItem.objects.create(title='Art', link='https://museum.example/art', category='culture', culture_source=self.source)
        ski = SkiNewsItem.objects.create(title='Snow', link='https://ski.example/news')
        client = APIClient()
        def ids(url):
            data = client.get(url).data
            if isinstance(data, dict):
                data = data['results']
            return [row['id'] for row in data]
        self.assertEqual(ids('/api/ski-news/?category=culture'), [culture.id])
        self.assertEqual(ids('/api/ski-news/'), [ski.id])
        self.source.is_active = False
        self.source.save()
        self.assertEqual(ids('/api/ski-news/?category=culture'), [])

    def test_ski_refresh_does_not_prune_cultural_articles(self):
        old = timezone.now() - timedelta(days=30)
        culture = SkiNewsItem.objects.create(title='Culture', link='https://museum.example/history', category='culture', culture_source=self.source, published_at=old)
        with patch('api.management.commands.fetch_ski_news_rss.urlopen', side_effect=URLError('offline')):
            call_command('fetch_ski_news_rss', stdout=StringIO())
        self.assertTrue(SkiNewsItem.objects.filter(pk=culture.pk).exists())

    def test_website_home_shows_culture_without_mixing_station_news(self):
        from django.urls import reverse
        culture = SkiNewsItem.objects.create(title='Museum discovery', link='https://museum.example/discovery', category='culture', culture_source=self.source)
        ski = SkiNewsItem.objects.create(title='Mountain news', link='https://ski.example/mountain')
        station = SkiStation.objects.create(name='Test mountain', latitude=45.1, longitude=5.8)
        home = self.client.get('/')
        self.assertEqual(home.status_code, 200)
        self.assertContains(home, culture.title)
        detail = self.client.get(reverse('ski_station_detail', args=[station.id]))
        self.assertEqual(detail.status_code, 200)
        self.assertContains(detail, ski.title)
        self.assertNotContains(detail, culture.title)
