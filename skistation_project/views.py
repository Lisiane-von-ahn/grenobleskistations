from django.shortcuts import render, get_object_or_404
from api.models import SkiStation, BusLine, ServiceStore, SkiCircuit
from django.db.models import Sum

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




