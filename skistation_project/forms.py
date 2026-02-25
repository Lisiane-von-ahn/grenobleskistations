from django import forms
from django.contrib.auth.models import User
from allauth.account.forms import LoginForm, SignupForm
from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from api.models import SkiMaterialListing, UserProfile, InstructorProfile, InstructorService, InstructorReview, Message, SnowConditionUpdate, PisteConditionReport, CrowdStatusUpdate
from django.db import models
from django.contrib.auth.models import User
from io import BytesIO
from django.core.files.base import ContentFile
from PIL import Image
import os
from django.contrib.auth import get_user_model

class UserRegistrationForm(forms.Form):
    first_name = forms.CharField(max_length=100, required=False)
    last_name = forms.CharField(max_length=100, required=False)
    email = forms.EmailField()
    password1 = forms.CharField(widget=forms.PasswordInput)
    password2 = forms.CharField(widget=forms.PasswordInput)

    def clean(self):
        cleaned_data = super().clean()
        password1 = cleaned_data.get("password1")
        password2 = cleaned_data.get("password2")
        
        if password1 != password2:
            raise forms.ValidationError("The passwords do not match.")


class CustomSignupForm(SignupForm):
    first_name = forms.CharField(max_length=150, required=False, label='Prénom')
    last_name = forms.CharField(max_length=150, required=False, label='Nom')

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['email'].label = 'Email'
        self.fields['email'].widget.attrs.update({'placeholder': 'you@example.com', 'autocomplete': 'email'})
        self.fields['first_name'].widget.attrs.update({'placeholder': 'Prénom', 'autocomplete': 'given-name'})
        self.fields['last_name'].widget.attrs.update({'placeholder': 'Nom', 'autocomplete': 'family-name'})

    def clean_email(self):
        email = (self.cleaned_data.get('email') or '').strip().lower()
        user_model = get_user_model()

        if user_model.objects.filter(email__iexact=email).exists():
            raise ValidationError("Un compte existe déjà avec cet email.")

        if user_model.objects.filter(username__iexact=email).exists():
            raise ValidationError("Cet identifiant email est déjà utilisé.")

        return email

    def save(self, request):
        user = super().save(request)
        user.username = user.email
        user.first_name = self.cleaned_data.get('first_name', '').strip()
        user.last_name = self.cleaned_data.get('last_name', '').strip()
        user.save(update_fields=['username', 'first_name', 'last_name'])
        return user


class CustomLoginForm(LoginForm):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        if 'login' in self.fields:
            self.fields['login'].label = 'Email'
            self.fields['login'].widget.attrs.update({'placeholder': 'you@example.com', 'autocomplete': 'email'})

class SkiMaterialListingForm(forms.ModelForm):
    image_file = forms.ImageField(required=False)  

    class Meta:
        model = SkiMaterialListing
        fields = [
            'title',
            'description',
            'ski_station',
            'city',
            'material_type',
            'listing_type',
            'is_free',
            'price',
            'deposit_amount',
            'available_from',
            'available_to',
            'image'
        ]

    def __init__(self, *args, **kwargs):
        super(SkiMaterialListingForm, self).__init__(*args, **kwargs)
        # Ajout des classes Bootstrap à chaque champ
        self.fields['title'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Titre de l\'article'})
        self.fields['description'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Description'})
        self.fields['ski_station'].widget.attrs.update({'class': 'form-control'})
        self.fields['city'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Ville de prise en charge'})
        self.fields['price'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Prix'})
        self.fields['material_type'].widget.attrs.update({'class': 'form-control'})
        self.fields['listing_type'].widget.attrs.update({'class': 'form-control'})
        self.fields['is_free'].widget.attrs.update({'class': 'form-check-input'})
        self.fields['deposit_amount'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Caution (optionnelle)'})
        self.fields['available_from'].widget = forms.DateInput(attrs={'class': 'form-control', 'type': 'date'})
        self.fields['available_to'].widget = forms.DateInput(attrs={'class': 'form-control', 'type': 'date'})
        self.fields['image'].widget.attrs.update({'class': 'form-control'})

    def clean(self):
        cleaned_data = super().clean()
        is_free = cleaned_data.get('is_free')
        price = cleaned_data.get('price')
        if not is_free and not price:
            self.add_error('price', 'A price is required for paid listings.')
        if is_free:
            cleaned_data['price'] = None
        return cleaned_data
    
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


class InstructorProfileForm(forms.ModelForm):
    image_file = forms.ImageField(
        required=False,
        widget=forms.ClearableFileInput(attrs={
            'class': 'form-control',
            'accept': 'image/*',
            'capture': 'user',
        })
    )

    class Meta:
        model = InstructorProfile
        fields = ['bio', 'years_experience', 'certifications', 'phone', 'is_active']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['bio'].widget.attrs.update({'class': 'form-control', 'rows': 4})
        self.fields['years_experience'].widget.attrs.update({'class': 'form-control'})
        self.fields['certifications'].widget.attrs.update({'class': 'form-control'})
        self.fields['phone'].widget.attrs.update({'class': 'form-control'})
        self.fields['is_active'].widget.attrs.update({'class': 'form-check-input'})

    def save(self, commit=True):
        instance = super().save(commit=False)
        image_file = self.cleaned_data.get('image_file')
        if image_file:
            img = Image.open(image_file)
            img_io = BytesIO()
            img.save(img_io, format='PNG')
            img_io.seek(0)
            instance.profile_photo = img_io.read()
        if commit:
            instance.save()
        return instance


class InstructorServiceForm(forms.ModelForm):
    class Meta:
        model = InstructorService
        fields = ['ski_station', 'title', 'description', 'duration_minutes', 'amount', 'currency', 'max_group_size', 'is_active']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        for field in ['ski_station', 'title', 'description', 'duration_minutes', 'amount', 'currency', 'max_group_size']:
            self.fields[field].widget.attrs.update({'class': 'form-control'})
        self.fields['is_active'].widget.attrs.update({'class': 'form-check-input'})


class InstructorReviewForm(forms.ModelForm):
    class Meta:
        model = InstructorReview
        fields = ['rating', 'comment']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['rating'].widget = forms.NumberInput(attrs={
            'class': 'form-control',
            'min': 1,
            'max': 5,
        })
        self.fields['comment'].widget.attrs.update({
            'class': 'form-control',
            'rows': 2,
            'placeholder': 'Votre retour sur le cours, pédagogie, ponctualité...'
        })

    def clean_rating(self):
        rating = self.cleaned_data.get('rating')
        if rating is None or rating < 1 or rating > 5:
            raise forms.ValidationError('La note doit être entre 1 et 5.')
        return rating


class MessageForm(forms.ModelForm):
    class Meta:
        model = Message
        fields = ['subject', 'body']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['subject'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Subject'})
        self.fields['body'].widget.attrs.update({'class': 'form-control', 'rows': 4, 'placeholder': 'Write your message...'})


class SnowConditionUpdateForm(forms.ModelForm):
    image_file = forms.ImageField(
        required=False,
        widget=forms.ClearableFileInput(attrs={
            'class': 'form-control',
            'accept': 'image/*',
            'capture': 'environment',
        })
    )

    class Meta:
        model = SnowConditionUpdate
        fields = ['note', 'snow_depth_cm']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['note'].widget.attrs.update({'class': 'form-control', 'rows': 3, 'placeholder': 'Décrivez l\'état de la neige, météo, visibilité...'})
        self.fields['snow_depth_cm'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Hauteur de neige (cm)'})

    def save(self, commit=True):
        instance = super().save(commit=False)
        image_file = self.cleaned_data.get('image_file')
        if image_file:
            instance.image = image_file.read()
        if commit:
            instance.save()
        return instance


class PisteConditionReportForm(forms.ModelForm):
    class Meta:
        model = PisteConditionReport
        fields = ['piste_rating', 'comment']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['piste_rating'].widget = forms.NumberInput(attrs={
            'class': 'form-control',
            'min': 1,
            'max': 5,
            'placeholder': 'Note piste (1-5)'
        })
        self.fields['comment'].widget.attrs.update({'class': 'form-control', 'rows': 2, 'placeholder': 'Ex: neige agréable, quelques files au télésiège...'})

    def clean_piste_rating(self):
        rating = self.cleaned_data.get('piste_rating')
        if rating is None or rating < 1 or rating > 5:
            raise forms.ValidationError('La note doit être entre 1 et 5.')
        return rating


class CrowdStatusUpdateForm(forms.ModelForm):
    class Meta:
        model = CrowdStatusUpdate
        fields = ['crowd_level']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['crowd_level'].widget.attrs.update({'class': 'form-control'})
        