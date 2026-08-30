from django.contrib import admin
from django import forms
from datetime import timedelta
from django.contrib.auth import get_user_model
from django.db.models import Count
from django.template.response import TemplateResponse
from django.urls import path
from django.utils import timezone
from .models import (
    AppUsageEvent,
    SkiStation,
    BusLine,
    ServiceStore,
    SkiCircuit,
    Message,
    UserProfile,
    SkiMaterialListing,
    SkiMaterialImage,
    SkiNewsItem,
    StationLiveStatus,
    StationOfficialSource,
    ModerationReport,
)

class SkiStationForm(forms.ModelForm):
    class Meta:
        model = SkiStation
        fields = '__all__'

    image_file = forms.ImageField(required=False)

    def clean_image_file(self):
        image = self.cleaned_data.get('image_file')
        if image:
            # Convert image file to binary data
            image_data = image.read()
            return image_data
        return None

    def save(self, commit=True):
        instance = super().save(commit=False)
        image_data = self.cleaned_data.get('image_file')
        if image_data:
            instance.image = image_data
        if commit:
            instance.save()
        return instance

class SkiStationAdmin(admin.ModelAdmin):
    form = SkiStationForm
    list_display = ('name', 'capacity', 'latitude', 'longitude')


@admin.register(AppUsageEvent)
class AppUsageEventAdmin(admin.ModelAdmin):
    list_display = ('created_at', 'feature_name', 'platform', 'method', 'status_code', 'user', 'path')
    list_filter = ('platform', 'is_api', 'feature_name', 'status_code', 'created_at')
    search_fields = ('feature_name', 'path', 'user__username', 'user__email')
    readonly_fields = ('created_at',)
    ordering = ('-created_at',)


def app_usage_dashboard_view(request):
    now = timezone.now()
    start_24h = now - timedelta(hours=24)
    start_30d = now - timedelta(days=30)

    user_model = get_user_model()
    total_users = user_model.objects.count()
    active_users_30d = user_model.objects.filter(last_login__gte=start_30d).count()

    events_24h = AppUsageEvent.objects.filter(created_at__gte=start_24h)
    events_30d = AppUsageEvent.objects.filter(created_at__gte=start_30d)

    top_features = list(
        events_30d.values('feature_name').annotate(total=Count('id')).order_by('-total')[:12]
    )
    top_paths = list(
        events_30d.values('path').annotate(total=Count('id')).order_by('-total')[:12]
    )
    platform_breakdown = list(
        events_30d.values('platform').annotate(total=Count('id')).order_by('-total')
    )

    context = {
        **admin.site.each_context(request),
        'title': 'App Usage Dashboard',
        'total_users': total_users,
        'active_users_30d': active_users_30d,
        'events_24h_count': events_24h.count(),
        'events_30d_count': events_30d.count(),
        'top_features': top_features,
        'top_paths': top_paths,
        'platform_breakdown': platform_breakdown,
    }
    return TemplateResponse(request, 'admin/app_usage_dashboard.html', context)


def _get_admin_urls(old_get_urls):
    def get_urls():
        custom_urls = [
            path('usage-dashboard/', admin.site.admin_view(app_usage_dashboard_view), name='app-usage-dashboard'),
        ]
        return custom_urls + old_get_urls()
    return get_urls


admin.site.get_urls = _get_admin_urls(admin.site.get_urls)


admin.site.register(SkiStation, SkiStationAdmin)
admin.site.register(BusLine)
admin.site.register(ServiceStore)
admin.site.register(SkiCircuit)
admin.site.register(Message)
admin.site.register(UserProfile)
admin.site.register(SkiMaterialListing)
admin.site.register(SkiMaterialImage)
admin.site.register(SkiNewsItem)
admin.site.register(StationLiveStatus)
admin.site.register(StationOfficialSource)

@admin.register(ModerationReport)
class ModerationReportAdmin(admin.ModelAdmin):
    list_display = ('target_type', 'target_id', 'reporter', 'status', 'created_at')
    list_filter = ('target_type', 'status', 'created_at')
    search_fields = ('reason', 'moderator_note', 'reporter__username')
    readonly_fields = ('reporter', 'target_type', 'target_id', 'reason', 'created_at')
