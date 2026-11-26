"""Read-only year-round discovery and transparent operator condition assessment."""
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from zoneinfo import ZoneInfo
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


def ski_assessment(status, *, now=None):
    # This is an indication for managed pistes, never an off-piste safety rating.
    if status is None:
        return 'unknown'
    age = (now or timezone.now()) - status.observed_at
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


def estimate_ski_crowd(status, *, now):
    """Return an ordinal calendar estimate for the ski area in local time."""
    local_time = now.astimezone(ZoneInfo('Europe/Paris'))
    assessment = ski_assessment(status, now=now)
    if assessment == 'closed':
        return {'state': 'closed'}
    open_confirmed = (
        assessment not in {'stale', 'closed'}
        and status is not None
        and (status.lifts_open or 0) > 0
        and (status.pistes_open or 0) > 0
    )
    if local_time.month not in {11, 12, 1, 2, 3, 4} and not open_confirmed:
        return {'state': 'out_of_season'}

    weekend = local_time.weekday() >= 5
    slots = (
        (8, 10, 'normal' if weekend else 'quiet'),
        (10, 14, 'busy' if weekend else 'normal'),
        (14, 17, 'normal' if weekend else 'quiet'),
    )
    return {
        'state': 'estimate',
        'open_confirmed': open_confirmed,
        'slots': [
            {'start': start, 'end': end, 'level': level, 'current': start <= local_time.hour < end}
            for start, end, level in slots
        ],
    }


def station_weather_reading(latitude, longitude):
    from .views import _fetch_weather_summary

    key = f"discovery-weather:{latitude}:{longitude}"
    weather = cache.get(key)
    if weather is None:
        weather = _fetch_weather_summary(latitude, longitude)
        cache.set(key, weather, 600 if weather else 60)
    return weather


@api_view(['GET'])
@permission_classes([AllowAny])
def station_weather(request):
    stations = SkiStation.objects.values('id', 'latitude', 'longitude')
    selected_station = request.query_params.get('station')
    if selected_station is not None:
        if not selected_station.isascii() or not selected_station.isdigit() or len(selected_station) > 9:
            return Response([], status=400)
        stations = stations.filter(pk=int(selected_station))
    stations = list(stations[:50])
    def fetch(station):
        weather = station_weather_reading(station['latitude'], station['longitude'])
        return {'id': station['id'], **weather}

    with ThreadPoolExecutor(max_workers=8) as pool:
        return Response(list(pool.map(fetch, stations)))
