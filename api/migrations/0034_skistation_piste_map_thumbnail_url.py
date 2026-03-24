from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0033_busline_missing_fields'),
    ]

    operations = [
        migrations.AddField(
            model_name='skistation',
            name='piste_map_thumbnail_url',
            field=models.URLField(blank=True, null=True),
        ),
    ]
