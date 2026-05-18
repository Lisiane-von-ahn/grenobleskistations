from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0038_carpool_reservation_approval_flow'),
    ]

    operations = [
        migrations.AddField(
            model_name='userprofile',
            name='organization_name',
            field=models.CharField(blank=True, max_length=120, null=True, unique=True),
        ),
    ]
