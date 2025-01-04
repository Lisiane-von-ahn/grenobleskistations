from django import forms
from django.contrib.auth.models import User
from api.models import SkiMaterialListing,UserProfile
from django.db import models
from django.contrib.auth.models import User
from io import BytesIO
from django.core.files.base import ContentFile
from PIL import Image
import os
from django.contrib.auth import get_user_model

class UserRegistrationForm(forms.Form):
    username = forms.CharField(max_length=100)
    email = forms.EmailField()
    password1 = forms.CharField(widget=forms.PasswordInput)
    password2 = forms.CharField(widget=forms.PasswordInput)

    def clean(self):
        cleaned_data = super().clean()
        password1 = cleaned_data.get("password1")
        password2 = cleaned_data.get("password2")
        
        if password1 != password2:
            raise forms.ValidationError("The passwords do not match.")

class SkiMaterialListingForm(forms.ModelForm):
    image_file = forms.ImageField(required=False)  

    class Meta:
        model = SkiMaterialListing
        fields = ['title', 'description', 'ski_station', 'city', 'price', 'material_type','image']

    def __init__(self, *args, **kwargs):
        super(SkiMaterialListingForm, self).__init__(*args, **kwargs)
        # Ajout des classes Bootstrap à chaque champ
        self.fields['title'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Titre de l\'article'})
        self.fields['description'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Description'})
        self.fields['ski_station'].widget.attrs.update({'class': 'form-control'})
        self.fields['city'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Ville de prise en charge'})
        self.fields['price'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Prix'})
        self.fields['material_type'].widget.attrs.update({'class': 'form-control'})
        self.fields['image'].widget.attrs.update({'class': 'form-control'})
    
    def save(self, commit=True):
        instance = super(SkiMaterialListingForm, self).save(commit=False)
        image_file = self.cleaned_data.get('image_file')

        if image_file:
            instance.image = image_file.read()  # Convert image to binary

        if commit:
            instance.save()
        return instance
    
class ProfileForm(forms.ModelForm):
    profile_picture = forms.ImageField(required=False)  # Image field for the profile picture

    class Meta:
        model = get_user_model()  # This uses the custom User model
        fields = ['first_name', 'last_name', 'email','profile_picture']

    def save(self, commit=True):
        instance = super(ProfileForm, self).save(commit=False)

        # Handle profile picture if included
        if 'profile_picture' in self.cleaned_data and self.cleaned_data['profile_picture']:
            profile_picture = self.cleaned_data['profile_picture']

            # Convert the InMemoryUploadedFile to bytes-like object (i.e. binary format)
            img = Image.open(profile_picture)
            img_io = BytesIO()
            img.save(img_io, format='PNG')  # Save the image as PNG (or other formats)
            img_io.seek(0)

            # Create or update the UserProfile if profile picture exists
            user_profile, created = UserProfile.objects.get_or_create(user=instance)
            user_profile.profile_picture = img_io.read()  # Save the image as binary
            user_profile.save()

        if commit:
            instance.save()

        return instance
        