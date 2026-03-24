package fr.grenobleski.nativeapp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.BusLineItem
import fr.grenobleski.nativeapp.data.model.ChatUserOption
import fr.grenobleski.nativeapp.data.model.FriendLink
import fr.grenobleski.nativeapp.data.model.InstructorItem
import fr.grenobleski.nativeapp.data.model.LoginRequest
import fr.grenobleski.nativeapp.data.model.LoginResponse
import fr.grenobleski.nativeapp.data.model.MarketplaceItem
import fr.grenobleski.nativeapp.data.model.MessageItem
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
import fr.grenobleski.nativeapp.data.model.RegisterRequest
import fr.grenobleski.nativeapp.data.model.ServiceStoreItem
import fr.grenobleski.nativeapp.data.model.SkiPartnerItem
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem
import fr.grenobleski.nativeapp.data.model.UserSession
import fr.grenobleski.nativeapp.data.network.GrenobleSkiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AuthRepository(
    private val service: GrenobleSkiApiService,
    private val siteBaseUrl: String,
) {
    private val normalizedBaseUrl = siteBaseUrl.trimEnd('/')
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)

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
        acceptTerms: Boolean,
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        val response = service.register(
            url = "$normalizedBaseUrl/api/auth/register/",
            payload = RegisterRequest(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                acceptTerms = acceptTerms,
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

    suspend fun fetchStationItems(token: String): Result<List<StationItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/skistations/", "/api/skistations"), authHeader)
            ?: fetchPayloadFromCandidates(listOf("/api/skistations/conditions/", "/api/skistations/conditions"), authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load stations."))

        val items = extractObjectList(payload).map { obj ->
            val altitude = obj.stringOrBlank("altitude").ifBlank {
                obj.intOrZero("altitude").takeIf { it > 0 }?.toString().orEmpty()
            }
            val distance = obj.stringOrBlank("distanceFromGrenoble", "distance_from_grenoble").ifBlank {
                obj.intOrZero("distanceFromGrenoble", "distance_from_grenoble").takeIf { it > 0 }?.toString().orEmpty()
            }
            val capacity = obj.stringOrBlank("capacity").ifBlank {
                obj.intOrZero("capacity").takeIf { it > 0 }?.toString().orEmpty()
            }
            val stationName = obj.stringOrBlank("name", "station_name").ifBlank { "Station" }
            val cameras = obj.arrayObjects("cameras").map { cameraObj ->
                StationCameraItem(
                    id = cameraObj.intOrZero("id"),
                    name = cameraObj.stringOrBlank("name").ifBlank { "Camera" },
                    cameraUrl = cameraObj.stringOrBlank("camera_url"),
                    thumbnailUrl = cameraObj.stringOrBlank("thumbnail_url"),
                    description = cameraObj.stringOrBlank("description"),
                    cameraType = cameraObj.stringOrBlank("camera_type"),
                )
            }.filter { it.cameraUrl.isNotBlank() }

            StationItem(
                id = obj.intOrZero("id"),
                name = stationName,
                altitudeLabel = altitude.ifBlank { "-" },
                distanceLabel = distance.ifBlank { "-" },
                capacityLabel = capacity.ifBlank { "-" },
                imageBase64 = obj.stringOrBlank("image"),
                latitude = obj.doubleOrNull("latitude"),
                longitude = obj.doubleOrNull("longitude"),
                cameras = cameras,
            )
        }
        Result.success(items)
    }

    suspend fun fetchBusLineItems(token: String): Result<List<BusLineItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/buslines/", "/api/buslines"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val items = extractObjectList(payload).map { obj ->
            BusLineItem(
                id = obj.intOrZero("id"),
                stationId = obj.intOrZero("ski_station", "ski_station_id"),
                stationName = obj.stringOrBlank("station_name", "ski_station_name"),
                busNumber = obj.stringOrBlank("bus_number").ifBlank { "-" },
                departureStop = obj.stringOrBlank("departure_stop").ifBlank { "-" },
                arrivalStop = obj.stringOrBlank("arrival_stop").ifBlank { "-" },
                frequency = obj.stringOrBlank("frequency").ifBlank { "-" },
                travelTime = obj.stringOrBlank("travel_time").ifBlank { "-" },
                routePoints = obj.stringOrBlank("route_points").ifBlank { "-" },
            )
        }
        Result.success(items)
    }

    suspend fun fetchServiceStoreItems(token: String): Result<List<ServiceStoreItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/servicestores/", "/api/servicestores"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val items = extractObjectList(payload).map { obj ->
            ServiceStoreItem(
                id = obj.intOrZero("id"),
                stationId = obj.intOrZero("ski_station", "ski_station_id"),
                stationName = obj.stringOrBlank("station_name", "ski_station_name"),
                name = obj.stringOrBlank("name").ifBlank { "Service" },
                type = obj.stringOrBlank("type").ifBlank { "-" },
                openingHours = obj.stringOrBlank("opening_hours").ifBlank { "-" },
                address = obj.stringOrBlank("address").ifBlank { "-" },
                phone = obj.stringOrBlank("phone"),
                websiteUrl = obj.stringOrBlank("website_url"),
                sourceNote = obj.stringOrBlank("source_note"),
            )
        }
        Result.success(items)
    }

    suspend fun fetchMarketplaceItems(token: String): Result<List<MarketplaceItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/skimaterial/", "/api/skimaterial"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val materialLabels = mapOf(
            "ski" to "Materiel ski",
            "boots" to "Chaussures",
            "helmet" to "Casque",
            "jacket" to "Veste",
            "pants" to "Pantalon",
            "gloves" to "Gants",
            "goggles" to "Masque",
            "service" to "Service",
            "transport" to "Transport",
            "accommodation" to "Hebergement",
            "other" to "Autre",
        )
        val transactionLabels = mapOf(
            "sale" to "A vendre",
            "rent" to "A louer",
            "lend" to "A preter",
            "service" to "Prestation",
        )

        val items = extractObjectList(payload).map { obj ->
            val sellerId = obj.intOrZero("user", "user_id")
            val imageGalleryBase64 = buildList {
                val coverImage = obj.stringOrBlank("image")
                if (coverImage.isNotBlank()) add(coverImage)
                addAll(obj.arrayObjectStrings("images", "image"))
            }.distinct()
            val previewImageBase64 = imageGalleryBase64.firstOrNull().orEmpty()
            val materialType = obj.stringOrBlank("material_type").lowercase()
            val transactionType = obj.stringOrBlank("transaction_type").lowercase()
            MarketplaceItem(
                id = obj.intOrZero("id"),
                title = obj.stringOrBlank("title", "material_type").ifBlank { "Annonce #${obj.intOrZero("id")}" },
                description = obj.stringOrBlank("description").ifBlank { "Aucune description." },
                city = obj.stringOrBlank("city").ifBlank { "-" },
                priceLabel = obj.stringOrBlank("price").ifBlank { "-" },
                conditionLabel = obj.stringOrBlank("condition").ifBlank { "-" },
                materialTypeLabel = materialLabels[materialType] ?: materialType.ifBlank { "Autre" },
                transactionTypeLabel = transactionLabels[transactionType] ?: transactionType.ifBlank { "-" },
                sellerId = sellerId,
                sellerLabel = if (sellerId > 0) "Vendeur #$sellerId" else "Vendeur",
                postedAtLabel = formatServerDateTime(obj.stringOrBlank("posted_at", "created_at")),
                previewImageBase64 = previewImageBase64,
                imageGalleryBase64 = imageGalleryBase64,
            )
        }
        Result.success(items)
    }

    suspend fun fetchInstructorItems(token: String): Result<List<InstructorItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/instructorprofiles/", "/api/instructorprofiles"), authHeader)
            ?: return@withContext Result.success(emptyList())

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
                userId = userObj?.intOrZero("id") ?: 0,
                displayName = displayName,
                bio = obj.stringOrBlank("bio", "description", "presentation").ifBlank { "Profil moniteur" },
                yearsExperience = obj.intOrZero("years_experience"),
                certifications = obj.stringOrBlank("certifications"),
                phone = obj.stringOrBlank("phone"),
                profilePhotoBase64 = obj.stringOrBlank("profile_photo"),
            )
        }
        Result.success(items)
    }

    suspend fun fetchPartnerItems(token: String): Result<List<SkiPartnerItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/skipartnerposts/", "/api/skipartnerposts"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val userMap = fetchChatUsers(token)
            .getOrDefault(emptyList())
            .associateBy { it.id }

        val levelLabels = mapOf(
            "beginner" to "Debutant",
            "intermediate" to "Intermediaire",
            "advanced" to "Avance",
        )

        val items = extractObjectList(payload).map { obj ->
            val organizerId = obj.intOrZero("user", "user_id")
            val organizerLabel = userMap[organizerId]?.label
                ?: "Utilisateur #$organizerId"

            SkiPartnerItem(
                id = obj.intOrZero("id"),
                organizerId = organizerId,
                organizerLabel = organizerLabel,
                title = obj.stringOrBlank("title").ifBlank { "Sortie ski" },
                message = obj.stringOrBlank("message").ifBlank { "-" },
                city = obj.stringOrBlank("city").ifBlank { "-" },
                stationLabel = obj.stringOrBlank("ski_station_name", "station_name").ifBlank { "-" },
                levelLabel = levelLabels[obj.stringOrBlank("skill_level")] ?: obj.stringOrBlank("skill_level").ifBlank { "-" },
                preferredDateLabel = obj.stringOrBlank("preferred_date").ifBlank { "-" },
            )
        }
        Result.success(items)
    }

    suspend fun fetchPisteItems(token: String): Result<List<PisteItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val conditionsPayload = fetchPayloadFromCandidates(listOf("/api/skistations/conditions/", "/api/skistations/conditions"), authHeader)
        if (conditionsPayload != null) {
            val items = extractObjectList(conditionsPayload).map { obj ->
                val ratingAvg = obj.stringOrBlank("rating_avg").ifBlank {
                    obj.get("rating_avg")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.toString().orEmpty()
                }
                val snowDepth = obj.stringOrBlank("snow_depth_cm")
                val temperatureValue = obj.stringOrBlank("temperature_c")
                PisteItem(
                    id = obj.intOrZero("id"),
                    stationName = obj.stringOrBlank("station_name", "name").ifBlank { "Station" },
                    altitudeLabel = obj.stringOrBlank("altitude").ifBlank { "-" },
                    distanceLabel = obj.stringOrBlank("distance_from_grenoble").ifBlank { "-" },
                    pisteMapUrl = obj.stringOrBlank("piste_map_url"),
                    pisteMapThumbnailUrl = obj.stringOrBlank("piste_map_thumbnail_url"),
                    latitude = obj.doubleOrNull("latitude"),
                    longitude = obj.doubleOrNull("longitude"),
                    ratingLabel = ratingAvg.ifBlank { "-" },
                    crowdLabel = obj.stringOrBlank("crowd_label").ifBlank { "normal" },
                    weatherLabel = obj.stringOrBlank("weather_description").ifBlank { "indisponible" },
                    temperatureLabel = temperatureValue.ifBlank { "-" },
                    snowDepthLabel = snowDepth.ifBlank { "-" },
                    comment = obj.stringOrBlank("latest_comment").ifBlank { "-" },
                    updatedAtLabel = formatServerDateTime(obj.stringOrBlank("updated_at")),
                )
            }
            return@withContext Result.success(items)
        }

        val payload = fetchPayloadFromCandidates(listOf("/api/pistereports/", "/api/pistereports"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val items = extractObjectList(payload).map { obj ->
            val stationObj = obj.get("ski_station")?.takeIf { it.isJsonObject }?.asJsonObject
            PisteItem(
                id = obj.intOrZero("id"),
                stationName = obj.stringOrBlank("ski_station_name").ifBlank {
                    stationObj?.stringOrBlank("name").orEmpty().ifBlank { "Station" }
                },
                altitudeLabel = stationObj?.stringOrBlank("altitude").orEmpty().ifBlank { "-" },
                distanceLabel = stationObj?.stringOrBlank("distanceFromGrenoble").orEmpty().ifBlank { "-" },
                pisteMapUrl = stationObj?.stringOrBlank("piste_map_url").orEmpty(),
                pisteMapThumbnailUrl = stationObj?.stringOrBlank("piste_map_thumbnail_url").orEmpty(),
                latitude = stationObj?.doubleOrNull("latitude"),
                longitude = stationObj?.doubleOrNull("longitude"),
                ratingLabel = obj.stringOrBlank("piste_rating").ifBlank { "-" },
                crowdLabel = obj.stringOrBlank("crowd_level").ifBlank { "normal" },
                weatherLabel = "indisponible",
                temperatureLabel = "-",
                snowDepthLabel = "-",
                comment = obj.stringOrBlank("comment").ifBlank { "-" },
                updatedAtLabel = formatServerDateTime(obj.stringOrBlank("created_at", "timestamp")),
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
                val senderRichObj = obj.get("sender_user")?.takeIf { it.isJsonObject }?.asJsonObject
                val recipientObj = obj.get("recipient")?.takeIf { it.isJsonObject }?.asJsonObject
                val recipientRichObj = obj.get("recipient_user")?.takeIf { it.isJsonObject }?.asJsonObject

                val senderPayload = senderRichObj ?: senderObj
                val recipientPayload = recipientRichObj ?: recipientObj

                val senderId = senderPayload?.intOrZero("id") ?: obj.intOrZero("sender", "sender_id")
                val recipientId = recipientPayload?.intOrZero("id") ?: obj.intOrZero("recipient", "recipient_id")

                val senderLabel = senderPayload?.stringOrBlank("display_name", "username", "email").orEmpty()
                    .ifBlank { obj.stringOrBlank("sender_username", "sender_name") }
                    .ifBlank { "Utilisateur" }
                val recipientLabel = recipientPayload?.stringOrBlank("display_name", "username", "email").orEmpty()
                    .ifBlank { obj.stringOrBlank("recipient_username", "recipient_name") }
                    .ifBlank { "Utilisateur" }

                MessageItem(
                    id = obj.intOrZero("id"),
                    senderId = senderId,
                    recipientId = recipientId,
                    senderLabel = senderLabel,
                    recipientLabel = recipientLabel,
                    senderPhotoBase64 = senderPayload?.stringOrBlank("profile_picture").orEmpty(),
                    senderPhotoUrl = senderPayload?.stringOrBlank("google_profile_picture_url").orEmpty(),
                    recipientPhotoBase64 = recipientPayload?.stringOrBlank("profile_picture").orEmpty(),
                    recipientPhotoUrl = recipientPayload?.stringOrBlank("google_profile_picture_url").orEmpty(),
                    body = obj.stringOrBlank("body", "message", "content").ifBlank { "-" },
                    createdAtLabel = formatServerDateTime(obj.stringOrBlank("created_at", "timestamp")),
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
        val payload = fetchPayloadFromCandidates(
            listOf("/api/auth/me/", "/api/auth/me", "/api/userprofile/me/"),
            authHeader,
        ) ?: return@withContext Result.success(
            ProfileInfo(
                userId = 0,
                displayName = "",
                email = "",
                username = "",
            )
        )

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
                firstName = firstName,
                lastName = lastName,
                profilePictureBase64 = user.stringOrBlank("profile_picture"),
                googleProfilePictureUrl = user.stringOrBlank("google_profile_picture_url"),
            )
        )
    }

    suspend fun fetchChatUsers(token: String): Result<List<ChatUserOption>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/userview/", "/api/userview"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val users = extractObjectList(payload).mapNotNull { obj ->
            val id = obj.intOrZero("id")
            if (id <= 0) return@mapNotNull null
            val label = listOf(
                obj.stringOrBlank("first_name"),
                obj.stringOrBlank("last_name"),
            ).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { obj.stringOrBlank("display_name", "username", "email") }
                .ifBlank { "Utilisateur #$id" }

            ChatUserOption(
                id = id,
                label = label,
                photoBase64 = obj.stringOrBlank("profile_picture"),
                photoUrl = obj.stringOrBlank("google_profile_picture_url"),
            )
        }
        Result.success(users)
    }

    suspend fun fetchFriendLinks(token: String): Result<List<FriendLink>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/userfriends/", "/api/userfriends"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val links = extractObjectList(payload).mapNotNull { obj ->
            val id = obj.intOrZero("id")
            val friendId = obj.intOrZero("friend", "friend_id")
            if (id <= 0 || friendId <= 0) {
                null
            } else {
                FriendLink(id = id, friendId = friendId)
            }
        }
        Result.success(links)
    }

    suspend fun addFriend(token: String, friendId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (friendId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid friend id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/userfriends/",
                authHeader = authHeader,
                payload = mapOf("friend" to friendId),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to add friend."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to add friend." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        Result.success(Unit)
    }

    suspend fun removeFriend(token: String, linkId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (linkId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid friend link."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.deleteResource(
                url = "$normalizedBaseUrl/api/userfriends/$linkId/",
                authHeader = authHeader,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to remove friend."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to remove friend." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        Result.success(Unit)
    }

    suspend fun markThreadAsRead(token: String, otherUserId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (otherUserId <= 0) {
            return@withContext Result.success(Unit)
        }

        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/messages/mark-read/",
                authHeader = authHeader,
                payload = mapOf("user_id" to otherUserId),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to mark messages as read."))

        if (!response.isSuccessful) {
            return@withContext Result.failure(
                IllegalStateException(response.errorBody()?.string().orEmpty().ifBlank { "Unable to mark messages as read." })
            )
        }

        Result.success(Unit)
    }

    suspend fun updateProfile(token: String, firstName: String, lastName: String, email: String): Result<ProfileInfo> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val response = runCatching {
            service.patchResource(
                url = "$normalizedBaseUrl/api/auth/profile/update/",
                authHeader = authHeader,
                payload = mapOf(
                    "first_name" to firstName.trim(),
                    "last_name" to lastName.trim(),
                    "email" to email.trim(),
                ),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to update profile."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to update profile." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        val payload = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Result.failure(IllegalStateException("Invalid profile response."))
        val user = payload.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Result.failure(IllegalStateException("Invalid profile user payload."))

        val first = user.stringOrBlank("first_name")
        val last = user.stringOrBlank("last_name")
        val displayName = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")

        Result.success(
            ProfileInfo(
                userId = user.intOrZero("id"),
                displayName = displayName.ifBlank { user.stringOrBlank("username", "email") },
                email = user.stringOrBlank("email"),
                username = user.stringOrBlank("username"),
                firstName = first,
                lastName = last,
                profilePictureBase64 = user.stringOrBlank("profile_picture"),
                googleProfilePictureUrl = user.stringOrBlank("google_profile_picture_url"),
            )
        )
    }

    suspend fun changePassword(
        token: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/auth/password/change/",
                authHeader = authHeader,
                payload = mapOf(
                    "current_password" to currentPassword,
                    "new_password" to newPassword,
                    "confirm_password" to confirmPassword,
                ),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to update password."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to update password." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        val payload = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return@withContext Result.failure(IllegalStateException("Invalid password update response."))
        val newToken = payload.stringOrBlank("token")
        if (newToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Password changed but token is missing."))
        }
        Result.success(newToken)
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
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to send message." }
            return@withContext Result.failure(IllegalStateException(bodyText))
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
        imagesBase64: List<String>,
        materialType: String,
        transactionType: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = mutableMapOf<String, Any>(
            "user" to userId,
            "title" to title,
            "description" to description,
            "city" to city,
            "material_type" to materialType,
            "transaction_type" to transactionType,
            "condition" to "good",
        )
        if (price.isNotBlank()) {
            payload["price"] = price
        }
        val cleanedImages = imagesBase64.filter { it.isNotBlank() }
        if (cleanedImages.isNotEmpty()) {
            payload["image"] = cleanedImages.first()
            payload["images"] = cleanedImages
        }

        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skimaterial/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to publish article."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val message = extractApiErrorMessage(bodyText, "Unable to publish article.")
            return@withContext Result.failure(IllegalStateException(message))
        }

        Result.success(Unit)
    }

    suspend fun publishPartnerPost(
        token: String,
        title: String,
        message: String,
        city: String,
        skillLevel: String,
        preferredDate: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = mutableMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "city" to city,
            "skill_level" to skillLevel,
        )
        if (preferredDate.isNotBlank()) {
            payload["preferred_date"] = preferredDate
        }

        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skipartnerposts/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to publish partner post."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val errorMessage = extractApiErrorMessage(bodyText, "Unable to publish partner post.")
            return@withContext Result.failure(IllegalStateException(errorMessage))
        }

        Result.success(Unit)
    }

    suspend fun rateSeller(
        token: String,
        listingId: Int,
        ratedUserId: Int,
        score: Int,
        comment: String,
        raterUserId: Int = 0,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (listingId <= 0 || ratedUserId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid seller rating payload."))
        }
        if (score !in 1..5) {
            return@withContext Result.failure(IllegalStateException("Score must be between 1 and 5."))
        }

        val authHeader = "Token $token"
        val payload = mutableMapOf<String, Any>(
            "listing" to listingId,
            "rated_user" to ratedUserId,
            "score" to score,
        )
        if (comment.isNotBlank()) {
            payload["comment"] = comment
        }
        if (raterUserId > 0) {
            payload["rater"] = raterUserId
        }

        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/marketplace-ratings/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to rate seller."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val message = extractApiErrorMessage(bodyText, "Unable to rate seller.")
            return@withContext Result.failure(IllegalStateException(message))
        }
        Result.success(Unit)
    }

    suspend fun rateStation(
        token: String,
        stationId: Int,
        score: Int,
        comment: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (stationId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid station id."))
        }
        if (score !in 1..5) {
            return@withContext Result.failure(IllegalStateException("Score must be between 1 and 5."))
        }

        val authHeader = "Token $token"
        val payload = mutableMapOf<String, Any>(
            "ski_station" to stationId,
            "piste_rating" to score,
            "crowd_level" to "normal",
        )
        if (comment.isNotBlank()) {
            payload["comment"] = comment
        }

        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/pistereports/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to rate station."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val message = extractApiErrorMessage(bodyText, "Unable to rate station.")
            return@withContext Result.failure(IllegalStateException(message))
        }
        Result.success(Unit)
    }

    private suspend fun fetchCount(path: String, authHeader: String): Int {
        return parseCount(fetchPayload(path, authHeader))
    }

    private suspend fun fetchPayloadFromCandidates(paths: List<String>, authHeader: String): JsonElement? {
        for (path in paths) {
            val payload = fetchPayload(path, authHeader)
            if (payload != null) {
                return payload
            }
        }
        return null
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

    private fun JsonObject.doubleOrNull(vararg keys: String): Double? {
        for (key in keys) {
            if (!has(key)) continue
            val value = get(key)
            if (value.isJsonNull || !value.isJsonPrimitive) continue
            runCatching { return value.asDouble }.getOrNull()
            runCatching { return value.asString.toDouble() }.getOrNull()
        }
        return null
    }

    private fun JsonObject.firstArrayObjectString(arrayKey: String, valueKey: String): String {
        if (!has(arrayKey)) return ""
        val arr = get(arrayKey)
        if (!arr.isJsonArray) return ""
        for (element in arr.asJsonArray) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val value = obj.stringOrBlank(valueKey)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun JsonObject.arrayObjectStrings(arrayKey: String, valueKey: String): List<String> {
        if (!has(arrayKey)) return emptyList()
        val arr = get(arrayKey)
        if (!arr.isJsonArray) return emptyList()

        return arr.asJsonArray.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            obj.stringOrBlank(valueKey).takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.arrayObjects(arrayKey: String): List<JsonObject> {
        if (!has(arrayKey)) return emptyList()
        val arr = get(arrayKey)
        if (!arr.isJsonArray) return emptyList()

        return arr.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    private fun extractApiErrorMessage(rawBody: String, fallback: String): String {
        val body = rawBody.trim()
        if (body.isBlank()) return fallback

        val parsed = runCatching { JsonParser.parseString(body) }.getOrNull()
        if (parsed == null) return body

        return extractMessageFromJson(parsed).ifBlank { fallback }
    }

    private fun extractMessageFromJson(element: JsonElement): String {
        if (element.isJsonNull) return ""

        if (element.isJsonPrimitive) {
            return runCatching { element.asString }.getOrDefault("").trim()
        }

        if (element.isJsonArray) {
            val arrayMessages = element.asJsonArray.mapNotNull { item ->
                val msg = extractMessageFromJson(item)
                msg.takeIf { it.isNotBlank() }
            }
            return arrayMessages.firstOrNull().orEmpty()
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val preferredKeys = listOf("detail", "error", "message", "non_field_errors", "comment", "score", "rated_user", "listing")

            for (key in preferredKeys) {
                if (!obj.has(key)) continue
                val msg = extractMessageFromJson(obj.get(key))
                if (msg.isNotBlank()) return msg
            }

            val firstEntry = obj.entrySet().firstOrNull()
            if (firstEntry != null) {
                val msg = extractMessageFromJson(firstEntry.value)
                if (msg.isNotBlank()) return msg
                return firstEntry.key
            }
        }

        return ""
    }

    private fun formatServerDateTime(raw: String): String {
        if (raw.isBlank()) return ""

        val zoned = runCatching {
            OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrNull()
        if (zoned != null) {
            return zoned.format(dateTimeFormatter)
        }

        val instant = runCatching {
            Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrNull()
        if (instant != null) {
            return instant.format(dateTimeFormatter)
        }

        val local = runCatching {
            LocalDateTime.parse(raw.replace("Z", ""))
        }.getOrNull()
        if (local != null) {
            return local.format(dateTimeFormatter)
        }

        return raw
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
