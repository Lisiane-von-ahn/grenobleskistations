from django.urls import path, include
from django.views.generic import RedirectView
from rest_framework.routers import DefaultRouter
from .views import (
    BusLineViewSet,
    InstructorProfileViewSet,
    InstructorReviewViewSet,
    InstructorServiceViewSet,
    MarketplaceDealViewSet,
    MarketplaceSavedFilterViewSet,
    MarketplaceUserRatingViewSet,
    MessageViewSet,
    PisteConditionReportViewSet,
    ServiceStoreViewSet,
    SkiCircuitViewSet,
    SkiMaterialListingViewSet,
    SkiPartnerPostViewSet,
    SkiPartnerReportViewSet,
    SkiStationViewSet,
    SkiStoryViewSet,
    SnowConditionUpdateViewSet,
    UserFriendViewSet,
    UserProfileViewSet,
    UserViewSet,
    auth_login_view, auth_logout_view, auth_me_view, auth_google_login_view,
    auth_register_view, login_view, mobile_bridge_info_view,
)
router = DefaultRouter()
router.register(r'skistations', SkiStationViewSet)
router.register(r'buslines', BusLineViewSet)
router.register(r'servicestores', ServiceStoreViewSet)
router.register(r'skicircuits', SkiCircuitViewSet)
router.register(r'skimaterial', SkiMaterialListingViewSet)
router.register(r'userprofile', UserProfileViewSet)
router.register(r'userview', UserViewSet)
router.register(r'messages', MessageViewSet, basename='messages')
router.register(r'snowupdates', SnowConditionUpdateViewSet, basename='snowupdates')
router.register(r'pistereports', PisteConditionReportViewSet, basename='pistereports')
router.register(r'instructorprofiles', InstructorProfileViewSet, basename='instructorprofiles')
router.register(r'instructorservices', InstructorServiceViewSet, basename='instructorservices')
router.register(r'instructorreviews', InstructorReviewViewSet, basename='instructorreviews')
router.register(r'skipartnerposts', SkiPartnerPostViewSet, basename='skipartnerposts')
router.register(r'skipartnerreports', SkiPartnerReportViewSet, basename='skipartnerreports')
router.register(r'skistories', SkiStoryViewSet, basename='skistories')
router.register(r'marketplace-saved-filters', MarketplaceSavedFilterViewSet, basename='marketplace-saved-filters')
router.register(r'marketplace-deals', MarketplaceDealViewSet, basename='marketplace-deals')
router.register(r'marketplace-ratings', MarketplaceUserRatingViewSet, basename='marketplace-ratings')
router.register(r'userfriends', UserFriendViewSet, basename='userfriends')

urlpatterns = [
    path('auth/register/', auth_register_view, name='auth-register'),
    path('auth/login/', auth_login_view, name='auth-login'),
    path('auth/google-login/', auth_google_login_view, name='auth-google-login'),
    path('auth/me/', auth_me_view, name='auth-me'),
    path('auth/logout/', auth_logout_view, name='auth-logout'),
    path('login/', login_view, name='login'),  # Add the login endpoint manually
    path('mobile/', mobile_bridge_info_view, name='api-mobile-bridge-info'),
    path(
        'mobile/auth/complete/',
        RedirectView.as_view(url='/mobile/auth/complete/', permanent=False, query_string=True),
        name='api-mobile-auth-complete',
    ),
    path(
        'mobile/auth/complete',
        RedirectView.as_view(url='/mobile/auth/complete/', permanent=False, query_string=True),
        name='api-mobile-auth-complete-noslash',
    ),
    path(
        'mobile/token-login/',
        RedirectView.as_view(url='/mobile/token-login/', permanent=False, query_string=True),
        name='api-mobile-token-login',
    ),
    path(
        'mobile/token-login',
        RedirectView.as_view(url='/mobile/token-login/', permanent=False, query_string=True),
        name='api-mobile-token-login-noslash',
    ),
] + router.urls

