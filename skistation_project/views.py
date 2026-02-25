from django.shortcuts import render, get_object_or_404, redirect
from api.models import (
    SkiStation,
    BusLine,
    ServiceStore,
    SkiCircuit,
    SkiMaterialListing,
    Message,
    UserProfile,
    InstructorProfile,
    InstructorService,
    InstructorReview,
    SnowConditionUpdate,
    PisteConditionReport,
    CrowdStatusUpdate,
)
from django.db.models import Sum, Count, Q, Avg, Min
from django.db.models import Case, When, Value, FloatField
from django.db.models.functions import TruncHour
from django.contrib.auth.forms import UserCreationForm
from .forms import (
    UserRegistrationForm,
    SkiMaterialListingForm,
    ProfileForm,
    InstructorProfileForm,
    InstructorServiceForm,
    InstructorReviewForm,
    MessageForm,
    SnowConditionUpdateForm,
    PisteConditionReportForm,
    CrowdStatusUpdateForm,
)
from allauth.socialaccount.providers.google.views import OAuth2LoginView
from allauth.socialaccount.providers.google.views import OAuth2CallbackView
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter
from allauth.account.adapter import DefaultAccountAdapter
from django.contrib.auth.decorators import login_required
from django.shortcuts import render, redirect
from django.contrib.auth.decorators import login_required
from django.contrib import messages
from django.contrib.auth import get_user_model
from django.http import HttpResponseForbidden
from urllib.parse import quote_plus
from django.utils import timezone
from datetime import timedelta
import json
import base64


SERVICE_CATEGORY_ORDER = [
    'Restauration',
    'Magasin / Location',
    'Atelier / Réparation',
    'Secours',
    'Information',
    'Autres',
]


def classify_service_type(raw_type):
    value = (raw_type or '').lower()
    if any(keyword in value for keyword in ['resto', 'restaurant', 'bar', 'snack', 'food', 'restauration']):
        return 'Restauration'
    if any(keyword in value for keyword in ['location', 'magasin', 'shop', 'boutique', 'matériel', 'materiel']):
        return 'Magasin / Location'
    if any(keyword in value for keyword in ['atelier', 'réparation', 'reparation', 'entretien', 'repair']):
        return 'Atelier / Réparation'
    if any(keyword in value for keyword in ['secours', 'urgence', 'pisteur', 'medical']):
        return 'Secours'
    if any(keyword in value for keyword in ['information', 'info', 'accueil']):
        return 'Information'
    return 'Autres'


def home(request):
    queryset = SkiStation.objects.annotate(
        num_circuits=Sum('skicircuit__num_pistes'),
        service_count=Count('servicestore', distinct=True),
        instructor_service_count=Count('instructor_services', distinct=True),
    )
    random_ski_stations = queryset.order_by('?')
    nearest_stations = queryset.order_by('distanceFromGrenoble')[:5]

    return render(request, 'index.html', {
        'ski_stations': random_ski_stations,
        'all': queryset,
        'nearest_stations': nearest_stations,
        'map_stations': list(queryset),
    })


def ski_station_detail(request, station_id):
    ski_station = get_object_or_404(SkiStation.objects.annotate(num_circuits=Sum('skicircuit__num_pistes')), id=station_id)
    bus_lines = BusLine.objects.filter(ski_station=ski_station)
    service_stores = ServiceStore.objects.filter(ski_station=ski_station)
    ski_circuits = SkiCircuit.objects.filter(ski_station=ski_station)
    snow_updates = SnowConditionUpdate.objects.filter(ski_station=ski_station).select_related('user').order_by('-created_at')[:8]
    piste_reports = PisteConditionReport.objects.filter(ski_station=ski_station).select_related('user').order_by('-created_at')[:12]
    crowd_updates = CrowdStatusUpdate.objects.filter(ski_station=ski_station).select_related('user').order_by('-created_at')[:20]
    piste_summary = PisteConditionReport.objects.filter(ski_station=ski_station).aggregate(
        avg_rating=Avg('piste_rating'),
        total_reports=Count('id'),
    )
    average_rating = piste_summary.get('avg_rating')
    average_rating_rounded = int(round(float(average_rating))) if average_rating is not None else 0
    latest_crowd_update = CrowdStatusUpdate.objects.filter(ski_station=ski_station).order_by('-created_at').first()
    last_crowd = latest_crowd_update.get_crowd_level_display() if latest_crowd_update else None
    last_crowd_key = latest_crowd_update.crowd_level if latest_crowd_update else 'normal'

    trend = request.GET.get('trend', '24h')
    window_hours = 3 if trend == '3h' else 24

    now = timezone.now().replace(minute=0, second=0, microsecond=0)
    start_window = now - timedelta(hours=window_hours - 1)
    hourly_reports = (
        PisteConditionReport.objects
        .filter(ski_station=ski_station, created_at__gte=start_window)
        .annotate(hour=TruncHour('created_at'))
        .values('hour')
        .annotate(
            reports_count=Count('id'),
            avg_rating=Avg('piste_rating'),
        )
        .order_by('hour')
    )

    hourly_crowd_updates = (
        CrowdStatusUpdate.objects
        .filter(ski_station=ski_station, created_at__gte=start_window)
        .annotate(hour=TruncHour('created_at'))
        .values('hour')
        .annotate(
            avg_crowd=Avg(
                Case(
                    When(crowd_level='quiet', then=Value(1.0)),
                    When(crowd_level='normal', then=Value(2.0)),
                    When(crowd_level='busy', then=Value(3.0)),
                    default=Value(2.0),
                    output_field=FloatField(),
                )
            )
        )
        .order_by('hour')
    )

    crowd_map = {
        item['hour']: round(float(item['avg_crowd']), 2) if item['avg_crowd'] is not None else None
        for item in hourly_crowd_updates
    }

    hourly_map = {
        item['hour']: {
            'reports_count': item['reports_count'] or 0,
            'avg_rating': round(float(item['avg_rating']), 2) if item['avg_rating'] is not None else None,
        }
        for item in hourly_reports
    }

    chart_labels = []
    chart_reports = []
    chart_ratings = []
    chart_crowd = []
    for i in range(window_hours):
        hour_key = start_window + timedelta(hours=i)
        row = hourly_map.get(hour_key)
        chart_labels.append(hour_key.strftime('%H:%M'))
        chart_reports.append(row['reports_count'] if row else 0)
        chart_ratings.append(row['avg_rating'] if row and row['avg_rating'] is not None else None)
        chart_crowd.append(crowd_map.get(hour_key))

    if request.method == 'POST':
        if not request.user.is_authenticated:
            return redirect('login')
        form_type = request.POST.get('form_type')
        if form_type == 'piste_report':
            existing_report = PisteConditionReport.objects.filter(ski_station=ski_station, user=request.user).first()
            piste_form = PisteConditionReportForm(request.POST, instance=existing_report)
            snow_form = SnowConditionUpdateForm()
            crowd_form = CrowdStatusUpdateForm(initial={'crowd_level': last_crowd_key})
            if piste_form.is_valid():
                report = piste_form.save(commit=False)
                report.ski_station = ski_station
                report.user = request.user
                if not report.crowd_level:
                    report.crowd_level = 'normal'
                report.save()
                return redirect('ski_station_detail', station_id=station_id)
        elif form_type == 'crowd_update':
            crowd_form = CrowdStatusUpdateForm(request.POST)
            piste_form = PisteConditionReportForm()
            snow_form = SnowConditionUpdateForm()
            if crowd_form.is_valid():
                crowd_update = crowd_form.save(commit=False)
                crowd_update.ski_station = ski_station
                crowd_update.user = request.user
                crowd_update.save()
                return redirect('ski_station_detail', station_id=station_id)
        else:
            snow_form = SnowConditionUpdateForm(request.POST, request.FILES)
            piste_form = PisteConditionReportForm()
            crowd_form = CrowdStatusUpdateForm(initial={'crowd_level': last_crowd_key})
            if snow_form.is_valid():
                update = snow_form.save(commit=False)
                update.ski_station = ski_station
                update.user = request.user
                update.save()
                return redirect('ski_station_detail', station_id=station_id)
    else:
        existing_report = PisteConditionReport.objects.filter(ski_station=ski_station, user=request.user).first() if request.user.is_authenticated else None
        snow_form = SnowConditionUpdateForm()
        piste_form = PisteConditionReportForm(instance=existing_report)
        crowd_form = CrowdStatusUpdateForm(initial={'crowd_level': last_crowd_key})

    context = {
        'station': ski_station,
        'bus_lines': bus_lines,
        'service_stores': service_stores,
        'ski_circuits': ski_circuits,
        'snow_form': snow_form,
        'snow_updates': snow_updates,
        'piste_form': piste_form,
        'crowd_form': crowd_form,
        'piste_reports': piste_reports,
        'crowd_updates': crowd_updates,
        'piste_average_rating': average_rating,
        'piste_average_rating_rounded': average_rating_rounded,
        'piste_report_count': piste_summary.get('total_reports') or 0,
        'piste_last_crowd': last_crowd,
        'piste_last_crowd_key': last_crowd_key,
        'piste_chart_labels': json.dumps(chart_labels),
        'piste_chart_reports': json.dumps(chart_reports),
        'piste_chart_ratings': json.dumps(chart_ratings),
        'piste_chart_crowd': json.dumps(chart_crowd),
        'piste_trend': trend,
        'piste_window_label': f'{window_hours}h',
        'default_departure': 'Grenoble Gare',
        'station_driving_url': f"https://www.google.com/maps/dir/?api=1&origin={quote_plus('Grenoble Gare')}&destination={ski_station.latitude},{ski_station.longitude}&travelmode=driving",
        'station_transit_url': f"https://www.google.com/maps/dir/?api=1&origin={quote_plus('Grenoble Gare')}&destination={ski_station.latitude},{ski_station.longitude}&travelmode=transit",
    }
    
    return render(request, 'details.html', context)


@login_required(login_url='login')
def delete_snow_update(request, station_id, update_id):
    station = get_object_or_404(SkiStation, id=station_id)
    update = get_object_or_404(SnowConditionUpdate, id=update_id, ski_station=station)

    if update.user != request.user:
        return HttpResponseForbidden('You can only delete your own snow update.')

    if request.method == 'POST':
        update.delete()

    return redirect('ski_station_detail', station_id=station_id)


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
    ski_stations = SkiStation.objects.order_by('distanceFromGrenoble')
    service_types = ServiceStore.objects.values_list('type', flat=True).distinct().order_by('type')
    service_categories = SERVICE_CATEGORY_ORDER

    services = ServiceStore.objects.select_related('ski_station').all().order_by('name')
    instructor_services = InstructorService.objects.select_related('instructor__user', 'ski_station').filter(is_active=True)
    search_query = request.GET.get('q', '').strip()
    name = request.GET.get('name', '')
    service_type = request.GET.get('type', '')
    service_category = request.GET.get('service_category', '').strip()
    ski_station_id = request.GET.get('ski_station', '')
    my_instructor_offers = request.GET.get('my_instructor_offers')
    selected_station = None

    if search_query:
        services = services.filter(
            Q(name__icontains=search_query)
            | Q(type__icontains=search_query)
            | Q(opening_hours__icontains=search_query)
            | Q(address__icontains=search_query)
            | Q(ski_station__name__icontains=search_query)
        )
        instructor_services = instructor_services.filter(
            Q(title__icontains=search_query)
            | Q(description__icontains=search_query)
            | Q(ski_station__name__icontains=search_query)
            | Q(instructor__user__username__icontains=search_query)
        )

    if name:
        services = services.filter(name__icontains=name)
    if service_type:
        services = services.filter(type=service_type)

    if service_category:
        if service_category == 'Restauration':
            services = services.filter(Q(type__icontains='restauration') | Q(type__icontains='resto') | Q(type__icontains='restaurant') | Q(type__icontains='bar') | Q(type__icontains='snack'))
        elif service_category == 'Magasin / Location':
            services = services.filter(Q(type__icontains='location') | Q(type__icontains='magasin') | Q(type__icontains='shop') | Q(type__icontains='boutique') | Q(type__icontains='matériel') | Q(type__icontains='materiel'))
        elif service_category == 'Atelier / Réparation':
            services = services.filter(Q(type__icontains='atelier') | Q(type__icontains='réparation') | Q(type__icontains='reparation') | Q(type__icontains='repair') | Q(type__icontains='entretien'))
        elif service_category == 'Secours':
            services = services.filter(Q(type__icontains='secours') | Q(type__icontains='urgence') | Q(type__icontains='medical') | Q(type__icontains='pisteur'))
        elif service_category == 'Information':
            services = services.filter(Q(type__icontains='information') | Q(type__icontains='info') | Q(type__icontains='accueil'))
        elif service_category == 'Autres':
            services = services.exclude(
                Q(type__icontains='restauration') | Q(type__icontains='resto') | Q(type__icontains='restaurant')
                | Q(type__icontains='location') | Q(type__icontains='magasin') | Q(type__icontains='shop')
                | Q(type__icontains='atelier') | Q(type__icontains='réparation') | Q(type__icontains='reparation')
                | Q(type__icontains='secours') | Q(type__icontains='urgence')
                | Q(type__icontains='information') | Q(type__icontains='info')
            )
    if ski_station_id:
        services = services.filter(ski_station_id=ski_station_id)
        instructor_services = instructor_services.filter(ski_station_id=ski_station_id)
        selected_station = ski_stations.filter(id=ski_station_id).first()
    if my_instructor_offers and request.user.is_authenticated:
        instructor_services = instructor_services.filter(instructor__user=request.user)

    station_stats_map = {}
    for service in services:
        station_name = service.ski_station.name if service.ski_station else ''
        service.address_display = service.address or f"{service.name}, {station_name}".strip(', ')
        service.google_maps_url = f"https://www.google.com/maps/search/?api=1&query={service.latitude},{service.longitude}"
        service.search_url = f"https://www.google.com/search?q={quote_plus(f'{service.name} {station_name}')}".strip()
        service.preferred_url = service.website_url or service.google_maps_url
        service.category_label = classify_service_type(service.type)

        station_id = service.ski_station.id if service.ski_station else None
        if station_id and station_id not in station_stats_map:
            station_stats_map[station_id] = {
                'station': service.ski_station,
                'counts': {category: 0 for category in SERVICE_CATEGORY_ORDER},
                'total': 0,
            }
        if station_id:
            station_stats_map[station_id]['counts'][service.category_label] += 1
            station_stats_map[station_id]['total'] += 1

    station_service_stats = sorted(
        station_stats_map.values(),
        key=lambda row: row['station'].distanceFromGrenoble if row['station'] else 9999,
    )

    for row in station_service_stats:
        row['ordered_counts'] = [row['counts'].get(category, 0) for category in SERVICE_CATEGORY_ORDER]

    context = {
        'services': services,
        'instructor_services': instructor_services,
        'service_types': service_types,
        'service_categories': service_categories,
        'ski_stations': ski_stations,
        'selected_station': selected_station,
        'search_query': search_query,
        'selected_service_category': service_category,
        'station_service_stats': station_service_stats,
    }

    return render(request, 'services.html', context)


def service_detail(request, service_id):
    service = get_object_or_404(ServiceStore.objects.select_related('ski_station'), id=service_id)
    station_name = service.ski_station.name if service.ski_station else ''
    address_display = service.address or f"{service.name}, {station_name}".strip(', ')
    google_maps_url = f"https://www.google.com/maps/search/?api=1&query={service.latitude},{service.longitude}"
    directions_url = f"https://www.google.com/maps/dir/?api=1&origin={quote_plus('Grenoble Gare')}&destination={service.latitude},{service.longitude}&travelmode=driving"

    return render(request, 'service_detail.html', {
        'service': service,
        'address_display': address_display,
        'google_maps_url': google_maps_url,
        'directions_url': directions_url,
    })

def bus_lines(request):
    departure = request.GET.get('departure', 'Grenoble Gare')
    selected_station_id = request.GET.get('station')
    schedule = request.GET.get('schedule', '')
    stations = SkiStation.objects.order_by('distanceFromGrenoble')

    selected_station = None
    bus_lines = BusLine.objects.select_related('ski_station').order_by('bus_number')
    if selected_station_id:
        selected_station = SkiStation.objects.filter(id=selected_station_id).first()
        if selected_station:
            bus_lines = bus_lines.filter(ski_station=selected_station)

    no_schedule_mention = ~Q(frequency__icontains='Semaine') & ~Q(frequency__icontains='Week-end') & ~Q(frequency__icontains='week-end')
    if schedule == 'weekday':
        bus_lines = bus_lines.filter(Q(frequency__icontains='Semaine') | no_schedule_mention)
    elif schedule == 'weekend':
        bus_lines = bus_lines.filter(Q(frequency__icontains='Week-end') | Q(frequency__icontains='week-end') | no_schedule_mention)

    transit_url = None
    driving_url = None
    if selected_station:
        destination = f"{selected_station.latitude},{selected_station.longitude}"
        encoded_departure = quote_plus(departure)
        transit_url = f"https://www.google.com/maps/dir/?api=1&origin={encoded_departure}&destination={destination}&travelmode=transit"
        driving_url = f"https://www.google.com/maps/dir/?api=1&origin={encoded_departure}&destination={destination}&travelmode=driving"

    bus_map_points = []
    for line in bus_lines:
        if line.departure_latitude and line.departure_longitude:
            bus_map_points.append({
                'line': line.bus_number,
                'station_name': line.ski_station.name if line.ski_station else '',
                'departure_stop': line.departure_stop,
                'route_points': line.route_points or '',
                'lat': float(line.departure_latitude),
                'lng': float(line.departure_longitude),
            })

    context = {
        'bus_lines': bus_lines,
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
    
@login_required(login_url='login')
def ski_material_listings(request):
    listings = SkiMaterialListing.objects.all().order_by('-posted_at')

    search_query = request.GET.get('q', '').strip()
    only_free = request.GET.get('only_free')
    listing_type = request.GET.get('listing_type')
    my_listings = request.GET.get('my_listings')

    if search_query:
        search_filter = (
            Q(title__icontains=search_query)
            | Q(description__icontains=search_query)
            | Q(city__icontains=search_query)
            | Q(material_type__icontains=search_query)
            | Q(ski_station__name__icontains=search_query)
            | Q(user__username__icontains=search_query)
        )

        lowered_query = search_query.lower()
        if lowered_query in ['gratuit', 'gratuite', 'free']:
            search_filter |= Q(is_free=True)
        if lowered_query in ['vente', 'vendre', 'sell']:
            search_filter |= Q(listing_type='sell')
        if lowered_query in ['prêt', 'pret', 'louer', 'lend']:
            search_filter |= Q(listing_type='lend')

        listings = listings.filter(search_filter)

    if only_free:
        listings = listings.filter(is_free=True)
    if listing_type:
        listings = listings.filter(listing_type=listing_type)
    if my_listings and request.user.is_authenticated:
        listings = listings.filter(user=request.user)

    listings = listings.select_related('user', 'ski_station').distinct()

    if request.method == 'POST':
        form = SkiMaterialListingForm(request.POST, request.FILES)
        if form.is_valid():
            listing = form.save(commit=False)
            listing.user = request.user  # Set the current user as the listing creator
            listing.save()
            return redirect('ski_material_listings')
    else:
        form = SkiMaterialListingForm()

    return render(request, 'ski_material_listings.html', {
        'form': form,
        'listings': listings,
        'search_query': search_query,
    })

def listing_detail(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id)
    return render(request, 'listing_detail.html', {'listing': listing})


@login_required(login_url='login')
def edit_listing(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id)
    if listing.user != request.user:
        return HttpResponseForbidden('You can only edit your own listing.')

    if request.method == 'POST':
        form = SkiMaterialListingForm(request.POST, request.FILES, instance=listing)
        if form.is_valid():
            form.save()
            return redirect('ski_material_listings')
    else:
        form = SkiMaterialListingForm(instance=listing)

    return render(request, 'edit_listing.html', {'form': form, 'listing': listing})


@login_required(login_url='login')
def delete_listing(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id)
    if listing.user != request.user:
        return HttpResponseForbidden('You can only delete your own listing.')

    if request.method == 'POST':
        listing.delete()
        return redirect('ski_material_listings')

    return redirect('listing_detail', id=id)

@login_required
def messages_view(request):
    users = get_user_model().objects.exclude(id=request.user.id).order_by('username')
    selected_user_id = request.GET.get('user')
    selected_user = None
    conversation = Message.objects.none()

    if selected_user_id:
        selected_user = get_object_or_404(get_user_model(), id=selected_user_id)
        conversation = Message.objects.filter(
            Q(sender=request.user, recipient=selected_user) |
            Q(sender=selected_user, recipient=request.user)
        ).order_by('created_at')
        Message.objects.filter(sender=selected_user, recipient=request.user, is_read=False).update(is_read=True)

    if request.method == 'POST' and selected_user:
        form = MessageForm(request.POST)
        if form.is_valid():
            msg = form.save(commit=False)
            msg.sender = request.user
            msg.recipient = selected_user
            msg.save()
            return redirect(f"/messages/?user={selected_user.id}")
    else:
        initial = {'subject': f'Conversation with {selected_user.username}'} if selected_user else {}
        form = MessageForm(initial=initial)

    conversation_list = []
    involved_messages = Message.objects.filter(Q(sender=request.user) | Q(recipient=request.user)).order_by('-created_at')
    seen = set()
    for msg in involved_messages:
        other_user = msg.recipient if msg.sender == request.user else msg.sender
        if other_user.id in seen:
            continue
        seen.add(other_user.id)
        conversation_list.append({'user': other_user, 'last_message': msg})

    unread_count = Message.objects.filter(recipient=request.user, is_read=False).count()

    return render(request, 'messages/messages.html', {
        'conversation_list': conversation_list,
        'conversation': conversation,
        'selected_user': selected_user,
        'all_users': users,
        'form': form,
        'unread_count': unread_count,
    })

def getMessagesAndCount(request):
    messages = Message.objects.filter(recipient=request.user).order_by('-created_at')
    
    unread_messages = messages.filter(is_read=False)
    unread_messages.update(is_read=True)

    unread_count = messages.filter(is_read=False).count()
    return messages,unread_count


@login_required
def become_instructor(request):
    profile, _ = InstructorProfile.objects.get_or_create(user=request.user)
    if request.method == 'POST':
        form = InstructorProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            return redirect('instructor_services')
    else:
        form = InstructorProfileForm(instance=profile)

    return render(request, 'instructor_register.html', {'form': form, 'profile': profile})


@login_required
def instructor_services_view(request):
    profile, _ = InstructorProfile.objects.get_or_create(user=request.user)
    services = InstructorService.objects.filter(instructor=profile).order_by('-created_at')

    if request.method == 'POST':
        form = InstructorServiceForm(request.POST)
        if form.is_valid():
            service = form.save(commit=False)
            service.instructor = profile
            service.save()
            return redirect('instructor_services')
    else:
        form = InstructorServiceForm()

    return render(request, 'instructor_services.html', {
        'profile': profile,
        'form': form,
        'services': services,
    })


@login_required(login_url='login')
def edit_instructor_service(request, service_id):
    service = get_object_or_404(InstructorService, id=service_id)
    if service.instructor.user != request.user:
        return HttpResponseForbidden('You can only edit your own instructor service.')

    if request.method == 'POST':
        form = InstructorServiceForm(request.POST, instance=service)
        if form.is_valid():
            form.save()
            return redirect('instructor_services')
    else:
        form = InstructorServiceForm(instance=service)

    return render(request, 'edit_instructor_service.html', {'form': form, 'service': service})


@login_required(login_url='login')
def delete_instructor_service(request, service_id):
    service = get_object_or_404(InstructorService, id=service_id)
    if service.instructor.user != request.user:
        return HttpResponseForbidden('You can only delete your own instructor service.')

    if request.method == 'POST':
        service.delete()
        return redirect('instructor_services')

    return redirect('instructor_services')


def instructors_list(request):
    if request.method == 'POST':
        if not request.user.is_authenticated:
            return redirect('login')
        if request.POST.get('form_type') == 'instructor_review':
            instructor_id = request.POST.get('instructor_id')
            instructor = get_object_or_404(InstructorProfile, id=instructor_id, is_active=True)
            review_form = InstructorReviewForm(request.POST)
            if review_form.is_valid():
                InstructorReview.objects.update_or_create(
                    instructor=instructor,
                    user=request.user,
                    defaults={
                        'rating': review_form.cleaned_data['rating'],
                        'comment': review_form.cleaned_data.get('comment', ''),
                    }
                )
                query_string = request.META.get('QUERY_STRING', '')
                return redirect(f"{request.path}?{query_string}" if query_string else request.path)

    search_query = request.GET.get('q', '').strip()
    station_id = request.GET.get('station', '').strip()
    price_min = request.GET.get('price_min', '').strip()
    price_max = request.GET.get('price_max', '').strip()
    my_offers = request.GET.get('my_offers')
    sort_by = request.GET.get('sort', 'relevance').strip()

    profiles = (
        InstructorProfile.objects
        .select_related('user')
        .filter(is_active=True, services__is_active=True)
    )

    if search_query:
        profiles = profiles.filter(
            Q(user__username__icontains=search_query)
            | Q(bio__icontains=search_query)
            | Q(certifications__icontains=search_query)
            | Q(services__title__icontains=search_query)
            | Q(services__description__icontains=search_query)
            | Q(services__ski_station__name__icontains=search_query)
        )

    if station_id:
        profiles = profiles.filter(services__ski_station_id=station_id)

    if price_min:
        try:
            profiles = profiles.filter(services__amount__gte=float(price_min))
        except (TypeError, ValueError):
            pass

    if price_max:
        try:
            profiles = profiles.filter(services__amount__lte=float(price_max))
        except (TypeError, ValueError):
            pass

    if my_offers and request.user.is_authenticated:
        profiles = profiles.filter(user=request.user)

    profiles = (
        profiles
        .annotate(
            active_services_count=Count('services', filter=Q(services__is_active=True), distinct=True),
            min_amount=Min('services__amount', filter=Q(services__is_active=True)),
            avg_rating=Avg('reviews__rating'),
            review_count=Count('reviews', distinct=True),
        )
        .prefetch_related('services__ski_station')
        .distinct()
    )

    if sort_by == 'price_asc':
        profiles = profiles.order_by('min_amount', '-active_services_count', '-created_at')
    elif sort_by == 'price_desc':
        profiles = profiles.order_by('-min_amount', '-active_services_count', '-created_at')
    elif sort_by == 'experience_desc':
        profiles = profiles.order_by('-years_experience', '-active_services_count', '-created_at')
    elif sort_by == 'newest':
        profiles = profiles.order_by('-created_at')
    else:
        sort_by = 'relevance'
        profiles = profiles.order_by('-active_services_count', '-years_experience', '-created_at')

    stations = SkiStation.objects.order_by('distanceFromGrenoble')

    profiles = list(profiles)
    user_review_map = {}
    if request.user.is_authenticated and profiles:
        review_qs = InstructorReview.objects.filter(user=request.user, instructor__in=profiles)
        user_review_map = {review.instructor_id: review for review in review_qs}

    for instructor in profiles:
        average = instructor.avg_rating
        instructor.avg_rating_rounded = int(round(float(average))) if average is not None else 0
        instructor.current_user_review = user_review_map.get(instructor.id)

    return render(request, 'instructors.html', {
        'instructors': profiles,
        'stations': stations,
        'search_query': search_query,
        'selected_station': station_id,
        'price_min': price_min,
        'price_max': price_max,
        'my_offers': my_offers,
        'sort_by': sort_by,
        'review_form': InstructorReviewForm(),
    })

@login_required
def profile_view(request):
    user = request.user  # Get the current user

    profile_picture_data = None
    try:
        user_profile = UserProfile.objects.get(user=user)  # Get the user profile
        if user_profile.profile_picture:
            # Convert binary data to base64 string for rendering in the template
            profile_picture_data = base64.b64encode(user_profile.profile_picture).decode('utf-8')
    except UserProfile.DoesNotExist:
        pass  # Handle case where UserProfile does not exist for the user

    if request.method == 'POST':
        form = ProfileForm(request.POST, request.FILES, instance=user)  # Add request.FILES
        if form.is_valid():
            form.save()  # Save the form data and profile picture
            return redirect('profile')  # Redirect to the profile page after saving
    else:
        form = ProfileForm(instance=user)  # Pass the instance here as well

    return render(request, 'profile.html', {
        'form': form,
        'profile_picture': profile_picture_data  # Pass the profile picture in base64
    })

@login_required
def delete_account(request):
    if request.method != 'POST':
        return redirect('profile')
    user = request.user
    user.delete()
    messages.success(request, 'Your account has been deleted successfully.')
    return redirect('my_template_view')
