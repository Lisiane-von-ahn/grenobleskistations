import base64
import json
import logging
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from django.conf import settings
from django.utils.dateparse import parse_datetime

GOOGLE_ROUTE_MODES = ('DRIVE', 'TRANSIT', 'BICYCLE', 'WALK')
logger = logging.getLogger(__name__)


ROUTE_ICONS = {'DRIVE': 'car', 'TRANSIT': 'bus', 'BICYCLE': 'bicycle', 'WALK': 'person-walking'}
TRANSIT_ICONS = {
    'TRAM': 'train-tram',
    'SUBWAY': 'train-subway',
    'METRO_RAIL': 'train-subway',
    'RAIL': 'train',
    'HEAVY_RAIL': 'train',
    'HIGH_SPEED_TRAIN': 'train',
    'LONG_DISTANCE_TRAIN': 'train',
    'COMMUTER_TRAIN': 'train',
    'FERRY': 'ship',
    'GONDOLA_LIFT': 'mountain',
    'CABLE_CAR': 'mountain',
    'FUNICULAR': 'train',
}

DETAIL_FIELDS = (
    'routes.localizedValues.distance,routes.localizedValues.duration,'
    'routes.legs.steps.navigationInstruction.instructions,routes.legs.steps.travelMode,'
    'routes.legs.steps.localizedValues.distance,routes.legs.steps.transitDetails.stopDetails,'
    'routes.legs.steps.transitDetails.transitLine'
)
MAP_FIELDS = (
    'routes.localizedValues.distance,routes.localizedValues.duration,'
    'routes.polyline.encodedPolyline,routes.legs.startLocation.latLng,routes.legs.endLocation.latLng'
)


def route_map_image(route, language):
    polyline = route.get('polyline', {}).get('encodedPolyline')
    if not polyline:
        return ''

    params = [
        ('size', '640x360'), ('scale', '2'), ('maptype', 'roadmap'),
        ('language', language), ('path', f'color:0x08776dff|weight:6|enc:{polyline}'),
        ('key', settings.GOOGLE_ROUTES_API_KEY),
    ]
    legs = route.get('legs', [])
    if legs:
        for location, color, label in (
            (legs[0].get('startLocation', {}), '0x086357', 'A'),
            (legs[-1].get('endLocation', {}), '0x184c76', 'B'),
        ):
            point = location.get('latLng', {})
            if point.get('latitude') is not None and point.get('longitude') is not None:
                params.append(('markers', f"color:{color}|label:{label}|{point['latitude']},{point['longitude']}"))

    url = 'https://maps.googleapis.com/maps/api/staticmap?' + urlencode(params)
    if len(url) > 16384:
        return ''
    try:
        with urlopen(url, timeout=6) as response:
            if response.headers.get_content_type() != 'image/png' or response.headers.get('X-Staticmap-API-Warning'):
                return ''
            image = response.read(2_000_001)
    except (URLError, OSError) as error:
        logger.warning('Google route map unavailable: %s', getattr(error, 'code', type(error).__name__))
        return ''
    if len(image) > 2_000_000 or not image.startswith(b'\x89PNG\r\n\x1a\n'):
        return ''
    return base64.b64encode(image).decode('ascii')


def calculate_station_route(station, origin, mode, language, *, show_map=False):
    if not settings.GOOGLE_ROUTES_API_KEY:
        return {'state': 'unconfigured'}

    request = Request(
        'https://routes.googleapis.com/directions/v2:computeRoutes',
        data=json.dumps({
            'origin': {'address': origin},
            'destination': {'location': {'latLng': {
                'latitude': float(station.latitude), 'longitude': float(station.longitude),
            }}},
            'travelMode': mode,
            'languageCode': language,
            'units': 'METRIC',
            **({'polylineQuality': 'OVERVIEW'} if show_map else {}),
        }).encode(),
        headers={
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': settings.GOOGLE_ROUTES_API_KEY,
            'X-Goog-FieldMask': MAP_FIELDS if show_map else DETAIL_FIELDS,
        },
        method='POST',
    )
    try:
        with urlopen(request, timeout=6) as response:
            routes = json.load(response).get('routes', [])
    except HTTPError as error:
        with error:
            try:
                payload = json.load(error)
            except (OSError, ValueError):
                payload = {}
        details = payload.get('error', {}).get('details', [])
        reasons = [item['reason'] for item in details if item.get('@type') == 'type.googleapis.com/google.rpc.ErrorInfo' and item.get('reason')]
        logger.warning('Google Routes rejected the request: HTTP %s; reasons=%s', error.code, reasons)
        return {'state': 'unavailable'}
    except (URLError, OSError, ValueError):
        return {'state': 'unavailable'}
    if not routes:
        return {'state': 'no_route'}

    route = routes[0]
    values = route.get('localizedValues', {})
    result = {
        'state': 'ready',
        'duration': values.get('duration', {}).get('text', ''),
        'distance': values.get('distance', {}).get('text', ''),
    }
    if show_map:
        result['map_image'] = route_map_image(route, language)
        return result

    steps = []
    transit_count = 0
    for leg in route.get('legs', []):
        for step in leg.get('steps', []):
            transit = step.get('transitDetails', {})
            line = transit.get('transitLine', {})
            stops = transit.get('stopDetails', {})
            if transit:
                transit_count += 1
            steps.append({
                'icon': (
                    TRANSIT_ICONS.get(line.get('vehicle', {}).get('type'), 'bus')
                    if transit else ROUTE_ICONS.get(step.get('travelMode', mode), ROUTE_ICONS[mode])
                ),
                'transfer': bool(transit and transit_count > 1),
                'instruction': step.get('navigationInstruction', {}).get('instructions', ''),
                'distance': step.get('localizedValues', {}).get('distance', {}).get('text', ''),
                'line': line.get('nameShort') or line.get('name', ''),
                'agency': ', '.join(agency['name'] for agency in line.get('agencies', []) if agency.get('name')),
                'departure': stops.get('departureStop', {}).get('name', ''),
                'arrival': stops.get('arrivalStop', {}).get('name', ''),
                'departure_time': parse_datetime(stops['departureTime']) if stops.get('departureTime') else None,
                'arrival_time': parse_datetime(stops['arrivalTime']) if stops.get('arrivalTime') else None,
            })

    result['steps'] = steps
    return result
