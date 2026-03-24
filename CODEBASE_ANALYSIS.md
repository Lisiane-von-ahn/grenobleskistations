# GrenobleSki Codebase Analysis: User Activity & Data Structure

**Generated:** March 24, 2026  
**Purpose:** Comprehensive analysis for implementing gamification/points system

---

## 1. USER MODELS & DATA STRUCTURE

### A. Core User Models

#### **UserProfile** (`api/models.py` line 383-415)
```python
class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile")
    profile_picture = models.BinaryField(null=True, blank=True)
    force_password_reset = models.BooleanField(default=False)
```
- **OneToOne relationship** with Django's built-in `User`
- Auto-created via signal on user registration
- Contains only profile photo and password reset flag
- **NO existing score/level/points/badge fields**

#### **User** (Django built-in)
- `username` - email address
- `email` - email address
- `first_name` - user's first name
- `last_name` - user's last name
- `date_joined` - registration timestamp
- `is_active`, `is_staff`, `is_superuser`

#### **Signal Handlers** (`skistation_project/signals.py`)
- `create_user_profile()` - auto-creates UserProfile on user registration
- `save_user_profile()` - syncs profile changes
- `on_user_logged_in()` - logs login events (auth logger)
- `on_user_logged_out()` - logs logout events
- `password_changed()` - clears force_password_reset flag

---

## 2. USER ACTIVITY TYPES (Potential Points-Earning Actions)

### A. Marketplace Activities

#### **SkiMaterialListing** (line 94-139)
- **User action:** Post a listing (for sale, rental, loan, service)
- **Tracked data:**
  - `user` - who posted
  - `posted_at` - timestamp
  - `material_type` - what category
  - `transaction_type` - sale/rent/lend/service
  - `brand`, `size`, `condition`
- **Methods:**
  - `get_seller_ratings()` - all ratings received
  - `get_seller_average_rating()` - avg score (1-5)
  - `get_seller_rating_count()` - total ratings received

#### **MarketplaceDeal** (line 348-361)
- **User action:** Initiate transaction between buyer/seller
- **Tracked data:**
  - `listing` - which item
  - `buyer`, `seller` - users involved
  - `buyer_confirmed`, `seller_confirmed` - agreement flags
  - `created_at`, `updated_at` - timestamps
- **Constraints:** One deal per listing per buyer

#### **MarketplaceUserRating** (line 363-381)
- **User action:** Rate another user (1-5 stars) after transaction
- **Tracked data:**
  - `rater` - who gave the rating
  - `rated_user` - who received it
  - `listing` - which transaction
  - `score` - 1-5 stars
  - `comment` - text feedback
  - `created_at`, `updated_at`
- **Constraints:** One rating per listing per rater

### B. Community Activities

#### **SkiPartnerPost** (line 300-325)
- **User action:** Publish a ski partner finding post
- **Tracked data:**
  - `user` - who posted
  - `ski_station`, `city`
  - `skill_level` - beginner/intermediate/advanced
  - `preferred_date` - when they want to ski
  - `is_active` - can deactivate post
  - `created_at`
- **Rate limiting:** Max 5 active posts per user, 5-minute cooldown

#### **SkiPartnerReport** (line 327-339)
- **User action:** Report inappropriate ski partner post
- **Tracked data:**
  - `reporter`, `post`
  - `reason` - why reported
  - `created_at`
- **Constraints:** One report per user per post

#### **SkiStory** (line 341-351)
- **User action:** Post a ski story with photo (expires in 24h)
- **Tracked data:**
  - `user` - who posted
  - `ski_station` - where
  - `caption` - text
  - `image` - binary photo data
  - `created_at`, `expires_at` (24h later)
- **No rating/commenting system**

### C. Condition Reporting Activities

#### **SnowConditionUpdate** (line 147-159)
- **User action:** Report current snow conditions
- **Tracked data:**
  - `user`, `ski_station`
  - `snow_depth_cm` - measurement
  - `note` - text
  - `image` - photo evidence
  - `created_at`

#### **PisteConditionReport** (line 162-178)
- **User action:** Rate piste conditions
- **Tracked data:**
  - `user`, `ski_station`
  - `piste_rating` - score (1-10?)
  - `crowd_level` - quiet/normal/busy (enum)
  - `comment` - text
  - `created_at`
- **Constraints:** One report per user per station (unique_together)

#### **CrowdStatusUpdate** (line 181-192)
- **User action:** Report current crowd levels
- **Tracked data:**
  - `user`, `ski_station`
  - `crowd_level` - quiet/normal/busy
  - `created_at`

### D. Instructor Activities

#### **InstructorProfile** (line 195-206)
- **User action:** Register as a ski instructor
- **Tracked data:**
  - `user` (OneToOne)
  - `bio`, `years_experience`
  - `certifications`
  - `phone`, `profile_photo`
  - `is_active` - can deactivate
  - `created_at`

#### **InstructorService** (line 209-227)
- **User action:** Instructor offers a ski lesson service
- **Tracked data:**
  - `instructor`, `ski_station`
  - `title`, `description`
  - `duration_minutes`, `amount` (price)
  - `max_group_size`
  - `is_active`
  - `created_at`

#### **InstructorReview** (line 230-240)
- **User action:** Review an instructor (1-5 stars)
- **Tracked data:**
  - `instructor`, `user` (reviewer)
  - `rating` - score 1-5
  - `comment`
  - `created_at`
- **Constraints:** One review per instructor per user

### E. Communication Activities

#### **Message** (line 257-267)
- **User action:** Send private message to another user
- **Tracked data:**
  - `sender`, `recipient`
  - `subject`, `body`
  - `created_at`
  - `is_read` - read status
- **No rating/reaction system**

### F. Social Activities

#### **UserFriend** (line 443-455)
- **User action:** Add another user as friend
- **Tracked data:**
  - `user`, `friend`
  - `created_at`
- **Constraints:** One friend link per user-friend pair

#### **MarketplaceSavedFilter** (line 293-307)
- **User action:** Save a marketplace search filter
- **Tracked data:**
  - `user`
  - `name`, `query` - saved search
  - `created_at`, `updated_at`
- **Not a major activity for points**

---

## 3. CURRENT STATS & TRACKING MECHANISMS

### A. What's Currently Tracked

#### **Marketplace Seller Stats**
```python
# From SkiMaterialListing
get_seller_ratings()        # All MarketplaceUserRating for this user
get_seller_average_rating() # Aggregated via models.Avg('score')
get_seller_rating_count()   # Count of ratings
```
- Displayed on [listing_detail.html](templates/listing_detail.html):
  - "Vendeur note X.X/5 (Y avis)" - seller average rating
  - Rating breakdown chart (1★ to 5★ distribution)

#### **Condition Reports**
- PisteConditionReport aggregation: `avg=Avg('piste_rating')`
- Used in ski conditions endpoint

#### **Instructor Ratings**
- InstructorReview model stores individual 1-5 ratings
- Aggregated but not centrally displayed

### B. What's NOT Currently Tracked

- ⚠️ **NO score/points system**
- ⚠️ **NO level/tier system**
- ⚠️ **NO badges/achievements**
- ⚠️ **NO activity streak tracking**
- ⚠️ **NO user reputation score**
- ⚠️ **NO activity counts per user**
- ⚠️ **NO ranking/leaderboards**

---

## 4. DJANGO ADMIN CONFIGURATION

### Registered Models (`api/admin.py`)
```python
admin.site.register(SkiStation, SkiStationAdmin)
admin.site.register(BusLine)
admin.site.register(ServiceStore)
admin.site.register(SkiCircuit)
admin.site.register(Message)
admin.site.register(UserProfile)
admin.site.register(SkiMaterialListing)
admin.site.register(SkiMaterialImage)
```

### Admin Customizations
- **SkiStationAdmin** - Custom form with image upload handling
- List displays: name, capacity, coordinates
- Others use default ModelAdmin

### Missing Admin Views
- No custom admin for ratings, reviews, activities
- No bulk activity management
- No user stats dashboard

---

## 5. WEB UI PATTERNS & STRUCTURE

### A. Template Patterns

#### **Premium Shell Header**
```html
<section class="premium-shell mb-4 p-3">
    <div class="premium-hero">
        <span class="premium-kicker">Category</span>
        <h2 class="premium-title h3 mb-1">Title</h2>
        <p class="premium-subtitle">Subtitle/description</p>
    </div>
</section>
```
Used on: marketplace, ski partners, ski stories

#### **Market Search Panel Card**
```html
<div class="market-search-panel">
    <!-- Content in cards -->
</div>
```
Used for listing details, filters, transaction info

#### **Cards Grid Layout**
```html
<div class="row g-4">
    <div class="col-lg-6">
        <article class="card border-0 shadow-sm p-4 market-card">
            <!-- Card content -->
        </article>
    </div>
</div>
```
Used for skiing partners, stories, listings

#### **Profile Section**
```html
<div class="profile-hero-wrap">
    <div class="profile-hero">
        <span class="profile-hero-kicker">Label</span>
        <h2>User Name</h2>
        <p>Bio/subtitle</p>
    </div>
</div>
```

#### **Rating Display**
```html
{% if seller_rating_avg %}
    <p>Vendeur note {{ seller_rating_avg|floatformat:1 }}/5 ({{ seller_rating_count }} avis)</p>
    {% for row in seller_rating_breakdown %}
        <progress value="{{ row.pct }}" max="100"></progress>
    {% endfor %}
{% endif %}
```

#### **Badge System** (already exists)
```html
<span class="badge-soft badge-action">{{ listing.get_transaction_type_display }}</span>
<span class="badge bg-primary">{{ post.get_skill_level_display }}</span>
<span class="badge bg-success">{{ total_partners }}</span>
```
Used for material type, skill level, status

#### **Stats Banner**
```html
<div class="card border-0 shadow-sm p-3 bg-gradient">
    <span class="badge bg-info text-dark">Station1 (12)</span>
    <span class="badge bg-success">45 active partners</span>
</div>
```

### B. Key Page Templates

| File | Purpose | Current Data Shown |
|------|---------|-------------------|
| [profile.html](templates/profile.html) | User profile page | Username, profile picture |
| [listing_detail.html](templates/listing_detail.html) | Marketplace item | Seller ratings, transaction status, deal management |
| [ski_partners.html](templates/ski_partners.html) | Partner finding | Posts, skill level, station, date, top stations stats |
| [base.html](templates/base.html) | Main layout | Navigation, user menu |
| [ski_material_listings.html](templates/ski_material_listings.html) | Marketplace list | Grid of items with filters |

---

## 6. API VIEWSETS & ENDPOINTS

### A. Read-Only Endpoints
- `SkiStationViewSet` - /stations/ with `.conditions` action
- `BusLineViewSet` - /bus-lines/
- `ServiceStoreViewSet` - /service-stores/
- `SkiCircuitViewSet` - /ski-circuits/
- `SkiStationCameraViewSet` - /cameras/ (with station filtering)

### B. User Activity Endpoints
- **SkiMaterialListingViewSet** - POST to create listing (sets user), GET to view
- **MessageViewSet** - POST/GET messages, `.mark-read` action
- **SnowConditionUpdateViewSet** - POST/GET reports (sets user)
- **PisteConditionReportViewSet** - POST/GET reports (sets user)
- **SkiPartnerPostViewSet** - POST/GET partner posts (sets user)
- **SkiPartnerReportViewSet** - POST/GET reports (sets reporter)
- **SkiStoryViewSet** - POST/GET stories (sets user, filters by expiration)
- **InstructorProfileViewSet** - POST/GET instructor profiles (sets user)
- **InstructorServiceViewSet** - POST/GET services
- **InstructorReviewViewSet** - POST/GET reviews (sets user)
- **MarketplaceDealViewSet** - POST/GET deals
- **MarketplaceUserRatingViewSet** - POST/GET ratings (sets user)
- **MarketplaceSavedFilterViewSet** - POST/GET saved filters
- **UserProfileViewSet** - GET user profile, `.me` action
- **UserViewSet** - GET users, `.register` action

### C. Permission Model
- Most POST operations require `IsAuthenticated`
- Most perform_create() methods auto-set `user=self.request.user`
- `register` endpoint uses `AllowAny`
- `/stations/conditions` endpoint uses `AllowAny`

---

## 7. KEY ACTIVITY QUERYSETS (Ready for Aggregation)

### Current Activity Count Opportunities
```python
# Marketplace
SkiMaterialListing.objects.filter(user=user_obj).count()
MarketplaceDeal.objects.filter(buyer=user_obj).count()
MarketplaceDeal.objects.filter(seller=user_obj).count()
MarketplaceUserRating.objects.filter(rater=user_obj).count()

# Community
SkiPartnerPost.objects.filter(user=user_obj).count()
SkiStory.objects.filter(user=user_obj).count()

# Expertise
SnowConditionUpdate.objects.filter(user=user_obj).count()
PisteConditionReport.objects.filter(user=user_obj).count()
CrowdStatusUpdate.objects.filter(user=user_obj).count()

# Instruction
InstructorProfile.objects.filter(user=user_obj).count()
InstructorService.objects.filter(instructor__user=user_obj).count()
InstructorReview.objects.filter(instructor__user=user_obj).count()

# Social
Message.objects.filter(sender=user_obj).count()
UserFriend.objects.filter(user=user_obj).count()
```

---

## 8. RECOMMENDATIONS FOR GAMIFICATION SYSTEM

### A. Points-Earning Activities (Priority Tiers)

**TIER 1 - High Value (10-50 points)**
- Post marketplace listing: 10 pts
- Receive positive rating (4-5 ⭐): 25 pts
- Publish ski partner post: 10 pts
- Publish ski story: 5 pts

**TIER 2 - Medium Value (5-15 points)**
- Report condition update (snow/piste/crowd): 5 pts
- Rate an instructor: 5 pts
- Send message: 1 pt (optional, could spam)
- Add friend: 2 pts

**TIER 3 - Premium Activities**
- Register as instructor: 25 pts
- Offer instructor service: 15 pts
- Complete marketplace deal: 20 pts (both buyer+seller)

### B. Potential Levels (Example)
- Level 1: 0-100 pts
- Level 2: 100-300 pts
- Level 3: 300-600 pts
- Level 4: 600-1000 pts
- Level 5: 1000+ pts

### C. Required Database Changes
- Add to `UserProfile`:
  - `total_points` (IntegerField, default=0)
  - `current_level` (IntegerField, default=1)
  - `last_activity_date` (DateTimeField, nullable)
  - `lifetime_points` (IntegerField, default=0, immutable history)
  - `streak_days` (IntegerField, default=0)

- Create `UserActivity` model:
  - `user` (ForeignKey)
  - `activity_type` (CharField, choices)
  - `points_earned` (IntegerField)
  - `description` (CharField)
  - `related_object_id` (IntegerField, nullable)
  - `created_at` (DateTimeField)

- Create `UserBadge` model:
  - `user` (ForeignKey)
  - `badge_type` (CharField)
  - `earned_at` (DateTimeField)

---

## 9. EXISTING INTEGRATION POINTS

### Where to Add Points Logic
1. **Views** - In `perform_create()` methods of ViewSets
2. **Signals** - New signal handlers for post_save
3. **Management Command** - For historical data migration
4. **Periodic Task** - For streak calculation (daily check-in)

### Django Installed Apps (from settings.py)
```python
INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'django.contrib.sites',
    'api',
    'rest_framework',
    'rest_framework.authtoken',
    'drf_yasg',  # Swagger docs
    'drf_spectacular',
    'skistation_project.apps.SkistationProjectConfig',
    'allauth',
    'allauth.account',
    'allauth.socialaccount',
    'allauth.socialaccount.providers.google',
]
```
- **No Celery/async tasks** - may need for streak calculations
- **Uses TokenAuthentication** - good for API

---

## 10. FRONTEND DISPLAY OPPORTUNITIES

### Profile Page Enhancement
- Add "Level X" badge with progress bar
- Show total points
- List recent activities (activity feed)
- Achievement badges
- Activity streak indicator

### Marketplace Listing
- Show seller's level badge
- Display seller's lifetime transactions
- Points earned for positive rating

### Ski Partners Page
- Show user level/badge on each post
- Filter by skill level (could include points level)

### Activity Feed (New)
- Timeline of user's activities
- Points earned per action
- Compare with friends

### Leaderboards (New)
- Weekly/monthly top contributors
- By activity type (top raters, instructors, reporters, etc.)

---

## SUMMARY TABLE

| Aspect | Current State | Points System Readiness |
|--------|---------------|------------------------|
| User Models | User + UserProfile | ✅ Ready (add points/level fields) |
| Activity Tracking | Basic (ratings only) | ✅ Can aggregate all activities |
| Rating System | Exists (reviews) | ✅ Leverage for points |
| Admin Interface | Basic CRUD | ⚠️ Needs dashboard for stats |
| API Structure | Clean ViewSets | ✅ Easy to hook points logic |
| Signals | Limited to auth | ✅ Can add user activity signals |
| Scoring Fields | None | ❌ MUST CREATE |
| Dashboard | None | ❌ MUST CREATE |
| Badges/Levels | None | ❌ MUST CREATE |

