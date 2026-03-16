from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0029_userfriend_userfriend_uniq_user_friend_link'),
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
        migrations.AddField(
            model_name='servicestore',
            name='address',
            field=models.CharField(blank=True, max_length=255, null=True),
        ),
        migrations.AddField(
            model_name='servicestore',
            name='phone',
            field=models.CharField(blank=True, max_length=40, null=True),
        ),
        migrations.AddField(
            model_name='servicestore',
            name='source_note',
            field=models.CharField(blank=True, max_length=255, null=True),
        ),
        migrations.AddField(
            model_name='servicestore',
            name='website_url',
            field=models.URLField(blank=True, null=True),
        ),
    ]