package fr.grenobleski.nativeapp.data

import com.google.gson.JsonElement
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.LoginRequest
import fr.grenobleski.nativeapp.data.model.LoginResponse
import fr.grenobleski.nativeapp.data.model.RegisterRequest
import fr.grenobleski.nativeapp.data.model.UserSession
import fr.grenobleski.nativeapp.data.network.GrenobleSkiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val service: GrenobleSkiApiService,
    private val siteBaseUrl: String,
) {
    private val normalizedBaseUrl = siteBaseUrl.trimEnd('/')

    suspend fun login(email: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val endpoints = listOf("/api/auth/login/", "/api/login/")
        var lastError = "Unable to authenticate with this server."

        for (endpoint in endpoints) {
            val response = service.login(
                url = "$normalizedBaseUrl$endpoint",
                payload = LoginRequest(email = email, password = password),
            )

            if (response.isSuccessful) {
                val body = response.body()
                val session = body.toSession(email)
                if (session != null) {
                    return@withContext Result.success(session)
                }
                lastError = body?.error ?: body?.detail ?: "Authentication succeeded but no token was returned."
                continue
            }

            if (response.code() in listOf(404, 405, 501)) {
                continue
            }

            val bodyText = response.errorBody()?.string().orEmpty()
            if (bodyText.isNotBlank()) {
                lastError = bodyText
            }
            break
        }

        Result.failure(IllegalStateException(lastError))
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        val response = service.register(
            url = "$normalizedBaseUrl/api/auth/register/",
            payload = RegisterRequest(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
            ),
        )

        if (response.isSuccessful) {
            val session = response.body().toSession(email)
            if (session != null) {
                return@withContext Result.success(session)
            }
            return@withContext Result.failure(
                IllegalStateException("Account created but no token was returned.")
            )
        }

        val bodyText = response.errorBody()?.string().orEmpty()
        val message = if (bodyText.isNotBlank()) bodyText else "Unable to create account."
        Result.failure(IllegalStateException(message))
    }

    suspend fun fetchDashboardCounts(token: String): Result<DashboardCounts> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"

        val counts = DashboardCounts(
            stations = fetchCount("/api/skistations/", authHeader),
            busLines = fetchCount("/api/buslines/", authHeader),
            services = fetchCount("/api/servicestores/", authHeader),
            marketplace = fetchCount("/api/skimaterial/", authHeader),
        )

        Result.success(counts)
    }

    private suspend fun fetchCount(path: String, authHeader: String): Int {
        val response = service.listResource("$normalizedBaseUrl$path", authHeader)
        if (!response.isSuccessful) {
            return 0
        }
        return parseCount(response.body())
    }

    private fun parseCount(payload: JsonElement?): Int {
        payload ?: return 0

        if (payload.isJsonArray) {
            return payload.asJsonArray.size()
        }

        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            if (obj.has("results") && obj.get("results").isJsonArray) {
                return obj.getAsJsonArray("results").size()
            }
        }

        return 0
    }

    private fun LoginResponse?.toSession(defaultEmail: String): UserSession? {
        val payload = this ?: return null
        val token = payload.token.orEmpty()
        if (token.isBlank()) {
            return null
        }

        val resolvedEmail = payload.user?.email.orEmpty().ifBlank { defaultEmail }
        val first = payload.user?.firstName.orEmpty()
        val last = payload.user?.lastName.orEmpty()
        val display = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
        val label = if (display.isBlank()) resolvedEmail else display

        return UserSession(token = token, email = resolvedEmail, displayName = label)
    }
}
