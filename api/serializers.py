import base64
from django.contrib.auth.models import User
from rest_framework import serializers

from .models import (
    BusLine,
    InstructorProfile,
    InstructorReview,
    InstructorService,
    MarketplaceDeal,
    MarketplaceSavedFilter,
    MarketplaceUserRating,
    Message,
    PisteConditionReport,
    ServiceStore,
    SkiCircuit,
    SkiMaterialImage,
    SkiMaterialListing,
    SkiPartnerPost,
    SkiPartnerReport,
    SkiStation,
    SkiStory,
    SnowConditionUpdate,
    UserFriend,
    UserProfile,
)

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


class SkiMaterialImageSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiMaterialImage
        fields = ['id', 'created_at', 'image']

class SkiMaterialListingSerializer(serializers.ModelSerializer):
    images = SkiMaterialImageSerializer(many=True, read_only=True)

    class Meta:
        model = SkiMaterialListing
        fields = '__all__'

class MessageSerializer(serializers.ModelSerializer):
    class Meta:
        model = Message
        fields = '__all__'
        read_only_fields = ['sender', 'created_at']

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


class SnowConditionUpdateSerializer(serializers.ModelSerializer):
    class Meta:
        model = SnowConditionUpdate
        fields = '__all__'


class PisteConditionReportSerializer(serializers.ModelSerializer):
    class Meta:
        model = PisteConditionReport
        fields = '__all__'


class InstructorProfileSerializer(serializers.ModelSerializer):
    user = UserSerializer(read_only=True)

    class Meta:
        model = InstructorProfile
        fields = '__all__'


class InstructorServiceSerializer(serializers.ModelSerializer):
    class Meta:
        model = InstructorService
        fields = '__all__'


class InstructorReviewSerializer(serializers.ModelSerializer):
    class Meta:
        model = InstructorReview
        fields = '__all__'


class SkiPartnerPostSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiPartnerPost
        fields = '__all__'


class SkiPartnerReportSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiPartnerReport
        fields = '__all__'


class SkiStorySerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiStory
        fields = '__all__'


class MarketplaceSavedFilterSerializer(serializers.ModelSerializer):
    class Meta:
        model = MarketplaceSavedFilter
        fields = '__all__'


class MarketplaceDealSerializer(serializers.ModelSerializer):
    class Meta:
        model = MarketplaceDeal
        fields = '__all__'


class MarketplaceUserRatingSerializer(serializers.ModelSerializer):
    class Meta:
        model = MarketplaceUserRating
        fields = '__all__'


class UserFriendSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserFriend
        fields = '__all__'