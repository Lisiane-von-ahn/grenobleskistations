import logging
import os

from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError as DjangoValidationError
from django.db.models import Q
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
    SkiMaterialListing,
    SkiPartnerPost,
    SkiPartnerReport,
    SkiStation,
    SkiStory,
    SnowConditionUpdate,
    UserFriend,
    UserProfile,
)
from .serializers import (
    BusLineSerializer,
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
    SkiPartnerPostSerializer,
    SkiPartnerReportSerializer,
    SkiStationSerializer,
    SkiStorySerializer,
    SnowConditionUpdateSerializer,
    UserFriendSerializer,
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


class SkiStationViewSet(viewsets.ModelViewSet):
    queryset = SkiStation.objects.all()
    serializer_class = SkiStationSerializer

class BusLineViewSet(viewsets.ModelViewSet):
    queryset = BusLine.objects.all()
    serializer_class = BusLineSerializer

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
        user_id = self.request.data.get('user')  # <- Prend l'id du POST envoyé
        if not user_id:
            raise ValidationError("User ID is required.")

        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            raise ValidationError("Invalid user ID.")

        serializer.save(user=user)
        
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

class UserProfileViewSet(viewsets.ModelViewSet):
    queryset = UserProfile.objects.all()
    serializer_class = UserProfileSerializer

    @action(detail=False, methods=['get'])
    def me(self, request):
        profile = UserProfile.objects.get(user=request.user)
        serializer = self.get_serializer(profile)
        return Response(serializer.data)

class UserViewSet(viewsets.ModelViewSet):
    queryset = User.objects.all()
    serializer_class = UserSerializer

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
        if self.action == 'list':
            return qs.filter(is_active=True)
        return qs

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


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
        serializer.save(rater=self.request.user)


class UserFriendViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated]
    serializer_class = UserFriendSerializer

    def get_queryset(self):
        user = _current_authenticated_user(self)
        if user is None:
            return UserFriend.objects.none()
        return UserFriend.objects.select_related('user', 'friend').filter(user=user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


def _serialize_user(user):
    profile = getattr(user, "profile", None)
    return {
        "id": user.id,
        "email": user.email,
        "username": user.username,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "has_profile_picture": bool(profile and profile.profile_picture),
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

    if not email or not password:
        return Response(
            {"error": "Email and password are required."},
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

