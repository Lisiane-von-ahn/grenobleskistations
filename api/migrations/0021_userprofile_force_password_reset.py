from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0020_servicestore_phone_servicestore_source_note'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='force_password_reset',
            field=models.BooleanField(default=False),
        ),
    ]
