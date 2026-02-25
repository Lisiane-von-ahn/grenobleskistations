from django.contrib.auth.models import User
from django.contrib.auth import authenticate, login
from rest_framework import viewsets, status, status
from rest_framework.response import Response
from rest_framework.decorators import action, api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.authtoken.models import Token
from rest_framework.authtoken.views import ObtainAuthToken
from .models import (
    SkiStation,
    BusLine,
    ServiceStore,
    SkiCircuit,
    SkiMaterialListing,
    Message,
    UserProfile,
    InstructorProfile,
    InstructorService,
)
from .serializers import (SkiStationSerializer, BusLineSerializer, ServiceStoreSerializer, 
                          SkiCircuitSerializer, SkiMaterialListingSerializer, MessageSerializer, UserProfileSerializer,
                          InstructorProfileSerializer, InstructorServiceSerializer)
from rest_framework.exceptions import ValidationError


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
    queryset = Message.objects.all()
    serializer_class = MessageSerializer

    def get_queryset(self):
        user = self.request.user
        return Message.objects.filter(sender=user) | Message.objects.filter(recipient=user)

class UserProfileViewSet(viewsets.ModelViewSet):
    queryset = UserProfile.objects.all()
    serializer_class = UserProfileSerializer

    @action(detail=False, methods=['get'])
    def me(self, request):
        profile = UserProfile.objects.get(user=request.user)
        serializer = self.get_serializer(profile)
        return Response(serializer.data)


class InstructorProfileViewSet(viewsets.ModelViewSet):
    queryset = InstructorProfile.objects.all()
    serializer_class = InstructorProfileSerializer

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class InstructorServiceViewSet(viewsets.ModelViewSet):
    queryset = InstructorService.objects.all()
    serializer_class = InstructorServiceSerializer

    def perform_create(self, serializer):
        instructor = InstructorProfile.objects.get(user=self.request.user)
        serializer.save(instructor=instructor)
        
class UserViewSet(viewsets.ModelViewSet):
    queryset = User.objects.all()
    serializer_class = UserProfileSerializer

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

@api_view(['POST'])
@permission_classes([AllowAny])
def login_view(request):
    login_identifier = (request.data.get('email') or request.data.get('username') or '').strip().lower()
    password = request.data.get('password')
    user = authenticate(request, username=login_identifier, password=password)
    if user is not None:
        login(request, user)
        token, created = Token.objects.get_or_create(user=user)
        return Response({'token': token.key}, status=status.HTTP_200_OK)
    return Response({'error': 'Invalid credentials'}, status=status.HTTP_401_UNAUTHORIZED)

