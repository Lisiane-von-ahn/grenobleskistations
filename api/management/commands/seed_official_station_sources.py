from django.core.management.base import BaseCommand

from api.models import SkiStation, StationOfficialSource


# Official editorial pages are recorded as provenance; verified RSS/Atom endpoints can be
# added from Django admin and will be ingested automatically by fetch_ski_news_rss.
SOURCES = {
    'chamrousse': ('Chamrousse official blog', 'https://www.chamrousse.com/blog-chamrousse.html'),
    'les 2 alpes': ('Les 2 Alpes official news', 'https://www.les2alpes.com/hiver/live/presse/'),
    'alpe d huez': ('Alpe d’Huez official site', 'https://www.alpedhuez.com/'),
}


class Command(BaseCommand):
    help = 'Register known official resort news pages as transparent source records.'

    def handle(self, *args, **options):
        created = 0
        for station in SkiStation.objects.all():
            normalized = station.name.lower().replace("'", ' ').replace('-', ' ')
            for match, (name, url) in SOURCES.items():
                if match in normalized:
                    _, was_created = StationOfficialSource.objects.get_or_create(
                        ski_station=station,
                        url=url,
                        defaults={'name': name, 'source_type': StationOfficialSource.TYPE_PAGE},
                    )
                    created += int(was_created)
        self.stdout.write(self.style.SUCCESS(f'Added {created} official station source records.'))
