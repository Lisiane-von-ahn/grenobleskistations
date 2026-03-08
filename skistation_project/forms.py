from django import forms
from api.models import SkiMaterialListing, UserProfile, SkiMaterialImage
from io import BytesIO
from PIL import Image
from django.contrib.auth import get_user_model


class MultipleFileInput(forms.ClearableFileInput):
    allow_multiple_selected = True


class MultipleFileField(forms.FileField):
    widget = MultipleFileInput

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
        widget=MultipleFileInput(attrs={'class': 'form-control'})
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
        # Ajout des classes Bootstrap à chaque champ
        self.fields['title'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Titre / Title'})
        self.fields['description'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Description / Description'})
        self.fields['ski_station'].widget.attrs.update({'class': 'form-control'})
        self.fields['city'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Ville / City'})
        self.fields['price'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Prix / Price'})
        self.fields['material_type'].widget.attrs.update({'class': 'form-control'})
        self.fields['transaction_type'].widget.attrs.update({'class': 'form-control'})
        self.fields['condition'].widget.attrs.update({'class': 'form-control'})
        self.fields['brand'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Marque / Brand'})
        self.fields['size'].widget.attrs.update({'class': 'form-control', 'placeholder': 'Taille / Size'})
        self.fields['image'].widget.attrs.update({'class': 'form-control'})

        self.fields['title'].label = 'Titre / Title'
        self.fields['description'].label = 'Description / Description'
        self.fields['ski_station'].label = 'Station de ski / Ski station'
        self.fields['city'].label = 'Ville / City'
        self.fields['price'].label = 'Prix / Price'
        self.fields['material_type'].label = 'Catégorie / Category'
        self.fields['transaction_type'].label = 'Type d\'offre / Offer type'
        self.fields['condition'].label = 'État / Condition'
        self.fields['brand'].label = 'Marque / Brand'
        self.fields['size'].label = 'Taille / Size'
        self.fields['image'].label = 'Photo principale / Main photo'
        self.fields['images'].label = 'Photos supplémentaires / Extra photos'
    
    def save(self, commit=True):
        instance = super(SkiMaterialListingForm, self).save(commit=False)
        image_file = self.cleaned_data.get('image_file')

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
