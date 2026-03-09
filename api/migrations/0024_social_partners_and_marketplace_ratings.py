from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0023_marketplacesavedfilter'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='SkiPartnerPost',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('title', models.CharField(max_length=120)),
                ('message', models.TextField()),
                ('city', models.CharField(blank=True, max_length=80)),
                ('skill_level', models.CharField(choices=[('beginner', 'Debutant'), ('intermediate', 'Intermediaire'), ('advanced', 'Avance')], default='intermediate', max_length=16)),
                ('preferred_date', models.DateField(blank=True, null=True)),
                ('is_active', models.BooleanField(default=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('ski_station', models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, to='api.skistation')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='ski_partner_posts', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
        migrations.CreateModel(
            name='MarketplaceUserRating',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('score', models.PositiveSmallIntegerField(choices=[(1, '1'), (2, '2'), (3, '3'), (4, '4'), (5, '5')])),
                ('comment', models.CharField(blank=True, max_length=300)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('listing', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='buyer_ratings', to='api.skimateriallisting')),
                ('rated_user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='marketplace_ratings_received', to=settings.AUTH_USER_MODEL)),
                ('rater', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='marketplace_ratings_given', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-updated_at'],
            },
        ),
        migrations.AddConstraint(
            model_name='marketplaceuserrating',
            constraint=models.UniqueConstraint(fields=('listing', 'rater'), name='uniq_marketplace_rating_per_listing_buyer'),
        ),
    ]
