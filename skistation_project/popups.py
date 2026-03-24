"""
Django mixin and utilities for showing points earned popups
after user activities (listings, photos, reviews, etc)
"""

from django.contrib.auth.mixins import LoginRequiredMixin
from django.shortcuts import redirect
from django.views.generic import CreateView, UpdateView
from django.urls import reverse_lazy
from django.http import JsonResponse
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from api.models import (
    SkiMaterialListing, 
    GamificationPoints, 
    UserGameStats,
    GamificationBadge,
    UserBadge
)


class PointsEarnedMixin:
    """
    Mixin for Django views that should show points earned popups.
    
    Automatically displays a popup showing:
    - Points earned
    - Activity type
    - Current level and XP progress
    - Any badges unlocked
    
    Usage:
        class MyListingCreateView(PointsEarnedMixin, CreateView):
            model = SkiMaterialListing
            points_activity_type = 'listing_created'
            points_value = 15
    """
    
    points_activity_type = None  # Override in subclass
    points_value = None  # Override in subclass
    show_points_popup = True
    
    def form_valid(self, form):
        # Save the object normally
        response = super().form_valid(form)
        
        # Award points if configured
        if self.show_points_popup and self.points_activity_type and self.request.user.is_authenticated:
            # Points are normally awarded by signals, but we can track here
            pass
        
        return response
    
    def get_success_url(self):
        """Override to add popup parameter"""
        url = super().get_success_url()
        if self.show_points_popup:
            separator = '&' if '?' in url else '?'
            url += f'{separator}show_points=1'
        return url


def get_user_points_for_activity(user, activity_type, points_earned):
    """
    Get detailed popup data for points earned event
    
    Returns JSON with:
    - points_earned
    - activity_type
    - current stats (level, total_points, etc)
    - badge unlock info (if applicable)
    """
    try:
        stats = user.game_stats
    except UserGameStats.DoesNotExist:
        stats = UserGameStats.objects.create(user=user)
    
    # Get the latest badge earned (if any)
    latest_badge = user.userbadge_set.order_by('-earned_at').first()
    badge_data = None
    
    if latest_badge and (latest_badge.earned_at.timestamp() > 
                        (GamificationPoints.objects.filter(user=user)
                         .order_by('-created_at').first().created_at.timestamp() - 5)):
        # Badge was earned very recently (within 5 seconds)
        badge_data = {
            'icon_emoji': latest_badge.badge.icon_emoji,
            'name': latest_badge.badge.name,
            'requirement_description': latest_badge.badge.requirement_description,
            'rarity': latest_badge.badge.rarity,
            'points_value': latest_badge.badge.points_value
        }
    
    return {
        'points_earned': points_earned,
        'activity_type': activity_type,
        'stats': {
            'level': stats.level,
            'total_points': stats.total_points,
            'experience_points': stats.experience_points,
            'badges_count': stats.badges_count,
            'daily_login_streak': stats.daily_login_streak,
            'total_listings_created': stats.total_listings_created,
            'total_deals_completed': stats.total_deals_completed,
            'total_reviews_written': stats.total_reviews_written,
            'average_seller_rating': stats.average_seller_rating,
        },
        'badge': badge_data
    }


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def get_latest_points_earned(request):
    """
    API endpoint to get the latest points earned event for a user.
    Called after form submission to populate the popup.
    
    Query params:
    - activity_type: 'listing_created', 'photo_added', 'review_written', etc
    
    Returns: JSON with points, stats, and badge info
    """
    activity_type = request.query_params.get('activity_type', 'unknown')
    
    # Get latest points record
    latest_points = GamificationPoints.objects.filter(
        user=request.user
    ).order_by('-created_at').first()
    
    if not latest_points:
        return Response({
            'error': 'No points earned yet',
            'activity_type': activity_type,
            'points_earned': 0
        }, status=404)
    
    points_data = get_user_points_for_activity(
        request.user,
        latest_points.activity_type,
        latest_points.points_earned
    )
    
    return Response(points_data)


class MaterialListingCreateView(PointsEarnedMixin, LoginRequiredMixin, CreateView):
    """
    Example view for creating material listings with points popup.
    Integrates the PointsEarnedMixin to show popup after creation.
    """
    model = SkiMaterialListing
    fields = ['title', 'description', 'category', 'price', 'image']
    success_url = reverse_lazy('listing-list')
    points_activity_type = 'listing_created'
    points_value = 15
    
    def form_valid(self, form):
        form.instance.user = self.request.user
        return super().form_valid(form)


# AJAX endpoint for manual popup trigger (if needed)
@api_view(['POST'])
@permission_classes([IsAuthenticated])
def trigger_points_popup(request):
    """
    Manually trigger a points popup (for cases where signal didn't fire)
    
    POST data:
    {
        'activity_type': 'listing_created',
        'points_earned': 15,
        'activity_id': 123  # ID of the related object
    }
    """
    activity_type = request.data.get('activity_type')
    points_earned = request.data.get('points_earned', 0)
    
    # Verify points exist
    points_record = GamificationPoints.objects.filter(
        user=request.user,
        activity_type=activity_type
    ).order_by('-created_at').first()
    
    if not points_record:
        return Response({'error': 'No matching points found'}, status=404)
    
    # Get detailed data for popup
    popup_data = get_user_points_for_activity(
        request.user,
        activity_type,
        points_earned
    )
    
    return Response(popup_data)
