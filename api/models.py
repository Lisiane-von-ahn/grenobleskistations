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
    frequency = models.CharField(max_length=120, null=True)
    travel_time = models.CharField(max_length=50, null=True)
    route_points = models.CharField(max_length=255, null=True, blank=True)
    departure_latitude = models.DecimalField(max_digits=8, decimal_places=6, null=True, blank=True)
    departure_longitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)

    def __str__(self):
        return self.bus_number


class ServiceStore(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, null=True)
    name = models.CharField(max_length=100)
    latitude = models.DecimalField(max_digits=8, decimal_places=6)
    longitude = models.DecimalField(max_digits=9, decimal_places=6)
    address = models.CharField(max_length=255, blank=True, null=True)
    website_url = models.URLField(blank=True, null=True)
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
    LISTING_TYPE_CHOICES = [
        ('lend', 'Lend'),
        ('sell', 'Sell'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE)
    title = models.CharField(max_length=200)
    description = models.TextField()
    ski_station = models.ForeignKey(SkiStation, on_delete=models.SET_NULL, null=True, blank=True)
    city = models.CharField(max_length=100)
    price = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)
    material_type = models.CharField(max_length=50)
    listing_type = models.CharField(max_length=10, choices=LISTING_TYPE_CHOICES, default='lend')
    is_free = models.BooleanField(default=True)
    deposit_amount = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)
    available_from = models.DateField(null=True, blank=True)
    available_to = models.DateField(null=True, blank=True)
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


class InstructorProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='instructor_profile')
    bio = models.TextField(blank=True)
    years_experience = models.PositiveIntegerField(default=0)
    certifications = models.CharField(max_length=255, blank=True)
    phone = models.CharField(max_length=30, blank=True)
    profile_photo = models.BinaryField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Instructor {self.user.username}"


class InstructorService(models.Model):
    instructor = models.ForeignKey(InstructorProfile, on_delete=models.CASCADE, related_name='services')
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='instructor_services')
    title = models.CharField(max_length=120)
    description = models.TextField()
    duration_minutes = models.PositiveIntegerField(default=60)
    amount = models.DecimalField(max_digits=10, decimal_places=2)
    currency = models.CharField(max_length=10, default='EUR')
    max_group_size = models.PositiveIntegerField(default=1)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.title} - {self.instructor.user.username}"


class InstructorReview(models.Model):
    instructor = models.ForeignKey(InstructorProfile, on_delete=models.CASCADE, related_name='reviews')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='instructor_reviews')
    rating = models.PositiveSmallIntegerField()
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('instructor', 'user')

    def __str__(self):
        return f"Review {self.instructor.user.username} by {self.user.username}: {self.rating}/5"


class SnowConditionUpdate(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='snow_updates')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='snow_updates')
    note = models.TextField(blank=True)
    snow_depth_cm = models.PositiveIntegerField(null=True, blank=True)
    image = models.BinaryField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Snow update {self.ski_station.name} - {self.user.username}"


class PisteConditionReport(models.Model):
    CROWD_CHOICES = [
        ('quiet', 'Peu de gens'),
        ('normal', 'Agréable'),
        ('busy', 'Bondé'),
    ]

    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='piste_reports')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='piste_reports')
    piste_rating = models.PositiveSmallIntegerField()
    crowd_level = models.CharField(max_length=10, choices=CROWD_CHOICES, default='normal')
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('ski_station', 'user')

    def __str__(self):
        return f"Piste report {self.ski_station.name} - {self.user.username}"


class CrowdStatusUpdate(models.Model):
    CROWD_CHOICES = [
        ('quiet', 'Peu de gens'),
        ('normal', 'Agréable'),
        ('busy', 'Bondé'),
    ]

    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='crowd_updates')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='crowd_updates')
    crowd_level = models.CharField(max_length=10, choices=CROWD_CHOICES, default='normal')
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Crowd update {self.ski_station.name} - {self.user.username}"
    
class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='profile', null=True)
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