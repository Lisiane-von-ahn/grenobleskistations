from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ('api', '0049_alter_skinewsitem_image_url_alter_skinewsitem_link_and_more'),
    ]

    operations = [
        migrations.AddField(
            model_name='skipartnerpost',
            name='carpool_mode',
            field=models.CharField(
                choices=[('offer', 'Offer a ride'), ('request', 'Request a ride')],
                default='offer',
                max_length=8,
            ),
        ),
    ]
