from django.utils.deprecation import MiddlewareMixin

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
