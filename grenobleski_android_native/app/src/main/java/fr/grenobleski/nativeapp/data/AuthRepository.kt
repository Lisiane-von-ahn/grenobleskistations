package fr.grenobleski.nativeapp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.InstructorItem
import fr.grenobleski.nativeapp.data.model.LoginRequest
import fr.grenobleski.nativeapp.data.model.LoginResponse
import fr.grenobleski.nativeapp.data.model.MarketplaceItem
import fr.grenobleski.nativeapp.data.model.MessageItem
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
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

    suspend fun fetchMarketplaceItems(token: String): Result<List<MarketplaceItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/skimaterial/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load marketplace."))

        val items = extractObjectList(payload).map { obj ->
            val sellerId = obj.intOrZero("user", "user_id")
            MarketplaceItem(
                id = obj.intOrZero("id"),
                title = obj.stringOrBlank("title", "material_type").ifBlank { "Annonce #${obj.intOrZero("id")}" },
                description = obj.stringOrBlank("description").ifBlank { "Aucune description." },
                city = obj.stringOrBlank("city").ifBlank { "-" },
                priceLabel = obj.stringOrBlank("price").ifBlank { "-" },
                conditionLabel = obj.stringOrBlank("condition").ifBlank { "-" },
                sellerId = sellerId,
                sellerLabel = if (sellerId > 0) "Vendeur #$sellerId" else "Vendeur",
                postedAtLabel = obj.stringOrBlank("posted_at", "created_at"),
            )
        }
        Result.success(items)
    }

    suspend fun fetchInstructorItems(token: String): Result<List<InstructorItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/instructorprofiles/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load instructors."))

        val items = extractObjectList(payload).map { obj ->
            val userObj = obj.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            val firstName = userObj?.stringOrBlank("first_name").orEmpty()
            val lastName = userObj?.stringOrBlank("last_name").orEmpty()
            val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            val displayName = fullName.ifBlank {
                userObj?.stringOrBlank("username", "email").orEmpty().ifBlank { "Moniteur #${obj.intOrZero("id")}" }
            }

            InstructorItem(
                id = obj.intOrZero("id"),
                displayName = displayName,
                bio = obj.stringOrBlank("bio", "description", "presentation").ifBlank { "Profil moniteur" },
            )
        }
        Result.success(items)
    }

    suspend fun fetchPisteItems(token: String): Result<List<PisteItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/pistereports/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load piste state."))

        val items = extractObjectList(payload).map { obj ->
            val stationObj = obj.get("ski_station")?.takeIf { it.isJsonObject }?.asJsonObject
            PisteItem(
                id = obj.intOrZero("id"),
                stationName = obj.stringOrBlank("ski_station_name").ifBlank {
                    stationObj?.stringOrBlank("name").orEmpty().ifBlank { "Station" }
                },
                rating = obj.intOrZero("piste_rating"),
                crowdLabel = obj.stringOrBlank("crowd_level").ifBlank { "normal" },
                comment = obj.stringOrBlank("comment").ifBlank { "-" },
            )
        }
        Result.success(items)
    }

    suspend fun fetchMessageItems(token: String): Result<List<MessageItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val candidates = listOf("/api/messages/", "/api/messages")

        for (path in candidates) {
            val response = runCatching {
                service.listResource("$normalizedBaseUrl$path", authHeader)
            }.getOrNull() ?: continue

            if (!response.isSuccessful) {
                if (response.code() in listOf(404, 405)) {
                    continue
                }
                // Non-fatal for now: keep native UI usable even if this endpoint is flaky in some deployments.
                return@withContext Result.success(emptyList())
            }

            val payload = response.body()
            val items = extractObjectList(payload).map { obj ->
                val senderObj = obj.get("sender")?.takeIf { it.isJsonObject }?.asJsonObject
                val senderName = senderObj?.stringOrBlank("username", "email").orEmpty()
                val senderId = if (senderObj != null) senderObj.intOrZero("id") else obj.intOrZero("sender", "sender_id")
                val recipientId = obj.intOrZero("recipient", "recipient_id")
                MessageItem(
                    id = obj.intOrZero("id"),
                    senderId = senderId,
                    recipientId = recipientId,
                    fromLabel = senderName.ifBlank {
                        obj.stringOrBlank("sender_username", "sender_name").ifBlank { "Utilisateur" }
                    },
                    body = obj.stringOrBlank("body", "message", "content").ifBlank { "-" },
                    createdAtLabel = obj.stringOrBlank("created_at", "timestamp").ifBlank { "" },
                    isRead = obj.boolOrFalse("is_read"),
                )
            }
            return@withContext Result.success(items)
        }

        // Endpoint unavailable in this deployment; do not break the whole native experience.
        Result.success(emptyList())
    }

    suspend fun fetchProfileInfo(token: String): Result<ProfileInfo> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/auth/me/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load profile."))

        val root = payload.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Result.failure(IllegalStateException("Invalid profile payload."))
        val user = root.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Result.failure(IllegalStateException("Invalid user payload."))

        val firstName = user.stringOrBlank("first_name")
        val lastName = user.stringOrBlank("last_name")
        val displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

        Result.success(
            ProfileInfo(
                userId = user.intOrZero("id"),
                displayName = displayName.ifBlank { user.stringOrBlank("username", "email") },
                email = user.stringOrBlank("email"),
                username = user.stringOrBlank("username"),
            )
        )
    }

    suspend fun sendMessage(
        token: String,
        recipientId: Int,
        subject: String,
        body: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/messages/",
                authHeader = authHeader,
                payload = mapOf(
                    "recipient" to recipientId,
                    "subject" to subject,
                    "body" to body,
                ),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to send message."))

        if (!response.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("Unable to send message."))
        }

        Result.success(Unit)
    }

    suspend fun publishMarketplaceListing(
        token: String,
        userId: Int,
        title: String,
        description: String,
        city: String,
        price: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = mutableMapOf<String, Any>(
            "user" to userId,
            "title" to title,
            "description" to description,
            "city" to city,
            "material_type" to "ski",
            "transaction_type" to "sale",
            "condition" to "good",
        )
        if (price.isNotBlank()) {
            payload["price"] = price
        }

        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skimaterial/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to publish article."))

        if (!response.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("Unable to publish article."))
        }

        Result.success(Unit)
    }

    private suspend fun fetchCount(path: String, authHeader: String): Int {
        return parseCount(fetchPayload(path, authHeader))
    }

    private suspend fun fetchPayload(path: String, authHeader: String): JsonElement? {
        val response = service.listResource("$normalizedBaseUrl$path", authHeader)
        if (!response.isSuccessful) {
            return null
        }
        return response.body()
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

    private fun extractObjectList(payload: JsonElement?): List<JsonObject> {
        payload ?: return emptyList()

        if (payload.isJsonArray) {
            return payload.asJsonArray.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject
            }
        }

        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            if (obj.has("results") && obj.get("results").isJsonArray) {
                return obj.getAsJsonArray("results").mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject
                }
            }
        }

        return emptyList()
    }

    private fun JsonObject.stringOrBlank(vararg keys: String): String {
        for (key in keys) {
            if (!has(key)) continue
            val value = get(key)
            if (value.isJsonNull) continue
            if (value.isJsonPrimitive) {
                return value.asString.orEmpty()
            }
        }
        return ""
    }

    private fun JsonObject.intOrZero(vararg keys: String): Int {
        for (key in keys) {
            if (!has(key)) continue
            val value = get(key)
            if (value.isJsonPrimitive) {
                runCatching { return value.asInt }.getOrNull()
            }
        }
        return 0
    }

    private fun JsonObject.boolOrFalse(vararg keys: String): Boolean {
        for (key in keys) {
            if (!has(key)) continue
            val value = get(key)
            if (value.isJsonPrimitive) {
                runCatching { return value.asBoolean }.getOrNull()
            }
        }
        return false
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
        val userId = payload.user?.id ?: 0

        return UserSession(token = token, email = resolvedEmail, displayName = label, userId = userId)
    }
}
