from django.db import models

class SkiStation(models.Model):
    name = models.CharField(max_length=100)
    latitude = models.DecimalField(max_digits=8, decimal_places=6)
    longitude = models.DecimalField(max_digits=9, decimal_places=6)
    capacity = models.IntegerField(null=True)
    image = models.BinaryField(null=True, blank=True) 

    def __str__(self):
        return self.name


class BusLine(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, null=True)
    bus_number = models.CharField(max_length=50)
    departure_stop = models.CharField(max_length=100)
    arrival_stop = models.CharField(max_length=100)
    frequency = models.CharField(max_length=50, null=True)
    travel_time = models.CharField(max_length=50, null=True)

    def __str__(self):
        return self.bus_number


class ServiceStore(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, null=True)
    name = models.CharField(max_length=100)
    latitude = models.DecimalField(max_digits=8, decimal_places=6)
    longitude = models.DecimalField(max_digits=9, decimal_places=6)
    type = models.CharField(max_length=50)
    opening_hours = models.CharField(max_length=100, null=True)

    def __str__(self):
        return self.name


class SkiCircuit(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, null=True)
    difficulty = models.CharField(max_length=20)
    num_pistes = models.IntegerField()

    def __str__(self):
        return f"{self.difficulty} - {self.num_pistes} pistes"
