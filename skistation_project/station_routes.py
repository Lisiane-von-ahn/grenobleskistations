import json
import logging
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from django.conf import settings
from django.utils.dateparse import parse_datetime

GOOGLE_ROUTE_MODES = ('DRIVE', 'TRANSIT', 'BICYCLE', 'WALK')
logger = logging.getLogger(__name__)


def calculate_station_route(station, origin, mode, language):
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
        }).encode(),
        headers={
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': settings.GOOGLE_ROUTES_API_KEY,
            'X-Goog-FieldMask': (
                'routes.localizedValues.distance,routes.localizedValues.duration,'
                'routes.legs.steps.navigationInstruction.instructions,'
                'routes.legs.steps.localizedValues.distance,'
                'routes.legs.steps.transitDetails.stopDetails,'
                'routes.legs.steps.transitDetails.transitLine'
            ),
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
    steps = []
    for leg in route.get('legs', []):
        for step in leg.get('steps', []):
            transit = step.get('transitDetails', {})
            line = transit.get('transitLine', {})
            stops = transit.get('stopDetails', {})
            steps.append({
                'instruction': step.get('navigationInstruction', {}).get('instructions', ''),
                'distance': step.get('localizedValues', {}).get('distance', {}).get('text', ''),
                'line': line.get('nameShort') or line.get('name', ''),
                'agency': ', '.join(agency['name'] for agency in line.get('agencies', []) if agency.get('name')),
                'departure': stops.get('departureStop', {}).get('name', ''),
                'arrival': stops.get('arrivalStop', {}).get('name', ''),
                'departure_time': parse_datetime(stops['departureTime']) if stops.get('departureTime') else None,
                'arrival_time': parse_datetime(stops['arrivalTime']) if stops.get('arrivalTime') else None,
            })

    return {
        'state': 'ready',
        'duration': values.get('duration', {}).get('text', ''),
        'distance': values.get('distance', {}).get('text', ''),
        'steps': steps,
    }
