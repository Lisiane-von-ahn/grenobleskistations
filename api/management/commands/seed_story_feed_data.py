import random
from datetime import timedelta
from io import BytesIO
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen

from django.contrib.auth.models import User
from django.core.management.base import BaseCommand
from django.db import transaction
from django.db.models import Q
from django.utils import timezone
from PIL import Image, ImageDraw, ImageOps

from api.models import Message, SkiStation, SkiStory, SkiStoryComment, SkiStoryLike, UserProfile


def _download_binary(url):
    request = Request(url, headers={"User-Agent": "GrenobleSkiSeed/1.0"})
    try:
        with urlopen(request, timeout=7) as response:
            return response.read()
    except (URLError, HTTPError, TimeoutError, ValueError):
        return None


def _normalize_image(binary_payload, width, height, image_format='JPEG'):
    if not binary_payload:
        return None
    try:
        img = Image.open(BytesIO(binary_payload)).convert('RGB')
        resampling = getattr(getattr(Image, 'Resampling', Image), 'LANCZOS', Image.LANCZOS)
        img = ImageOps.fit(img, (width, height), method=resampling)
        output = BytesIO()
        img.save(output, format=image_format, quality=88)
        return output.getvalue()
    except Exception:
        return None

def _build_ski_avatar(seed_value):
    randomizer = random.Random(seed_value)
    img = Image.new('RGB', (128, 128), (randomizer.randint(10, 60), randomizer.randint(90, 140), randomizer.randint(170, 230)))
    draw = ImageDraw.Draw(img)
    draw.ellipse((10, 10, 118, 118), outline=(240, 250, 255), width=5)
    draw.line((38, 92, 90, 92), fill=(255, 255, 255), width=5)
    draw.line((45, 34, 83, 74), fill=(255, 255, 255), width=6)
    draw.line((83, 34, 45, 74), fill=(255, 255, 255), width=6)

    buffer = BytesIO()
    img.save(buffer, format='PNG')
    return buffer.getvalue()


def _build_story_image(seed_value, station_name, weather):
    randomizer = random.Random(seed_value)
    img = Image.new('RGB', (900, 560), (18, 65, 120))
    draw = ImageDraw.Draw(img)

    # Snow slope background.
    draw.polygon([(0, 560), (0, 340), (360, 420), (900, 280), (900, 560)], fill=(245, 250, 255))
    draw.polygon([(0, 340), (200, 250), (410, 340)], fill=(190, 220, 245))
    draw.polygon([(320, 300), (580, 180), (900, 320)], fill=(165, 205, 236))

    # Ski tracks.
    for i in range(6):
        y = 330 + i * 35 + randomizer.randint(-8, 8)
        draw.arc((80, y, 860, y + 90), start=190, end=355, fill=(210, 232, 247), width=2)

    # Caption band.
    draw.rectangle((0, 472, 900, 560), fill=(8, 34, 68))
    title = f"{station_name[:36]} | {weather[:18]}" if station_name else f"Grenoble Ski | {weather[:18]}"
    draw.text((24, 504), title, fill=(236, 246, 255))

    buffer = BytesIO()
    img.save(buffer, format='PNG')
    return buffer.getvalue()

COMMENTS_POOL = [
    "Super conditions ce matin.",
    "Vue incroyable au sommet.",
    "Neige fraiche, piste parfaite.",
    "Un peu de monde mais excellente ambiance.",
    "Top pour une sortie en famille.",
    "Parking plein, venez tot.",
    "Moniteur recommande pour debutants.",
    "Belle lumiere et neige stable.",
    "Bus pratique depuis Grenoble.",
    "Session courte mais intense.",
]

CAPTION_POOL = [
    "Powder day!",
    "Sunset ride in the Alps.",
    "Morning run, fresh snow.",
    "Great visibility and calm slopes.",
    "Après-ski vibes.",
    "Family ski day.",
    "Quick lunch, back on tracks.",
    "Bluebird day at the station.",
    "Perfect carving conditions.",
    "Exploring a new route today.",
]

WEATHER_POOL = [
    'sunny',
    'cloudy',
    'powder snow',
    'light snow',
    'foggy',
    'windy',
]

FIRST_NAMES = [
    "Adam", "Luke", "Noah", "Liam", "Ethan", "Mason", "Leo", "Hugo", "Arthur", "Louis",
    "Jules", "Nolan", "Theo", "Gabriel", "Max", "Paul", "Oscar", "Alex", "Tom", "Sam",
    "Emma", "Chloe", "Lea", "Sarah", "Mia", "Lina", "Camille", "Alice", "Eva", "Zoe",
    "Sofia", "Nina", "Clara", "Manon", "Louise", "Jeanne", "Ines", "Mila", "Elsa", "Lucie",
]

LAST_NAMES = [
    "Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau",
    "Simon", "Laurent", "Lefevre", "Mercier", "Garcia", "Faure", "Roux", "Vincent", "Muller", "Lambert",
    "Rossi", "Fischer", "Schmidt", "Lopez", "Bianchi", "Navarro", "Guerin", "Chevalier", "Henry", "Boyer",
]

CROWD_POOL = [
    SkiStory.CROWD_QUIET,
    SkiStory.CROWD_NORMAL,
    SkiStory.CROWD_BUSY,
    SkiStory.CROWD_WILD,
]

# Demo-only internet photo pools for richer seeded content.
STORY_IMAGE_URLS = [
    "https://picsum.photos/id/1018/1200/800",
    "https://picsum.photos/id/1015/1200/800",
    "https://picsum.photos/id/1024/1200/800",
    "https://picsum.photos/id/1036/1200/800",
    "https://picsum.photos/id/1043/1200/800",
    "https://picsum.photos/id/1059/1200/800",
    "https://picsum.photos/id/1068/1200/800",
    "https://picsum.photos/id/1074/1200/800",
]

PROFILE_IMAGE_URLS = [
    "https://randomuser.me/api/portraits/men/12.jpg",
    "https://randomuser.me/api/portraits/men/22.jpg",
    "https://randomuser.me/api/portraits/men/33.jpg",
    "https://randomuser.me/api/portraits/men/48.jpg",
    "https://randomuser.me/api/portraits/women/10.jpg",
    "https://randomuser.me/api/portraits/women/24.jpg",
    "https://randomuser.me/api/portraits/women/31.jpg",
    "https://randomuser.me/api/portraits/women/45.jpg",
]


class Command(BaseCommand):
    help = "Seed fake story feed data (users, stories, likes, comments)"

    def add_arguments(self, parser):
        parser.add_argument("--users", type=int, default=100, help="Number of users to ensure")
        parser.add_argument("--stories", type=int, default=400, help="Number of stories to create")
        parser.add_argument("--max-likes", type=int, default=30, help="Max likes per story")
        parser.add_argument("--max-comments", type=int, default=12, help="Max comments per story")
        parser.add_argument("--password", type=str, default="SkiStories123!", help="Password for seeded users")
        parser.add_argument("--messages", type=int, default=800, help="Number of messages to generate")
        parser.add_argument("--reset", action="store_true", help="Delete existing stories/likes/comments before seeding")

    def _seeded_user_queryset(self):
        # Keep reset safe: remove only accounts created by this seeder conventions.
        return User.objects.filter(
            Q(username__startswith="story_user_")
            | Q(email__iendswith="@grenobleski.local")
        )

    @transaction.atomic
    def handle(self, *args, **options):
        users_target = max(1, options["users"])
        stories_target = max(1, options["stories"])
        max_likes = max(0, options["max_likes"])
        max_comments = max(0, options["max_comments"])
        password = options["password"]
        messages_target = max(0, options["messages"])
        reset = options["reset"]

        stations = list(SkiStation.objects.order_by("id"))
        if not stations:
            self.stdout.write(self.style.ERROR("No ski stations found. Seed stations first."))
            return

        if reset:
            SkiStoryComment.objects.all().delete()
            SkiStoryLike.objects.all().delete()
            SkiStory.objects.all().delete()
            Message.objects.all().delete()
            seed_users_qs = self._seeded_user_queryset()
            seed_users_count = seed_users_qs.count()
            seed_users_qs.delete()
            self.stdout.write(self.style.WARNING("Existing story feed data deleted."))
            self.stdout.write(self.style.WARNING(f"Seed users deleted: {seed_users_count}"))

        story_image_cache = {}
        profile_image_cache = {}

        seeded_users = []
        for i in range(1, users_target + 1):
            first_name = FIRST_NAMES[(i - 1) % len(FIRST_NAMES)]
            last_name = LAST_NAMES[((i - 1) // len(FIRST_NAMES)) % len(LAST_NAMES)]
            base_username = f"{first_name.lower()}.{last_name.lower()}{i:02d}"
            username = base_username
            email = f"{username}@grenobleski.local"

            legacy_username = f"story_user_{i:03d}"
            legacy_user = User.objects.filter(username=legacy_username).first()

            # Keep usernames unique when migrating legacy seed accounts.
            if legacy_user is not None:
                suffix = 1
                while User.objects.filter(username=username).exclude(id=legacy_user.id).exists():
                    suffix += 1
                    username = f"{base_username}_{suffix}"
                email = f"{username}@grenobleski.local"

                created = False
                user = legacy_user
                user.username = username
                user.email = email
                user.first_name = first_name
                user.last_name = last_name
                user.save(update_fields=["username", "email", "first_name", "last_name"])
            else:
                suffix = 1
                while User.objects.filter(username=username).exists():
                    suffix += 1
                    username = f"{base_username}_{suffix}"
                email = f"{username}@grenobleski.local"

                user, created = User.objects.get_or_create(
                    username=username,
                    defaults={
                        "email": email,
                        "first_name": first_name,
                        "last_name": last_name,
                    },
                )
            if created:
                user.set_password(password)
                user.save(update_fields=["password"])
            profile, _ = UserProfile.objects.get_or_create(user=user)
            # Keep most users in public mode, some users with private-by-default messages.
            profile.messages_private_by_default = (i % 9 == 0)

            profile_url = PROFILE_IMAGE_URLS[(i - 1) % len(PROFILE_IMAGE_URLS)]
            profile_picture = profile_image_cache.get(profile_url)
            if profile_picture is None:
                profile_picture = _normalize_image(_download_binary(profile_url), 256, 256, image_format='PNG')
                profile_image_cache[profile_url] = profile_picture
            profile.profile_picture = profile_picture or _build_ski_avatar(seed_value=i)
            profile.save(update_fields=["messages_private_by_default", "profile_picture"])
            seeded_users.append(user)

        now = timezone.now()
        stories = []
        for i in range(stories_target):
            author = random.choice(seeded_users)
            station = random.choice(stations)
            created_at = now - timedelta(hours=random.randint(0, 120), minutes=random.randint(0, 59))
            expires_at = created_at + timedelta(hours=24)
            caption = random.choice(CAPTION_POOL)
            crowd = random.choice(CROWD_POOL)
            weather = random.choice(WEATHER_POOL)
            temperature_c = random.randint(-12, 10)
            snow_depth = random.randint(5, 120)

            crowd_bonus = {
                SkiStory.CROWD_QUIET: 15,
                SkiStory.CROWD_NORMAL: 8,
                SkiStory.CROWD_BUSY: 3,
                SkiStory.CROWD_WILD: -3,
            }.get(crowd, 0)
            weather_bonus = 10 if 'snow' in weather or 'sunny' in weather else 2
            temp_bonus = 10 if -8 <= temperature_c <= 2 else 0
            snow_bonus = 15 if snow_depth >= 40 else (8 if snow_depth >= 15 else 0)
            fun_score = max(0, min(100, 45 + crowd_bonus + weather_bonus + temp_bonus + snow_bonus))

            story_url = STORY_IMAGE_URLS[i % len(STORY_IMAGE_URLS)]
            if story_url not in story_image_cache:
                story_image_cache[story_url] = _normalize_image(_download_binary(story_url), 900, 560, image_format='JPEG')
            story_image_binary = story_image_cache.get(story_url) or _build_story_image(
                seed_value=i * 97 + author.id,
                station_name=station.name,
                weather=weather,
            )

            story = SkiStory.objects.create(
                user=author,
                ski_station=station,
                caption=caption,
                image=story_image_binary,
                crowd_level=crowd,
                weather_label=weather,
                temperature_c=temperature_c,
                snow_depth_cm=snow_depth,
                fun_score=fun_score,
                expires_at=expires_at,
            )

            SkiStory.objects.filter(id=story.id).update(created_at=created_at)
            story.created_at = created_at
            stories.append(story)

        likes_created = 0
        comments_created = 0
        messages_created = 0

        for story in stories:
            likes_count = random.randint(0, max_likes)
            commenters_count = random.randint(0, max_comments)

            if likes_count > 0:
                liker_sample = random.sample(seeded_users, k=min(likes_count, len(seeded_users)))
                like_rows = [SkiStoryLike(story=story, user=user) for user in liker_sample if user.id != story.user_id]
                SkiStoryLike.objects.bulk_create(like_rows, ignore_conflicts=True)
                likes_created += len(like_rows)

            if commenters_count > 0:
                commenter_sample = random.sample(seeded_users, k=min(commenters_count, len(seeded_users)))
                rows = []
                for user in commenter_sample:
                    if user.id == story.user_id:
                        continue
                    body = random.choice(COMMENTS_POOL)
                    rows.append(SkiStoryComment(story=story, user=user, body=body))
                created_rows = SkiStoryComment.objects.bulk_create(rows)
                comments_created += len(created_rows)

        # Public messages by default, with occasional private messages.
        message_rows = []
        for _ in range(messages_target):
            sender = random.choice(seeded_users)
            recipient = random.choice(seeded_users)
            if sender.id == recipient.id:
                continue
            sender_profile = getattr(sender, 'profile', None)
            private_default = bool(getattr(sender_profile, 'messages_private_by_default', False))
            is_private = private_default or (random.random() < 0.1)
            message_rows.append(
                Message(
                    sender=sender,
                    recipient=recipient,
                    subject='Community chat',
                    body=random.choice(COMMENTS_POOL),
                    is_private=is_private,
                )
            )
        if message_rows:
            created_messages = Message.objects.bulk_create(message_rows)
            messages_created = len(created_messages)

        self.stdout.write(self.style.SUCCESS("Story feed seed complete."))
        self.stdout.write(
            f"users={len(seeded_users)} stories={len(stories)} likes={likes_created} comments={comments_created} messages={messages_created}"
        )
        self.stdout.write("Tip: use --reset to rebuild from scratch.")
