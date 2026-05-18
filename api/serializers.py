import base64
from django.contrib.auth.models import User
from rest_framework import serializers

try:
    from allauth.socialaccount.models import SocialAccount
except Exception:
    SocialAccount = None

from .models import (
    BusLine,
    GamificationBadge,
    GamificationPoints,
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
    CarpoolReservation,
    SkiPartnerReport,
    SkiStation,
    SkiStationCamera,
    SkiNewsItem,
    SkiStory,
    SkiStoryComment,
    SkiStoryLike,
    SnowConditionUpdate,
    UserBadge,
    UserFriend,
    UserGameStats,
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
    cameras = serializers.SerializerMethodField()
    bus_lines = serializers.SerializerMethodField()

    class Meta:
        model = SkiStation
        fields = '__all__'

    def get_cameras(self, obj):
        cameras = obj.cameras.filter(is_active=True)
        return SkiStationCameraSerializer(cameras, many=True).data

    def get_bus_lines(self, obj):
        bus_lines = obj.bus_lines.all()
        return BusLineSerializer(bus_lines, many=True).data

class BusLineSerializer(serializers.ModelSerializer):
    class Meta:
        model = BusLine
        fields = '__all__'

class SkiStationCameraSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiStationCamera
        fields = '__all__'
        read_only_fields = ['created_at', 'updated_at']

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
    seller_info = serializers.SerializerMethodField()
    seller_ratings = serializers.SerializerMethodField()

    class Meta:
        model = SkiMaterialListing
        fields = '__all__'

    def get_seller_info(self, obj):
        """Get seller profile information"""
        return {
            'id': obj.user.id,
            'display_name': _display_name_for_user(obj.user),
            'username': obj.user.username,
            'email': obj.user.email,
            'profile_picture': _profile_picture_for_user(obj.user),
            'google_profile_picture_url': _google_picture_for_user(obj.user),
        }

    def get_seller_ratings(self, obj):
        """Get seller's rating statistics and recent comments"""
        from django.db.models import Avg
        
        ratings = MarketplaceUserRating.objects.filter(rated_user=obj.user).order_by('-created_at')
        
        if not ratings.exists():
            return {
                'average_score': None,
                'total_ratings': 0,
                'recent_comments': [],
            }
        
        avg_score = ratings.aggregate(Avg('score'))['score__avg']
        
        # Get last 3 ratings with comments
        recent_ratings = ratings.filter(comment__isnull=False, comment__gt='')[:3]
        recent_comments = [
            {
                'score': rating.score,
                'comment': rating.comment,
                'created_at': rating.created_at.isoformat(),
                'rater_name': _display_name_for_user(rating.rater),
            }
            for rating in recent_ratings
        ]
        
        return {
            'average_score': round(avg_score, 1) if avg_score else None,
            'total_ratings': ratings.count(),
            'recent_comments': recent_comments,
        }

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
            'is_private',
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
        fields = ['id', 'user', 'profile_picture', 'organization_name', 'messages_private_by_default']

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
    ski_station_name = serializers.SerializerMethodField()
    organizer_display = serializers.SerializerMethodField()
    seats_reserved = serializers.SerializerMethodField()
    seats_remaining = serializers.SerializerMethodField()
    my_reserved_seats = serializers.SerializerMethodField()
    my_reservation_status = serializers.SerializerMethodField()
    pending_reservations = serializers.SerializerMethodField()

    class Meta:
        model = SkiPartnerPost
        fields = '__all__'
        read_only_fields = ['user', 'created_at']

    def get_ski_station_name(self, obj):
        station = getattr(obj, 'ski_station', None)
        return station.name if station else ''

    def get_organizer_display(self, obj):
        return _display_name_for_user(obj.user)

    def get_seats_reserved(self, obj):
        return obj.seats_reserved if obj.is_carpool else 0

    def get_seats_remaining(self, obj):
        return obj.seats_remaining if obj.is_carpool else 0

    def get_my_reserved_seats(self, obj):
        request = self.context.get('request')
        if not request or not request.user.is_authenticated:
            return 0
        reservation = obj.reservations.filter(
            user=request.user,
            status__in=[CarpoolReservation.STATUS_PENDING, CarpoolReservation.STATUS_ACTIVE],
        ).first()
        if not reservation:
            return 0
        return int(reservation.seats_reserved or 0)

    def get_my_reservation_status(self, obj):
        request = self.context.get('request')
        if not request or not request.user.is_authenticated:
            return ''
        reservation = obj.reservations.filter(
            user=request.user,
            status__in=[CarpoolReservation.STATUS_PENDING, CarpoolReservation.STATUS_ACTIVE],
        ).first()
        return reservation.status if reservation else ''

    def get_pending_reservations(self, obj):
        request = self.context.get('request')
        if not request or not request.user.is_authenticated:
            return []
        if request.user.id != obj.user_id:
            return []

        pending_rows = (
            obj.reservations.filter(status=CarpoolReservation.STATUS_PENDING)
            .select_related('user')
            .order_by('created_at')[:20]
        )
        payload = []
        for reservation in pending_rows:
            payload.append(
                {
                    'reservation_id': reservation.id,
                    'user_id': reservation.user_id,
                    'user_label': _display_name_for_user(reservation.user),
                    'seats_reserved': int(reservation.seats_reserved or 0),
                    'created_at': reservation.created_at,
                }
            )
        return payload


class CarpoolReservationSerializer(serializers.ModelSerializer):
    class Meta:
        model = CarpoolReservation
        fields = '__all__'
        read_only_fields = ['user', 'created_at', 'updated_at']


class SkiPartnerReportSerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiPartnerReport
        fields = '__all__'


class SkiStorySerializer(serializers.ModelSerializer):
    class Meta:
        model = SkiStory
        fields = '__all__'


class SkiStoryCommentSerializer(serializers.ModelSerializer):
    user_label = serializers.SerializerMethodField()

    class Meta:
        model = SkiStoryComment
        fields = ['id', 'story', 'user', 'user_label', 'body', 'created_at']
        read_only_fields = ['story', 'user', 'created_at']

    def get_user_label(self, obj):
        return _display_name_for_user(obj.user)


class SkiStoryFeedSerializer(serializers.ModelSerializer):
    user_label = serializers.SerializerMethodField()
    image_base64 = serializers.SerializerMethodField()
    ski_station_name = serializers.SerializerMethodField()
    like_count = serializers.SerializerMethodField()
    comment_count = serializers.SerializerMethodField()
    is_liked_by_me = serializers.SerializerMethodField()
    recent_comments = serializers.SerializerMethodField()

    class Meta:
        model = SkiStory
        fields = [
            'id',
            'user',
            'user_label',
            'ski_station',
            'ski_station_name',
            'caption',
            'image_base64',
            'crowd_level',
            'weather_label',
            'temperature_c',
            'snow_depth_cm',
            'fun_score',
            'created_at',
            'expires_at',
            'like_count',
            'comment_count',
            'is_liked_by_me',
            'recent_comments',
        ]

    def get_user_label(self, obj):
        return _display_name_for_user(obj.user)

    def get_image_base64(self, obj):
        return _encode_binary_field(obj.image)

    def get_ski_station_name(self, obj):
        return getattr(obj.ski_station, 'name', '') or ''

    def get_like_count(self, obj):
        if hasattr(obj, 'like_count'):
            return int(obj.like_count or 0)
        return obj.likes.count()

    def get_comment_count(self, obj):
        if hasattr(obj, 'comment_count'):
            return int(obj.comment_count or 0)
        return obj.comments.count()

    def get_is_liked_by_me(self, obj):
        request = self.context.get('request')
        user = getattr(request, 'user', None)
        if not user or not user.is_authenticated:
            return False
        return SkiStoryLike.objects.filter(story=obj, user=user).exists()

    def get_recent_comments(self, obj):
        recent = obj.comments.select_related('user').order_by('-created_at')[:3]
        return SkiStoryCommentSerializer(recent, many=True).data


class SkiNewsItemSerializer(serializers.ModelSerializer):
    station_name = serializers.SerializerMethodField()

    class Meta:
        model = SkiNewsItem
        fields = [
            'id',
            'title',
            'summary',
            'link',
            'source_name',
            'source_url',
            'language',
            'ski_station',
            'station_name',
            'image_url',
            'published_at',
            'is_highlighted',
        ]

    def get_station_name(self, obj):
        return getattr(obj.ski_station, 'name', '') or ''


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
        read_only_fields = ['rater', 'created_at', 'updated_at']


class UserFriendSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserFriend
        fields = '__all__'


class GamificationPointsSerializer(serializers.ModelSerializer):
    user_display_name = serializers.SerializerMethodField()
    
    class Meta:
        model = GamificationPoints
        fields = '__all__'
        read_only_fields = ['user', 'created_at']

    def get_user_display_name(self, obj):
        return _display_name_for_user(obj.user)


class GamificationBadgeSerializer(serializers.ModelSerializer):
    class Meta:
        model = GamificationBadge
        fields = '__all__'
        read_only_fields = ['created_at']


class UserBadgeSerializer(serializers.ModelSerializer):
    badge = GamificationBadgeSerializer(read_only=True)
    
    class Meta:
        model = UserBadge
        fields = '__all__'
        read_only_fields = ['user', 'earned_at']


class UserGameStatsSerializer(serializers.ModelSerializer):
    user = UserSerializer(read_only=True)
    earned_badges = UserBadgeSerializer(read_only=True, many=True)
    recent_points = serializers.SerializerMethodField()
    
    class Meta:
        model = UserGameStats
        fields = [
            'id',
            'user',
            'total_points',
            'level',
            'experience_points',
            'badges_count',
            'daily_login_streak',
            'last_login_date',
            'total_listings_created',
            'total_deals_completed',
            'total_reviews_written',
            'average_seller_rating',
            'earned_badges',
            'recent_points',
            'updated_at',
        ]
        read_only_fields = ['user', 'updated_at']

    def get_recent_points(self, obj):
        """Get recent 5 points earned"""
        recent = obj.user.gamification_points.all()[:5]
        return GamificationPointsSerializer(recent, many=True).data