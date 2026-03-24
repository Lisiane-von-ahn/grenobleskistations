from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0032_skistationcamera'),
    ]

    operations = [
        migrations.AddField(
            model_name='busline',
            name='arrival_latitude',
            field=models.DecimalField(blank=True, decimal_places=6, max_digits=8, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='arrival_longitude',
            field=models.DecimalField(blank=True, decimal_places=6, max_digits=9, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='itinerary_url',
            field=models.URLField(blank=True, help_text='URL to external itinerary/timetable', null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='detailed_route',
            field=models.JSONField(blank=True, help_text='JSON array of stops with coordinates', null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='first_departure',
            field=models.TimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='last_departure',
            field=models.TimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='notes',
            field=models.TextField(blank=True, help_text='Additional notes about the bus line', null=True),
        ),
    ]
