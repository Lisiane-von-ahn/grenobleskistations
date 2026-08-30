package fr.grenobleski.nativeapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("accept_terms") val acceptTerms: Boolean,
)

data class UserDto(
    val id: Int?,
    val email: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
)

data class LoginResponse(
    val token: String?,
    val user: UserDto?,
    val detail: String?,
    val error: String?,
)

data class UserSession(
    val token: String,
    val email: String,
    val displayName: String,
    val userId: Int = 0,
)

data class DashboardCounts(
    val stations: Int = 0,
    val busLines: Int = 0,
    val services: Int = 0,
    val marketplace: Int = 0,
)

data class StoryCommentItem(
    val id: Int,
    val userLabel: String,
    val body: String,
    val createdAtLabel: String,
)

data class StoryItem(
    val id: Int,
    val userId: Int,
    val userLabel: String,
    val stationId: Int,
    val stationName: String,
    val caption: String,
    val imageBase64: String,
    val createdAtLabel: String,
    val createdAtRaw: String,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val recentComments: List<StoryCommentItem> = emptyList(),
)

data class StoryPage(
    val items: List<StoryItem>,
    val hasNextPage: Boolean,
    val nextPage: Int?,
)

data class StoryStats(
    val totalActiveStories: Int = 0,
    val avgFunScore: Double = 0.0,
    val momentVibe: String = "good",
    val crowdBreakdown: Map<String, Int> = emptyMap(),
    val weatherBreakdown: Map<String, Int> = emptyMap(),
)

data class UserActivitySummary(
    val userId: Int,
    val displayName: String,
    val username: String,
    val organizationName: String,
    val storiesCount: Int,
    val commentsCount: Int,
    val publicMessagesCount: Int,
    val friendsCount: Int,
    val recentStoryCaptions: List<String> = emptyList(),
    val recentComments: List<String> = emptyList(),
)

data class SkiNewsItem(
    val id: Int,
    val title: String,
    val summary: String,
    val link: String,
    val sourceName: String,
    val language: String,
    val stationId: Int?,
    val stationName: String,
    val publishedAtLabel: String,
    val publishedAtRaw: String,
    val highlighted: Boolean,
)

enum class NativeTab {
    HOME,
    NEWS,
    STORIES,
    COMMUNITY,
    STATIONS,
    BUS_LINES,
    SERVICES,
    MARKETPLACE,
    PARTNERS,
    INSTRUCTORS,
    PISTES,
    MESSAGES,
    PROFILE,
}

data class StationItem(
    val id: Int,
    val name: String,
    val altitudeLabel: String,
    val distanceLabel: String,
    val capacityLabel: String,
    val imageBase64: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pisteMapUrl: String = "",
    val pisteMapThumbnailUrl: String = "",
    val cameras: List<StationCameraItem> = emptyList(),
)

data class StationCameraItem(
    val id: Int,
    val name: String,
    val cameraUrl: String,
    val thumbnailUrl: String = "",
    val description: String = "",
    val cameraType: String = "",
)

data class BusLineItem(
    val id: Int,
    val stationId: Int,
    val stationName: String,
    val busNumber: String,
    val departureStop: String,
    val arrivalStop: String,
    val frequency: String,
    val travelTime: String,
    val routePoints: String,
)

data class ServiceStoreItem(
    val id: Int,
    val stationId: Int,
    val stationName: String,
    val name: String,
    val type: String,
    val openingHours: String,
    val address: String,
    val phone: String,
    val websiteUrl: String,
    val sourceNote: String,
)

data class MarketplaceItem(
    val id: Int,
    val title: String,
    val description: String,
    val city: String,
    val priceLabel: String,
    val conditionLabel: String,
    val materialTypeLabel: String,
    val transactionTypeLabel: String,
    val sellerId: Int,
    val sellerLabel: String,
    val sellerPhotoBase64: String = "",
    val sellerPhotoUrl: String = "",
    val postedAtLabel: String,
    val previewImageBase64: String,
    val imageGalleryBase64: List<String> = emptyList(),
)

data class MarketplacePage(
    val items: List<MarketplaceItem>,
    val hasNextPage: Boolean,
    val nextPage: Int? = null,
)

data class SkiPartnerItem(
    val id: Int,
    val organizerId: Int,
    val organizerLabel: String,
    val title: String,
    val message: String,
    val city: String,
    val stationLabel: String,
    val levelLabel: String,
    val preferredDateLabel: String,
    val isCarpool: Boolean = false,
    val departureCity: String = "",
    val departureDateTimeLabel: String = "",
    val seatsReserved: Int = 0,
    val seatsRemaining: Int = 0,
    val totalSeats: Int = 0,
    val myReservedSeats: Int = 0,
    val myReservationStatus: String = "",
    val pendingReservations: List<CarpoolPendingReservation> = emptyList(),
)

data class CarpoolPendingReservation(
    val reservationId: Int,
    val userId: Int,
    val userLabel: String,
    val seatsReserved: Int,
)

data class InstructorItem(
    val id: Int,
    val userId: Int,
    val displayName: String,
    val bio: String,
    val yearsExperience: Int,
    val certifications: String,
    val phone: String,
    val profilePhotoBase64: String,
)

data class PisteItem(
    val id: Int,
    val stationName: String,
    val altitudeLabel: String,
    val distanceLabel: String,
    val pisteMapUrl: String = "",
    val pisteMapThumbnailUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ratingLabel: String,
    val crowdLabel: String,
    val weatherLabel: String,
    val temperatureLabel: String,
    val snowDepthLabel: String,
    val comment: String,
    val updatedAtLabel: String,
)

data class MessageItem(
    val id: Int,
    val senderId: Int,
    val recipientId: Int,
    val senderLabel: String,
    val recipientLabel: String,
    val senderPhotoBase64: String,
    val senderPhotoUrl: String,
    val recipientPhotoBase64: String,
    val recipientPhotoUrl: String,
    val body: String,
    val createdAtLabel: String,
    val isRead: Boolean,
)

data class ProfileInfo(
    val userId: Int,
    val displayName: String,
    val email: String,
    val username: String,
    val firstName: String = "",
    val lastName: String = "",
    val profilePictureBase64: String = "",
    val googleProfilePictureUrl: String = "",
)

data class ChatUserOption(
    val id: Int,
    val label: String,
    val photoBase64: String,
    val photoUrl: String,
)

data class FriendLink(
    val id: Int,
    val friendId: Int,
)

data class FriendInvitation(
    val id: Int,
    val fromUserId: Int,
    val toUserId: Int,
    val status: String,
)
