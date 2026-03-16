from django.contrib import admin
from django.contrib.auth import views as auth_views
from django.urls import path, include
from django.views.generic import RedirectView
from drf_yasg.views import get_schema_view
from drf_yasg import openapi
from rest_framework import permissions
from . import views

schema_view = get_schema_view(
   openapi.Info(
      title="Ski Station API",
      default_version='v1',
      description="API for managing ski stations, bus lines, service stores, and ski circuits",
   ),
   public=True,
   permission_classes=(permissions.AllowAny,),
)

urlpatterns = [
   path('i18n/', include('django.conf.urls.i18n')),
   path('set-language/', views.set_language_view, name='set_language_safe'),
   path('ads.txt', views.ads_txt, name='ads_txt'),
    path('admin/', admin.site.urls, name='admin2'),
    path('api/', include('api.urls')),
    path('swagger/', schema_view.with_ui('swagger', cache_timeout=0), name='schema-swagger-ui'),
    path('', views.home, name='my_template_view'),
    path('ski-station/<int:station_id>/', views.ski_station_detail, name='ski_station_detail'),
   path('ski-station/<int:station_id>/snow-update/<int:update_id>/delete/', views.delete_snow_update, name='delete_snow_update'),
    path('search/', views.ski_station_search, name='ski_station_search'),
    path('services/', views.service_search, name='services'),
   path('services/<int:service_id>/', views.service_detail, name='service_detail'),
    path('bus/', views.bus_lines, name='bus'),
   path('ski-partners/', views.ski_partners, name='ski_partners'),
   path('ski-partners/new/', views.ski_partner_publish, name='ski_partner_publish'),
   path('ski-stories/', views.ski_stories, name='ski_stories'),
   path('ski-stories/<int:story_id>/delete/', views.delete_story, name='delete_story'),
    path('terms/', views.terms_and_conditions, name='terms'),
   path('privacy/', views.privacy_policy, name='privacy'),
   path('login/', RedirectView.as_view(pattern_name='account_login', permanent=False), name='login'),
    path('logout/', auth_views.LogoutView.as_view(next_page='my_template_view'), name='logout'),
   path('register/', RedirectView.as_view(pattern_name='account_signup', permanent=False), name='register'),
   path('password/reset/', RedirectView.as_view(pattern_name='account_reset_password', permanent=False), name='password_reset'),
   path('password/reset/done/', RedirectView.as_view(pattern_name='account_reset_password_done', permanent=False), name='password_reset_done'),
   path('mobile/auth/complete/', views.mobile_auth_complete, name='mobile_auth_complete'),
   path(
      'mobile/auth/complete',
      RedirectView.as_view(pattern_name='mobile_auth_complete', permanent=False, query_string=True),
      name='mobile_auth_complete_noslash',
   ),
   path('mobile/token-login/', views.mobile_token_login, name='mobile_token_login'),
   path(
      'mobile/token-login',
      RedirectView.as_view(pattern_name='mobile_token_login', permanent=False, query_string=True),
      name='mobile_token_login_noslash',
   ),
    path('accounts/', include('allauth.urls')),
    path('ski-material-listings/', views.ski_material_listings, name='ski_material_listings'),
    path('listing/<int:id>/', views.listing_detail, name='listing_detail'),
   path('listing/<int:id>/edit/', views.edit_listing, name='edit_listing'),
   path('listing/<int:id>/delete/', views.delete_listing, name='delete_listing'),
    path('messages/', views.messages_view, name='messages'),
   path('messages/search-users/', views.messages_user_search, name='messages_user_search'),
   path('messages/add-friend/', views.messages_add_friend, name='messages_add_friend'),
   path('messages/remove-friend/', views.messages_remove_friend, name='messages_remove_friend'),
   path('instructors/', views.instructors_list, name='instructors'),
   path('instructor/register/', views.become_instructor, name='become_instructor'),
   path('instructor/services/', views.instructor_services_view, name='instructor_services'),
   path('instructor/cancel/', views.cancel_instructor_profile, name='cancel_instructor_profile'),
   path('instructor/services/<int:service_id>/edit/', views.edit_instructor_service, name='edit_instructor_service'),
   path('instructor/services/<int:service_id>/delete/', views.delete_instructor_service, name='delete_instructor_service'),
    path('profile/', views.profile_view, name='profile'),
    path('delete_account/', views.delete_account, name='delete_account'),
    path('ajouter-materiel/', views.ajouter_materiel, name='ajouter_materiel'),
]