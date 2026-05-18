from django.db import migrations, models
import django.db.models.deletion
import django.utils.timezone


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0041_story_context_and_message_privacy'),
    ]

    operations = [
        migrations.CreateModel(
            name='SkiNewsItem',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('title', models.CharField(max_length=255)),
                ('summary', models.TextField(blank=True)),
                ('link', models.URLField(unique=True)),
                ('source_name', models.CharField(blank=True, max_length=120)),
                ('source_url', models.URLField(blank=True)),
                ('language', models.CharField(choices=[('fr', 'Francais'), ('en', 'English')], default='fr', max_length=2)),
                ('image_url', models.URLField(blank=True)),
                ('published_at', models.DateTimeField(default=django.utils.timezone.now)),
                ('is_highlighted', models.BooleanField(default=False)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('ski_station', models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='news_items', to='api.skistation')),
            ],
            options={
                'ordering': ['-is_highlighted', '-published_at', '-id'],
            },
        ),
    ]
