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

enum class NativeTab {
    HOME,
    STATIONS,
    BUS_LINES,
    SERVICES,
    MARKETPLACE,
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
    val sellerId: Int,
    val sellerLabel: String,
    val postedAtLabel: String,
    val previewImageBase64: String,
    val imageGalleryBase64: List<String> = emptyList(),
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
