from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0024_social_partners_and_marketplace_ratings'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='SkiPartnerReport',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('reason', models.CharField(blank=True, max_length=220)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('post', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='reports', to='api.skipartnerpost')),
                ('reporter', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='ski_partner_reports', to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.AddConstraint(
            model_name='skipartnerreport',
            constraint=models.UniqueConstraint(fields=('post', 'reporter'), name='uniq_partner_report_per_user_post'),
        ),
        migrations.CreateModel(
            name='MarketplaceDeal',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('buyer_confirmed', models.BooleanField(default=False)),
                ('seller_confirmed', models.BooleanField(default=False)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('buyer', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='marketplace_deals_as_buyer', to=settings.AUTH_USER_MODEL)),
                ('listing', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='deals', to='api.skimateriallisting')),
                ('seller', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='marketplace_deals_as_seller', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-updated_at'],
            },
        ),
        migrations.AddConstraint(
            model_name='marketplacedeal',
            constraint=models.UniqueConstraint(fields=('listing', 'buyer'), name='uniq_marketplace_deal_per_listing_buyer'),
        ),
    ]
