import base64
from django.contrib.auth.models import User
from rest_framework import serializers

try:
    from allauth.socialaccount.models import SocialAccount
except Exception:
    SocialAccount = None

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


def _encode_binary_field(value):
    if not value:
        return None
    return base64.b64encode(value).decode('utf-8')


def _profile_picture_for_user(user):
    profile = getattr(user, 'profile', None)
    return _encode_binary_field(getattr(profile, 'profile_picture', None))


def _google_picture_for_user(user):
    if SocialAccount is None:
        return None
    social = SocialAccount.objects.filter(user=user, provider='google').first()
    if social is None:
        return None
    return (social.extra_data or {}).get('picture')


def _display_name_for_user(user):
    first = (user.first_name or '').strip()
    last = (user.last_name or '').strip()
    if first or last:
        return f"{first} {last}".strip()
    return (user.username or user.email or '').strip()

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
    sender_user = serializers.SerializerMethodField()
    recipient_user = serializers.SerializerMethodField()

    class Meta:
        model = Message
        fields = [
            'id',
            'sender',
            'recipient',
            'subject',
            'body',
            'created_at',
            'is_read',
            'sender_user',
            'recipient_user',
        ]
        read_only_fields = ['sender', 'created_at', 'sender_user', 'recipient_user']

    def _serialize_user(self, user):
        return {
            'id': user.id,
            'display_name': _display_name_for_user(user),
            'username': user.username,
            'email': user.email,
            'profile_picture': _profile_picture_for_user(user),
            'google_profile_picture_url': _google_picture_for_user(user),
        }

    def get_sender_user(self, obj):
        return self._serialize_user(obj.sender)

    def get_recipient_user(self, obj):
        return self._serialize_user(obj.recipient)

class UserSerializer(serializers.ModelSerializer):
    display_name = serializers.SerializerMethodField()
    profile_picture = serializers.SerializerMethodField()
    google_profile_picture_url = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = [
            'id',
            'username',
            'email',
            'first_name',
            'last_name',
            'display_name',
            'profile_picture',
            'google_profile_picture_url',
            'is_staff',
            'date_joined',
        ]

    def get_display_name(self, obj):
        return _display_name_for_user(obj)

    def get_profile_picture(self, obj):
        return _profile_picture_for_user(obj)

    def get_google_profile_picture_url(self, obj):
        return _google_picture_for_user(obj)

class UserProfileSerializer(serializers.ModelSerializer):
    user = UserSerializer()  # Nested serializer for full user info
    profile_picture = serializers.SerializerMethodField()

    class Meta:
        model = UserProfile
        fields = ['id', 'user', 'profile_picture']

    def get_profile_picture(self, obj):
        return _encode_binary_field(obj.profile_picture)


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