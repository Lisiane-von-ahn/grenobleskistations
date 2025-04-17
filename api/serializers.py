import base64
from rest_framework import serializers
from django.contrib.auth.models import User
from .models import SkiStation, BusLine, ServiceStore, SkiCircuit,SkiMaterialListing,Message, UserProfile

class SkiStationSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiStation
        fields = '__all__'

class BusLineSerializer(serializers.ModelSerializer):
    class Meta:
        model = BusLine
        fields = '__all__'

class ServiceStoreSerializer(serializers.ModelSerializer):
    class Meta:
        model = ServiceStore
        fields = '__all__'

class SkiCircuitSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiCircuit
        fields = '__all__'

class SkiMaterialListingSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiMaterialListing
        fields = '__all__'

class MessageSerializer(serializers.ModelSerializer):
    class Meta:
        model = Message
        fields = '__all__'

class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ['id', 'username', 'email', 'first_name', 'last_name', 'is_staff', 'date_joined']

class UserProfileSerializer(serializers.ModelSerializer):
    user = UserSerializer()  # Nested serializer for full user info
    profile_picture = serializers.SerializerMethodField()

    class Meta:
        model = UserProfile
        fields = ['id', 'user', 'profile_picture']

    def get_profile_picture(self, obj):
        if obj.profile_picture:
            return base64.b64encode(obj.profile_picture).decode('utf-8')
        return None