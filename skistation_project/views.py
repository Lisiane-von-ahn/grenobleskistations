from django.shortcuts import render, get_object_or_404, redirect
from django.http import HttpResponse, HttpResponseRedirect, JsonResponse
from django.views.decorators.http import require_GET
from django.conf import settings
from django.contrib.auth import login
from api.models import (
    SkiStation,
    BusLine,
    ServiceStore,
    SkiCircuit,
    SkiMaterialListing,
    PisteConditionReport,
    SnowConditionUpdate,
    CrowdStatusUpdate,
    Message,
    MarketplaceSavedFilter,
    MarketplaceDeal,
    MarketplaceUserRating,
    SkiPartnerPost,
    SkiPartnerReport,
    SkiStory,
    UserProfile,
    UserFriend,
    InstructorProfile,
    InstructorService,
)
from django.db.models import Sum
from django.db.models import Q
from django.db.models import Avg
from django.db.models import Count
from django.db.models import Min
from django.db.models import Value
from django.db.models import IntegerField
from django.urls import reverse
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.models import User
from rest_framework.authtoken.models import Token
from django.core.paginator import Paginator
from .forms import (
    SkiMaterialListingForm,
    ProfileForm,
    PisteConditionReportForm,
    SnowConditionUpdateForm,
    InstructorProfileForm,
    InstructorServiceForm,
    get_marketplace_choices,
)
from allauth.socialaccount.providers.google.views import OAuth2LoginView
from allauth.socialaccount.providers.google.views import OAuth2CallbackView
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter
from allauth.account.adapter import DefaultAccountAdapter
from django.contrib.auth.decorators import login_required
from django.contrib import messages
import base64
from html import escape
import json
import os
from datetime import timedelta, date
from urllib.parse import quote_plus, urlencode
import re
from django.utils.translation import check_for_language
from django.utils import translation
from django.utils.translation import gettext as _
from django.utils.http import url_has_allowed_host_and_scheme
from django.utils import timezone
from allauth.socialaccount.models import SocialAccount


PARTNER_POST_ACTIVE_LIMIT = 5
PARTNER_POST_COOLDOWN_MINUTES = 5


def _mask_sensitive_contact_data(text):
    if not text:
        return text
    # Hide direct contact details in public posts to keep messaging inside platform chat.
    value = re.sub(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', '[email masque]', text)
    value = re.sub(r'(?:\+?\d[\d\s().-]{7,}\d)', '[telephone masque]', value)
    return value


def _partner_organizer_display(user):
    first = (user.first_name or '').strip()
    last = (user.last_name or '').strip()
    if first or last:
        return f"{first} {last}".strip()
    return user.username


def home(request):
    queryset = SkiStation.objects.annotate(
        num_circuits=Sum('skicircuit__num_pistes'),
        service_count=Count('servicestore', distinct=True),
        instructor_service_count=Value(0, output_field=IntegerField()),
    )

    print(queryset.query)  # This will print the SQL query in the console
    for station in queryset:
        print(f"{station.name}: {station.num_circuits} circuits")

    random_ski_stations = queryset.order_by('?')
    nearest_stations = queryset.order_by('distanceFromGrenoble', 'name')[:5]
    map_stations = queryset.order_by('distanceFromGrenoble', 'name')

    return render(
        request,
        'index.html',
        {
            'ski_stations': random_ski_stations,
            'all': queryset,
            'nearest_stations': nearest_stations,
            'map_stations': map_stations,
        },
    )


def ski_station_detail(request, station_id):
    ski_station = get_object_or_404(
        SkiStation.objects.annotate(num_circuits=Sum('skicircuit__num_pistes')),
        id=station_id,
    )
    bus_lines = BusLine.objects.filter(ski_station=ski_station)
    service_stores = ServiceStore.objects.filter(ski_station=ski_station)
    ski_circuits = SkiCircuit.objects.filter(ski_station=ski_station)

    trend = request.GET.get('trend', '24h')
    if trend not in ('3h', '24h'):
        trend = '24h'
    window_delta = timedelta(hours=3 if trend == '3h' else 24)
    window_start = timezone.now() - window_delta

    if request.method == 'POST' and request.user.is_authenticated:
        form_type = request.POST.get('form_type', '').strip()

        if form_type == 'piste_report':
            piste_form_post = PisteConditionReportForm(request.POST)
            if piste_form_post.is_valid():
                PisteConditionReport.objects.update_or_create(
                    ski_station=ski_station,
                    user=request.user,
                    defaults={
                        'piste_rating': piste_form_post.cleaned_data['piste_rating'],
                        'comment': piste_form_post.cleaned_data.get('comment', '').strip(),
                        'crowd_level': PisteConditionReport.CROWD_NORMAL,
                    },
                )
                messages.success(request, _('Avis piste enregistre.'))
            else:
                messages.error(request, _('Veuillez verifier le formulaire piste.'))
            return redirect(f"{reverse('ski_station_detail', args=[station_id])}?trend={trend}")

        if form_type == 'crowd_update':
            crowd_level = request.POST.get('crowd_level', '').strip()
            valid_levels = {choice[0] for choice in PisteConditionReport.CROWD_CHOICES}
            if crowd_level in valid_levels:
                CrowdStatusUpdate.objects.create(
                    ski_station=ski_station,
                    user=request.user,
                    crowd_level=crowd_level,
                )
                messages.success(request, _('Affluence mise a jour.'))
            else:
                messages.error(request, _('Valeur d\'affluence invalide.'))
            return redirect(f"{reverse('ski_station_detail', args=[station_id])}?trend={trend}")

        if form_type == 'snow_update':
            snow_form_post = SnowConditionUpdateForm(request.POST, request.FILES)
            if snow_form_post.is_valid():
                update = snow_form_post.save(commit=False)
                update.ski_station = ski_station
                update.user = request.user
                image_file = snow_form_post.cleaned_data.get('image_file')
                if image_file:
                    update.image = image_file.read()
                update.save()
                messages.success(request, _('Actualisation neige publiee.'))
            else:
                messages.error(request, _('Veuillez verifier le formulaire neige.'))
            return redirect(f"{reverse('ski_station_detail', args=[station_id])}?trend={trend}")

    user_piste_report = None
    if request.user.is_authenticated:
        user_piste_report = PisteConditionReport.objects.filter(
            ski_station=ski_station,
            user=request.user,
        ).first()

    piste_form = PisteConditionReportForm(instance=user_piste_report)
    snow_form = SnowConditionUpdateForm()

    snow_updates = SnowConditionUpdate.objects.filter(ski_station=ski_station).select_related('user').order_by('-created_at')[:20]
    piste_reports = PisteConditionReport.objects.filter(ski_station=ski_station).select_related('user').order_by('-created_at')[:20]
    window_reports = PisteConditionReport.objects.filter(
        ski_station=ski_station,
        created_at__gte=window_start,
    ).order_by('created_at')

    crowd_to_value = {
        PisteConditionReport.CROWD_QUIET: 1,
        PisteConditionReport.CROWD_NORMAL: 2,
        PisteConditionReport.CROWD_BUSY: 3,
    }

    chart_labels = [report.created_at.strftime('%H:%M') for report in window_reports]
    chart_reports = [1 for _ in window_reports]
    chart_ratings = [report.piste_rating for report in window_reports]
    chart_crowd = [crowd_to_value.get(report.crowd_level, 2) for report in window_reports]

    avg_rating = PisteConditionReport.objects.filter(ski_station=ski_station).aggregate(avg=Avg('piste_rating')).get('avg')
    report_count = PisteConditionReport.objects.filter(ski_station=ski_station).count()

    last_crowd = CrowdStatusUpdate.objects.filter(ski_station=ski_station).order_by('-created_at').first()
    crowd_display_map = {
        PisteConditionReport.CROWD_QUIET: _('Peu'),
        PisteConditionReport.CROWD_NORMAL: _('Agreable'),
        PisteConditionReport.CROWD_BUSY: _('Bonde'),
    }
    last_crowd_key = last_crowd.crowd_level if last_crowd else None
    last_crowd_label = crowd_display_map.get(last_crowd_key)

    destination = f"{ski_station.latitude},{ski_station.longitude}"
    station_transit_url = f"https://www.google.com/maps/dir/?api=1&destination={destination}&travelmode=transit"
    station_driving_url = f"https://www.google.com/maps/dir/?api=1&destination={destination}&travelmode=driving"

    context = {
        'station': ski_station,
        'bus_lines': bus_lines,
        'service_stores': service_stores,
        'ski_circuits': ski_circuits,
        'station_transit_url': station_transit_url,
        'station_driving_url': station_driving_url,
        'piste_form': piste_form,
        'snow_form': snow_form,
        'piste_reports': piste_reports,
        'snow_updates': snow_updates,
        'piste_average_rating': avg_rating,
        'piste_average_rating_rounded': int(round(avg_rating)) if avg_rating else 0,
        'piste_report_count': report_count,
        'piste_last_crowd': last_crowd_label,
        'piste_last_crowd_key': last_crowd_key,
        'piste_window_label': _('3 heures') if trend == '3h' else _('24 heures'),
        'piste_trend': trend,
        'piste_chart_labels': json.dumps(chart_labels),
        'piste_chart_reports': json.dumps(chart_reports),
        'piste_chart_ratings': json.dumps(chart_ratings),
        'piste_chart_crowd': json.dumps(chart_crowd),
    }

    return render(request, 'details.html', context)


def ski_station_search(request):
    query = SkiStation.objects.all()
    
    # Filters
    capacity = request.GET.get('capacity')
    distance = request.GET.get('distance')
    altitude = request.GET.get('altitude')
    
    if capacity:
        query = query.filter(capacity__gte=capacity)
    if distance:
        query = query.filter(distanceFromGrenoble__lte=distance)
    if altitude:
        query = query.filter(altitude__gte=altitude)
    
    context = {
        'ski_stations': query,
    }
    
    return render(request, 'search.html', context)


def service_search(request):
    ski_stations = SkiStation.objects.order_by('name')

    search_query = request.GET.get('q', '').strip()
    service_type = request.GET.get('type', '').strip()
    selected_service_category = request.GET.get('service_category', '').strip()
    ski_station_id = request.GET.get('ski_station', '').strip()
    my_instructor_offers = request.GET.get('my_instructor_offers', '').strip()

    service_types = list(ServiceStore.objects.values_list('type', flat=True).distinct().order_by('type'))
    # Fixed category display order
    SERVICE_CATEGORIES_ORDER = ['Information', 'École de ski', 'Magasin / Location', 'Restaurant', 'Secours']
    stored_categories = set(service_types)
    service_categories = [c for c in SERVICE_CATEGORIES_ORDER if c in stored_categories]
    # append any extra categories not in the fixed list
    for c in sorted(stored_categories):
        if c not in service_categories:
            service_categories.append(c)

    services_qs = ServiceStore.objects.select_related('ski_station').order_by('ski_station__name', 'type', 'name')

    if search_query:
        services_qs = services_qs.filter(
            Q(name__icontains=search_query)
            | Q(type__icontains=search_query)
            | Q(address__icontains=search_query)
            | Q(ski_station__name__icontains=search_query)
        )
    if service_type:
        services_qs = services_qs.filter(type=service_type)
    if selected_service_category:
        services_qs = services_qs.filter(type=selected_service_category)
    if ski_station_id:
        services_qs = services_qs.filter(ski_station_id=ski_station_id)

    selected_station = None
    if ski_station_id:
        selected_station = SkiStation.objects.filter(id=ski_station_id).first()

    # Annotate each service with computed display attributes
    services_list = list(services_qs)
    for svc in services_list:
        address = (svc.address or '').strip()
        svc.address_display = address or 'Adresse non renseignée'
        if svc.website_url:
            svc.preferred_url = svc.website_url
        else:
            addr_query = quote_plus(f"{svc.name} {address}")
            svc.preferred_url = f"https://www.google.com/search?q={addr_query}"
        lat = float(svc.latitude)
        lng = float(svc.longitude)
        svc.google_maps_url = f"https://www.google.com/maps/search/?api=1&query={lat},{lng}"
        svc.search_url = f"https://www.google.com/search?q={quote_plus(svc.name + ' ' + (svc.ski_station.name if svc.ski_station else ''))}"
        svc.category_label = svc.type or 'Service'

    # Station service stats table
    all_stations_qs = SkiStation.objects.order_by('name')
    station_service_stats = []
    for station in all_stations_qs:
        counts_by_cat = {
            row['type']: row['cnt']
            for row in ServiceStore.objects.filter(ski_station=station).values('type').annotate(cnt=Count('id'))
        }
        total = sum(counts_by_cat.values())
        if total == 0:
            continue
        ordered_counts = [counts_by_cat.get(cat, 0) for cat in service_categories]
        station_service_stats.append({
            'station': station,
            'ordered_counts': ordered_counts,
            'total': total,
        })
    station_service_stats.sort(key=lambda r: r['total'], reverse=True)

    # Instructor services
    instructor_services_qs = (
        InstructorService.objects.select_related('instructor', 'instructor__user', 'ski_station')
        .filter(is_active=True)
    )
    if search_query:
        instructor_services_qs = instructor_services_qs.filter(
            Q(title__icontains=search_query)
            | Q(description__icontains=search_query)
            | Q(ski_station__name__icontains=search_query)
            | Q(instructor__user__username__icontains=search_query)
        )
    if ski_station_id:
        instructor_services_qs = instructor_services_qs.filter(ski_station_id=ski_station_id)
    if my_instructor_offers and request.user.is_authenticated:
        instructor_services_qs = instructor_services_qs.filter(instructor__user=request.user)

    context = {
        'services': services_list,
        'service_types': service_types,
        'service_categories': service_categories,
        'ski_stations': ski_stations,
        'search_query': search_query,
        'selected_service_category': selected_service_category,
        'selected_station': selected_station,
        'station_service_stats': station_service_stats,
        'instructor_services': list(instructor_services_qs[:60]),
    }

    return render(request, 'services.html', context)

def bus_lines(request):
    def _extract_url(text):
        if not text:
            return None
        match = re.search(r'https?://\S+', str(text))
        return match.group(0) if match else None

    def _line_url(line):
        # Try explicit URL fields first when available in the model instance.
        for field_name in ('website_url', 'line_url', 'info_url', 'url'):
            value = getattr(line, field_name, None)
            if value:
                return value

        # Try extracting a URL embedded in textual fields.
        for text_field in ('route_points', 'frequency', 'travel_time'):
            embedded = _extract_url(getattr(line, text_field, None))
            if embedded:
                return embedded

        # Fallback: search the official mobility provider with line + destination.
        query = quote_plus(f"metromobilite ligne {line.bus_number} {line.arrival_stop or ''}")
        return f"https://www.google.com/search?q={query}"

    all_lines = BusLine.objects.select_related('ski_station').all().order_by('bus_number')
    stations = SkiStation.objects.order_by('name')

    departure = request.GET.get('departure', '').strip() or 'Grenoble Gare Routiere'
    station_id = request.GET.get('station', '').strip()
    schedule = request.GET.get('schedule', '').strip().lower()

    lines = all_lines
    selected_station = None

    if station_id:
        try:
            selected_station = stations.get(id=station_id)
            lines = lines.filter(ski_station=selected_station)
        except SkiStation.DoesNotExist:
            selected_station = None

    if schedule == 'weekday':
        lines = lines.exclude(frequency__icontains='week-end').exclude(frequency__icontains='weekend')
    elif schedule == 'weekend':
        lines = lines.filter(Q(frequency__icontains='week-end') | Q(frequency__icontains='weekend'))

    bus_lines_data = []
    bus_map_points = []
    for line in lines:
        line_url = _line_url(line)
        item = {
            'id': line.id,
            'bus_number': line.bus_number,
            'departure_stop': line.departure_stop,
            'arrival_stop': line.arrival_stop,
            'frequency': line.frequency,
            'travel_time': line.travel_time,
            'route_points': getattr(line, 'route_points', ''),
            'ski_station': line.ski_station,
            'line_url': line_url,
        }
        bus_lines_data.append(item)

        dep_lat = getattr(line, 'departure_latitude', None)
        dep_lng = getattr(line, 'departure_longitude', None)
        if dep_lat is not None and dep_lng is not None:
            bus_map_points.append(
                {
                    'line': line.bus_number,
                    'station_name': line.ski_station.name if line.ski_station else '',
                    'departure_stop': line.departure_stop,
                    'route_points': getattr(line, 'route_points', ''),
                    'lat': dep_lat,
                    'lng': dep_lng,
                }
            )

    transit_url = None
    driving_url = None
    if selected_station:
        destination = f"{selected_station.latitude},{selected_station.longitude}"
        origin = quote_plus(departure)
        transit_url = f"https://www.google.com/maps/dir/?api=1&origin={origin}&destination={destination}&travelmode=transit"
        driving_url = f"https://www.google.com/maps/dir/?api=1&origin={origin}&destination={destination}&travelmode=driving"

    context = {
        'bus_lines': bus_lines_data,
        'stations': stations,
        'selected_station': selected_station,
        'departure': departure,
        'schedule': schedule,
        'transit_url': transit_url,
        'driving_url': driving_url,
        'bus_map_points': bus_map_points,
    }

    return render(request, 'bus.html', context)

def terms_and_conditions(request):
    return render(request, 'terms.html')


def privacy_policy(request):
    return render(request, 'privacy.html')


@require_GET
def ads_txt(request):
    content = (settings.ADS_TXT_CONTENT or '').strip()
    if not content:
        # Keep endpoint present even before publisher data is configured.
        content = '# ADS_TXT_CONTENT not configured'
    lines = [line.rstrip() for line in content.splitlines() if line.strip()]
    body = '\n'.join(lines) + '\n'
    return HttpResponse(body, content_type='text/plain; charset=utf-8')

def register(request):
    if request.method == 'POST':
        form = UserCreationForm(request.POST, request.FILES)
        if form.is_valid():
            form.save()
            return redirect('login')  # Redirect to login after registration
    else:
        form = UserCreationForm()

    return render(request, 'register.html', {'form': form})

class CustomGoogleLoginView(OAuth2LoginView):
    def get(self, request, *args, **kwargs):
        adapter = DefaultSocialAccountAdapter()
        return super().get(request, *args, **kwargs)
    
class CustomGoogleCallbackView(OAuth2CallbackView):
    def get(self, request, *args, **kwargs):
        # Custom logic, if needed
        return super().get(request, *args, **kwargs)    
    
class CustomAccountAdapter(DefaultAccountAdapter):
    def save_user(self, request, user, form, commit=True):
        # Set the username to the email
        print("teste");
        user.username = user.email
        return super().save_user(request, user, form, commit)


@require_GET
def set_language_view(request):
    lang_code = request.GET.get('language', '').strip()
    next_url = request.GET.get('next', '/')
    if not url_has_allowed_host_and_scheme(next_url, allowed_hosts={request.get_host()}, require_https=request.is_secure()):
        next_url = '/'

    response = HttpResponseRedirect(next_url)
    if check_for_language(lang_code):
        translation.activate(lang_code)
        request.session[settings.LANGUAGE_COOKIE_NAME] = lang_code
        response.set_cookie(settings.LANGUAGE_COOKIE_NAME, lang_code)
    return response


@login_required(login_url='account_login')
def mobile_auth_complete(request):
    token, _ = Token.objects.get_or_create(user=request.user)

    display_name = f"{(request.user.first_name or '').strip()} {(request.user.last_name or '').strip()}".strip()
    if not display_name:
        display_name = (request.user.email or request.user.username or '').strip()

    app_redirect_base = os.getenv('MOBILE_APP_AUTH_REDIRECT', 'grenobleski://auth').strip() or 'grenobleski://auth'
    separator = '&' if '?' in app_redirect_base else '?'
    payload = urlencode(
        {
            'token': token.key,
            'email': request.user.email or '',
            'name': display_name,
        }
    )
    deep_link_url = f"{app_redirect_base}{separator}{payload}"

    if app_redirect_base.startswith(('http://', 'https://')):
        response = HttpResponse(status=302)
        response['Location'] = deep_link_url
        return response

    escaped_url = escape(deep_link_url, quote=True)
    return HttpResponse(
        f"""<!doctype html>
<html lang=\"en\">
    <head>
        <meta charset=\"utf-8\">
        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
        <meta http-equiv=\"refresh\" content=\"0;url={escaped_url}\">
        <title>Open GrenobleSki</title>
        <style>
            body {{ font-family: Arial, sans-serif; background: #f5f7fb; color: #172033; margin: 0; }}
            main {{ max-width: 32rem; margin: 10vh auto; background: #fff; padding: 2rem; border-radius: 1rem; box-shadow: 0 20px 40px rgba(23, 32, 51, 0.12); }}
            a {{ display: inline-block; margin-top: 1rem; padding: 0.9rem 1.2rem; background: #0f766e; color: #fff; text-decoration: none; border-radius: 0.75rem; font-weight: 600; }}
            p {{ line-height: 1.5; }}
        </style>
    </head>
    <body>
        <main>
            <h1>Open GrenobleSki</h1>
            <p>If the app does not open automatically, tap the button below.</p>
            <a href=\"{escaped_url}\">Open the app</a>
        </main>
        <script>
            const target = {deep_link_url!r};
            window.location.replace(target);
            setTimeout(() => window.location.href = target, 700);
        </script>
    </body>
</html>""",
        content_type='text/html; charset=utf-8',
    )


@require_GET
def mobile_token_login(request):
    token_value = (request.GET.get('token') or '').strip()
    next_url = (request.GET.get('next') or '/').strip() or '/'

    if not url_has_allowed_host_and_scheme(
        next_url,
        allowed_hosts={request.get_host()},
        require_https=request.is_secure(),
    ) and not next_url.startswith('/'):
        next_url = '/'

    if not token_value:
        return redirect(f"{reverse('account_login')}?next={quote_plus(next_url)}")

    token = Token.objects.select_related('user').filter(key=token_value).first()
    if token is None:
        return redirect(f"{reverse('account_login')}?next={quote_plus(next_url)}")

    login(request, token.user, backend='skistation_project.backends.EmailOrUsernameModelBackend')
    return redirect(next_url)
    
@login_required(login_url='login')
def ski_material_listings(request):
    saved_filters_qs = MarketplaceSavedFilter.objects.filter(user=request.user).order_by('-updated_at')
    max_saved_filters = 10

    if request.method == 'POST' and request.POST.get('filter_action'):
        filter_action = request.POST.get('filter_action', '').strip()
        filter_query = request.POST.get('filter_query', '').strip()

        if filter_action == 'save':
            filter_name = request.POST.get('filter_name', '').strip()
            if not filter_name:
                messages.error(request, _('Nom de filtre requis.'))
            else:
                MarketplaceSavedFilter.objects.update_or_create(
                    user=request.user,
                    name=filter_name[:40],
                    defaults={'query': filter_query[:500]},
                )

                # Keep only the most recent N saved filters per user.
                stale_ids = list(
                    MarketplaceSavedFilter.objects.filter(user=request.user)
                    .order_by('-updated_at')
                    .values_list('id', flat=True)[max_saved_filters:]
                )
                if stale_ids:
                    MarketplaceSavedFilter.objects.filter(id__in=stale_ids).delete()

                messages.success(request, _('Filtre enregistre.'))

        if filter_action == 'delete':
            filter_name = request.POST.get('filter_name', '').strip()
            MarketplaceSavedFilter.objects.filter(user=request.user, name=filter_name).delete()
            messages.success(request, _('Filtre supprime.'))

        redirect_query = f"?{filter_query}" if filter_query else ''
        return redirect(f"{reverse('ski_material_listings')}{redirect_query}")

    base_queryset = SkiMaterialListing.objects.select_related('user', 'ski_station').prefetch_related('images').order_by('-posted_at')
    filtered_listings = base_queryset

    preset = request.GET.get('preset', '').strip().lower()
    q = request.GET.get('q', '').strip()
    transaction_type = request.GET.get('transaction_type', '').strip()
    material_type = request.GET.get('material_type', '').strip()
    condition = request.GET.get('condition', '').strip()
    city = request.GET.get('city', '').strip()
    min_price = request.GET.get('min_price', '').strip()
    max_price = request.GET.get('max_price', '').strip()
    mine_only = request.GET.get('my_listings', '').strip().lower() in ('1', 'true', 'yes')
    sort = request.GET.get('sort', 'recent').strip() or 'recent'
    per_page_raw = request.GET.get('per_page', '9').strip()

    # Fast presets for common marketplace journeys on mobile and desktop.
    if preset == 'weekend':
        if not transaction_type:
            transaction_type = 'rent'
        if not max_price:
            max_price = '120'
        if 'sort' not in request.GET:
            sort = 'price_asc'
    elif preset == 'budget':
        if not max_price:
            max_price = '200'
        if condition == '':
            condition = 'good'
        if 'sort' not in request.GET:
            sort = 'price_asc'
    elif preset == 'premium':
        if not condition:
            condition = 'excellent'
        if 'sort' not in request.GET:
            sort = 'price_desc'
    elif preset == 'safety':
        if not material_type:
            material_type = 'helmet'
        if not transaction_type:
            transaction_type = 'rent'

    if q:
        filtered_listings = filtered_listings.filter(
            Q(title__icontains=q)
            | Q(description__icontains=q)
            | Q(city__icontains=q)
            | Q(brand__icontains=q)
            | Q(size__icontains=q)
            | Q(user__username__icontains=q)
            | Q(ski_station__name__icontains=q)
        )
    if transaction_type:
        filtered_listings = filtered_listings.filter(transaction_type=transaction_type)
    if material_type:
        filtered_listings = filtered_listings.filter(material_type=material_type)
    if condition:
        filtered_listings = filtered_listings.filter(condition=condition)
    if city:
        filtered_listings = filtered_listings.filter(city__icontains=city)
    if min_price:
        filtered_listings = filtered_listings.filter(price__gte=min_price)
    if max_price:
        filtered_listings = filtered_listings.filter(price__lte=max_price)

    if request.method == 'POST':
        form = SkiMaterialListingForm(request.POST, request.FILES)
        if form.is_valid():
            listing = form.save(commit=False)
            listing.user = request.user  # Set the current user as the listing creator
            listing.save()

            uploaded_images = request.FILES.getlist('images')
            if not listing.image and uploaded_images:
                first_image = uploaded_images[0].read()
                if first_image:
                    listing.image = first_image
                    listing.save(update_fields=['image'])
                uploaded_images[0].seek(0)
            form.save_extra_images(listing, uploaded_images)

            return redirect('ski_material_listings')
    else:
        form = SkiMaterialListingForm()

    choice_labels = get_marketplace_choices(translation.get_language())

    sort_choices = {
        'recent': '-posted_at',
        'oldest': 'posted_at',
        'price_asc': 'price',
        'price_desc': '-price',
        'title_asc': 'title',
    }
    if sort not in sort_choices:
        sort = 'recent'

    try:
        per_page = int(per_page_raw)
    except (TypeError, ValueError):
        per_page = 9
    if per_page not in (9, 18, 30):
        per_page = 9

    my_filtered_qs = filtered_listings.filter(user=request.user)
    marketplace_filtered_qs = filtered_listings.exclude(user=request.user)

    visible_queryset = my_filtered_qs if mine_only else marketplace_filtered_qs
    visible_queryset = visible_queryset.order_by(sort_choices[sort], '-id')

    paginator = Paginator(visible_queryset, per_page)
    page_obj = paginator.get_page(request.GET.get('page'))

    seller_ids = {item.user_id for item in page_obj.object_list}
    seller_rating_map = {
        row['rated_user']: {
            'avg': row['avg'],
            'count': row['count'],
        }
        for row in MarketplaceUserRating.objects.filter(rated_user_id__in=seller_ids)
        .values('rated_user')
        .annotate(avg=Avg('score'), count=Count('id'))
    }
    for item in page_obj.object_list:
        stats = seller_rating_map.get(item.user_id)
        item.seller_rating_avg = stats['avg'] if stats else None
        item.seller_rating_count = stats['count'] if stats else 0

    query_params = request.GET.copy()
    query_params.pop('page', None)
    pagination_query = query_params.urlencode()

    transaction_totals = {
        item['transaction_type']: item['total']
        for item in filtered_listings.values('transaction_type').annotate(total=Count('id'))
    }
    material_totals = {
        item['material_type']: item['total']
        for item in filtered_listings.values('material_type').annotate(total=Count('id'))
    }

    transaction_stats = [
        {
            'code': code,
            'label': label,
            'total': transaction_totals.get(code, 0),
        }
        for code, label in choice_labels['transaction_type']
    ]
    material_stats = [
        {
            'code': code,
            'label': label,
            'total': material_totals.get(code, 0),
        }
        for code, label in choice_labels['material_type']
    ]

    visible_total_count = visible_queryset.count()
    facilities = {
        'with_photo_count': filtered_listings.exclude(image__isnull=True).count(),
        'with_station_count': filtered_listings.exclude(ski_station__isnull=True).count(),
        'mine_count': my_filtered_qs.count(),
        'market_count': marketplace_filtered_qs.count(),
    }

    return render(
        request,
        'ski_material_listings.html',
        {
            'form': form,
            'listings': filtered_listings,
            'listings_page': page_obj,
            'mine_only': mine_only,
            'visible_total_count': visible_total_count,
            'selected_transaction_type': transaction_type,
            'selected_material_type': material_type,
            'selected_condition': condition,
            'query_text': q,
            'preset': preset,
            'sort': sort,
            'per_page': per_page,
            'min_price': min_price,
            'max_price': max_price,
            'city': city,
            'pagination_query': pagination_query,
            'current_filter_query': pagination_query,
            'saved_filters': saved_filters_qs,
            'saved_filters_limit': max_saved_filters,
            'transaction_stats': transaction_stats,
            'material_stats': material_stats,
            'facilities': facilities,
            'material_choices': choice_labels['material_type'],
            'condition_choices': choice_labels['condition'],
            'transaction_choices': choice_labels['transaction_type'],
        },
    )

def listing_detail(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id)
    gallery_images = listing.images.all()
    seller_pending_deals = MarketplaceDeal.objects.none()

    if request.method == 'POST' and request.user.is_authenticated and request.user != listing.user:
        form_type = request.POST.get('form_type', '').strip()

        # Backward compatibility: legacy templates/tests post only `body` without form_type.
        if not form_type and (request.POST.get('body') or '').strip():
            form_type = 'contact'

        if form_type == 'contact':
            body = request.POST.get('body', '').strip()
            if body:
                Message.objects.create(
                    sender=request.user,
                    recipient=listing.user,
                    subject=f"A propos de: {listing.title}",
                    body=body,
                )
                messages.success(request, 'Message envoye au vendeur.')
            return redirect('listing_detail', id=listing.id)

        if form_type == 'deal_request':
            MarketplaceDeal.objects.get_or_create(
                listing=listing,
                buyer=request.user,
                defaults={'seller': listing.user},
            )
            messages.success(request, 'Demande de transaction envoyee.')
            return redirect('listing_detail', id=listing.id)

        if form_type == 'deal_confirm_buyer':
            deal, _ = MarketplaceDeal.objects.get_or_create(
                listing=listing,
                buyer=request.user,
                defaults={'seller': listing.user},
            )
            deal.buyer_confirmed = True
            deal.save(update_fields=['buyer_confirmed', 'updated_at'])
            messages.success(request, 'Vous avez confirme la transaction.')
            return redirect('listing_detail', id=listing.id)

        if form_type == 'rating':
            try:
                score = int(request.POST.get('score', '').strip())
            except (TypeError, ValueError):
                score = 0
            comment = request.POST.get('comment', '').strip()
            verified_deal = MarketplaceDeal.objects.filter(
                listing=listing,
                buyer=request.user,
                seller=listing.user,
                buyer_confirmed=True,
                seller_confirmed=True,
            ).exists()
            if not verified_deal:
                messages.error(request, 'La note est disponible uniquement pour une transaction confirmee par les deux parties.')
            elif score not in (1, 2, 3, 4, 5):
                messages.error(request, 'Note invalide.')
            else:
                MarketplaceUserRating.objects.update_or_create(
                    listing=listing,
                    rater=request.user,
                    defaults={
                        'rated_user': listing.user,
                        'score': score,
                        'comment': comment[:300],
                    },
                )
                messages.success(request, 'Merci pour votre note.')
            return redirect('listing_detail', id=listing.id)

    if request.method == 'POST' and request.user.is_authenticated and request.user == listing.user:
        form_type = request.POST.get('form_type', '').strip()
        if form_type == 'deal_confirm_seller':
            deal_id = request.POST.get('deal_id', '').strip()
            deal = MarketplaceDeal.objects.filter(id=deal_id, listing=listing, seller=request.user).first()
            if deal:
                deal.seller_confirmed = True
                deal.save(update_fields=['seller_confirmed', 'updated_at'])
                messages.success(request, 'Transaction confirmee cote vendeur.')
            return redirect('listing_detail', id=listing.id)

    seller_rating_stats = MarketplaceUserRating.objects.filter(rated_user=listing.user).aggregate(
        avg=Avg('score'),
        count=Count('id'),
    )

    score_rows = MarketplaceUserRating.objects.filter(rated_user=listing.user).values('score').annotate(total=Count('id'))
    score_count_map = {row['score']: row['total'] for row in score_rows}
    total_scores = seller_rating_stats.get('count') or 0
    seller_rating_breakdown = []
    for score in (5, 4, 3, 2, 1):
        score_total = score_count_map.get(score, 0)
        pct = int(round((score_total * 100) / total_scores)) if total_scores else 0
        seller_rating_breakdown.append({'score': score, 'total': score_total, 'pct': pct})

    existing_user_rating = None
    user_deal = None
    can_rate = False
    if request.user.is_authenticated and request.user != listing.user:
        existing_user_rating = MarketplaceUserRating.objects.filter(listing=listing, rater=request.user).first()
        user_deal = MarketplaceDeal.objects.filter(listing=listing, buyer=request.user).first()
        can_rate = bool(user_deal and user_deal.buyer_confirmed and user_deal.seller_confirmed)

    if request.user.is_authenticated and request.user == listing.user:
        seller_pending_deals = MarketplaceDeal.objects.filter(listing=listing, seller=request.user).select_related('buyer')[:20]

    listing_ratings = MarketplaceUserRating.objects.filter(listing=listing).select_related('rater')[:10]

    return render(
        request,
        'listing_detail.html',
        {
            'listing': listing,
            'gallery_images': gallery_images,
            'seller_rating_avg': seller_rating_stats.get('avg'),
            'seller_rating_count': seller_rating_stats.get('count') or 0,
            'seller_rating_breakdown': seller_rating_breakdown,
            'existing_user_rating': existing_user_rating,
            'user_deal': user_deal,
            'can_rate': can_rate,
            'seller_pending_deals': seller_pending_deals,
            'listing_ratings': listing_ratings,
        },
    )


def ski_partners(request):
    station_id = request.GET.get('station', '').strip()
    level = request.GET.get('level', '').strip()
    city = request.GET.get('city', '').strip()

    posts = SkiPartnerPost.objects.filter(is_active=True).select_related('user', 'ski_station').annotate(report_count=Count('reports'))
    posts = posts.filter(report_count__lt=3)
    if station_id:
        posts = posts.filter(ski_station_id=station_id)
    if level:
        posts = posts.filter(skill_level=level)
    if city:
        posts = posts.filter(city__icontains=city)

    if request.method == 'POST' and request.user.is_authenticated:
        form_type = request.POST.get('form_type', '').strip()
        if form_type == 'delete':
            post_id = request.POST.get('post_id', '').strip()
            post = SkiPartnerPost.objects.filter(id=post_id, user=request.user).first()
            if post:
                post.delete()
                messages.success(request, 'Annonce partenaire supprimee.')
            return redirect('ski_partners')

        if form_type == 'report':
            post_id = request.POST.get('post_id', '').strip()
            reason = request.POST.get('reason', '').strip()
            post = SkiPartnerPost.objects.filter(id=post_id, is_active=True).first()
            if post and post.user_id != request.user.id:
                SkiPartnerReport.objects.update_or_create(
                    post=post,
                    reporter=request.user,
                    defaults={'reason': reason[:220]},
                )
                report_count = SkiPartnerReport.objects.filter(post=post).count()
                if report_count >= 3:
                    post.is_active = False
                    post.save(update_fields=['is_active'])
                messages.success(request, 'Signalement enregistre.')
            return redirect('ski_partners')

    post_list = list(posts[:60])
    for post in post_list:
        post.organizer_display = _partner_organizer_display(post.user)

    # Statistiques top stations/services
    top_stations_services = (
        SkiStation.objects.annotate(count=Count('servicestore', distinct=True))
        .order_by('-count', 'name')[:5]
        .values('name', 'count')
    )
    total_partners = SkiPartnerPost.objects.filter(is_active=True).count()

    return render(
        request,
        'ski_partners.html',
        {
            'partner_posts': post_list,
            'stations': SkiStation.objects.order_by('name'),
            'selected_station': station_id,
            'selected_level': level,
            'selected_city': city,
            'level_choices': SkiPartnerPost.LEVEL_CHOICES,
            'top_stations_services': top_stations_services,
            'total_partners': total_partners,
        },
    )


@login_required
def ski_partner_publish(request):
    if request.method == 'POST':
        title = request.POST.get('title', '').strip()
        message_body = request.POST.get('message', '').strip()
        skill_level = request.POST.get('skill_level', SkiPartnerPost.LEVEL_INTERMEDIATE).strip()
        preferred_date = request.POST.get('preferred_date', '').strip() or None
        city_post = request.POST.get('city', '').strip()
        station_post = request.POST.get('ski_station', '').strip() or None

        valid_levels = {choice[0] for choice in SkiPartnerPost.LEVEL_CHOICES}
        title = _mask_sensitive_contact_data(title[:120])
        message_body = _mask_sensitive_contact_data(message_body)

        active_count = SkiPartnerPost.objects.filter(user=request.user, is_active=True).count()
        latest_post = SkiPartnerPost.objects.filter(user=request.user).order_by('-created_at').first()
        too_soon = bool(
            latest_post and latest_post.created_at >= timezone.now() - timedelta(minutes=PARTNER_POST_COOLDOWN_MINUTES)
        )

        if not title or not message_body:
            messages.error(request, 'Titre et description requis.')
        elif skill_level not in valid_levels:
            messages.error(request, 'Niveau invalide.')
        elif active_count >= PARTNER_POST_ACTIVE_LIMIT:
            messages.error(request, 'Limite atteinte: desactivez/supprimez une sortie active avant d\'en publier une nouvelle.')
        elif too_soon:
            messages.error(request, 'Publication trop rapide. Merci d\'attendre quelques minutes avant une nouvelle annonce.')
        elif preferred_date:
            try:
                parsed_date = date.fromisoformat(preferred_date)
            except ValueError:
                parsed_date = None
            if not parsed_date:
                messages.error(request, 'Date invalide.')
                return redirect('ski_partner_publish')
            if parsed_date < timezone.localdate():
                messages.error(request, 'La date de sortie doit etre aujourd\'hui ou plus tard.')
                return redirect('ski_partner_publish')
            preferred_date = parsed_date
        else:
            SkiPartnerPost.objects.create(
                user=request.user,
                ski_station_id=int(station_post) if station_post and station_post.isdigit() else None,
                title=title,
                message=message_body,
                city=city_post[:80],
                skill_level=skill_level,
                preferred_date=preferred_date,
            )
            messages.success(request, 'Sortie partenaire publiee.')
            return redirect('ski_partners')

    return render(
        request,
        'ski_partner_publish.html',
        {
            'stations': SkiStation.objects.order_by('name'),
            'level_choices': SkiPartnerPost.LEVEL_CHOICES,
        },
    )


def ski_stories(request):
    now = timezone.now()
    active_stories = SkiStory.objects.filter(expires_at__gt=now).select_related('user', 'ski_station')

    if request.method == 'POST':
        if not request.user.is_authenticated:
            return redirect('login')

        caption = request.POST.get('caption', '').strip()
        station_post = request.POST.get('ski_station', '').strip() or None
        image_file = request.FILES.get('image_file')

        if not image_file:
            messages.error(request, 'Photo requise pour publier une story.')
        elif not (getattr(image_file, 'content_type', '') or '').lower().startswith('image/'):
            messages.error(request, 'Le fichier doit etre une image.')
        elif getattr(image_file, 'size', 0) > 5 * 1024 * 1024:
            messages.error(request, 'Image trop volumineuse (max 5 Mo).')
        else:
            SkiStory.objects.create(
                user=request.user,
                ski_station_id=int(station_post) if station_post and station_post.isdigit() else None,
                caption=caption[:180],
                image=image_file.read(),
                expires_at=now + timedelta(hours=24),
            )
            messages.success(request, 'Story publiee pour 24h.')
            return redirect('ski_stories')

    return render(
        request,
        'ski_stories.html',
        {
            'stories': active_stories[:120],
            'stations': SkiStation.objects.order_by('name'),
        },
    )


@login_required
def delete_story(request, story_id):
    if request.method != 'POST':
        messages.error(request, 'Methode non autorisee pour la suppression.')
        return redirect('ski_stories')

    story = get_object_or_404(SkiStory, id=story_id, user=request.user)
    story.delete()
    messages.success(request, 'Story supprimee.')
    return redirect('ski_stories')


@login_required
def ajouter_materiel(request):
    if request.method == 'POST':
        form = SkiMaterialListingForm(request.POST, request.FILES)
        if form.is_valid():
            listing = form.save(commit=False)
            listing.user = request.user  # Set the current user as the listing creator
            listing.save()

            uploaded_images = request.FILES.getlist('images')
            if not listing.image and uploaded_images:
                first_image = uploaded_images[0].read()
                if first_image:
                    listing.image = first_image
                    listing.save(update_fields=['image'])
                uploaded_images[0].seek(0)
            form.save_extra_images(listing, uploaded_images)

            return redirect('ski_material_listings')
    else:
        form = SkiMaterialListingForm()
    return render(request, 'ajouter_materiel.html', {'form': form})


@login_required
def messages_view(request):
    def _display_name(user_obj):
        first = (user_obj.first_name or '').strip()
        last = (user_obj.last_name or '').strip()
        if first or last:
            return f"{first} {last}".strip()
        return user_obj.username

    def _avatar_payload(user_obj):
        avatar_base64 = None
        avatar_url = None

        profile = getattr(user_obj, 'profile', None)
        if profile and getattr(profile, 'profile_picture', None):
            avatar_base64 = base64.b64encode(profile.profile_picture).decode('utf-8')

        social = SocialAccount.objects.filter(user=user_obj, provider='google').first()
        if social:
            avatar_url = (social.extra_data or {}).get('picture')

        return {
            'display_name': _display_name(user_obj),
            'avatar_base64': avatar_base64,
            'avatar_url': avatar_url,
        }

    selected_user_id = request.GET.get('user', '').strip()
    listing_id = request.GET.get('listing', '').strip()
    prefill_subject = request.GET.get('subject', '').strip()
    prefill_body = request.GET.get('body', '').strip()

    selected_recipient = None
    if selected_user_id:
        selected_recipient = User.objects.filter(id=selected_user_id).exclude(id=request.user.id).first()

    listing = None
    if listing_id:
        listing = SkiMaterialListing.objects.filter(id=listing_id).select_related('user').first()

    if request.method == 'POST':
        recipient_id = request.POST.get('recipient_id', '').strip()
        subject = request.POST.get('subject', '').strip()
        body = request.POST.get('body', '').strip()

        recipient = User.objects.filter(id=recipient_id).exclude(id=request.user.id).first()
        if not recipient:
            messages.error(request, 'Destinataire invalide.')
        elif not body:
            messages.error(request, 'Veuillez saisir un message.')
        else:
            effective_subject = subject or prefill_subject or 'Message chat'
            Message.objects.create(
                sender=request.user,
                recipient=recipient,
                subject=effective_subject[:255],
                body=body,
            )
            messages.success(request, 'Message envoye.')
            return redirect(f"{reverse('messages')}?user={recipient.id}")

        selected_recipient = recipient or selected_recipient
        prefill_subject = subject or prefill_subject
        prefill_body = body

    if listing and not prefill_subject:
        prefill_subject = f"Question annonce: {listing.title}"
    if listing and not prefill_body:
        prefill_body = 'Bonjour, votre article est-il toujours disponible ?'

    message_pairs = (
        Message.objects.filter(Q(sender=request.user) | Q(recipient=request.user))
        .select_related('sender', 'recipient')
        .order_by('created_at')
    )

    flattened_ids = set()
    for row in message_pairs:
        if row.sender_id != request.user.id:
            flattened_ids.add(row.sender_id)
        if row.recipient_id != request.user.id:
            flattened_ids.add(row.recipient_id)

    friend_ids = set(UserFriend.objects.filter(user=request.user).values_list('friend_id', flat=True))
    flattened_ids |= friend_ids

    if selected_recipient:
        flattened_ids.add(selected_recipient.id)

    contacts_qs = User.objects.filter(id__in=flattened_ids)
    contacts_map = {user.id: user for user in contacts_qs}
    contacts = []

    for user_id, contact in contacts_map.items():
        last_message = Message.objects.filter(
            (Q(sender=request.user, recipient_id=user_id) | Q(sender_id=user_id, recipient=request.user))
        ).order_by('-created_at').first()

        unread_for_contact = Message.objects.filter(
            sender_id=user_id,
            recipient=request.user,
            is_read=False,
        ).count()

        contacts.append({
            'user': contact,
            'last_message': last_message,
            'unread_count': unread_for_contact,
            'is_friend': user_id in friend_ids,
            **_avatar_payload(contact),
        })

    contacts.sort(
        key=lambda row: row['last_message'].created_at if row['last_message'] else timezone.make_aware(timezone.datetime.min),
        reverse=True,
    )

    if not selected_recipient and contacts:
        selected_recipient = contacts[0]['user']

    selected_is_friend = bool(selected_recipient and selected_recipient.id in friend_ids)

    conversation_messages = []
    if selected_recipient:
        conversation_qs = Message.objects.filter(
            Q(sender=request.user, recipient=selected_recipient)
            | Q(sender=selected_recipient, recipient=request.user)
        ).order_by('created_at')
        conversation_messages = list(conversation_qs)
        Message.objects.filter(sender=selected_recipient, recipient=request.user, is_read=False).update(is_read=True)

    unread_count = Message.objects.filter(recipient=request.user, is_read=False).count()

    contact_user_ids = {item['user'].id for item in contacts}
    suggestion_ids = []

    instructor_ids = list(
        InstructorProfile.objects.filter(is_active=True)
        .exclude(user=request.user)
        .values_list('user_id', flat=True)[:30]
    )
    seller_ids = list(
        SkiMaterialListing.objects.exclude(user=request.user)
        .order_by('-posted_at')
        .values_list('user_id', flat=True)[:40]
    )
    partner_ids = list(
        SkiPartnerPost.objects.filter(is_active=True)
        .exclude(user=request.user)
        .order_by('-created_at')
        .values_list('user_id', flat=True)[:40]
    )

    for user_id in instructor_ids + seller_ids + partner_ids:
        if user_id in contact_user_ids:
            continue
        if user_id in suggestion_ids:
            continue
        suggestion_ids.append(user_id)
        if len(suggestion_ids) >= 8:
            break

    suggestions_map = {
        user.id: user
        for user in User.objects.filter(id__in=suggestion_ids)
    }
    suggestions = []
    for user_id in suggestion_ids:
        user = suggestions_map.get(user_id)
        if user:
            suggestions.append({'user': user, 'is_friend': user_id in friend_ids, **_avatar_payload(user)})

    selected_recipient_context = _avatar_payload(selected_recipient) if selected_recipient else None

    return render(request, 'messages/messages.html', {
        'unread_count': unread_count,
        'contacts': contacts,
        'selected_recipient': selected_recipient,
        'selected_recipient_context': selected_recipient_context,
        'prefill_subject': prefill_subject,
        'prefill_body': prefill_body,
        'context_listing': listing,
        'conversation_messages': conversation_messages,
        'selected_is_friend': selected_is_friend,
        'suggestions': suggestions,
    })


@login_required
def messages_user_search(request):
    query = request.GET.get('q', '').strip()
    if len(query) < 2:
        return JsonResponse({'results': []})

    friend_ids = set(UserFriend.objects.filter(user=request.user).values_list('friend_id', flat=True))
    users = (
        User.objects.exclude(id=request.user.id)
        .filter(
            Q(username__icontains=query)
            | Q(first_name__icontains=query)
            | Q(last_name__icontains=query)
        )
        .order_by('username')[:12]
    )

    results = []
    for user in users:
        display_name = f"{(user.first_name or '').strip()} {(user.last_name or '').strip()}".strip() or user.username
        avatar_base64 = None
        avatar_url = None

        profile = getattr(user, 'profile', None)
        if profile and getattr(profile, 'profile_picture', None):
            avatar_base64 = base64.b64encode(profile.profile_picture).decode('utf-8')

        social = SocialAccount.objects.filter(user=user, provider='google').first()
        if social:
            avatar_url = (social.extra_data or {}).get('picture')

        results.append(
            {
                'id': user.id,
                'username': user.username,
                'display_name': display_name,
                'is_friend': user.id in friend_ids,
                'avatar_base64': avatar_base64,
                'avatar_url': avatar_url,
            }
        )
    return JsonResponse({'results': results})


@login_required
def messages_add_friend(request):
    if request.method != 'POST':
        return JsonResponse({'ok': False, 'error': 'method_not_allowed'}, status=405)

    friend_id = (request.POST.get('friend_id') or '').strip()
    friend = User.objects.filter(id=friend_id).exclude(id=request.user.id).first()
    if not friend:
        return JsonResponse({'ok': False, 'error': 'invalid_friend'}, status=400)

    UserFriend.objects.get_or_create(user=request.user, friend=friend)
    UserFriend.objects.get_or_create(user=friend, friend=request.user)
    return JsonResponse({'ok': True})


@login_required
def messages_remove_friend(request):
    if request.method != 'POST':
        return JsonResponse({'ok': False, 'error': 'method_not_allowed'}, status=405)

    friend_id = (request.POST.get('friend_id') or '').strip()
    friend = User.objects.filter(id=friend_id).exclude(id=request.user.id).first()
    if not friend:
        return JsonResponse({'ok': False, 'error': 'invalid_friend'}, status=400)

    UserFriend.objects.filter(user=request.user, friend=friend).delete()
    UserFriend.objects.filter(user=friend, friend=request.user).delete()
    return JsonResponse({'ok': True})

def getMessagesAndCount(request):
    inbox_messages = Message.objects.filter(recipient=request.user).order_by('-created_at')

    unread_count = inbox_messages.filter(is_read=False).count()
    inbox_messages.filter(is_read=False).update(is_read=True)
    return inbox_messages, unread_count

@login_required
def profile_view(request):
    user = request.user  # Get the current user

    profile_picture_data = None
    google_profile_picture_url = None
    google_full_name = None
    try:
        user_profile = UserProfile.objects.get(user=user)  # Get the user profile
        if user_profile.profile_picture:
            # Convert binary data to base64 string for rendering in the template
            profile_picture_data = base64.b64encode(user_profile.profile_picture).decode('utf-8')
    except UserProfile.DoesNotExist:
        pass  # Handle case where UserProfile does not exist for the user

    # If the user signed in with Google, expose Google profile info as fallback.
    social = SocialAccount.objects.filter(user=user, provider='google').first()
    if social:
        extra = social.extra_data or {}
        google_profile_picture_url = extra.get('picture')
        given_name = (extra.get('given_name') or '').strip()
        family_name = (extra.get('family_name') or '').strip()
        full_name = (extra.get('name') or '').strip()
        google_full_name = full_name or f"{given_name} {family_name}".strip() or None

    if request.method == 'POST':
        form = ProfileForm(request.POST, request.FILES, instance=user)  # Add request.FILES
        if form.is_valid():
            form.save()  # Save the form data and profile picture
            return redirect('profile')  # Redirect to the profile page after saving
    else:
        form = ProfileForm(instance=user)  # Pass the instance here as well

    return render(request, 'profile.html', {
        'form': form,
        'profile_picture': profile_picture_data,  # Pass local profile picture in base64
        'google_profile_picture_url': google_profile_picture_url,
        'google_full_name': google_full_name,
    })

@login_required
def delete_account(request):
    user = request.user
    user.delete()
    messages.success(request, 'Your account has been deleted successfully.')
    return redirect('my_template_view')  # Redirect to home or another page


@login_required
def edit_listing(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id, user=request.user)
    if request.method == 'POST':
        form = SkiMaterialListingForm(request.POST, request.FILES, instance=listing)
        if form.is_valid():
            listing = form.save()
            uploaded_images = request.FILES.getlist('images')
            form.save_extra_images(listing, uploaded_images)
            messages.success(request, 'Annonce mise a jour.')
            return redirect('listing_detail', id=listing.id)
    else:
        form = SkiMaterialListingForm(instance=listing)
    return render(request, 'ajouter_materiel.html', {'form': form})


@login_required
def delete_listing(request, id):
    if request.method != 'POST':
        messages.error(request, 'Methode non autorisee pour la suppression.')
        return redirect('listing_detail', id=id)

    listing = get_object_or_404(SkiMaterialListing, id=id, user=request.user)
    listing.delete()
    messages.success(request, 'Annonce supprimee.')
    return redirect('ski_material_listings')


@login_required
def delete_snow_update(request, station_id, update_id):
    update = get_object_or_404(
        SnowConditionUpdate,
        id=update_id,
        ski_station_id=station_id,
        user=request.user,
    )
    update.delete()
    messages.success(request, _('Publication supprimee.'))
    return redirect('ski_station_detail', station_id=station_id)


def service_detail(request, service_id):
    service = get_object_or_404(ServiceStore, id=service_id)
    return render(request, 'service_detail.html', {'service': service})


def instructors_list(request):
    if request.method == 'POST' and request.user.is_authenticated:
        form_type = request.POST.get('form_type', '').strip()
        if form_type == 'instructor_review':
            instructor_id = request.POST.get('instructor_id', '').strip()
            try:
                rating = int(request.POST.get('rating', '').strip())
            except (TypeError, ValueError):
                rating = 0
            comment = request.POST.get('comment', '').strip()

            instructor = InstructorProfile.objects.filter(id=instructor_id, is_active=True).first()
            if not instructor:
                messages.error(request, 'Moniteur introuvable.')
                return redirect('instructors')

            if instructor.user_id == request.user.id:
                messages.error(request, 'Vous ne pouvez pas evaluer votre propre profil.')
                return redirect('instructors')

            if rating < 1 or rating > 5:
                messages.error(request, 'Veuillez choisir une note entre 1 et 5.')
                return redirect('instructors')

            from api.models import InstructorReview

            InstructorReview.objects.update_or_create(
                instructor=instructor,
                user=request.user,
                defaults={'rating': rating, 'comment': comment},
            )
            messages.success(request, 'Votre avis a ete enregistre.')
            return redirect('instructors')

    search_query = request.GET.get('q', '').strip()
    selected_station = request.GET.get('station', '').strip()
    price_min = request.GET.get('price_min', '').strip()
    price_max = request.GET.get('price_max', '').strip()
    sort_by = request.GET.get('sort', 'relevance').strip() or 'relevance'
    my_offers = request.GET.get('my_offers', '0') == '1'

    instructors_qs = (
        InstructorProfile.objects.select_related('user')
        .filter(is_active=True)
        .annotate(
            active_services_count=Count('services', filter=Q(services__is_active=True), distinct=True),
            min_amount=Min('services__amount', filter=Q(services__is_active=True)),
            avg_rating=Avg('reviews__rating'),
            review_count=Count('reviews', distinct=True),
        )
    )

    if search_query:
        instructors_qs = instructors_qs.filter(
            Q(user__username__icontains=search_query)
            | Q(user__first_name__icontains=search_query)
            | Q(user__last_name__icontains=search_query)
            | Q(bio__icontains=search_query)
            | Q(certifications__icontains=search_query)
            | Q(services__title__icontains=search_query)
            | Q(services__description__icontains=search_query)
            | Q(services__ski_station__name__icontains=search_query)
        )

    if selected_station:
        instructors_qs = instructors_qs.filter(services__ski_station_id=selected_station, services__is_active=True)

    if price_min:
        try:
            instructors_qs = instructors_qs.filter(min_amount__gte=float(price_min))
        except ValueError:
            price_min = ''

    if price_max:
        try:
            instructors_qs = instructors_qs.filter(min_amount__lte=float(price_max))
        except ValueError:
            price_max = ''

    if my_offers:
        if request.user.is_authenticated:
            instructors_qs = instructors_qs.filter(user=request.user)
        else:
            instructors_qs = instructors_qs.none()

    sort_map = {
        'relevance': ['-active_services_count', '-review_count', '-years_experience', '-created_at'],
        'price_asc': ['min_amount', '-review_count', '-created_at'],
        'price_desc': ['-min_amount', '-review_count', '-created_at'],
        'experience_desc': ['-years_experience', '-review_count', '-created_at'],
        'newest': ['-created_at'],
    }
    if sort_by not in sort_map:
        sort_by = 'relevance'

    instructors_qs = instructors_qs.distinct().order_by(*sort_map[sort_by])

    current_reviews = {}
    if request.user.is_authenticated and instructors_qs:
        from api.models import InstructorReview

        current_reviews = {
            row.instructor_id: row
            for row in InstructorReview.objects.filter(
                user=request.user,
                instructor_id__in=[item.id for item in instructors_qs],
            )
        }

    instructors = list(instructors_qs)
    for instructor in instructors:
        avg_val = instructor.avg_rating or 0
        instructor.avg_rating_rounded = int(round(avg_val)) if avg_val else 0
        instructor.current_user_review = current_reviews.get(instructor.id)

    stations = SkiStation.objects.order_by('name')

    # Statistiques top stations par nombre de moniteurs/services
    top_stations_instructors = (
        SkiStation.objects.annotate(count=Count('instructorprofile', distinct=True))
        .order_by('-count', 'name')[:5]
        .values('name', 'count')
    )
    total_instructors = InstructorProfile.objects.filter(is_active=True).count()

    return render(
        request,
        'instructors.html',
        {
            'instructors': instructors,
            'stations': stations,
            'search_query': search_query,
            'selected_station': selected_station,
            'price_min': price_min,
            'price_max': price_max,
            'sort_by': sort_by,
            'my_offers': my_offers,
            'top_stations_instructors': top_stations_instructors,
            'total_instructors': total_instructors,
        },
    )


@login_required
def become_instructor(request):
    profile, _ = InstructorProfile.objects.get_or_create(user=request.user)

    if request.method == 'POST':
        form = InstructorProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            messages.success(request, 'Profil moniteur enregistre.')
            return redirect('instructors')
        messages.error(request, 'Veuillez verifier les champs du formulaire.')
    else:
        form = InstructorProfileForm(instance=profile)

    return render(request, 'instructor_register.html', {'form': form, 'profile': profile})


@login_required
def instructor_services_view(request):
    profile, _ = InstructorProfile.objects.get_or_create(user=request.user)

    if request.method == 'POST':
        form = InstructorServiceForm(request.POST)
        if form.is_valid():
            service = form.save(commit=False)
            service.instructor = profile
            service.save()
            messages.success(request, 'Offre moniteur publiee.')
            return redirect('instructor_services')
        messages.error(request, 'Veuillez corriger le formulaire avant de publier.')
    else:
        form = InstructorServiceForm(initial={'currency': 'EUR', 'duration_minutes': 60, 'max_group_size': 1, 'is_active': True})

    query_text = request.GET.get('q', '').strip()
    filter_station = request.GET.get('station', '').strip()
    filter_status = request.GET.get('status', '').strip()
    filter_min_amount = request.GET.get('min_amount', '').strip()
    filter_max_amount = request.GET.get('max_amount', '').strip()
    sort_by = request.GET.get('sort', 'newest').strip() or 'newest'

    services = InstructorService.objects.filter(instructor=profile).select_related('ski_station')

    if query_text:
        services = services.filter(
            Q(title__icontains=query_text)
            | Q(description__icontains=query_text)
            | Q(ski_station__name__icontains=query_text)
        )

    if filter_station:
        services = services.filter(ski_station_id=filter_station)

    if filter_status == 'active':
        services = services.filter(is_active=True)
    elif filter_status == 'inactive':
        services = services.filter(is_active=False)

    if filter_min_amount:
        try:
            services = services.filter(amount__gte=float(filter_min_amount))
        except ValueError:
            filter_min_amount = ''

    if filter_max_amount:
        try:
            services = services.filter(amount__lte=float(filter_max_amount))
        except ValueError:
            filter_max_amount = ''

    sort_map = {
        'newest': ['-created_at'],
        'oldest': ['created_at'],
        'price_asc': ['amount', '-created_at'],
        'price_desc': ['-amount', '-created_at'],
        'duration_asc': ['duration_minutes', '-created_at'],
    }
    if sort_by not in sort_map:
        sort_by = 'newest'
    services = services.order_by(*sort_map[sort_by])

    stations = SkiStation.objects.order_by('name')
    base_services_qs = InstructorService.objects.filter(instructor=profile)
    stats = {
        'total': base_services_qs.count(),
        'active': base_services_qs.filter(is_active=True).count(),
        'inactive': base_services_qs.filter(is_active=False).count(),
    }

    return render(
        request,
        'instructor_services.html',
        {
            'form': form,
            'services': services,
            'profile': profile,
            'stations': stations,
            'query_text': query_text,
            'filter_station': filter_station,
            'filter_status': filter_status,
            'filter_min_amount': filter_min_amount,
            'filter_max_amount': filter_max_amount,
            'sort_by': sort_by,
            'stats': stats,
        },
    )


@login_required
def cancel_instructor_profile(request):
    if request.method != 'POST':
        return redirect('instructor_services')

    profile = InstructorProfile.objects.filter(user=request.user).first()
    if not profile:
        messages.info(request, 'Aucun profil moniteur actif trouve.')
        return redirect('instructors')

    profile.is_active = False
    profile.save(update_fields=['is_active'])
    InstructorService.objects.filter(instructor=profile).update(is_active=False)
    messages.success(request, 'Votre profil moniteur a ete desactive.')
    return redirect('instructors')


@login_required
def edit_instructor_service(request, service_id):
    service = get_object_or_404(InstructorService, id=service_id, instructor__user=request.user)

    if request.method == 'POST':
        form = InstructorServiceForm(request.POST, instance=service)
        if form.is_valid():
            form.save()
            messages.success(request, 'Offre moniteur mise a jour.')
            return redirect('instructor_services')
        messages.error(request, 'Veuillez corriger les champs en erreur.')
    else:
        form = InstructorServiceForm(instance=service)

    return render(request, 'edit_instructor_service.html', {'form': form, 'service': service})


@login_required
def delete_instructor_service(request, service_id):
    service = get_object_or_404(InstructorService, id=service_id, instructor__user=request.user)
    service.delete()
    messages.success(request, 'Offre moniteur supprimee.')
    return redirect('instructor_services')
