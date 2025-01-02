from django.utils.deprecation import MiddlewareMixin

class CookieConsentMiddleware(MiddlewareMixin):
    def process_request(self, request):
        consent = request.COOKIES.get('cookie_consent', None)
        request.cookie_consent = consent
