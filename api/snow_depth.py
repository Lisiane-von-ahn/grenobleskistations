"""Resolve measured snow depth before falling back to an attributed model estimate."""

import json
import math
from datetime import timedelta, timezone as utc
from urllib.parse import urlencode
from urllib.request import urlopen

from django.conf import settings
from django.core.cache import cache
from django.utils import timezone
from django.utils.dateparse import parse_datetime


def observed_snow_depth(station, now):
    live = getattr(station, 'live_status', None)
    if (
        live and (live.snow_depth_cm is not None or live.snow_base_cm is not None)
        and now - timedelta(hours=24) <= live.observed_at <= now + timedelta(minutes=15)
    ):
        return {
            'cm': live.snow_depth_cm if live.snow_depth_cm is not None else live.snow_base_cm,
            'source': 'operator', 'observed_at': live.observed_at, 'source_url': live.source_url,
            'elevation_m': live.base_altitude_m if live.snow_depth_cm is None else None,
        }
    reported = getattr(station, 'recent_snow_depth', None)
    if reported is not None:
        return {'cm': reported, 'source': 'community'}
    return None


def _model_readings(stations, key):
    params = urlencode({
        'latitude': ','.join(str(station.latitude) for station in stations),
        'longitude': ','.join(str(station.longitude) for station in stations),
        'current': 'snow_depth',
        'models': 'icon_seamless',
        'timezone': 'GMT',
        'apikey': key,
    })
    try:
        with urlopen(f'https://customer-api.open-meteo.com/v1/forecast?{params}', timeout=3) as response:
            readings = json.load(response)
    except (OSError, ValueError):
        return []
    if isinstance(readings, dict):
        readings = [readings]
    return readings if isinstance(readings, list) and len(readings) == len(stations) else []


def _model_depth(reading, now):
    current = reading.get('current', {})
    depth = current.get('snow_depth')
    if reading.get('current_units', {}).get('snow_depth') != 'm':
        return None
    if isinstance(depth, bool) or not isinstance(depth, (float, int)) or not math.isfinite(depth) or depth < 0:
        return None
    try:
        timestamp = parse_datetime(current.get('time') or '')
    except ValueError:
        return None
    if timestamp is None:
        return None
    timestamp = timestamp.replace(tzinfo=utc.utc) if timezone.is_naive(timestamp) else timestamp
    if not now - timedelta(hours=3) <= timestamp <= now + timedelta(minutes=15):
        return None
    result = {'cm': round(depth * 100), 'source': 'open_meteo', 'observed_at': timestamp,
              'source_url': 'https://open-meteo.com/en/docs/dwd-api'}
    elevation = reading.get('elevation')
    if isinstance(elevation, (float, int)) and math.isfinite(elevation):
        result['elevation_m'] = round(elevation)
    return result


def station_snow_depths(stations, *, now=None):
    now = now or timezone.now()
    results = {station.pk: observed_snow_depth(station, now) for station in stations}
    key = settings.OPEN_METEO_API_KEY
    missing = [station for station in stations if results[station.pk] is None]
    if not key or not missing:
        return results

    cache_keys = {station.pk: f'snow-depth:icon:{station.latitude}:{station.longitude}' for station in missing}
    cached = cache.get_many(cache_keys.values())
    pending = []
    for station in missing:
        cache_key = cache_keys[station.pk]
        if cache_key in cached:
            results[station.pk] = cached[cache_key] or None
        else:
            pending.append(station)

    for start in range(0, len(pending), 50):
        batch = pending[start:start + 50]
        readings = _model_readings(batch, key)
        for index, station in enumerate(batch):
            depth = _model_depth(readings[index], now) if readings and isinstance(readings[index], dict) else None
            results[station.pk] = depth
            cache.set(cache_keys[station.pk], depth or {}, 1800 if depth else 300)
    return results
