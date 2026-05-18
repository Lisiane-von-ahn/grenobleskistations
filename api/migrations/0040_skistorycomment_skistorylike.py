from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0039_userprofile_organization_name'),
    ]

    operations = [
        migrations.CreateModel(
            name='SkiStoryComment',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('body', models.CharField(max_length=300)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('story', models.ForeignKey(on_delete=models.deletion.CASCADE, related_name='comments', to='api.skistory')),
                ('user', models.ForeignKey(on_delete=models.deletion.CASCADE, related_name='story_comments', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
        migrations.CreateModel(
            name='SkiStoryLike',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('story', models.ForeignKey(on_delete=models.deletion.CASCADE, related_name='likes', to='api.skistory')),
                ('user', models.ForeignKey(on_delete=models.deletion.CASCADE, related_name='liked_ski_stories', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
        migrations.AddConstraint(
            model_name='skistorylike',
            constraint=models.UniqueConstraint(fields=('story', 'user'), name='uniq_story_like_per_user'),
        ),
    ]
