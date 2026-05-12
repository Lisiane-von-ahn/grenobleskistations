import logging
import os
import base64
import binascii
import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from django.conf import settings
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from django.core.cache import cache
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import transaction
from django.db.models import Avg, Count, Q, Sum
from django.utils import timezone
from rest_framework import status, viewsets
from rest_framework.authentication import SessionAuthentication, TokenAuthentication
from rest_framework.authtoken.models import Token
from rest_framework.decorators import (
    action,
    api_view,
    authentication_classes,
    permission_classes,
)
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

try:
    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token as google_id_token
except Exception:
    google_requests = None
    google_id_token = None

try:
    from allauth.socialaccount.models import SocialAccount
except Exception:
    SocialAccount = None

from .models import (
    BusLine,
    CrowdStatusUpdate,
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
    SkiMaterialListing,
    SkiMaterialImage,
    CarpoolReservation,
    SkiPartnerPost,
    SkiPartnerReport,
    SkiStation,
    SkiStationCamera,
    SkiStory,
    SnowConditionUpdate,
    UserBadge,
    UserFriend,
    UserGameStats,
    UserProfile,
)
from .serializers import (
    BusLineSerializer,
    GamificationBadgeSerializer,
    GamificationPointsSerializer,
    InstructorProfileSerializer,
    InstructorReviewSerializer,
    InstructorServiceSerializer,
    MarketplaceDealSerializer,
    MarketplaceSavedFilterSerializer,
    MarketplaceUserRatingSerializer,
    MessageSerializer,
    PisteConditionReportSerializer,
    ServiceStoreSerializer,
    SkiCircuitSerializer,
    SkiMaterialListingSerializer,
    CarpoolReservationSerializer,
    SkiPartnerPostSerializer,
    SkiPartnerReportSerializer,
    SkiStationSerializer,
    SkiStationCameraSerializer,
    SkiStorySerializer,
    SnowConditionUpdateSerializer,
    UserBadgeSerializer,
    UserFriendSerializer,
    UserGameStatsSerializer,
    UserProfileSerializer,
    UserSerializer,
)

logger = logging.getLogger("skistation.auth")
APP_AUTH_BACKEND = "skistation_project.backends.EmailOrUsernameModelBackend"


def _current_authenticated_user(view):
    if getattr(view, "swagger_fake_view", False):
        return None

    request = getattr(view, "request", None)
    user = getattr(request, "user", None)
    if user is None or not getattr(user, "is_authenticated", False):
        return None
    return user


def _encode_binary_field(value):
    if not value:
        return None
    return base64.b64encode(value).decode("utf-8")


def _send_platform_message(sender, recipient, subject, body):
    if not sender or not recipient or sender.id == recipient.id:
        return
    Message.objects.create(
        sender=sender,
        recipient=recipient,
        subject=(subject or 'Notification covoiturage')[:255],
        body=(body or '')[:4000],
    )


def _decode_base64_binary(value):
    if value in (None, ""):
        return None
    if isinstance(value, bytes):
        return value
    if not isinstance(value, str):
        raise ValidationError("Invalid image payload.")

    raw = value.strip()
    if raw.startswith("data:") and "," in raw:
        raw = raw.split(",", 1)[1]
    if not raw:
        return None

    try:
        return base64.b64decode(raw, validate=True)
    except (ValueError, binascii.Error):
        raise ValidationError("Invalid base64 image payload.")


def _fetch_weather_summary(latitude, longitude):
    api_key = (getattr(settings, "WEATHER_API_KEY", "") or "").strip()
    if not api_key or api_key == "qssdsdsd":
        return {}

    query = urlencode(
        {
            "lat": str(latitude),
            "lon": str(longitude),
            "appid": api_key,
            "units": "metric",
            "lang": "fr",
        }
    )
    url = f"https://api.openweathermap.org/data/2.5/weather?{query}"

    try:
        with urlopen(url, timeout=4) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception:
        logger.warning("Weather fetch failed for lat=%s lon=%s", latitude, longitude, exc_info=True)
        return {}

    weather = payload.get("weather") or []
    main = payload.get("main") or {}
    snow = payload.get("snow") or {}

    return {
        "weather_description": ((weather[0] or {}).get("description") if weather else "") or "",
        "temperature_c": main.get("temp"),
        "feels_like_c": main.get("feels_like"),
        "snow_cm": snow.get("1h") or snow.get("3h"),
    }


class SkiStationViewSet(viewsets.ModelViewSet):
    queryset = SkiStation.objects.all()
    serializer_class = SkiStationSerializer

    @action(detail=False, methods=["get"], permission_classes=[AllowAny])
    def conditions(self, request):
        crowd_labels = {
            PisteConditionReport.CROWD_QUIET: "Peu",
            PisteConditionReport.CROWD_NORMAL: "Agreable",
            PisteConditionReport.CROWD_BUSY: "Bonde",
        }
        stations = SkiStation.objects.order_by("distanceFromGrenoble", "name")[:18]
        results = []

        for station in stations:
            latest_report = PisteConditionReport.objects.filter(ski_station=station).order_by("-created_at").first()
            latest_snow = SnowConditionUpdate.objects.filter(ski_station=station).order_by("-created_at").first()
            latest_crowd = CrowdStatusUpdate.objects.filter(ski_station=station).order_by("-created_at").first()
            avg_rating = PisteConditionReport.objects.filter(ski_station=station).aggregate(avg=Avg("piste_rating")).get("avg")
            weather = _fetch_weather_summary(station.latitude, station.longitude)

            updated_candidates = [
                value for value in [
                    getattr(latest_report, "created_at", None),
                    getattr(latest_snow, "created_at", None),
                    getattr(latest_crowd, "created_at", None),
                ] if value is not None
            ]
            updated_at = max(updated_candidates) if updated_candidates else None

            results.append(
                {
                    "id": station.id,
                    "station_name": station.name,
                    "altitude": station.altitude,
                    "distance_from_grenoble": station.distanceFromGrenoble,
                    "piste_map_url": station.piste_map_url or "",
                    "piste_map_thumbnail_url": station.piste_map_thumbnail_url or "",
                    "latitude": station.latitude,
                    "longitude": station.longitude,
                    "weather_description": weather.get("weather_description") or "indisponible",
                    "temperature_c": weather.get("temperature_c"),
                    "feels_like_c": weather.get("feels_like_c"),
                    "snow_depth_cm": getattr(latest_snow, "snow_depth_cm", None) or weather.get("snow_cm"),
                    "crowd_label": crowd_labels.get(getattr(latest_crowd, "crowd_level", ""), "normal"),
                    "rating_avg": round(avg_rating, 1) if avg_rating is not None else None,
                    "latest_comment": getattr(latest_report, "comment", "") or "",
                    "updated_at": updated_at.isoformat() if updated_at is not None else "",
                }
            )

        return Response(results, status=status.HTTP_200_OK)

class BusLineViewSet(viewsets.ModelViewSet):
    queryset = BusLine.objects.all()
    serializer_class = BusLineSerializer

class SkiStationCameraViewSet(viewsets.ModelViewSet):
    queryset = SkiStationCamera.objects.filter(is_active=True)
    serializer_class = SkiStationCameraSerializer
    
    def get_queryset(self):
        queryset = SkiStationCamera.objects.filter(is_active=True)
        ski_station_id = self.request.query_params.get('ski_station_id', None)
        if ski_station_id is not None:
            queryset = queryset.filter(ski_station_id=ski_station_id)
        return queryset

class ServiceStoreViewSet(viewsets.ModelViewSet):
    queryset = ServiceStore.objects.all()
    serializer_class = ServiceStoreSerializer

class SkiCircuitViewSet(viewsets.ModelViewSet):
    queryset = SkiCircuit.objects.all()
    serializer_class = SkiCircuitSerializer

class SkiMaterialListingViewSet(viewsets.ModelViewSet):
    queryset = SkiMaterialListing.objects.all()
    serializer_class = SkiMaterialListingSerializer

    def perform_create(self, serializer):
        user_id = self.request.data.get('user')
        user = None
        if user_id:
            try:
                user = User.objects.get(id=user_id)
            except User.DoesNotExist:
                raise ValidationError("Invalid user ID.")
        elif self.request.user and self.request.user.is_authenticated:
            user = self.request.user

        if user is None:
            raise ValidationError("User ID is required.")

        listing = serializer.save(user=user)

        images_payload = self.request.data.get('images', None)
        if images_payload is None and hasattr(self.request.data, 'getlist'):
            images_payload = self.request.data.getlist('images')

        if isinstance(images_payload, str):
            images_payload = images_payload.strip()
            if images_payload.startswith('['):
                try:
                    images_payload = json.loads(images_payload)
                except json.JSONDecodeError:
                    images_payload = []
            elif images_payload:
                images_payload = [images_payload]
            else:
                images_payload = []

        if not isinstance(images_payload, list):
            images_payload = []

        for raw_image in images_payload:
            image_bytes = _decode_base64_binary(raw_image)
            if image_bytes is None:
                continue
            SkiMaterialImage.objects.create(listing=listing, image=image_bytes)
        
class MessageViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = MessageSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return Message.objects.none()
        return Message.objects.filter(Q(sender=user) | Q(recipient=user)).order_by('-created_at')

    def perform_create(self, serializer):
        sender = self.request.user
        recipient_id = self.request.data.get('recipient')
        if not recipient_id:
            raise ValidationError('recipient is required.')
        serializer.save(sender=sender)

    @action(detail=False, methods=['post'], url_path='mark-read')
    def mark_read(self, request):
        other_user_id = request.data.get('user_id')
        try:
            other_user_id = int(other_user_id)
        except (TypeError, ValueError):
            return Response({'error': 'user_id is required.'}, status=status.HTTP_400_BAD_REQUEST)

        if other_user_id <= 0:
            return Response({'error': 'user_id is invalid.'}, status=status.HTTP_400_BAD_REQUEST)

        updated = Message.objects.filter(
            sender_id=other_user_id,
            recipient=request.user,
            is_read=False,
        ).update(is_read=True)
        return Response({'updated': updated}, status=status.HTTP_200_OK)

class UserProfileViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    queryset = UserProfile.objects.select_related('user').all()
    serializer_class = UserProfileSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return UserProfile.objects.none()
        return UserProfile.objects.select_related('user').filter(user=user)

    @action(detail=False, methods=['get'])
    def me(self, request):
        profile = UserProfile.objects.get(user=request.user)
        serializer = self.get_serializer(profile)
        return Response(serializer.data)

class UserViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    queryset = User.objects.all()
    serializer_class = UserSerializer

    def get_queryset(self):
        qs = User.objects.all().order_by('-date_joined')
        user = _current_authenticated_user(self)
        if user is not None:
            qs = qs.exclude(id=user.id)

        if self.action == 'list':
            query = (self.request.query_params.get('q') or '').strip()
            if query:
                qs = qs.filter(
                    Q(first_name__icontains=query)
                    | Q(last_name__icontains=query)
                    | Q(username__icontains=query)
                    | Q(email__icontains=query)
                )
            return qs

        return qs

    @action(detail=False, methods=['post'], permission_classes=[AllowAny])
    def register(self, request):
        email = (request.data.get('email') or '').strip().lower()
        password = request.data.get('password')
        first_name = (request.data.get('first_name') or '').strip()
        last_name = (request.data.get('last_name') or '').strip()

        if not email or not password:
            return Response({'error': 'Email and password are required.'}, status=status.HTTP_400_BAD_REQUEST)
        if User.objects.filter(email__iexact=email).exists():
            return Response({'error': 'A user with this email already exists.'}, status=status.HTTP_400_BAD_REQUEST)

        user = User.objects.create_user(
            username=email,
            email=email,
            password=password,
            first_name=first_name,
            last_name=last_name,
        )
        return Response({'message': 'User created successfully.'}, status=status.HTTP_201_CREATED)


class SnowConditionUpdateViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = SnowConditionUpdateSerializer

    def get_queryset(self):
        return SnowConditionUpdate.objects.select_related('ski_station', 'user').all()

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class PisteConditionReportViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = PisteConditionReportSerializer

    def get_queryset(self):
        return PisteConditionReport.objects.select_related('ski_station', 'user').all()

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class InstructorProfileViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = InstructorProfileSerializer

    def get_queryset(self):
        if self.action == 'list':
            return InstructorProfile.objects.select_related('user').filter(is_active=True)
        return InstructorProfile.objects.select_related('user').all()

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class InstructorServiceViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = InstructorServiceSerializer

    def get_queryset(self):
        qs = InstructorService.objects.select_related('instructor', 'instructor__user', 'ski_station')
        if self.action == 'list':
            return qs.filter(is_active=True)
        return qs


class InstructorReviewViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = InstructorReviewSerializer

    def get_queryset(self):
        return InstructorReview.objects.select_related('instructor', 'instructor__user', 'user').all()

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class SkiPartnerPostViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = SkiPartnerPostSerializer

    def get_queryset(self):
        qs = SkiPartnerPost.objects.select_related('user', 'ski_station').all()
        kind = (self.request.query_params.get('kind') or '').strip().lower()

        if kind == 'carpool':
            carpool_filter = (
                Q(is_carpool=True)
                | Q(title__icontains='covoitur')
                | Q(message__icontains='covoitur')
                | Q(title__icontains='carpool')
                | Q(message__icontains='carpool')
                | Q(title__icontains='trajet')
                | Q(message__icontains='trajet')
                | Q(title__icontains='voiture')
                | Q(message__icontains='voiture')
                | Q(title__icontains='ride share')
                | Q(message__icontains='ride share')
            )
            qs = qs.filter(carpool_filter)

        if self.action == 'list':
            return qs.filter(is_active=True)
        return qs

    def perform_create(self, serializer):
        is_carpool = bool(serializer.validated_data.get('is_carpool'))
        save_kwargs = {'user': self.request.user}
        if is_carpool and not serializer.validated_data.get('departure_city'):
            save_kwargs['departure_city'] = serializer.validated_data.get('city', '')
        serializer.save(**save_kwargs)

    @action(detail=False, methods=['get'], permission_classes=[IsAuthenticated], url_path='my-reservations')
    def my_reservations(self, request):
        reservations = (
            CarpoolReservation.objects.filter(
                user=request.user,
                status__in=[CarpoolReservation.STATUS_PENDING, CarpoolReservation.STATUS_ACTIVE],
                post__is_active=True,
                post__is_carpool=True,
            )
            .select_related('post', 'post__ski_station', 'post__user')
            .order_by('post__departure_datetime', '-created_at')
        )

        payload = []
        for reservation in reservations:
            post_data = SkiPartnerPostSerializer(reservation.post, context={'request': request}).data
            payload.append(
                {
                    'reservation_id': reservation.id,
                    'seats_reserved': reservation.seats_reserved,
                    'status': reservation.status,
                    'created_at': reservation.created_at,
                    'updated_at': reservation.updated_at,
                    'post': post_data,
                }
            )
        return Response(payload)

    @action(detail=True, methods=['post'], permission_classes=[IsAuthenticated])
    def reserve(self, request, pk=None):
        post = self.get_object()
        if not post.is_active:
            raise ValidationError('This carpool post is no longer active.')
        if not post.is_carpool:
            raise ValidationError('Reservation is only available for carpool posts.')
        if post.user_id == request.user.id:
            raise ValidationError('You cannot reserve seats on your own carpool.')

        try:
            requested_seats = int(request.data.get('seats', 1))
        except (TypeError, ValueError):
            requested_seats = 1
        if requested_seats < 1:
            raise ValidationError('seats must be at least 1.')
        if requested_seats > int(post.total_seats or 0):
            raise ValidationError(f'seats must be <= {int(post.total_seats or 0)} for this carpool.')

        with transaction.atomic():
            locked_post = SkiPartnerPost.objects.select_for_update().get(id=post.id)
            reservation, _created = CarpoolReservation.objects.update_or_create(
                post=locked_post,
                user=request.user,
                defaults={
                    'seats_reserved': requested_seats,
                    'status': CarpoolReservation.STATUS_PENDING,
                },
            )

        _send_platform_message(
            sender=request.user,
            recipient=post.user,
            subject=f'Demande de reservation: {post.title}',
            body=(
                f"{request.user.username} a demande {requested_seats} place(s) pour votre covoiturage "
                f"\"{post.title}\"."
            ),
        )

        return Response(
            {
                'detail': 'Reservation request sent. Awaiting organizer approval.',
                'reservation': CarpoolReservationSerializer(reservation, context={'request': request}).data,
                'post': SkiPartnerPostSerializer(self.get_object(), context={'request': request}).data,
            }
        )

    @action(detail=True, methods=['post'], permission_classes=[IsAuthenticated])
    def cancel_reservation(self, request, pk=None):
        post = self.get_object()
        reservation = CarpoolReservation.objects.filter(
            post=post,
            user=request.user,
            status__in=[CarpoolReservation.STATUS_PENDING, CarpoolReservation.STATUS_ACTIVE],
        ).first()
        if not reservation:
            raise ValidationError('No pending/active reservation found for this post.')

        reservation.status = CarpoolReservation.STATUS_CANCELLED
        reservation.save(update_fields=['status', 'updated_at'])
        _send_platform_message(
            sender=request.user,
            recipient=post.user,
            subject=f'Annulation de reservation: {post.title}',
            body=(
                f"{request.user.username} a annule sa reservation ({reservation.seats_reserved} place(s)) "
                f"pour votre covoiturage \"{post.title}\"."
            ),
        )
        return Response(
            {
                'detail': 'Reservation cancelled.',
                'post': SkiPartnerPostSerializer(self.get_object(), context={'request': request}).data,
            }
        )

    @action(detail=True, methods=['post'], permission_classes=[IsAuthenticated])
    def approve_reservation(self, request, pk=None):
        post = self.get_object()
        if post.user_id != request.user.id:
            raise ValidationError('Only the organizer can approve reservations.')

        reservation_id = request.data.get('reservation_id')
        if not reservation_id:
            raise ValidationError('reservation_id is required.')

        with transaction.atomic():
            locked_post = SkiPartnerPost.objects.select_for_update().get(id=post.id)
            reservation = (
                CarpoolReservation.objects.select_for_update()
                .filter(id=reservation_id, post=locked_post, status=CarpoolReservation.STATUS_PENDING)
                .select_related('user')
                .first()
            )
            if not reservation:
                raise ValidationError('Pending reservation not found.')

            seats_taken = int(
                CarpoolReservation.objects.filter(post=locked_post, status=CarpoolReservation.STATUS_ACTIVE)
                .exclude(id=reservation.id)
                .aggregate(total=Sum('seats_reserved'))['total']
                or 0
            )
            capacity_left = max(int(locked_post.total_seats or 0) - seats_taken, 0)
            if int(reservation.seats_reserved or 0) > capacity_left:
                raise ValidationError(f'Only {capacity_left} seat(s) available.')

            reservation.status = CarpoolReservation.STATUS_ACTIVE
            reservation.save(update_fields=['status', 'updated_at'])

        _send_platform_message(
            sender=request.user,
            recipient=reservation.user,
            subject=f'Reservation approuvee: {post.title}',
            body=(
                f"Votre demande de {reservation.seats_reserved} place(s) pour \"{post.title}\" "
                'a ete approuvee.'
            ),
        )
        return Response(
            {
                'detail': 'Reservation approved.',
                'reservation': CarpoolReservationSerializer(reservation, context={'request': request}).data,
                'post': SkiPartnerPostSerializer(self.get_object(), context={'request': request}).data,
            }
        )

    @action(detail=True, methods=['post'], permission_classes=[IsAuthenticated])
    def reject_reservation(self, request, pk=None):
        post = self.get_object()
        if post.user_id != request.user.id:
            raise ValidationError('Only the organizer can reject reservations.')

        reservation_id = request.data.get('reservation_id')
        if not reservation_id:
            raise ValidationError('reservation_id is required.')

        reservation = (
            CarpoolReservation.objects.filter(
                id=reservation_id,
                post=post,
                status=CarpoolReservation.STATUS_PENDING,
            )
            .select_related('user')
            .first()
        )
        if not reservation:
            raise ValidationError('Pending reservation not found.')

        reservation.status = CarpoolReservation.STATUS_REJECTED
        reservation.save(update_fields=['status', 'updated_at'])
        _send_platform_message(
            sender=request.user,
            recipient=reservation.user,
            subject=f'Reservation refusee: {post.title}',
            body=f"Votre demande de reservation pour \"{post.title}\" a ete refusee.",
        )
        return Response(
            {
                'detail': 'Reservation rejected.',
                'reservation': CarpoolReservationSerializer(reservation, context={'request': request}).data,
                'post': SkiPartnerPostSerializer(self.get_object(), context={'request': request}).data,
            }
        )


class SkiPartnerReportViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = SkiPartnerReportSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return SkiPartnerReport.objects.none()
        return SkiPartnerReport.objects.select_related('post', 'reporter').filter(reporter=user)

    def perform_create(self, serializer):
        serializer.save(reporter=self.request.user)


class SkiStoryViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = SkiStorySerializer

    def get_queryset(self):
        now = timezone.now()
        return SkiStory.objects.select_related('user', 'ski_station').filter(expires_at__gt=now)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class MarketplaceSavedFilterViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = MarketplaceSavedFilterSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return MarketplaceSavedFilter.objects.none()
        return MarketplaceSavedFilter.objects.filter(user=user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class MarketplaceDealViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = MarketplaceDealSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return MarketplaceDeal.objects.none()
        return MarketplaceDeal.objects.select_related('listing', 'buyer', 'seller').filter(
            Q(buyer=user) | Q(seller=user)
        )


class MarketplaceUserRatingViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = MarketplaceUserRatingSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return MarketplaceUserRating.objects.none()
        return MarketplaceUserRating.objects.select_related('listing', 'rater', 'rated_user').filter(
            Q(rater=user) | Q(rated_user=user)
        )

    def perform_create(self, serializer):
        listing = serializer.validated_data.get('listing')
        rated_user = serializer.validated_data.get('rated_user')
        rater = self.request.user

        if listing is None:
            raise ValidationError('listing is required.')
        if rated_user is None:
            raise ValidationError('rated_user is required.')
        if rated_user.id == rater.id:
            raise ValidationError('You cannot rate yourself.')
        if listing.user_id != rated_user.id:
            raise ValidationError('rated_user must be the owner of the listing.')

        serializer.save(rater=rater)


class UserFriendViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = UserFriendSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return UserFriend.objects.none()
        return UserFriend.objects.select_related('user', 'friend').filter(user=user)

    def perform_create(self, serializer):
        friend = serializer.validated_data.get('friend')
        user = self.request.user
        if friend is None:
            raise ValidationError('friend is required.')
        if friend.id == user.id:
            raise ValidationError('Cannot add yourself as friend.')

        UserFriend.objects.get_or_create(user=user, friend=friend)
        UserFriend.objects.get_or_create(user=friend, friend=user)

    def destroy(self, request, *args, **kwargs):
        instance = self.get_object()
        if instance.user_id != request.user.id:
            return Response(status=status.HTTP_403_FORBIDDEN)

        friend_id = instance.friend_id
        UserFriend.objects.filter(user=request.user, friend_id=friend_id).delete()
        UserFriend.objects.filter(user_id=friend_id, friend=request.user).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


class UserGameStatsViewSet(viewsets.ReadOnlyModelViewSet):
    """User gamification statistics - read only"""
    permission_classes = [IsAuthenticated]
    serializer_class = UserGameStatsSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return UserGameStats.objects.none()
        # Users can only view their own stats
        return UserGameStats.objects.filter(user=user)

    @action(detail=False, methods=['get'])
    def me(self, request):
        """Get current user's game stats"""
        try:
            stats = UserGameStats.objects.get(user=request.user)
            serializer = self.get_serializer(stats)
            return Response(serializer.data)
        except UserGameStats.DoesNotExist:
            return Response({'detail': 'Stats not found'}, status=status.HTTP_404_NOT_FOUND)

    @action(detail=False, methods=['get'])
    def leaderboard(self, request):
        """Get top players by level and points"""
        limit = int(request.query_params.get('limit', 10))
        stats = UserGameStats.objects.order_by('-level', '-total_points')[:limit]
        serializer = self.get_serializer(stats, many=True)
        return Response(serializer.data)


class GamificationPointsViewSet(viewsets.ReadOnlyModelViewSet):
    """View earned points history - read only"""
    permission_classes = [IsAuthenticated]
    serializer_class = GamificationPointsSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return GamificationPoints.objects.none()
        return GamificationPoints.objects.filter(user=user).order_by('-created_at')

    @action(detail=False, methods=['get'])
    def summary(self, request):
        """Get points summary by activity type"""
        from django.db.models import Sum
        
        user = request.user
        summary = GamificationPoints.objects.filter(user=user).values('activity_type').annotate(
            total_points=Sum('points_earned'),
            count=Count('id')
        ).order_by('-total_points')
        
        return Response(summary)


class GamificationBadgeViewSet(viewsets.ReadOnlyModelViewSet):
    """Available badges - read only"""
    serializer_class = GamificationBadgeSerializer
    queryset = GamificationBadge.objects.all().order_by('-points_value')
    permission_classes = [AllowAny]


class UserBadgeViewSet(viewsets.ReadOnlyModelViewSet):
    """User earned badges - read only"""
    permission_classes = [IsAuthenticated]
    serializer_class = UserBadgeSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return UserBadge.objects.none()
        return UserBadge.objects.filter(user=user).select_related('badge').order_by('-earned_at')

    @action(detail=False, methods=['get'])
    def available(self, request):
        """Get badges not yet earned by user"""
        earned_badge_ids = UserBadge.objects.filter(user=request.user).values_list('badge_id', flat=True)
        available_badges = GamificationBadge.objects.exclude(id__in=earned_badge_ids).order_by('-points_value')
        serializer = GamificationBadgeSerializer(available_badges, many=True)
        return Response(serializer.data)


def _serialize_user(user):
    profile = getattr(user, "profile", None)
    google_profile_picture_url = None
    if SocialAccount is not None:
        social = SocialAccount.objects.filter(user=user, provider="google").first()
        if social is not None:
            google_profile_picture_url = (social.extra_data or {}).get("picture")

    return {
        "id": user.id,
        "email": user.email,
        "username": user.username,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "has_profile_picture": bool(profile and profile.profile_picture),
        "profile_picture": _encode_binary_field(getattr(profile, "profile_picture", None)),
        "google_profile_picture_url": google_profile_picture_url,
    }


def _get_google_client_ids():
    raw = os.getenv("GOOGLE_OAUTH_CLIENT_IDS") or os.getenv("GOOGLE_OAUTH_CLIENT_ID") or ""
    return [client_id.strip() for client_id in raw.split(",") if client_id.strip()]


def _verify_google_token(token):
    if google_requests is None or google_id_token is None:
        raise ValidationError("Google login dependencies are missing on the server.")

    request_adapter = google_requests.Request()
    client_ids = _get_google_client_ids()

    if client_ids:
        for client_id in client_ids:
            try:
                return google_id_token.verify_oauth2_token(token, request_adapter, client_id)
            except Exception:
                continue
        raise ValidationError("Google token audience is not allowed.")

    # Fallback for dev mode where no client ID is configured.
    return google_id_token.verify_oauth2_token(token, request_adapter)


@api_view(["POST"])
@permission_classes([AllowAny])
def auth_register_view(request):
    email = (request.data.get("email") or "").strip().lower()
    password = request.data.get("password")
    first_name = (request.data.get("first_name") or "").strip()
    last_name = (request.data.get("last_name") or "").strip()
    accept_terms_raw = request.data.get("accept_terms")

    accepted_values = {True, "true", "1", 1, "yes", "on", "True", "YES", "ON"}
    accept_terms = accept_terms_raw in accepted_values

    if not email or not password:
        return Response(
            {"error": "Email and password are required."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if not accept_terms:
        return Response(
            {"error": "Terms and Privacy Policy acceptance is required."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if User.objects.filter(email__iexact=email).exists():
        return Response(
            {"error": "A user with this email already exists."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        validate_password(password)
    except DjangoValidationError as exc:
        return Response({"error": exc.messages}, status=status.HTTP_400_BAD_REQUEST)

    user = User.objects.create_user(
        username=email,
        email=email,
        password=password,
        first_name=first_name,
        last_name=last_name,
    )
    token, _ = Token.objects.get_or_create(user=user)
    login(request, user, backend=APP_AUTH_BACKEND)

    return Response(
        {
            "token": token.key,
            "user": _serialize_user(user),
            "message": "User created successfully.",
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([AllowAny])
def auth_login_view(request):
    login_identifier = (
        (request.data.get("email") or request.data.get("username") or "").strip().lower()
    )
    password = request.data.get("password")

    user = authenticate(request, username=login_identifier, password=password)
    if user is None:
        return Response({"error": "Invalid credentials"}, status=status.HTTP_401_UNAUTHORIZED)

    login(request, user, backend=APP_AUTH_BACKEND)
    token, _ = Token.objects.get_or_create(user=user)
    return Response(
        {
            "token": token.key,
            "user": _serialize_user(user),
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([AllowAny])
def auth_google_login_view(request):
    token_value = request.data.get("id_token")
    if not token_value:
        return Response({"error": "id_token is required."}, status=status.HTTP_400_BAD_REQUEST)

    try:
        id_info = _verify_google_token(token_value)
    except ValidationError as exc:
        return Response({"error": str(exc.detail)}, status=status.HTTP_400_BAD_REQUEST)
    except Exception:
        logger.exception("Failed Google token verification")
        return Response({"error": "Google token is invalid."}, status=status.HTTP_400_BAD_REQUEST)

    email = (id_info.get("email") or "").strip().lower()
    if not email:
        return Response(
            {"error": "Google account does not provide an email."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if id_info.get("email_verified") is not True:
        return Response(
            {"error": "Google email is not verified."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    user = User.objects.filter(email__iexact=email).first()
    is_new_user = user is None
    if user is None:
        user = User.objects.create_user(
            username=email,
            email=email,
            password=User.objects.make_random_password(),
            first_name=(id_info.get("given_name") or "").strip(),
            last_name=(id_info.get("family_name") or "").strip(),
        )
    else:
        has_updates = False
        if not user.username:
            user.username = email
            has_updates = True
        if not user.first_name and id_info.get("given_name"):
            user.first_name = id_info.get("given_name").strip()
            has_updates = True
        if not user.last_name and id_info.get("family_name"):
            user.last_name = id_info.get("family_name").strip()
            has_updates = True
        if has_updates:
            user.save(update_fields=["username", "first_name", "last_name"])

    login(request, user, backend=APP_AUTH_BACKEND)
    token, _ = Token.objects.get_or_create(user=user)
    return Response(
        {
            "token": token.key,
            "user": _serialize_user(user),
            "is_new_user": is_new_user,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
@authentication_classes([TokenAuthentication, SessionAuthentication])
@permission_classes([IsAuthenticated])
def auth_me_view(request):
    return Response({"user": _serialize_user(request.user)}, status=status.HTTP_200_OK)


@api_view(["PATCH", "POST"])
@authentication_classes([TokenAuthentication, SessionAuthentication])
@permission_classes([IsAuthenticated])
def auth_profile_update_view(request):
    user = request.user
    email = (request.data.get('email') or user.email or '').strip().lower()
    first_name = (request.data.get('first_name') or user.first_name or '').strip()
    last_name = (request.data.get('last_name') or user.last_name or '').strip()

    if not email:
        return Response({"error": "Email is required."}, status=status.HTTP_400_BAD_REQUEST)

    duplicate = User.objects.filter(email__iexact=email).exclude(id=user.id).exists()
    if duplicate:
        return Response({"error": "A user with this email already exists."}, status=status.HTTP_400_BAD_REQUEST)

    updated_fields = []
    if user.email != email:
        user.email = email
        user.username = email
        updated_fields.extend(["email", "username"])
    if user.first_name != first_name:
        user.first_name = first_name
        updated_fields.append("first_name")
    if user.last_name != last_name:
        user.last_name = last_name
        updated_fields.append("last_name")
    if updated_fields:
        user.save(update_fields=updated_fields)

    profile, _ = UserProfile.objects.get_or_create(user=user)
    if 'clear_profile_picture' in request.data and request.data.get('clear_profile_picture'):
        profile.profile_picture = None
        profile.save(update_fields=['profile_picture'])
    elif 'profile_picture' in request.data:
        image_payload = request.data.get('profile_picture')
        try:
            decoded = _decode_base64_binary(image_payload)
        except ValidationError as exc:
            return Response({"error": str(exc.detail)}, status=status.HTTP_400_BAD_REQUEST)
        if decoded is not None:
            profile.profile_picture = decoded
            profile.save(update_fields=['profile_picture'])

    return Response(
        {
            "message": "Profile updated.",
            "user": _serialize_user(user),
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@authentication_classes([TokenAuthentication, SessionAuthentication])
@permission_classes([IsAuthenticated])
def auth_password_change_view(request):
    user = request.user
    current_password = request.data.get('current_password') or ''
    new_password = request.data.get('new_password') or ''
    confirm_password = request.data.get('confirm_password') or ''

    if not current_password or not new_password or not confirm_password:
        return Response({"error": "All password fields are required."}, status=status.HTTP_400_BAD_REQUEST)

    if not user.check_password(current_password):
        return Response({"error": "Current password is incorrect."}, status=status.HTTP_400_BAD_REQUEST)

    if new_password != confirm_password:
        return Response({"error": "Passwords do not match."}, status=status.HTTP_400_BAD_REQUEST)

    try:
        validate_password(new_password, user=user)
    except DjangoValidationError as exc:
        return Response({"error": exc.messages}, status=status.HTTP_400_BAD_REQUEST)

    user.set_password(new_password)
    user.save(update_fields=['password'])

    Token.objects.filter(user=user).delete()
    token = Token.objects.create(user=user)
    login(request, user, backend=APP_AUTH_BACKEND)

    return Response(
        {
            "message": "Password updated.",
            "token": token.key,
            "user": _serialize_user(user),
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@authentication_classes([TokenAuthentication, SessionAuthentication])
@permission_classes([IsAuthenticated])
def auth_logout_view(request):
    Token.objects.filter(user=request.user).delete()
    logout(request)
    return Response({"message": "Logged out."}, status=status.HTTP_200_OK)

@api_view(['POST'])
@permission_classes([AllowAny])
def login_view(request):
    login_identifier = (request.data.get("email") or request.data.get("username") or "").strip().lower()
    password = request.data.get("password")
    user = authenticate(request, username=login_identifier, password=password)
    if user is not None:
        login(request, user)
        token, _ = Token.objects.get_or_create(user=user)
        return Response(
            {
                "token": token.key,
                "user": _serialize_user(user),
            },
            status=status.HTTP_200_OK,
        )
    return Response({"error": "Invalid credentials"}, status=status.HTTP_401_UNAUTHORIZED)


@api_view(["GET"])
@permission_classes([AllowAny])
def mobile_bridge_info_view(request):
    return Response(
        {
            "status": "ok",
            "endpoints": {
                "api_mobile_auth_complete": "/api/mobile/auth/complete/",
                "api_mobile_token_login": "/api/mobile/token-login/",
                "mobile_auth_complete": "/mobile/auth/complete/",
                "mobile_token_login": "/mobile/token-login/",
            },
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
@permission_classes([AllowAny])
def overpass_nearby_view(request):
    try:
        lat = float(request.GET.get('lat', '0'))
        lon = float(request.GET.get('lon', '0'))
    except (TypeError, ValueError):
        return Response({'error': 'lat/lon are required numeric query params.'}, status=status.HTTP_400_BAD_REQUEST)

    try:
        amenity_radius = max(500, min(int(request.GET.get('amenity_radius', '5000')), 15000))
    except ValueError:
        amenity_radius = 5000
    try:
        piste_radius = max(1000, min(int(request.GET.get('piste_radius', '7000')), 25000))
    except ValueError:
        piste_radius = 7000

    cache_key = f"overpass_nearby:{lat:.4f}:{lon:.4f}:{amenity_radius}:{piste_radius}"
    cached = cache.get(cache_key)
    if cached is not None:
        return Response(cached)

    overpass_query = f'''
        [out:json][timeout:20];
        (
          node["amenity"~"parking|toilets|restaurant|cafe"](around:{amenity_radius},{lat},{lon});
          way["piste:type"="downhill"](around:{piste_radius},{lat},{lon});
        );
        out body geom;
    '''.strip()

    request_body = urlencode({'data': overpass_query}).encode('utf-8')
    outgoing = Request(
        'https://overpass-api.de/api/interpreter',
        data=request_body,
        headers={
            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'User-Agent': 'GrenobleSki/1.0',
        },
    )

    try:
        with urlopen(outgoing, timeout=8) as response:
            payload = json.loads(response.read().decode('utf-8'))
    except (HTTPError, URLError, TimeoutError, ValueError):
        logger.warning('Overpass proxy failed for lat=%s lon=%s', lat, lon, exc_info=True)
        return Response({'elements': [], 'source': 'overpass', 'cached': False, 'error': 'overpass_unavailable'})

    elements = payload.get('elements') if isinstance(payload, dict) else []
    result = {
        'elements': elements if isinstance(elements, list) else [],
        'source': 'overpass',
        'cached': False,
    }
    cache.set(cache_key, result, timeout=60 * 15)
    return Response(result)

