from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0011_busline_route_points_and_coords'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='SnowConditionUpdate',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('note', models.TextField(blank=True)),
                ('snow_depth_cm', models.PositiveIntegerField(blank=True, null=True)),
                ('image', models.BinaryField(blank=True, null=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('ski_station', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='snow_updates', to='api.skistation')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='snow_updates', to=settings.AUTH_USER_MODEL)),
            ],
        ),
    ]
