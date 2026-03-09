from django.shortcuts import render, get_object_or_404, redirect
from django.http import HttpResponseRedirect
from django.views.decorators.http import require_GET
from django.conf import settings
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
    UserProfile,
)
from django.db.models import Sum
from django.db.models import Q
from django.db.models import Avg
from django.db.models import Count
from django.db.models import Value
from django.db.models import IntegerField
from django.urls import reverse
from django.contrib.auth.forms import UserCreationForm
from django.core.paginator import Paginator
from .forms import (
    SkiMaterialListingForm,
    ProfileForm,
    PisteConditionReportForm,
    SnowConditionUpdateForm,
    get_marketplace_choices,
)
from allauth.socialaccount.providers.google.views import OAuth2LoginView
from allauth.socialaccount.providers.google.views import OAuth2CallbackView
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter
from allauth.account.adapter import DefaultAccountAdapter
from django.contrib.auth.decorators import login_required
from django.contrib import messages
import base64
import json
from datetime import timedelta
from urllib.parse import quote_plus
import re
from django.utils.translation import check_for_language
from django.utils import translation
from django.utils.translation import gettext as _
from django.utils.http import url_has_allowed_host_and_scheme
from django.utils import timezone
from allauth.socialaccount.models import SocialAccount


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
    # Récupérer toutes les stations de ski
    ski_stations = SkiStation.objects.all()

    # Récupérer tous les types distincts de services
    service_types = ServiceStore.objects.values_list('type', flat=True).distinct()

    # Filtrer les services en fonction des critères de recherche
    services = ServiceStore.objects.all()
    name = request.GET.get('name', '')
    service_type = request.GET.get('type', '')
    ski_station_id = request.GET.get('ski_station', '')

    if name:
        services = services.filter(name__icontains=name)
    if service_type:
        services = services.filter(type=service_type)
    if ski_station_id:
        services = services.filter(ski_station_id=ski_station_id)

    context = {
        'services': services,
        'service_types': service_types,
        'ski_stations': ski_stations,
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
        form_type = request.POST.get('form_type', 'create').strip()
        if form_type == 'create':
            title = request.POST.get('title', '').strip()
            message_body = request.POST.get('message', '').strip()
            skill_level = request.POST.get('skill_level', SkiPartnerPost.LEVEL_INTERMEDIATE).strip()
            preferred_date = request.POST.get('preferred_date', '').strip() or None
            city_post = request.POST.get('city', '').strip()
            station_post = request.POST.get('ski_station', '').strip() or None

            valid_levels = {choice[0] for choice in SkiPartnerPost.LEVEL_CHOICES}
            if not title or not message_body:
                messages.error(request, 'Titre et description requis.')
            elif skill_level not in valid_levels:
                messages.error(request, 'Niveau invalide.')
            else:
                SkiPartnerPost.objects.create(
                    user=request.user,
                    ski_station_id=int(station_post) if station_post and station_post.isdigit() else None,
                    title=title[:120],
                    message=message_body,
                    city=city_post[:80],
                    skill_level=skill_level,
                    preferred_date=preferred_date,
                )
                messages.success(request, 'Annonce partenaire publiee.')
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

    return render(
        request,
        'ski_partners.html',
        {
            'partner_posts': posts[:60],
            'stations': SkiStation.objects.order_by('name'),
            'selected_station': station_id,
            'selected_level': level,
            'selected_city': city,
            'level_choices': SkiPartnerPost.LEVEL_CHOICES,
        },
    )


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
    inbox_messages, unread_count = getMessagesAndCount(request)
    sent_messages = Message.objects.filter(sender=request.user).exclude(recipient=request.user).order_by('-created_at')

    return render(request, 'messages/messages.html', {
        'messages': inbox_messages,
        'sent_messages': sent_messages,
        'unread_count': unread_count,
    })

def getMessagesAndCount(request):
    messages = Message.objects.filter(recipient=request.user).order_by('-created_at')

    unread_count = messages.filter(is_read=False).count()
    messages.filter(is_read=False).update(is_read=True)
    return messages,unread_count

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
    return render(request, 'instructors.html')


@login_required
def become_instructor(request):
    if request.method == 'POST':
        messages.success(request, 'Demande moniteur enregistree.')
        return redirect('instructors')
    return render(request, 'instructor_register.html')


@login_required
def instructor_services_view(request):
    return render(request, 'instructor_services.html')


@login_required
def edit_instructor_service(request, service_id):
    messages.info(request, 'Edition service moniteur indisponible pour le moment.')
    return redirect('instructor_services')


@login_required
def delete_instructor_service(request, service_id):
    messages.info(request, 'Suppression service moniteur indisponible pour le moment.')
    return redirect('instructor_services')
