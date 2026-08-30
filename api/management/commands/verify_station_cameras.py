from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from django.core.management.base import BaseCommand
from django.utils import timezone

from api.models import SkiStationCamera


class Command(BaseCommand):
    help = 'Verify active webcam URLs and timestamp successful checks for freshness indicators.'

    def handle(self, *args, **options):
        verified = 0
        for camera in SkiStationCamera.objects.filter(is_active=True):
            try:
                request = Request(camera.thumbnail_url or camera.camera_url, method='HEAD', headers={'User-Agent': 'GrenobleSkiCameraCheck/1.0'})
                with urlopen(request, timeout=8) as response:
                    if 200 <= response.status < 400:
                        camera.last_verified_at = timezone.now()
                        camera.save(update_fields=['last_verified_at'])
                        verified += 1
            except (HTTPError, URLError, TimeoutError, ValueError):
                continue
        self.stdout.write(self.style.SUCCESS(f'Verified {verified} active camera feeds.'))
