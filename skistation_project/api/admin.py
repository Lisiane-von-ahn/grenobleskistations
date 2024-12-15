from django.contrib import admin
from .models import SkiStation, BusLine, ServiceStore, SkiCircuit

admin.site.register(SkiStation)
admin.site.register(BusLine)
admin.site.register(ServiceStore)
admin.site.register(SkiCircuit)
