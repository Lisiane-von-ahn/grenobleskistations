import os

from django.contrib.auth import get_user_model
from django.contrib.sites.models import Site
from django.core.management.base import BaseCommand
from django.conf import settings

from api.models import UserProfile


class Command(BaseCommand):
    help = "Create bootstrap admin superuser once and mark password reset mandatory on first login."

    def handle(self, *args, **options):
        site_id = getattr(settings, 'SITE_ID', 1)
        site_domain = (os.getenv('DJANGO_SITE_DOMAIN', 'www.grenobleski.fr') or 'www.grenobleski.fr').strip()
        site_name = (os.getenv('DJANGO_SITE_NAME', 'GrenobleSki') or 'GrenobleSki').strip()

        Site.objects.update_or_create(
            id=site_id,
            defaults={
                'domain': site_domain,
                'name': site_name,
            },
        )

        username = (os.getenv('BOOTSTRAP_ADMIN_USERNAME', 'admin') or 'admin').strip()
        email = (os.getenv('BOOTSTRAP_ADMIN_EMAIL', 'admin@grenobleski.local') or 'admin@grenobleski.local').strip().lower()
        password = os.getenv('BOOTSTRAP_ADMIN_PASSWORD', 'admin')

        user_model = get_user_model()

        existing = user_model.objects.filter(username__iexact=username).first()
        if existing is None:
            existing = user_model.objects.filter(email__iexact=email).first()

        if existing:
            changed = False
            if not existing.is_staff:
                existing.is_staff = True
                changed = True
            if not existing.is_superuser:
                existing.is_superuser = True
                changed = True
            if changed:
                existing.save(update_fields=['is_staff', 'is_superuser'])

            profile, _ = UserProfile.objects.get_or_create(user=existing)
            if profile.force_password_reset:
                self.stdout.write(self.style.SUCCESS(f"Bootstrap admin already exists: {existing.username} (password reset still required)."))
            else:
                self.stdout.write(self.style.SUCCESS(f"Bootstrap admin already exists: {existing.username}."))
            return

        admin = user_model.objects.create_superuser(
            username=username,
            email=email,
            password=password,
        )
        profile, _ = UserProfile.objects.get_or_create(user=admin)
        profile.force_password_reset = True
        profile.save(update_fields=['force_password_reset'])

        self.stdout.write(self.style.SUCCESS(f"Created bootstrap superuser '{username}' (first-login password reset required)."))
