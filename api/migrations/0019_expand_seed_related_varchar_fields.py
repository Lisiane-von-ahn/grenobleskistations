from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0018_alter_busline_frequency'),
    ]

    operations = [
        migrations.AlterField(
            model_name='busline',
            name='bus_number',
            field=models.CharField(max_length=120),
        ),
        migrations.AlterField(
            model_name='busline',
            name='travel_time',
            field=models.CharField(max_length=120, null=True),
        ),
        migrations.AlterField(
            model_name='servicestore',
            name='type',
            field=models.CharField(max_length=100),
        ),
    ]
