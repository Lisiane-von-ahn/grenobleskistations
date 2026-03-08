from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import (
    SkiStationViewSet, BusLineViewSet, ServiceStoreViewSet, SkiCircuitViewSet,
    SkiMaterialListingViewSet, UserProfileViewSet, UserViewSet, login_view,
    InstructorProfileViewSet, InstructorServiceViewSet
)
router = DefaultRouter()
router.register(r'skistations', SkiStationViewSet)
router.register(r'buslines', BusLineViewSet)
router.register(r'servicestores', ServiceStoreViewSet)
router.register(r'skicircuits', SkiCircuitViewSet)
router.register(r'skimaterial', SkiMaterialListingViewSet)
router.register(r'userprofile', UserProfileViewSet)
router.register(r'userview', UserViewSet)
router.register(r'instructors', InstructorProfileViewSet)
router.register(r'instructor-services', InstructorServiceViewSet)

urlpatterns = [
    path('login/', login_view, name='login'),  # Add the login endpoint manually
] + router.urls

