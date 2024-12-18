from django.contrib import admin
from django import forms
import io
from .models import SkiStation, BusLine, ServiceStore, SkiCircuit

class SkiStationForm(forms.ModelForm):
    class Meta:
        model = SkiStation
        fields = '__all__'

    image_file = forms.ImageField(required=False)

    def clean_image_file(self):
        image = self.cleaned_data.get('image_file')
        if image:
            # Convert image file to binary data
            image_data = image.read()
            return image_data
        return None

    def save(self, commit=True):
        instance = super().save(commit=False)
        image_data = self.cleaned_data.get('image_file')
        if image_data:
            instance.image = image_data
        if commit:
            instance.save()
        return instance

class SkiStationAdmin(admin.ModelAdmin):
    form = SkiStationForm
    list_display = ('name', 'capacity', 'latitude', 'longitude')


admin.site.register(SkiStation, SkiStationAdmin)
admin.site.register(BusLine)
admin.site.register(ServiceStore)
admin.site.register(SkiCircuit)
