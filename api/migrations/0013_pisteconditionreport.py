from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0012_snowconditionupdate'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='PisteConditionReport',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('piste_rating', models.PositiveSmallIntegerField()),
                ('crowd_level', models.CharField(choices=[('quiet', 'Peu de gens'), ('normal', 'Agréable'), ('busy', 'Bondé')], default='normal', max_length=10)),
                ('comment', models.TextField(blank=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('ski_station', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='piste_reports', to='api.skistation')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='piste_reports', to=settings.AUTH_USER_MODEL)),
            ],
        ),
    ]
