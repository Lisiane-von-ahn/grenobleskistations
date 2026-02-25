from django.contrib import admin
from django.contrib.auth import views as auth_views
from django.urls import path, include
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
    path('terms/', views.terms_and_conditions, name='terms'),
    path('login/', auth_views.LoginView.as_view(template_name='account/login.html'), name='login'),
    path('logout/', auth_views.LogoutView.as_view(next_page='my_template_view'), name='logout'),
    path('register/', views.register, name='register'),
    path('accounts/', include('allauth.urls')),
    path('ski-material-listings/', views.ski_material_listings, name='ski_material_listings'),
    path('listing/<int:id>/', views.listing_detail, name='listing_detail'),
   path('listing/<int:id>/edit/', views.edit_listing, name='edit_listing'),
   path('listing/<int:id>/delete/', views.delete_listing, name='delete_listing'),
    path('messages/', views.messages_view, name='messages'),
   path('instructors/', views.instructors_list, name='instructors'),
   path('instructor/register/', views.become_instructor, name='become_instructor'),
   path('instructor/services/', views.instructor_services_view, name='instructor_services'),
   path('instructor/services/<int:service_id>/edit/', views.edit_instructor_service, name='edit_instructor_service'),
   path('instructor/services/<int:service_id>/delete/', views.delete_instructor_service, name='delete_instructor_service'),
    path('profile/', views.profile_view, name='profile'),
    path('delete_account/', views.delete_account, name='delete_account'),
]