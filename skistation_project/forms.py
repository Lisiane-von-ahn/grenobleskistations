from django import forms
from django.utils.translation import gettext_lazy as _
from django.utils.translation import get_language
from api.models import (
    SkiMaterialListing,
    UserProfile,
    SkiMaterialImage,
    PisteConditionReport,
    SnowConditionUpdate,
)
from io import BytesIO
from PIL import Image
from django.contrib.auth import get_user_model
from allauth.account.forms import LoginForm, SignupForm


class MultipleFileInput(forms.ClearableFileInput):
    allow_multiple_selected = True


class MultipleFileField(forms.FileField):
    widget = MultipleFileInput


def get_marketplace_choices(lang_code=None):
    lang = (lang_code or get_language() or 'fr').lower()
    is_en = lang.startswith('en')

    if is_en:
        return {
            'transaction_type': [
                ('sale', 'For sale'),
                ('rent', 'For rent'),
                ('lend', 'For loan'),
            ],
            'material_type': [
                ('ski', 'Skis'),
                ('boots', 'Boots'),
                ('helmet', 'Helmet'),
                ('jacket', 'Jacket'),
                ('pants', 'Pants'),
                ('gloves', 'Gloves'),
                ('goggles', 'Goggles'),
                ('other', 'Other'),
            ],
            'condition': [
                ('new', 'New'),
                ('excellent', 'Excellent'),
                ('good', 'Good'),
                ('fair', 'Fair'),
            ],
        }

    return {
        'transaction_type': [
            ('sale', 'A vendre'),
            ('rent', 'A louer'),
            ('lend', 'A preter'),
        ],
        'material_type': [
            ('ski', 'Skis'),
            ('boots', 'Chaussures'),
            ('helmet', 'Casque'),
            ('jacket', 'Veste'),
            ('pants', 'Pantalon'),
            ('gloves', 'Gants'),
            ('goggles', 'Masque'),
            ('other', 'Autre'),
        ],
        'condition': [
            ('new', 'Neuf'),
            ('excellent', 'Excellent'),
            ('good', 'Bon'),
            ('fair', 'Correct'),
        ],
    }


class CustomLoginForm(LoginForm):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        if 'login' in self.fields:
            self.fields['login'].widget.attrs.update({
                'class': 'form-control',
                'placeholder': 'Email',
                'autocomplete': 'email',
            })
        if 'password' in self.fields:
            self.fields['password'].widget.attrs.update({
                'class': 'form-control',
                'placeholder': 'Mot de passe / Password',
                'autocomplete': 'current-password',
            })


class CustomSignupForm(SignupForm):
    first_name = forms.CharField(max_length=150, required=False)
    last_name = forms.CharField(max_length=150, required=False)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        for field_name in ('email', 'password1', 'password2', 'first_name', 'last_name'):
            if field_name in self.fields:
                self.fields[field_name].widget.attrs.update({'class': 'form-control'})

    def save(self, request):
        user = super().save(request)
        email = (user.email or '').strip().lower()
        if email and user.username != email:
            user.username = email
        user.first_name = self.cleaned_data.get('first_name', '').strip()
        user.last_name = self.cleaned_data.get('last_name', '').strip()
        user.save(update_fields=['username', 'first_name', 'last_name'])
        return user

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
    images = MultipleFileField(
        required=False,
        widget=MultipleFileInput(attrs={'class': 'visually-hidden', 'id': 'extra-images-input', 'accept': 'image/*'})
    )

    class Meta:
        model = SkiMaterialListing
        fields = [
            'title',
            'description',
            'ski_station',
            'city',
            'price',
            'material_type',
            'transaction_type',
            'condition',
            'brand',
            'size',
            'image',
        ]

    def __init__(self, *args, **kwargs):
        super(SkiMaterialListingForm, self).__init__(*args, **kwargs)
        lang = (get_language() or 'fr').lower()
        is_en = lang.startswith('en')
        choices = get_marketplace_choices(lang)

        self.fields['title'].widget.attrs.update({'class': 'form-control form-control-lg', 'placeholder': 'Title' if is_en else 'Titre'})
        self.fields['description'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Describe the item condition, age, and usage' if is_en else 'Decrivez l\'etat, l\'anciennete et l\'usage'})
        self.fields['description'].widget.attrs.update({'rows': 4})
        self.fields['ski_station'].widget.attrs.update({'class': 'form-select'})
        self.fields['city'].widget.attrs.update({'class': 'form-control', 'placeholder': 'City' if is_en else 'Ville'})
        self.fields['price'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Price in EUR' if is_en else 'Prix en EUR'})
        self.fields['material_type'].widget.attrs.update({'class': 'form-select'})
        self.fields['transaction_type'].widget.attrs.update({'class': 'form-select'})
        self.fields['condition'].widget.attrs.update({'class': 'form-select'})
        self.fields['brand'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Brand (optional)' if is_en else 'Marque (optionnel)'})
        self.fields['size'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Size (optional)' if is_en else 'Taille (optionnel)'})
        self.fields['image'].widget.attrs.update({'class': 'visually-hidden', 'id': 'main-image-input', 'accept': 'image/*'})

        self.fields['transaction_type'].choices = choices['transaction_type']
        self.fields['material_type'].choices = choices['material_type']
        self.fields['condition'].choices = choices['condition']

        self.fields['title'].label = _('Titre') if not is_en else _('Title')
        self.fields['description'].label = _('Description')
        self.fields['ski_station'].label = _('Station de ski') if not is_en else _('Ski station')
        self.fields['city'].label = _('Ville') if not is_en else _('City')
        self.fields['price'].label = _('Prix') if not is_en else _('Price')
        self.fields['material_type'].label = _('Categorie') if not is_en else _('Category')
        self.fields['transaction_type'].label = _('Type d\'offre') if not is_en else _('Offer type')
        self.fields['condition'].label = _('Etat') if not is_en else _('Condition')
        self.fields['brand'].label = _('Marque') if not is_en else _('Brand')
        self.fields['size'].label = _('Taille') if not is_en else _('Size')
        self.fields['image'].label = _('Photo principale') if not is_en else _('Main photo')
        self.fields['images'].label = _('Photos supplementaires') if not is_en else _('Extra photos')
    
    def save(self, commit=True):
        instance = super(SkiMaterialListingForm, self).save(commit=False)
        main_image = self.cleaned_data.get('image')
        image_file = self.cleaned_data.get('image_file')

        if main_image and hasattr(main_image, 'read'):
            main_image.seek(0)
            instance.image = main_image.read()
        elif main_image:
            instance.image = main_image

        if image_file:
            instance.image = image_file.read()  # Convert image to binary

        if commit:
            instance.save()
        return instance

    def save_extra_images(self, listing, uploaded_files):
        for uploaded in uploaded_files:
            uploaded.seek(0)
            image_bytes = uploaded.read()
            if image_bytes:
                SkiMaterialImage.objects.create(listing=listing, image=image_bytes)
    
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
        
class MaterielForm(forms.ModelForm):
    class Meta:
        model = SkiMaterialListing
        fields = [
            'title',
            'description',
            'city',
            'material_type',
            'transaction_type',
            'condition',
            'brand',
            'size',
            'price',
            'image',
        ]
        widgets = {
            'description': forms.Textarea(attrs={'rows': 4}),
            'price': forms.NumberInput(attrs={'step': '0.01'}),
        }


class PisteConditionReportForm(forms.ModelForm):
    class Meta:
        model = PisteConditionReport
        fields = ['piste_rating', 'comment']
        widgets = {
            'comment': forms.Textarea(attrs={'class': 'form-control', 'rows': 2, 'placeholder': _('Partagez les conditions de piste...')}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['piste_rating'].required = True
        self.fields['comment'].required = False
        self.fields['comment'].label = _('Commentaire')


class SnowConditionUpdateForm(forms.ModelForm):
    image_file = forms.ImageField(required=False)

    class Meta:
        model = SnowConditionUpdate
        fields = ['note', 'snow_depth_cm']
        widgets = {
            'note': forms.Textarea(attrs={'class': 'form-control', 'rows': 2, 'placeholder': _('Ex: neige fraiche ce matin, piste centrale verglacee...')}),
            'snow_depth_cm': forms.NumberInput(attrs={'class': 'form-control', 'min': '0', 'step': '1'}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['note'].required = False
        self.fields['snow_depth_cm'].required = False
        self.fields['note'].label = _('Commentaire')
        self.fields['snow_depth_cm'].label = _('Hauteur de neige (cm)')
        self.fields['image_file'].label = _('Photo')
