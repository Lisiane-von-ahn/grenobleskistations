from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0040_skistorycomment_skistorylike'),
    ]

    operations = [
        migrations.AddField(
            model_name='message',
            name='is_private',
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name='skistory',
            name='crowd_level',
            field=models.CharField(
                choices=[('quiet', 'Quiet'), ('normal', 'Normal'), ('busy', 'Busy'), ('wild', 'Wild')],
                default='normal',
                max_length=12,
            ),
        ),
        migrations.AddField(
            model_name='skistory',
            name='weather_label',
            field=models.CharField(blank=True, max_length=40),
        ),
        migrations.AddField(
            model_name='skistory',
            name='temperature_c',
            field=models.IntegerField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='skistory',
            name='snow_depth_cm',
            field=models.IntegerField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='skistory',
            name='fun_score',
            field=models.PositiveSmallIntegerField(default=50),
        ),
        migrations.AddField(
            model_name='userprofile',
            name='messages_private_by_default',
            field=models.BooleanField(default=False),
        ),
    ]
