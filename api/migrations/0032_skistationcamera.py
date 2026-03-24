from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0031_skistation_piste_map_url'),
    ]

    operations = [
        migrations.CreateModel(
            name='SkiStationCamera',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(help_text='Name/location of the camera', max_length=200)),
                ('camera_url', models.URLField(help_text='URL to the live camera feed (MJPEG stream, m3u8, or still image)')),
                ('thumbnail_url', models.URLField(blank=True, help_text='URL to a static thumbnail image', null=True)),
                ('location_latitude', models.DecimalField(blank=True, decimal_places=6, max_digits=8, null=True)),
                ('location_longitude', models.DecimalField(blank=True, decimal_places=6, max_digits=9, null=True)),
                (
                    'camera_type',
                    models.CharField(
                        choices=[
                            ('live_stream', 'Live Stream (MJPEG)'),
                            ('hls_stream', 'HLS Stream (m3u8)'),
                            ('snapshot', 'Snapshot Only'),
                        ],
                        default='snapshot',
                        max_length=20,
                    ),
                ),
                ('description', models.TextField(blank=True, null=True)),
                ('is_active', models.BooleanField(default=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                (
                    'ski_station',
                    models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='cameras', to='api.skistation'),
                ),
            ],
            options={
                'ordering': ['name'],
            },
        ),
    ]
