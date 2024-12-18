from django.shortcuts import render
from api.models import SkiStation, BusLine, ServiceStore, SkiCircuit

def home(request):

    queryset = SkiStation.objects.all()

    random_ski_stations = SkiStation.objects.order_by('?')[:3]

    return render(request, 'index.html', {'ski_stations': random_ski_stations, 'all': queryset})

