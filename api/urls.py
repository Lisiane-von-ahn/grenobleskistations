from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import (
    SkiStationViewSet, BusLineViewSet, ServiceStoreViewSet, SkiCircuitViewSet,
    SkiMaterialListingViewSet, UserProfileViewSet, UserViewSet,
    auth_login_view, auth_logout_view, auth_me_view, auth_google_login_view,
    auth_register_view, login_view,
)
router = DefaultRouter()
router.register(r'skistations', SkiStationViewSet)
router.register(r'buslines', BusLineViewSet)
router.register(r'servicestores', ServiceStoreViewSet)
router.register(r'skicircuits', SkiCircuitViewSet)
router.register(r'skimaterial', SkiMaterialListingViewSet)
router.register(r'userprofile', UserProfileViewSet)
router.register(r'userview', UserViewSet)

urlpatterns = [
    path('auth/register/', auth_register_view, name='auth-register'),
    path('auth/login/', auth_login_view, name='auth-login'),
    path('auth/google-login/', auth_google_login_view, name='auth-google-login'),
    path('auth/me/', auth_me_view, name='auth-me'),
    path('auth/logout/', auth_logout_view, name='auth-logout'),
    path('login/', login_view, name='login'),  # Add the login endpoint manually
] + router.urls

