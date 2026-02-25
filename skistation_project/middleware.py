import logging

from django.utils.deprecation import MiddlewareMixin
from django.shortcuts import redirect
from django.urls import reverse

from api.models import UserProfile


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
