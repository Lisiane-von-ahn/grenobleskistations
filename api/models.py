from django.db import models
from django.contrib.auth.models import User
from django.dispatch import receiver
from django.db.models.signals import post_save
from django.core.files.base import ContentFile
from django.utils import timezone
from io import BytesIO
from datetime import timedelta
from PIL import Image


def story_default_expiration():
    return timezone.now() + timedelta(hours=24)

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
    bus_number = models.CharField(max_length=120)
    departure_stop = models.CharField(max_length=100)
    arrival_stop = models.CharField(max_length=100)
    frequency = models.CharField(max_length=120, null=True)
    travel_time = models.CharField(max_length=120, null=True)
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
    type = models.CharField(max_length=100)
    opening_hours = models.CharField(max_length=100, null=True)
    address = models.CharField(max_length=255, null=True, blank=True)
    website_url = models.URLField(null=True, blank=True)
    phone = models.CharField(max_length=40, null=True, blank=True)
    source_note = models.CharField(max_length=255, null=True, blank=True)

    def __str__(self):
        return self.name


class SkiCircuit(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, null=True)
    difficulty = models.CharField(max_length=20)
    num_pistes = models.IntegerField()

    def __str__(self):
        return f"{self.difficulty} - {self.num_pistes} pistes"

class SkiMaterialListing(models.Model):    
    TRANSACTION_CHOICES = [
        ('sale', 'For Sale'),
        ('rent', 'For Rent'),
        ('lend', 'For Loan'),
    ]

    CONDITION_CHOICES = [
        ('new', 'New'),
        ('excellent', 'Excellent'),
        ('good', 'Good'),
        ('fair', 'Fair'),
    ]

    MATERIAL_CHOICES = [
        ('ski', 'Ski'),
        ('boots', 'Boots'),
        ('helmet', 'Helmet'),
        ('jacket', 'Jacket'),
        ('pants', 'Pants'),
        ('gloves', 'Gloves'),
        ('goggles', 'Goggles'),
        ('other', 'Other'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE)
    title = models.CharField(max_length=200)
    description = models.TextField()
    ski_station = models.ForeignKey(SkiStation, on_delete=models.SET_NULL, null=True, blank=True)
    city = models.CharField(max_length=100)  # New field to specify pickup city
    price = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)
    material_type = models.CharField(max_length=20, choices=MATERIAL_CHOICES, default='ski')
    transaction_type = models.CharField(max_length=10, choices=TRANSACTION_CHOICES, default='sale')
    condition = models.CharField(max_length=12, choices=CONDITION_CHOICES, default='good')
    brand = models.CharField(max_length=100, blank=True)
    size = models.CharField(max_length=30, blank=True)
    image = models.BinaryField(null=True, blank=True, editable=True) 
    posted_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.title


class SkiMaterialImage(models.Model):
    listing = models.ForeignKey(SkiMaterialListing, on_delete=models.CASCADE, related_name='images')
    image = models.BinaryField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"Image for {self.listing.title}"


class SnowConditionUpdate(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='snow_updates')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='snow_updates')
    note = models.TextField(blank=True)
    snow_depth_cm = models.PositiveIntegerField(null=True, blank=True)
    image = models.BinaryField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Snow update {self.ski_station.name} by {self.user.username}"


class PisteConditionReport(models.Model):
    CROWD_QUIET = 'quiet'
    CROWD_NORMAL = 'normal'
    CROWD_BUSY = 'busy'

    CROWD_CHOICES = [
        (CROWD_QUIET, 'Peu de gens'),
        (CROWD_NORMAL, 'Agreable'),
        (CROWD_BUSY, 'Bonde'),
    ]

    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='piste_reports')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='piste_reports')
    piste_rating = models.PositiveSmallIntegerField()
    crowd_level = models.CharField(max_length=10, choices=CROWD_CHOICES, default=CROWD_NORMAL)
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('ski_station', 'user')
        ordering = ['-created_at']

    def __str__(self):
        return f"Piste report {self.ski_station.name} by {self.user.username}"


class CrowdStatusUpdate(models.Model):
    ski_station = models.ForeignKey(SkiStation, on_delete=models.CASCADE, related_name='crowd_updates')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='crowd_updates')
    crowd_level = models.CharField(max_length=10, choices=PisteConditionReport.CROWD_CHOICES, default=PisteConditionReport.CROWD_NORMAL)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Crowd update {self.ski_station.name} by {self.user.username}"


class InstructorProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='instructor_profile')
    bio = models.TextField(blank=True)
    years_experience = models.PositiveIntegerField(default=0)
    certifications = models.CharField(max_length=255, blank=True)
    phone = models.CharField(max_length=30, blank=True)
    is_active = models.BooleanField(default=True)
    profile_photo = models.BinaryField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Instructor profile {self.user.username}"


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

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.title} ({self.instructor.user.username})"


class InstructorReview(models.Model):
    instructor = models.ForeignKey(InstructorProfile, on_delete=models.CASCADE, related_name='reviews')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='instructor_reviews')
    rating = models.PositiveSmallIntegerField()
    comment = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('instructor', 'user')
        ordering = ['-created_at']

    def __str__(self):
        return f"Review {self.user.username} -> {self.instructor.user.username}"


class Message(models.Model):
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_messages')
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_messages')
    subject = models.CharField(max_length=255)
    body = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    is_read = models.BooleanField(default=False)

    def __str__(self):
        return f"Message from {self.sender} to {self.recipient} on {self.created_at}"


class MarketplaceSavedFilter(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='marketplace_saved_filters')
    name = models.CharField(max_length=40)
    query = models.CharField(max_length=500)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']
        constraints = [
            models.UniqueConstraint(fields=['user', 'name'], name='uniq_marketplace_filter_per_user_name'),
        ]

    def __str__(self):
        return f"{self.user.username}: {self.name}"


class SkiPartnerPost(models.Model):
    LEVEL_BEGINNER = 'beginner'
    LEVEL_INTERMEDIATE = 'intermediate'
    LEVEL_ADVANCED = 'advanced'

    LEVEL_CHOICES = [
        (LEVEL_BEGINNER, 'Debutant'),
        (LEVEL_INTERMEDIATE, 'Intermediaire'),
        (LEVEL_ADVANCED, 'Avance'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='ski_partner_posts')
    ski_station = models.ForeignKey(SkiStation, on_delete=models.SET_NULL, null=True, blank=True)
    title = models.CharField(max_length=120)
    message = models.TextField()
    city = models.CharField(max_length=80, blank=True)
    skill_level = models.CharField(max_length=16, choices=LEVEL_CHOICES, default=LEVEL_INTERMEDIATE)
    preferred_date = models.DateField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.user.username}: {self.title}"


class SkiPartnerReport(models.Model):
    post = models.ForeignKey(SkiPartnerPost, on_delete=models.CASCADE, related_name='reports')
    reporter = models.ForeignKey(User, on_delete=models.CASCADE, related_name='ski_partner_reports')
    reason = models.CharField(max_length=220, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=['post', 'reporter'], name='uniq_partner_report_per_user_post'),
        ]

    def __str__(self):
        return f"Report {self.reporter.username} -> post#{self.post_id}"


class SkiStory(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='ski_stories')
    ski_station = models.ForeignKey(SkiStation, on_delete=models.SET_NULL, null=True, blank=True, related_name='ski_stories')
    caption = models.CharField(max_length=180, blank=True)
    image = models.BinaryField()
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(default=story_default_expiration)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Story {self.user.username} #{self.id}"


class MarketplaceDeal(models.Model):
    listing = models.ForeignKey(SkiMaterialListing, on_delete=models.CASCADE, related_name='deals')
    buyer = models.ForeignKey(User, on_delete=models.CASCADE, related_name='marketplace_deals_as_buyer')
    seller = models.ForeignKey(User, on_delete=models.CASCADE, related_name='marketplace_deals_as_seller')
    buyer_confirmed = models.BooleanField(default=False)
    seller_confirmed = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']
        constraints = [
            models.UniqueConstraint(fields=['listing', 'buyer'], name='uniq_marketplace_deal_per_listing_buyer'),
        ]

    def __str__(self):
        return f"Deal listing#{self.listing_id} buyer={self.buyer_id}"


class MarketplaceUserRating(models.Model):
    SCORE_CHOICES = [(i, str(i)) for i in range(1, 6)]

    listing = models.ForeignKey(SkiMaterialListing, on_delete=models.CASCADE, related_name='buyer_ratings')
    rater = models.ForeignKey(User, on_delete=models.CASCADE, related_name='marketplace_ratings_given')
    rated_user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='marketplace_ratings_received')
    score = models.PositiveSmallIntegerField(choices=SCORE_CHOICES)
    comment = models.CharField(max_length=300, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']
        constraints = [
            models.UniqueConstraint(fields=['listing', 'rater'], name='uniq_marketplace_rating_per_listing_buyer'),
        ]

    def __str__(self):
        return f"{self.rater.username} -> {self.rated_user.username} ({self.score})"
    
class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile", null=True)
    profile_picture = models.BinaryField(null=True, blank=True)
    force_password_reset = models.BooleanField(default=False)

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


class UserFriend(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='friend_links')
    friend = models.ForeignKey(User, on_delete=models.CASCADE, related_name='friend_of_links')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=['user', 'friend'], name='uniq_user_friend_link'),
        ]
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.user.username} -> {self.friend.username}"
        
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