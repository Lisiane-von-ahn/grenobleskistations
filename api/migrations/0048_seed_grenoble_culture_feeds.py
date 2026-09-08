from django.db import migrations


SOURCES = [
    ('Musée de Grenoble', 'https://www.museedegrenoble.fr/rss_actualite.rss', 'museum'),
    ('MC2 · Maison de la Culture', 'https://www.mc2grenoble.fr/feed/', 'performing_arts'),
    ('La Casemate', 'https://lacasemate.fr/feed/', 'science'),
]


def seed_sources(apps, schema_editor):
    Source = apps.get_model('api', 'CultureFeedSource')
    for name, url, topic in SOURCES:
        Source.objects.using(schema_editor.connection.alias).get_or_create(
            url=url, defaults={'name': name, 'topic': topic, 'language': 'fr'},
        )


class Migration(migrations.Migration):
    dependencies = [('api', '0047_culturefeedsource_skinewsitem_category_and_more')]
    operations = [migrations.RunPython(seed_sources, migrations.RunPython.noop)]
