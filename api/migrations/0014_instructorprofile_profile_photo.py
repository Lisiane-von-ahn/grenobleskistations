from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0013_pisteconditionreport'),
    ]

    operations = [
        migrations.AddField(
            model_name='instructorprofile',
            name='profile_photo',
            field=models.BinaryField(blank=True, null=True),
        ),
    ]
