import os

from allauth.socialaccount.models import SocialApp
from django.conf import settings
from django.contrib.sites.models import Site
from django.core.management.base import BaseCommand


class Command(BaseCommand):
    help = "Ensure a Google SocialApp exists for the current Django site."

    def handle(self, *args, **options):
        client_id = (os.getenv('GOOGLE_WEB_CLIENT_ID', '') or '').strip()
        client_secret = (os.getenv('GOOGLE_WEB_CLIENT_SECRET', '') or '').strip()
        client_key = (os.getenv('GOOGLE_WEB_CLIENT_KEY', '') or '').strip()

        if not client_id or not client_secret:
            self.stdout.write(self.style.WARNING('Google SocialApp skipped: GOOGLE_WEB_CLIENT_ID/SECRET not configured.'))
            return

        site_id = getattr(settings, 'SITE_ID', 1)
        site_domain = (os.getenv('DJANGO_SITE_DOMAIN', 'www.grenobleski.fr') or 'www.grenobleski.fr').strip()
        site_name = (os.getenv('DJANGO_SITE_NAME', 'GrenobleSki') or 'GrenobleSki').strip()
        site, _ = Site.objects.update_or_create(
            id=site_id,
            defaults={
                'domain': site_domain,
                'name': site_name,
            },
        )

        app = SocialApp.objects.filter(provider='google', sites=site).order_by('id').first()
        if app is None:
            app = SocialApp.objects.filter(provider='google').order_by('id').first()
        if app is None:
            app = SocialApp(provider='google', name='Google')

        app.name = 'Google'
        app.provider = 'google'
        app.client_id = client_id
        app.secret = client_secret
        app.key = client_key
        app.save()
        app.sites.add(site)

        for duplicate in SocialApp.objects.filter(provider='google', sites=site).exclude(id=app.id):
            duplicate.sites.remove(site)

        self.stdout.write(self.style.SUCCESS(f"Google SocialApp ready for site '{site.domain}'."))