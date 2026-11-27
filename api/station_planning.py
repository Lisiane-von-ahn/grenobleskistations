from datetime import timedelta

from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .models import SkiStation, StationAlert, UserProfile


@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def station_favorites(request):
    profile, _ = UserProfile.objects.get_or_create(user=request.user)
    if request.method == 'POST':
        station_id = request.data.get('station_id')
        enabled = request.data.get('enabled')
        if not isinstance(enabled, bool):
            return Response({'detail': 'enabled must be a boolean'}, status=400)
        try:
            station = SkiStation.objects.get(pk=int(station_id))
        except (ValueError, TypeError, OverflowError, SkiStation.DoesNotExist):
            return Response({'detail': 'Unknown station'}, status=400)
        if enabled:
            profile.favorite_stations.add(station)
        else:
            profile.favorite_stations.remove(station)
    return Response({'station_ids': list(profile.favorite_stations.values_list('pk', flat=True))})


@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def station_alerts(request):
    alerts = StationAlert.objects.filter(user=request.user)
    if request.method == 'POST':
        ids = request.data.get('ids')
        if not isinstance(ids, list) or len(ids) > 100 or any(type(value) is not int for value in ids):
            return Response({'detail': 'ids must contain up to 100 integer IDs'}, status=400)
        alerts.filter(pk__in=ids, read_at__isnull=True).update(read_at=timezone.now())
    profile, _ = UserProfile.objects.get_or_create(user=request.user)
    favorites = profile.favorite_stations.values_list('pk', flat=True)
    recent = alerts.filter(
        station_id__in=favorites, created_at__gte=timezone.now() - timedelta(days=7),
    ).select_related('station')[:50]
    return Response([{
        'id': alert.pk, 'station_id': alert.station_id, 'station_name': alert.station.name,
        'kind': alert.kind, 'observed_at': alert.observed_at, 'read': alert.read_at is not None,
    } for alert in recent])
