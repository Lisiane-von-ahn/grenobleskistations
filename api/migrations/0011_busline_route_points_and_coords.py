from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0010_instructor_and_lending_updates'),
    ]

    operations = [
        migrations.AddField(
            model_name='busline',
            name='departure_latitude',
            field=models.DecimalField(blank=True, decimal_places=6, max_digits=8, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='departure_longitude',
            field=models.DecimalField(blank=True, decimal_places=6, max_digits=9, null=True),
        ),
        migrations.AddField(
            model_name='busline',
            name='route_points',
            field=models.CharField(blank=True, max_length=255, null=True),
        ),
    ]
