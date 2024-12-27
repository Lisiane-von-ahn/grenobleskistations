from django.conf import settings

def global_variables(request):
    
    print ("tets")
    print (settings.WEATHER_API_KEY)
    
    return {
        'WEATHER_API_KEY': settings.WEATHER_API_KEY,
    }
    