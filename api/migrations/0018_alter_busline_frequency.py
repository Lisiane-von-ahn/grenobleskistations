from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0017_crowdstatusupdate_and_unique_piste_report'),
    ]

    operations = [
        migrations.AlterField(
            model_name='busline',
            name='frequency',
            field=models.CharField(max_length=120, null=True),
        ),
    ]
