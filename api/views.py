from rest_framework import viewsets
from .models import SkiStation, BusLine, ServiceStore, SkiCircuit
from .serializers import SkiStationSerializer, BusLineSerializer, ServiceStoreSerializer, SkiCircuitSerializer

class SkiStationViewSet(viewsets.ModelViewSet):
    queryset = SkiStation.objects.all()
    serializer_class = SkiStationSerializer

class BusLineViewSet(viewsets.ModelViewSet):
    queryset = BusLine.objects.all()
    serializer_class = BusLineSerializer

class ServiceStoreViewSet(viewsets.ModelViewSet):
    queryset = ServiceStore.objects.all()
    serializer_class = ServiceStoreSerializer

class SkiCircuitViewSet(viewsets.ModelViewSet):
    queryset = SkiCircuit.objects.all()
    serializer_class = SkiCircuitSerializer
