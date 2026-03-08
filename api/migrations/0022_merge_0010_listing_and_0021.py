# Generated manually to resolve divergent migration branches.
from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0010_listing_marketplace_upgrade'),
        ('api', '0021_userprofile_force_password_reset'),
    ]

    operations = []
