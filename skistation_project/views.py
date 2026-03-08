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
    Message,
    UserProfile,
)
from django.db.models import Sum
from django.db.models import Q
from django.urls import reverse
from django.contrib.auth.forms import UserCreationForm
from .forms import SkiMaterialListingForm, ProfileForm
from allauth.socialaccount.providers.google.views import OAuth2LoginView
from allauth.socialaccount.providers.google.views import OAuth2CallbackView
from allauth.socialaccount.adapter import DefaultSocialAccountAdapter
from allauth.account.adapter import DefaultAccountAdapter
from django.contrib.auth.decorators import login_required
from django.contrib import messages
import base64
from django.utils.translation import check_for_language
from django.utils import translation
from django.utils.http import url_has_allowed_host_and_scheme


def home(request):

    queryset = SkiStation.objects.annotate(num_circuits=Sum('skicircuit__num_pistes'))

    print(queryset.query)  # This will print the SQL query in the console
    for station in queryset:
        print(f"{station.name}: {station.num_circuits} circuits")

    random_ski_stations = queryset.order_by('?')

    return render(request, 'index.html', {'ski_stations': random_ski_stations, 'all': queryset})


def ski_station_detail(request, station_id):
    ski_station = get_object_or_404(SkiStation.objects.annotate(num_circuits=Sum('skicircuit__num_pistes')), id=station_id)
    bus_lines = BusLine.objects.filter(ski_station=ski_station)
    service_stores = ServiceStore.objects.filter(ski_station=ski_station)
    ski_circuits = SkiCircuit.objects.filter(ski_station=ski_station)

    context = {
        'station': ski_station,
        'bus_lines': bus_lines,
        'service_stores': service_stores,
        'ski_circuits': ski_circuits,
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
    # Récupérer toutes les lignes de bus
    bus_lines = BusLine.objects.all()

    context = {
        'bus_lines': bus_lines,
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
    all_listings = SkiMaterialListing.objects.select_related('user', 'ski_station').prefetch_related('images').order_by('-posted_at')

    q = request.GET.get('q', '').strip()
    transaction_type = request.GET.get('transaction_type', '').strip()
    material_type = request.GET.get('material_type', '').strip()
    condition = request.GET.get('condition', '').strip()
    city = request.GET.get('city', '').strip()
    min_price = request.GET.get('min_price', '').strip()
    max_price = request.GET.get('max_price', '').strip()
    mine_only = request.GET.get('my_listings', '').strip().lower() in ('1', 'true', 'yes')

    if q:
        all_listings = all_listings.filter(
            Q(title__icontains=q)
            | Q(description__icontains=q)
            | Q(city__icontains=q)
            | Q(brand__icontains=q)
            | Q(size__icontains=q)
            | Q(user__username__icontains=q)
            | Q(ski_station__name__icontains=q)
        )
    if transaction_type:
        all_listings = all_listings.filter(transaction_type=transaction_type)
    if material_type:
        all_listings = all_listings.filter(material_type=material_type)
    if condition:
        all_listings = all_listings.filter(condition=condition)
    if city:
        all_listings = all_listings.filter(city__icontains=city)
    if min_price:
        all_listings = all_listings.filter(price__gte=min_price)
    if max_price:
        all_listings = all_listings.filter(price__lte=max_price)
    if mine_only:
        all_listings = all_listings.filter(user=request.user)

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

    my_listings = all_listings.filter(user=request.user)
    marketplace_listings = all_listings.exclude(user=request.user)

    return render(
        request,
        'ski_material_listings.html',
        {
            'form': form,
            'listings': all_listings,
            'my_listings': my_listings,
            'marketplace_listings': marketplace_listings,
            'mine_only': mine_only,
            'selected_transaction_type': transaction_type,
            'selected_material_type': material_type,
            'selected_condition': condition,
            'query_text': q,
            'min_price': min_price,
            'max_price': max_price,
            'city': city,
            'material_choices': SkiMaterialListing.MATERIAL_CHOICES,
            'condition_choices': SkiMaterialListing.CONDITION_CHOICES,
            'transaction_choices': SkiMaterialListing.TRANSACTION_CHOICES,
        },
    )

def listing_detail(request, id):
    listing = get_object_or_404(SkiMaterialListing, id=id)
    gallery_images = listing.images.all()

    if request.method == 'POST' and request.user.is_authenticated and request.user != listing.user:
        body = request.POST.get('body', '').strip()
        if body:
            Message.objects.create(
                sender=request.user,
                recipient=listing.user,
                subject=f"À propos de: {listing.title}",
                body=body,
            )
            messages.success(
                request,
                'Message envoye au vendeur.'
            )
            return redirect('listing_detail', id=listing.id)

    return render(request, 'listing_detail.html', {'listing': listing, 'gallery_images': gallery_images})


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
    messages.info(request, 'Suppression indisponible pour le moment.')
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
