from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0015_instructorreview'),
    ]

    operations = [
        migrations.AddField(
            model_name='servicestore',
            name='address',
            field=models.CharField(blank=True, max_length=255, null=True),
        ),
        migrations.AddField(
            model_name='servicestore',
            name='website_url',
            field=models.URLField(blank=True, null=True),
        ),
    ]
