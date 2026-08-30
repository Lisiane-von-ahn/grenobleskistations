import re
import xml.etree.ElementTree as ET
from datetime import timedelta
from email.utils import parsedate_to_datetime
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen

from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from api.models import SkiNewsItem, SkiStation, StationOfficialSource

RSS_FEEDS = [
    {
        'url': 'https://news.google.com/rss/search?q=stations+de+ski+Grenoble+when%3A7d&hl=fr&gl=FR&ceid=FR%3Afr',
        'language': SkiNewsItem.LANG_FR,
        'source_name': 'Google News',
    },
    {
        'url': 'https://news.google.com/rss/search?q=ski+resorts+near+Grenoble+when%3A7d&hl=en-US&gl=US&ceid=US%3Aen',
        'language': SkiNewsItem.LANG_EN,
        'source_name': 'Google News',
    },
]

HIGHLIGHT_TERMS = {
    'fr': ['neige', 'poudreuse', 'station', 'alpes', 'isere', 'grenoble', 'ski'],
    'en': ['snow', 'powder', 'resort', 'alps', 'grenoble', 'ski'],
}


def _strip_html(text):
    if not text:
        return ''
    return re.sub(r'<[^>]+>', ' ', text).replace('\n', ' ').strip()


def _extract_image_url(description):
    if not description:
        return ''
    match = re.search(r'src=["\']([^"\']+)["\']', description)
    return match.group(1) if match else ''


def _fit_max_len(value, max_len):
    if not value:
        return ''
    if max_len is None or len(value) <= max_len:
        return value
    return value[:max_len]


def _parse_pub_date(value):
    if not value:
        return timezone.now()
    try:
        dt = parsedate_to_datetime(value)
        if timezone.is_naive(dt):
            dt = timezone.make_aware(dt)
        return dt.astimezone(timezone.get_current_timezone())
    except Exception:
        return timezone.now()


def _station_for_text(stations, text):
    lowered = (text or '').lower()
    for station in stations:
        if station.name.lower() in lowered:
            return station
    return None


class Command(BaseCommand):
    help = 'Fetch ski news from RSS feeds and store highlighted entries for Grenoble area.'

    def add_arguments(self, parser):
        parser.add_argument('--max-items', type=int, default=80)
        parser.add_argument('--days', type=int, default=10)
        parser.add_argument('--sample-on-failure', action='store_true')
        parser.add_argument('--official-only', action='store_true')

    @transaction.atomic
    def handle(self, *args, **options):
        max_items = max(10, int(options['max_items']))
        days = max(1, int(options['days']))
        sample_on_failure = bool(options['sample_on_failure'])
        now = timezone.now()
        min_published = now - timedelta(days=days)

        stations = list(SkiStation.objects.order_by('distanceFromGrenoble', 'name')[:80])
        link_max_len = SkiNewsItem._meta.get_field('link').max_length
        source_url_max_len = SkiNewsItem._meta.get_field('source_url').max_length
        image_url_max_len = SkiNewsItem._meta.get_field('image_url').max_length
        fetched_count = 0
        skipped_too_long = 0
        touched_links = set()
        official_sources = list(StationOfficialSource.objects.select_related('ski_station').filter(is_active=True, source_type=StationOfficialSource.TYPE_RSS))
        feeds = [] if options['official_only'] else list(RSS_FEEDS)
        feeds.extend({'url': source.url, 'language': source.language, 'source_name': source.name, 'station': source.ski_station, 'source': source} for source in official_sources)

        for feed in feeds:
            req = Request(feed['url'], headers={'User-Agent': 'GrenobleSkiNewsBot/1.0'})
            try:
                with urlopen(req, timeout=8) as resp:
                    raw_xml = resp.read()
            except (HTTPError, URLError, TimeoutError) as exc:
                self.stdout.write(self.style.WARNING(f"RSS fetch failed for {feed['url']}: {exc}"))
                if feed.get('source'):
                    feed['source'].last_error = str(exc)[:300]
                    feed['source'].save(update_fields=['last_error'])
                continue

            try:
                root = ET.fromstring(raw_xml)
            except ET.ParseError as exc:
                self.stdout.write(self.style.WARNING(f"Invalid XML feed {feed['url']}: {exc}"))
                continue

            if feed.get('source'):
                feed['source'].last_synced_at = now
                feed['source'].last_error = ''
                feed['source'].save(update_fields=['last_synced_at', 'last_error'])

            for item in root.findall('.//item'):
                title = (item.findtext('title') or '').strip()
                link = (item.findtext('link') or '').strip()
                description_raw = (item.findtext('description') or '').strip()
                description = _strip_html(description_raw)
                pub_date = _parse_pub_date(item.findtext('pubDate'))

                if not title or not link:
                    continue

                if link_max_len and len(link) > link_max_len:
                    skipped_too_long += 1
                    continue

                if pub_date < min_published:
                    continue

                station = feed.get('station') or _station_for_text(stations, f"{title} {description}")
                terms = HIGHLIGHT_TERMS.get(feed['language'], [])
                haystack = f"{title} {description}".lower()
                is_highlighted = any(term in haystack for term in terms) or station is not None

                source_name = feed.get('source_name') or (item.findtext('source') or '').strip()
                image_url = _fit_max_len(_extract_image_url(description_raw), image_url_max_len)
                summary = description[:600]
                source_url = _fit_max_len(feed['url'], source_url_max_len)

                SkiNewsItem.objects.update_or_create(
                    link=link,
                    defaults={
                        'title': title[:255],
                        'summary': summary,
                        'source_name': source_name[:120],
                        'source_url': source_url,
                        'language': feed['language'],
                        'ski_station': station,
                        'image_url': image_url,
                        'published_at': pub_date,
                        'is_highlighted': is_highlighted,
                    },
                )
                touched_links.add(link)
                fetched_count += 1
                if fetched_count >= max_items:
                    break

            if fetched_count >= max_items:
                break

        if fetched_count == 0 and sample_on_failure:
            fallback_station = stations[0] if stations else None
            for idx, lang in enumerate([SkiNewsItem.LANG_FR, SkiNewsItem.LANG_EN], start=1):
                link = f'https://www.grenobleski.fr/news/sample-{lang}-{idx}'
                SkiNewsItem.objects.update_or_create(
                    link=link,
                    defaults={
                        'title': 'Meteo ski et ouverture des pistes pres de Grenoble' if lang == 'fr' else 'Ski weather and slopes opening near Grenoble',
                        'summary': 'Actualites montagne, enneigement et circulation vers les stations proches de Grenoble.' if lang == 'fr' else 'Mountain updates, snowfall and road access for Grenoble nearby resorts.',
                        'source_name': 'GrenobleSki',
                        'source_url': 'https://www.grenobleski.fr/',
                        'language': lang,
                        'ski_station': fallback_station,
                        'image_url': '',
                        'published_at': now,
                        'is_highlighted': True,
                    },
                )
                touched_links.add(link)
            fetched_count = 2

        SkiNewsItem.objects.filter(published_at__lt=min_published).delete()

        self.stdout.write(self.style.SUCCESS(f'RSS sync complete: {fetched_count} news items upserted.'))
        if skipped_too_long:
            self.stdout.write(self.style.WARNING(f'Skipped {skipped_too_long} entries with link length > {link_max_len}.'))
