from django.db import models
from django.contrib.auth.models import User
from django.dispatch import receiver
from django.db.models.signals import post_save
from django.core.files.base import ContentFile
from io import BytesIO
from PIL import Image

class SkiStation(models.Model):
    name = models.CharField(max_length=100)
    latitude = models.DecimalField(max_digits=8, decimal_places=6)
    longitude = models.DecimalField(max_digits=9, decimal_places=6)
    capacity = models.IntegerField(null=True)
    image = models.BinaryField(null=True, blank=True) 
    altitude = models.IntegerField(null=False,default=1000)
    distanceFromGrenoble = models.IntegerField(null=False,default=100)

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

class SkiMaterialListing(models.Model):
    TYPE_CHOICES = [
        ('sale', 'For Sale'),
        ('donation', 'For Donation')
    ]
    
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    title = models.CharField(max_length=200)
    description = models.TextField()
    ski_station = models.ForeignKey(SkiStation, on_delete=models.SET_NULL, null=True, blank=True)
    city = models.CharField(max_length=100)  # New field to specify pickup city
    price = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)
    material_type = models.CharField(max_length=10, choices=TYPE_CHOICES)
    image = models.BinaryField(null=True, blank=True, editable=True) 
    posted_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.title


class Message(models.Model):
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_messages')
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_messages')
    subject = models.CharField(max_length=255)
    body = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    is_read = models.BooleanField(default=False)

    def __str__(self):
        return f"Message from {self.sender} to {self.recipient} on {self.created_at}"
    
class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile_api")
    profile_picture = models.BinaryField(null=True, blank=True)

    def __str__(self):
        return f'{self.user.username} Profile'

    def save(self, *args, **kwargs):
        if self.profile_picture and isinstance(self.profile_picture, Image.Image):
            # Convert the image to binary format before saving
            img = Image.open(self.profile_picture)
            img_io = BytesIO()
            img.save(img_io, format='PNG')  # Save the image as PNG or other formats you prefer
            img_io.seek(0)
            self.profile_picture = img_io.read()

        super(UserProfile, self).save(*args, **kwargs)

    def get_profile_picture(self):
        if self.profile_picture:
            return ContentFile(self.profile_picture)
        return None

    def has_profile_picture(self):
        return self.profile_picture is not None
        
@receiver(post_save, sender=User)
def create_user_profile(sender, instance, created, **kwargs):
    if created:
        UserProfile.objects.create(user=instance)

@receiver(post_save, sender=User)
def save_user_profile(sender, instance, **kwargs):
    try:
        instance.profile.save()    
    except:
        print ("Does not do anything")