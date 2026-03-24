from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0030_restore_service_and_bus_fields'),
    ]

    operations = [
        migrations.AddField(
            model_name='skistation',
            name='piste_map_url',
            field=models.URLField(blank=True, null=True),
        ),
    ]
