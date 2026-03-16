from django.conf import settings
from api.models import Message, SkiMaterialListing, SkiPartnerPost

def global_variables(request):
    return {
        'WEATHER_API_KEY': settings.WEATHER_API_KEY,
        'ENABLE_WEB_ADS': settings.ENABLE_WEB_ADS,
        'WEB_ADS_PROVIDER': settings.WEB_ADS_PROVIDER,
        'ADSENSE_CLIENT_ID': settings.ADSENSE_CLIENT_ID,
        'ADSENSE_SLOT_FOOTER': settings.ADSENSE_SLOT_FOOTER,
        'PROPELLERADS_SCRIPT_SRC': settings.PROPELLERADS_SCRIPT_SRC,
        'PROPELLERADS_CONTAINER_ID': settings.PROPELLERADS_CONTAINER_ID,
        'WEB_ADS_SCRIPT_SRC': settings.WEB_ADS_SCRIPT_SRC,
        'WEB_ADS_CONTAINER_ID': settings.WEB_ADS_CONTAINER_ID,
    }


def city_autocomplete_values(request):
    values = []
    seen = set()

    for city_name in SkiMaterialListing.objects.exclude(city='').values_list('city', flat=True).distinct()[:300]:
        cleaned = (city_name or '').strip()
        key = cleaned.lower()
        if cleaned and key not in seen:
            seen.add(key)
            values.append(cleaned)

    for city_name in SkiPartnerPost.objects.exclude(city='').values_list('city', flat=True).distinct()[:300]:
        cleaned = (city_name or '').strip()
        key = cleaned.lower()
        if cleaned and key not in seen:
            seen.add(key)
            values.append(cleaned)

    return {'city_autocomplete_values': values}

def unread_message_count(request):
    if request.user.is_authenticated:  # Ensure the user is logged in
        unread_count = Message.objects.filter(recipient_id=request.user.id, is_read=False).count() 
    else:
        unread_count = 0  # If not logged in, return 0

    return {'unread_count': unread_count}
