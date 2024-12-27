from django.shortcuts import render, get_object_or_404
from api.models import SkiStation, BusLine, ServiceStore, SkiCircuit
from django.db.models import Sum

def home(request):

    queryset = SkiStation.objects.annotate(num_circuits=Sum('skicircuit__num_pistes'))

    print(queryset.query)  # This will print the SQL query in the console
    for station in queryset:
        print(f"{station.name}: {station.num_circuits} circuits")

    random_ski_stations = queryset.order_by('?')[:4]

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

