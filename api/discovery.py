"""Read-only year-round discovery and transparent operator condition assessment."""
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from django.core.cache import cache
from django.utils import timezone
from rest_framework import serializers, viewsets
from rest_framework.permissions import AllowAny
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import GrenoblePlace, SkiStation


class GrenoblePlaceSerializer(serializers.ModelSerializer):
    class Meta:
        model = GrenoblePlace
        fields = '__all__'


class GrenoblePlaceViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = GrenoblePlace.objects.all()
    serializer_class = GrenoblePlaceSerializer
    permission_classes = [AllowAny]
    pagination_class = None


def ski_assessment(status):
    # This is an indication for managed pistes, never an off-piste safety rating.
    if status is None:
        return 'unknown'
    age = timezone.now() - status.observed_at
    if age < timedelta(minutes=-15) or age > timedelta(hours=6):
        return 'stale'
    if status.pistes_open == 0 or status.lifts_open == 0:
        return 'closed'
    if status.temperature_c is None or status.snow_depth_cm is None or status.pistes_open is None or status.lifts_open is None:
        return 'unknown'
    if status.snow_depth_cm == 0 or status.temperature_c > 5 or status.temperature_c < -20:
        return 'unfavourable'
    # Wind, visibility and snow quality are not available; never promise ideal skiing.
    return 'check_bulletin'


@api_view(['GET'])
@permission_classes([AllowAny])
def station_weather(request):
    from .views import _fetch_weather_summary
    stations = list(SkiStation.objects.values('id', 'latitude', 'longitude')[:50])
    def fetch(station):
        key = f"discovery-weather:{station['latitude']}:{station['longitude']}"
        weather = cache.get(key)
        if weather is None:
            weather = _fetch_weather_summary(station['latitude'], station['longitude'])
            cache.set(key, weather, 600 if weather else 60)
        return {'id': station['id'], **weather}
    with ThreadPoolExecutor(max_workers=8) as pool:
        return Response(list(pool.map(fetch, stations)))
