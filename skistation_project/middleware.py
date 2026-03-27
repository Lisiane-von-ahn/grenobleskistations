import logging

from django.utils.deprecation import MiddlewareMixin
from django.shortcuts import redirect
from django.urls import reverse

from api.models import AppUsageEvent, UserProfile


exception_logger = logging.getLogger('skistation.exceptions')


class ExceptionLoggingMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        return self.get_response(request)

    def process_exception(self, request, exception):
        exception_logger.exception(
            "Unhandled exception path=%s method=%s user=%s ip=%s",
            request.path,
            request.method,
            getattr(getattr(request, 'user', None), 'id', 'anonymous'),
            request.META.get('REMOTE_ADDR', ''),
        )
        return None

class CookieConsentMiddleware(MiddlewareMixin):
    def process_request(self, request):
        # Récupère l'état du consentement des cookies (accepte ou refuse)
        consent = request.COOKIES.get('cookie_consent', None)
        request.cookie_consent = consent

    def process_response(self, request, response):
        # Si le consentement n'existe pas, il est initialisé à 'unknown'
        if not request.COOKIES.get('cookie_consent'):
            response.set_cookie('cookie_consent', 'unknown', max_age=365*24*60*60)
        return response


class ForcePasswordResetMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        user = getattr(request, 'user', None)
        if user and user.is_authenticated:
            profile = UserProfile.objects.filter(user=user).only('force_password_reset').first()
            must_reset = bool(profile and profile.force_password_reset)
            if must_reset:
                target = reverse('account_change_password')
                exempt_prefixes = (
                    target,
                    reverse('account_logout'),
                    '/logout/',
                    '/admin/logout/',
                    '/static/',
                    '/media/',
                )
                if not any(request.path.startswith(prefix) for prefix in exempt_prefixes):
                    return redirect(target)

        return self.get_response(request)


class UsageAnalyticsMiddleware:
    """Capture high-level product usage events for admin analytics dashboards."""

    FEATURE_PREFIX_MAP = [
        ('/covoiturage', 'covoiturage'),
        ('/ski-partners', 'ski_partners'),
        ('/ski-station/', 'station_detail'),
        ('/bus', 'bus'),
        ('/services', 'services'),
        ('/messages', 'messages'),
        ('/instructors', 'instructors'),
        ('/profile', 'profile'),
        ('/api/skipartnerposts', 'api_carpool_partners'),
        ('/api/skistations/conditions', 'api_station_conditions'),
        ('/api/buslines', 'api_bus'),
        ('/api/messages', 'api_messages'),
        ('/api/overpass/nearby', 'api_overpass_proxy'),
        ('/api/auth/', 'api_auth'),
    ]

    SKIP_PREFIXES = (
        '/static/',
        '/media/',
        '/favicon.ico',
        '/robots.txt',
        '/ads.txt',
        '/admin/jsi18n/',
    )

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)

        path = request.path or '/'
        if any(path.startswith(prefix) for prefix in self.SKIP_PREFIXES):
            return response
        if path.startswith('/admin/'):
            return response

        feature_name = self._resolve_feature(path)
        if not feature_name:
            return response

        user = getattr(request, 'user', None)
        user_obj = user if getattr(user, 'is_authenticated', False) else None
        user_agent = (request.META.get('HTTP_USER_AGENT') or '')[:220]
        platform = self._resolve_platform(path, user_agent)

        try:
            AppUsageEvent.objects.create(
                user=user_obj,
                feature_name=feature_name,
                path=path[:255],
                method=(request.method or 'GET')[:8],
                status_code=int(getattr(response, 'status_code', 200) or 200),
                platform=platform,
                is_api=path.startswith('/api/'),
                metadata={
                    'query_string': (request.META.get('QUERY_STRING') or '')[:300],
                    'user_agent': user_agent,
                },
            )
        except Exception:
            # Analytics should never break user traffic.
            exception_logger.warning('Failed to record usage analytics for path=%s', path, exc_info=True)

        return response

    def _resolve_feature(self, path):
        for prefix, feature_name in self.FEATURE_PREFIX_MAP:
            if path.startswith(prefix):
                return feature_name
        return None

    @staticmethod
    def _resolve_platform(path, user_agent):
        if path.startswith('/api/'):
            if 'GrenobleSki' in user_agent or 'Toga' in user_agent:
                return AppUsageEvent.PLATFORM_MOBILE
            return AppUsageEvent.PLATFORM_API
        if 'Mobile' in user_agent or 'Android' in user_agent or 'iPhone' in user_agent:
            return AppUsageEvent.PLATFORM_MOBILE
        return AppUsageEvent.PLATFORM_WEB
