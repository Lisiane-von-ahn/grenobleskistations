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
)

data class DashboardCounts(
    val stations: Int = 0,
    val busLines: Int = 0,
    val services: Int = 0,
    val marketplace: Int = 0,
)
