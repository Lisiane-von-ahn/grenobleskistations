from django.conf import settings
from api.models import Message

def global_variables(request):
    
    print ("tets")
    print (settings.WEATHER_API_KEY)
    
    return {
        'WEATHER_API_KEY': settings.WEATHER_API_KEY,
    }

def unread_message_count(request):
    if request.user.is_authenticated:  # Ensure the user is logged in
        unread_count = Message.objects.filter(recipient_id=request.user.id, is_read=False).count() 
    else:
        unread_count = 0  # If not logged in, return 0

    return {'unread_count': unread_count}
