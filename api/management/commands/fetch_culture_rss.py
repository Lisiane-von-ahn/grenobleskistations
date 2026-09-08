"""Import short, dated excerpts from Grenoble cultural institutions' own feeds."""
import html
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from email.utils import parsedate_to_datetime
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone
from django.utils.html import strip_tags
from api.models import CultureFeedSource, SkiNewsItem

ATOM = '{http://www.w3.org/2005/Atom}'
MAX_BYTES = 2 * 1024 * 1024


def plain_text(value):
    value = re.sub(r'<(script|style)\b[^>]*>.*?</\1>', '', value or '', flags=re.I | re.S)
    return ' '.join(html.unescape(strip_tags(value)).split())


def publication_date(value):
    if not value:
        return None
    try:
        parsed = parsedate_to_datetime(value)
    except (ValueError, TypeError, OverflowError):
        try:
            parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
        except (ValueError, TypeError):
            return None
    if timezone.is_naive(parsed):
        parsed = timezone.make_aware(parsed)
    return parsed


def safe_url(value, base):
    url = urljoin(base, (value or '').strip())
    return url if value and urlsplit(url).scheme == 'https' and urlsplit(url).hostname else ''


def parse_entries(raw, base):
    root = ET.fromstring(raw)
    if root.tag not in ('rss', f'{ATOM}feed'):
        raise ValueError('Expected an RSS or Atom document')
    nodes = root.findall('./channel/item') if root.tag == 'rss' else root.findall(f'{ATOM}entry')
    for item in nodes:
        atom = item.tag.startswith(ATOM)
        def value(name):
            return item.findtext(f'{ATOM}{name}' if atom else name) or ''
        link = value('link')
        if atom:
            link = next((a.get('href', '') for a in item.findall(f'{ATOM}link') if a.get('rel', 'alternate') == 'alternate'), '')
        enclosure = item.find('enclosure')
        image = enclosure.get('url', '') if enclosure is not None and enclosure.get('type', '').startswith('image/') else ''
        yield {
            'title': plain_text(value('title'))[:255],
            'link': safe_url(link, base),
            'summary': plain_text(value('summary') if atom else value('description'))[:280],
            'published_at': publication_date(value('published') or value('updated') if atom else value('pubDate')),
            'image_url': safe_url(image, base),
        }


class Command(BaseCommand):
    help = 'Refresh museum, performing arts and science RSS; preserve previous news if a source fails.'

    def add_arguments(self, parser):
        parser.add_argument('--days', type=int, default=180)
        parser.add_argument('--max-items', type=int, default=15, help='Maximum items per source')

    def handle(self, *args, **options):
        now = timezone.now()
        cutoff = now - timedelta(days=max(1, options['days']))
        total = 0
        for source in CultureFeedSource.objects.filter(is_active=True):
            try:
                if not safe_url(source.url, source.url):
                    raise ValueError('Feed must use HTTPS')
                with urlopen(Request(source.url, headers={'User-Agent': 'GrenobleSkiCulture/1.0'}), timeout=10) as response:
                    raw = response.read(MAX_BYTES + 1)
                if len(raw) > MAX_BYTES:
                    raise ValueError('Feed exceeds 2 MiB')
                entries = list(parse_entries(raw, source.url))
                imported = 0
                with transaction.atomic():
                    for entry in entries:
                        if not entry['title'] or not entry['link'] or len(entry['link']) > SkiNewsItem._meta.get_field('link').max_length:
                            continue
                        date = entry['published_at']
                        if date is None or date < cutoff or date > now + timedelta(days=1):
                            continue
                        if len(entry['image_url']) > SkiNewsItem._meta.get_field('image_url').max_length:
                            entry['image_url'] = ''
                        link = entry.pop('link')
                        SkiNewsItem.objects.update_or_create(link=link, defaults={
                            **entry, 'category': 'culture', 'culture_source': source,
                            'source_name': source.name, 'source_url': source.url,
                            'language': source.language, 'is_highlighted': False, 'ski_station': None,
                        })
                        imported += 1
                        if imported >= max(1, options['max_items']):
                            break
                    # Prune only this healthy culture source; never touch ski news.
                    SkiNewsItem.objects.filter(category='culture', culture_source=source, published_at__lt=cutoff).delete()
                    source.last_synced_at = now
                    source.last_error = ''
                    source.save(update_fields=['last_synced_at', 'last_error'])
                total += imported
                self.stdout.write(f'{source.name}: {imported} articles refreshed')
            except (OSError, ValueError, ET.ParseError) as exc:
                source.last_error = str(exc)[:300]
                source.save(update_fields=['last_error'])
                self.stderr.write(f'{source.name}: feed unavailable; existing articles retained')
        self.stdout.write(self.style.SUCCESS(f'Culture RSS: {total} articles refreshed.'))
