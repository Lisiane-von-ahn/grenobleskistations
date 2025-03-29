from rest_framework import serializers
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

class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserProfile
        fields = '__all__'
