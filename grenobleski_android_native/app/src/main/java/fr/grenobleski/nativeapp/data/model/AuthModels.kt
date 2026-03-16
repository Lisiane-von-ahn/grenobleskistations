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
    MARKETPLACE,
    INSTRUCTORS,
    PISTES,
    MESSAGES,
    PROFILE,
}

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
)

data class InstructorItem(
    val id: Int,
    val displayName: String,
    val bio: String,
)

data class PisteItem(
    val id: Int,
    val stationName: String,
    val rating: Int,
    val crowdLabel: String,
    val comment: String,
)

data class MessageItem(
    val id: Int,
    val senderId: Int,
    val recipientId: Int,
    val fromLabel: String,
    val body: String,
    val createdAtLabel: String,
    val isRead: Boolean,
)

data class ProfileInfo(
    val userId: Int,
    val displayName: String,
    val email: String,
    val username: String,
)

data class ChatUserOption(
    val id: Int,
    val label: String,
)
