package fr.grenobleski.nativeapp.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.BusLineItem
import fr.grenobleski.nativeapp.data.model.CarpoolPendingReservation
import fr.grenobleski.nativeapp.data.model.ChatUserOption
import fr.grenobleski.nativeapp.data.model.FriendLink
import fr.grenobleski.nativeapp.data.model.FriendInvitation
import fr.grenobleski.nativeapp.data.model.InstructorItem
import fr.grenobleski.nativeapp.data.model.LoginRequest
import fr.grenobleski.nativeapp.data.model.LoginResponse
import fr.grenobleski.nativeapp.data.model.MarketplacePage
import fr.grenobleski.nativeapp.data.model.MarketplaceItem
import fr.grenobleski.nativeapp.data.model.MessageItem
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
import fr.grenobleski.nativeapp.data.model.RegisterRequest
import fr.grenobleski.nativeapp.data.model.ServiceStoreItem
import fr.grenobleski.nativeapp.data.model.SkiNewsItem
import fr.grenobleski.nativeapp.data.model.SkiPartnerItem
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem
import fr.grenobleski.nativeapp.data.model.StoryCommentItem
import fr.grenobleski.nativeapp.data.model.StoryItem
import fr.grenobleski.nativeapp.data.model.StoryPage
import fr.grenobleski.nativeapp.data.model.StoryStats
import fr.grenobleski.nativeapp.data.model.UserActivitySummary
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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.ResponseBody

class AuthRepository(
    private val service: GrenobleSkiApiService,
    private val siteBaseUrl: String,
) {
    private data class MarketplaceParseResult(
        val items: List<MarketplaceItem>,
        val hasNextPage: Boolean,
        val nextPage: Int?,
    )

    private val normalizedBaseUrl = siteBaseUrl.trimEnd('/')
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

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

    suspend fun fetchStoriesPage(
        token: String,
        page: Int,
        pageSize: Int = 5,
        stationId: Int? = null,
        query: String = "",
    ): Result<StoryPage> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 20)
        val sb = StringBuilder("/api/skistories/feed/?page=$safePage&page_size=$safePageSize")
        if (stationId != null && stationId > 0) {
            sb.append("&ski_station=").append(stationId)
        }
        if (query.isNotBlank()) {
            sb.append("&q=").append(java.net.URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString()))
        }

        val payload = fetchPayload(sb.toString(), authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load stories."))

        if (!payload.isJsonObject) {
            return@withContext Result.failure(IllegalStateException("Invalid stories payload."))
        }

        val root = payload.asJsonObject
        val results = extractObjectList(payload)
        val items = results.map { obj ->
            val recentComments = obj.arrayObjects("recent_comments").map { c ->
                StoryCommentItem(
                    id = c.intOrZero("id"),
                    userLabel = c.stringOrBlank("user_label").ifBlank { "User" },
                    body = c.stringOrBlank("body"),
                    createdAtLabel = formatServerDateTime(c.stringOrBlank("created_at")),
                )
            }

            StoryItem(
                id = obj.intOrZero("id"),
                userId = obj.intOrZero("user", "user_id"),
                userLabel = obj.stringOrBlank("user_label").ifBlank { "User" },
                stationId = obj.intOrZero("ski_station", "ski_station_id"),
                stationName = obj.stringOrBlank("ski_station_name").ifBlank { "-" },
                caption = obj.stringOrBlank("caption"),
                imageBase64 = obj.stringOrBlank("image_base64", "image"),
                createdAtLabel = formatServerDateTime(obj.stringOrBlank("created_at")),
                createdAtRaw = obj.stringOrBlank("created_at"),
                likeCount = obj.intOrZero("like_count"),
                commentCount = obj.intOrZero("comment_count"),
                likedByMe = obj.boolOrFalse("is_liked_by_me"),
                recentComments = recentComments,
            )
        }

        val nextRaw = root.stringOrBlank("next")
        val nextPage = parsePageFromUrl(nextRaw)
        Result.success(
            StoryPage(
                items = items,
                hasNextPage = nextRaw.isNotBlank(),
                nextPage = nextPage,
            )
        )
    }

    suspend fun likeStory(token: String, storyId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (storyId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid story id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skistories/$storyId/like/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to like story."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(extractApiErrorMessage(bodyText, "Unable to like story.")))
        }
        Result.success(Unit)
    }

    suspend fun unlikeStory(token: String, storyId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (storyId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid story id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skistories/$storyId/unlike/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to unlike story."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(extractApiErrorMessage(bodyText, "Unable to unlike story.")))
        }
        Result.success(Unit)
    }

    suspend fun commentStory(token: String, storyId: Int, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (storyId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid story id."))
        }
        if (body.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Comment cannot be empty."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skistories/$storyId/comment/",
                authHeader = authHeader,
                payload = mapOf("body" to body.trim()),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to comment story."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(extractApiErrorMessage(bodyText, "Unable to comment story.")))
        }
        Result.success(Unit)
    }

    suspend fun fetchStoryStats(token: String): Result<StoryStats> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/skistories/stats/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load story stats."))
        if (!payload.isJsonObject) {
            return@withContext Result.failure(IllegalStateException("Invalid story stats payload."))
        }
        val obj = payload.asJsonObject
        val crowd = mutableMapOf<String, Int>()
        obj.getAsJsonObject("crowd_breakdown")?.entrySet()?.forEach { entry ->
            crowd[entry.key] = runCatching { entry.value.asInt }.getOrDefault(0)
        }
        val weather = mutableMapOf<String, Int>()
        obj.getAsJsonObject("weather_breakdown")?.entrySet()?.forEach { entry ->
            weather[entry.key] = runCatching { entry.value.asInt }.getOrDefault(0)
        }

        Result.success(
            StoryStats(
                totalActiveStories = obj.intOrZero("total_active_stories"),
                avgFunScore = obj.doubleOrNull("avg_fun_score") ?: 0.0,
                momentVibe = obj.stringOrBlank("moment_vibe").ifBlank { "good" },
                crowdBreakdown = crowd,
                weatherBreakdown = weather,
            )
        )
    }

    suspend fun fetchSkiNews(token: String, highlightedOnly: Boolean = false): Result<List<SkiNewsItem>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val language = if (Locale.getDefault().language.lowercase().startsWith("en")) "en" else "fr"
        val endpoint = if (highlightedOnly) {
            "/api/ski-news/?language=$language&highlighted=true"
        } else {
            "/api/ski-news/?language=$language"
        }

        val payload = fetchPayload(endpoint, authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load ski news."))

        val items = extractObjectList(payload).map { obj ->
            SkiNewsItem(
                id = obj.intOrZero("id"),
                title = obj.stringOrBlank("title"),
                summary = obj.stringOrBlank("summary"),
                link = obj.stringOrBlank("link"),
                sourceName = obj.stringOrBlank("source_name").ifBlank { "News" },
                language = obj.stringOrBlank("language").ifBlank { language },
                stationId = obj.intOrZero("ski_station", "ski_station_id").takeIf { it > 0 },
                stationName = obj.stringOrBlank("station_name"),
                publishedAtLabel = formatServerDateTime(obj.stringOrBlank("published_at")),
                publishedAtRaw = obj.stringOrBlank("published_at"),
                highlighted = obj.boolOrFalse("is_highlighted"),
            )
        }.filter { it.title.isNotBlank() && it.link.isNotBlank() }

        Result.success(items)
    }

    suspend fun fetchUserActivity(token: String, userId: Int): Result<UserActivitySummary> = withContext(Dispatchers.IO) {
        if (userId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid user id."))
        }
        val authHeader = "Token $token"
        val payload = fetchPayload("/api/userview/$userId/activity/", authHeader)
            ?: return@withContext Result.failure(IllegalStateException("Unable to load user activity."))
        if (!payload.isJsonObject) {
            return@withContext Result.failure(IllegalStateException("Invalid user activity payload."))
        }
        val root = payload.asJsonObject
        val userObj = root.getAsJsonObject("user")
        val statsObj = root.getAsJsonObject("stats")

        val recentStoryCaptions = root.arrayObjects("recent_stories").map { it.stringOrBlank("caption") }.filter { it.isNotBlank() }
        val recentComments = root.arrayObjects("recent_comments").map { it.stringOrBlank("body") }.filter { it.isNotBlank() }

        Result.success(
            UserActivitySummary(
                userId = userObj?.intOrZero("id") ?: userId,
                displayName = userObj?.stringOrBlank("display_name").orEmpty().ifBlank { "User #$userId" },
                username = userObj?.stringOrBlank("username").orEmpty(),
                organizationName = userObj?.stringOrBlank("organization_name").orEmpty(),
                storiesCount = statsObj?.intOrZero("stories_count") ?: 0,
                commentsCount = statsObj?.intOrZero("comments_count") ?: 0,
                publicMessagesCount = statsObj?.intOrZero("public_messages_count") ?: 0,
                friendsCount = statsObj?.intOrZero("friends_count") ?: 0,
                recentStoryCaptions = recentStoryCaptions,
                recentComments = recentComments,
            )
        )
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
                pisteMapUrl = obj.stringOrBlank("piste_map_url"),
                pisteMapThumbnailUrl = obj.stringOrBlank("piste_map_thumbnail_url"),
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

    suspend fun fetchMarketplaceItems(
        token: String,
        page: Int = 1,
        pageSize: Int = 18,
    ): Result<MarketplacePage> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(6, 24)
        val rawBody = fetchRawBodyFromCandidates(
            paths = listOf(
                "/api/skimaterial/?page=$safePage&page_size=$safePageSize",
                "/api/skimaterial/",
                "/api/skimaterial",
            ),
            authHeader = authHeader,
        ) ?: return@withContext Result.success(
            MarketplacePage(
                items = emptyList(),
                hasNextPage = false,
                nextPage = null,
            )
        )

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

        val parsed = parseMarketplaceItemsStreaming(rawBody, materialLabels, transactionLabels)
        Result.success(
            MarketplacePage(
                items = parsed.items,
                hasNextPage = parsed.hasNextPage,
                nextPage = parsed.nextPage,
            )
        )
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
                isCarpool = obj.boolOrFalse("is_carpool"),
                departureCity = obj.stringOrBlank("departure_city").ifBlank { obj.stringOrBlank("city") },
                departureDateTimeLabel = formatServerDateTime(obj.stringOrBlank("departure_datetime", "preferred_date")),
                seatsReserved = obj.intOrZero("seats_reserved"),
                seatsRemaining = obj.intOrZero("seats_remaining"),
                totalSeats = obj.intOrZero("total_seats"),
                myReservedSeats = obj.intOrZero("my_reserved_seats"),
                myReservationStatus = obj.stringOrBlank("my_reservation_status"),
                pendingReservations = obj.arrayObjects("pending_reservations").map { pendingObj ->
                    CarpoolPendingReservation(
                        reservationId = pendingObj.intOrZero("reservation_id", "id"),
                        userId = pendingObj.intOrZero("user_id", "user"),
                        userLabel = pendingObj.stringOrBlank("user_label", "username", "user_name").ifBlank {
                            val fallback = pendingObj.intOrZero("user_id", "user")
                            if (fallback > 0) "Utilisateur #$fallback" else "Utilisateur"
                        },
                        seatsReserved = pendingObj.intOrZero("seats_reserved", "seats"),
                    )
                },
            )
        }
        Result.success(items)
    }

    suspend fun requestCarpoolReservation(token: String, postId: Int, seats: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (postId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid carpool post id."))
        }
        val authHeader = "Token $token"
        val payload = mapOf("seats" to seats.coerceAtLeast(1))
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skipartnerposts/$postId/reserve/",
                authHeader = authHeader,
                payload = payload,
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to request reservation."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val message = extractApiErrorMessage(bodyText, "Unable to request reservation.")
            return@withContext Result.failure(IllegalStateException(message))
        }
        Result.success(Unit)
    }

    suspend fun cancelCarpoolReservation(token: String, postId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (postId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid carpool post id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/skipartnerposts/$postId/cancel_reservation/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to cancel reservation."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty()
            val message = extractApiErrorMessage(bodyText, "Unable to cancel reservation.")
            return@withContext Result.failure(IllegalStateException(message))
        }
        Result.success(Unit)
    }

    suspend fun approveCarpoolReservation(token: String, postId: Int, reservationId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (postId <= 0 || reservationId <= 0) {
                return@withContext Result.failure(IllegalStateException("Invalid reservation payload."))
            }
            val authHeader = "Token $token"
            val response = runCatching {
                service.postResource(
                    url = "$normalizedBaseUrl/api/skipartnerposts/$postId/approve_reservation/",
                    authHeader = authHeader,
                    payload = mapOf("reservation_id" to reservationId),
                )
            }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to approve reservation."))

            if (!response.isSuccessful) {
                val bodyText = response.errorBody()?.string().orEmpty()
                val message = extractApiErrorMessage(bodyText, "Unable to approve reservation.")
                return@withContext Result.failure(IllegalStateException(message))
            }
            Result.success(Unit)
        }

    suspend fun rejectCarpoolReservation(token: String, postId: Int, reservationId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (postId <= 0 || reservationId <= 0) {
                return@withContext Result.failure(IllegalStateException("Invalid reservation payload."))
            }
            val authHeader = "Token $token"
            val response = runCatching {
                service.postResource(
                    url = "$normalizedBaseUrl/api/skipartnerposts/$postId/reject_reservation/",
                    authHeader = authHeader,
                    payload = mapOf("reservation_id" to reservationId),
                )
            }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to reject reservation."))

            if (!response.isSuccessful) {
                val bodyText = response.errorBody()?.string().orEmpty()
                val message = extractApiErrorMessage(bodyText, "Unable to reject reservation.")
                return@withContext Result.failure(IllegalStateException(message))
            }
            Result.success(Unit)
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
        val rawBody = fetchRawBodyFromCandidates(
            paths = listOf(
                "/api/messages/?page=1&page_size=60",
                "/api/messages/",
                "/api/messages",
            ),
            authHeader = authHeader,
        )

        if (rawBody != null) {
            return@withContext Result.success(parseMessageItemsStreaming(rawBody))
        }

        // Endpoint unavailable in this deployment; do not break the whole native experience.
        Result.success(emptyList())
    }

    private fun parseMessageItemsStreaming(rawBody: ResponseBody): List<MessageItem> {
        rawBody.use { body ->
            body.charStream().use { stream ->
                JsonReader(stream).use { reader ->
                    return parseMessageRoot(reader)
                }
            }
        }
    }

    private fun parseMessageRoot(reader: JsonReader): List<MessageItem> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> parseMessageArray(reader)
            JsonToken.BEGIN_OBJECT -> {
                val items = mutableListOf<MessageItem>()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "results" -> items.addAll(parseMessageArray(reader))
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                items
            }
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }

    private data class ParsedUserSummary(
        val id: Int,
        val label: String,
        val photoBase64: String,
        val photoUrl: String,
    )

    private fun parseMessageArray(reader: JsonReader): List<MessageItem> {
        val items = mutableListOf<MessageItem>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (items.size >= 250) {
                reader.skipValue()
                continue
            }
            items.add(parseMessageItem(reader))
        }
        reader.endArray()
        return items
    }

    private fun parseMessageItem(reader: JsonReader): MessageItem {
        val maxAvatarChars = 180_000
        var id = 0
        var senderId = 0
        var recipientId = 0
        var senderLabel = ""
        var recipientLabel = ""
        var senderPhotoBase64 = ""
        var senderPhotoUrl = ""
        var recipientPhotoBase64 = ""
        var recipientPhotoUrl = ""
        var body = ""
        var createdAt = ""
        var isRead = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextIntSafely(reader)
                "sender", "sender_user" -> {
                    val parsed = parseUserSummarySafely(reader)
                    if (senderId <= 0) senderId = parsed.id
                    if (senderLabel.isBlank()) senderLabel = parsed.label
                    if (senderPhotoBase64.isBlank()) senderPhotoBase64 = parsed.photoBase64
                    if (senderPhotoUrl.isBlank()) senderPhotoUrl = parsed.photoUrl
                }
                "recipient", "recipient_user" -> {
                    val parsed = parseUserSummarySafely(reader)
                    if (recipientId <= 0) recipientId = parsed.id
                    if (recipientLabel.isBlank()) recipientLabel = parsed.label
                    if (recipientPhotoBase64.isBlank()) recipientPhotoBase64 = parsed.photoBase64
                    if (recipientPhotoUrl.isBlank()) recipientPhotoUrl = parsed.photoUrl
                }
                "sender_id" -> {
                    if (senderId <= 0) {
                        senderId = nextIntSafely(reader)
                    } else {
                        reader.skipValue()
                    }
                }
                "recipient_id" -> {
                    if (recipientId <= 0) {
                        recipientId = nextIntSafely(reader)
                    } else {
                        reader.skipValue()
                    }
                }
                "sender_username", "sender_name" -> {
                    if (senderLabel.isBlank()) senderLabel = nextStringSafely(reader) else reader.skipValue()
                }
                "recipient_username", "recipient_name" -> {
                    if (recipientLabel.isBlank()) recipientLabel = nextStringSafely(reader) else reader.skipValue()
                }
                "sender_photo", "sender_profile_picture", "sender_photo_base64" -> {
                    if (senderPhotoBase64.isBlank()) senderPhotoBase64 = nextStringWithMaxLength(reader, maxAvatarChars) else reader.skipValue()
                }
                "sender_photo_url", "sender_google_profile_picture_url" -> {
                    if (senderPhotoUrl.isBlank()) senderPhotoUrl = nextStringSafely(reader) else reader.skipValue()
                }
                "recipient_photo", "recipient_profile_picture", "recipient_photo_base64" -> {
                    if (recipientPhotoBase64.isBlank()) recipientPhotoBase64 = nextStringWithMaxLength(reader, maxAvatarChars) else reader.skipValue()
                }
                "recipient_photo_url", "recipient_google_profile_picture_url" -> {
                    if (recipientPhotoUrl.isBlank()) recipientPhotoUrl = nextStringSafely(reader) else reader.skipValue()
                }
                "body", "message", "content" -> {
                    if (body.isBlank()) body = nextStringSafely(reader) else reader.skipValue()
                }
                "created_at", "timestamp" -> {
                    if (createdAt.isBlank()) createdAt = nextStringSafely(reader) else reader.skipValue()
                }
                "is_read" -> isRead = nextBooleanSafely(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return MessageItem(
            id = id,
            senderId = senderId,
            recipientId = recipientId,
            senderLabel = senderLabel.ifBlank { "Utilisateur" },
            recipientLabel = recipientLabel.ifBlank { "Utilisateur" },
            senderPhotoBase64 = senderPhotoBase64,
            senderPhotoUrl = senderPhotoUrl,
            recipientPhotoBase64 = recipientPhotoBase64,
            recipientPhotoUrl = recipientPhotoUrl,
            body = body.ifBlank { "-" },
            createdAtLabel = formatServerDateTime(createdAt),
            isRead = isRead,
        )
    }

    private fun parseUserSummarySafely(reader: JsonReader): ParsedUserSummary {
        val maxAvatarChars = 180_000
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                var id = 0
                var label = ""
                var photoBase64 = ""
                var photoUrl = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = nextIntSafely(reader)
                        "display_name", "username", "email" -> if (label.isBlank()) label = nextStringSafely(reader) else reader.skipValue()
                        "google_profile_picture_url" -> if (photoUrl.isBlank()) photoUrl = nextStringSafely(reader) else reader.skipValue()
                        "profile_picture", "photo", "photo_base64" -> {
                            if (photoBase64.isBlank()) {
                                photoBase64 = nextStringWithMaxLength(reader, maxAvatarChars)
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                ParsedUserSummary(id = id, label = label, photoBase64 = photoBase64, photoUrl = photoUrl)
            }
            JsonToken.NUMBER, JsonToken.STRING -> ParsedUserSummary(
                id = nextIntSafely(reader),
                label = "",
                photoBase64 = "",
                photoUrl = "",
            )
            JsonToken.NULL -> {
                reader.nextNull()
                ParsedUserSummary(0, "", "", "")
            }
            else -> {
                reader.skipValue()
                ParsedUserSummary(0, "", "", "")
            }
        }
    }

    private fun nextBooleanSafely(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                false
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER, JsonToken.STRING -> {
                val normalized = nextStringSafely(reader).trim().lowercase()
                normalized == "1" || normalized == "true" || normalized == "yes"
            }
            else -> {
                reader.skipValue()
                false
            }
        }
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
        val rawBody = fetchRawBodyFromCandidates(
            paths = listOf("/api/userview/?page=1&page_size=120", "/api/userview/", "/api/userview"),
            authHeader = authHeader,
        ) ?: return@withContext Result.success(emptyList())

        Result.success(parseChatUsersStreaming(rawBody))
    }

    private fun parseChatUsersStreaming(rawBody: ResponseBody): List<ChatUserOption> {
        rawBody.use { body ->
            body.charStream().use { stream ->
                JsonReader(stream).use { reader ->
                    return parseChatUsersRoot(reader)
                }
            }
        }
    }

    private fun parseChatUsersRoot(reader: JsonReader): List<ChatUserOption> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> parseChatUsersArray(reader)
            JsonToken.BEGIN_OBJECT -> {
                val users = mutableListOf<ChatUserOption>()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "results" -> users.addAll(parseChatUsersArray(reader))
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                users
            }
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }

    private fun parseChatUsersArray(reader: JsonReader): List<ChatUserOption> {
        val users = mutableListOf<ChatUserOption>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (users.size >= 500) {
                reader.skipValue()
                continue
            }
            parseChatUser(reader)?.let { users.add(it) }
        }
        reader.endArray()
        return users
    }

    private fun parseChatUser(reader: JsonReader): ChatUserOption? {
        val maxAvatarChars = 180_000
        var id = 0
        var firstName = ""
        var lastName = ""
        var displayName = ""
        var username = ""
        var email = ""
        var photoBase64 = ""
        var photoUrl = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextIntSafely(reader)
                "first_name" -> firstName = nextStringSafely(reader)
                "last_name" -> lastName = nextStringSafely(reader)
                "display_name" -> displayName = nextStringSafely(reader)
                "username" -> username = nextStringSafely(reader)
                "email" -> email = nextStringSafely(reader)
                "google_profile_picture_url" -> photoUrl = nextStringSafely(reader)
                "profile_picture", "photo", "photo_base64" -> photoBase64 = nextStringWithMaxLength(reader, maxAvatarChars)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (id <= 0) return null

        val label = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { displayName }
            .ifBlank { username }
            .ifBlank { email }
            .ifBlank { "Utilisateur #$id" }

        return ChatUserOption(
            id = id,
            label = label,
            photoBase64 = photoBase64,
            photoUrl = photoUrl,
        )
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

    suspend fun fetchFriendInvitations(token: String): Result<List<FriendInvitation>> = withContext(Dispatchers.IO) {
        val authHeader = "Token $token"
        val payload = fetchPayloadFromCandidates(listOf("/api/friend-invitations/", "/api/friend-invitations"), authHeader)
            ?: return@withContext Result.success(emptyList())

        val invitations = extractObjectList(payload).mapNotNull { obj ->
            val id = obj.intOrZero("id")
            val fromUserId = obj.intOrZero("from_user")
            val toUserId = obj.intOrZero("to_user")
            val status = obj.stringOrBlank("status")
            if (id <= 0 || fromUserId <= 0 || toUserId <= 0 || status.isBlank()) {
                null
            } else {
                FriendInvitation(
                    id = id,
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    status = status,
                )
            }
        }
        Result.success(invitations)
    }

    suspend fun addFriend(token: String, friendId: Int): Result<String> = withContext(Dispatchers.IO) {
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

        val statusValue = runCatching {
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.stringOrBlank("status")
        }.getOrNull().orEmpty().ifBlank { "sent" }

        Result.success(statusValue)
    }

    suspend fun acceptFriendInvitation(token: String, invitationId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (invitationId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid invitation id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/friend-invitations/$invitationId/accept/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to accept invitation."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to accept invitation." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        Result.success(Unit)
    }

    suspend fun declineFriendInvitation(token: String, invitationId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (invitationId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid invitation id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/friend-invitations/$invitationId/decline/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to decline invitation."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to decline invitation." }
            return@withContext Result.failure(IllegalStateException(bodyText))
        }

        Result.success(Unit)
    }

    suspend fun cancelFriendInvitation(token: String, invitationId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (invitationId <= 0) {
            return@withContext Result.failure(IllegalStateException("Invalid invitation id."))
        }
        val authHeader = "Token $token"
        val response = runCatching {
            service.postResource(
                url = "$normalizedBaseUrl/api/friend-invitations/$invitationId/cancel/",
                authHeader = authHeader,
                payload = emptyMap(),
            )
        }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("Unable to cancel invitation."))

        if (!response.isSuccessful) {
            val bodyText = response.errorBody()?.string().orEmpty().ifBlank { "Unable to cancel invitation." }
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
            service.postResourceRaw(
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

        response.body()?.close()

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

    private suspend fun fetchRawBodyFromCandidates(paths: List<String>, authHeader: String): ResponseBody? {
        for (path in paths) {
            val response = service.listResourceRaw("$normalizedBaseUrl$path", authHeader)
            if (!response.isSuccessful) {
                continue
            }
            val body = response.body()
            if (body != null) {
                return body
            }
        }
        return null
    }

    private fun parseMarketplaceItemsStreaming(
        rawBody: ResponseBody,
        materialLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
    ): MarketplaceParseResult {
        rawBody.use { body ->
            body.charStream().use { stream ->
                JsonReader(stream).use { reader ->
                    return parseMarketplaceRoot(reader, materialLabels, transactionLabels)
                }
            }
        }
    }

    private fun parseMarketplaceRoot(
        reader: JsonReader,
        materialLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
    ): MarketplaceParseResult {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> MarketplaceParseResult(
                items = parseMarketplaceArray(reader, materialLabels, transactionLabels),
                hasNextPage = false,
                nextPage = null,
            )
            JsonToken.BEGIN_OBJECT -> {
                val items = mutableListOf<MarketplaceItem>()
                var nextRaw = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "results" -> items.addAll(parseMarketplaceArray(reader, materialLabels, transactionLabels))
                        "next" -> nextRaw = nextStringSafely(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                MarketplaceParseResult(
                    items = items,
                    hasNextPage = nextRaw.isNotBlank(),
                    nextPage = parsePageFromUrl(nextRaw),
                )
            }
            else -> {
                reader.skipValue()
                MarketplaceParseResult(
                    items = emptyList(),
                    hasNextPage = false,
                    nextPage = null,
                )
            }
        }
    }

    private fun parseMarketplaceArray(
        reader: JsonReader,
        materialLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
    ): List<MarketplaceItem> {
        val items = mutableListOf<MarketplaceItem>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (items.size >= 300) {
                reader.skipValue()
                continue
            }
            items.add(parseMarketplaceItem(reader, materialLabels, transactionLabels))
        }
        reader.endArray()
        return items
    }

    private fun parseMarketplaceItem(
        reader: JsonReader,
        materialLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
    ): MarketplaceItem {
        // Keep only a small number of image references but allow real-world base64 sizes.
        val maxPreviewChars = 4_000_000
        val maxGalleryChars = 4_000_000
        val maxGalleryItems = 2
        var id = 0
        var title = ""
        var description = ""
        var city = ""
        var price = ""
        var condition = ""
        var materialType = ""
        var transactionType = ""
        var sellerId = 0
        var sellerLabel = ""
        var sellerPhotoBase64 = ""
        var sellerPhotoUrl = ""
        var postedAt = ""
        var previewImage = ""
        val galleryImages = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextIntSafely(reader)
                "title" -> title = nextStringSafely(reader)
                "description" -> description = nextStringSafely(reader)
                "city" -> city = nextStringSafely(reader)
                "price" -> price = nextStringSafely(reader)
                "condition" -> condition = nextStringSafely(reader)
                "material_type" -> materialType = nextStringSafely(reader)
                "transaction_type" -> transactionType = nextStringSafely(reader)
                "user_id" -> sellerId = nextIntSafely(reader)
                "user" -> {
                    val parsed = parseMarketplaceSellerSummary(reader)
                    if (sellerId <= 0) sellerId = parsed.id
                    if (sellerLabel.isBlank()) sellerLabel = parsed.label
                    if (sellerPhotoBase64.isBlank()) sellerPhotoBase64 = parsed.photoBase64
                    if (sellerPhotoUrl.isBlank()) sellerPhotoUrl = parsed.photoUrl
                }
                "seller_info" -> {
                    val parsed = parseMarketplaceSellerSummary(reader)
                    if (sellerId <= 0) sellerId = parsed.id
                    if (sellerLabel.isBlank()) sellerLabel = parsed.label
                    if (sellerPhotoBase64.isBlank()) sellerPhotoBase64 = parsed.photoBase64
                    if (sellerPhotoUrl.isBlank()) sellerPhotoUrl = parsed.photoUrl
                }
                "posted_at", "created_at" -> {
                    if (postedAt.isBlank()) {
                        postedAt = nextStringSafely(reader)
                    } else {
                        reader.skipValue()
                    }
                }
                "preview_image", "preview_image_base64", "thumbnail", "thumbnail_url", "photo", "photo_base64" -> {
                    if (previewImage.isBlank()) {
                        previewImage = nextStringWithMaxLength(reader, maxPreviewChars)
                    } else {
                        reader.skipValue()
                    }
                }
                "image", "image_base64", "image_url", "url" -> {
                    if (previewImage.isBlank()) {
                        previewImage = nextStringWithMaxLength(reader, maxPreviewChars)
                    } else {
                        reader.skipValue()
                    }
                }
                "images" -> parseImageArray(reader, galleryImages, maxGalleryItems, maxGalleryChars)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (previewImage.isBlank()) {
            previewImage = galleryImages.firstOrNull().orEmpty()
        }

        val materialTypeKey = materialType.lowercase()
        val transactionTypeKey = transactionType.lowercase()
        return MarketplaceItem(
            id = id,
            title = title.ifBlank { if (materialType.isNotBlank()) materialType else "Annonce #$id" },
            description = description.ifBlank { "Aucune description." },
            city = city.ifBlank { "-" },
            priceLabel = price.ifBlank { "-" },
            conditionLabel = condition.ifBlank { "-" },
            materialTypeLabel = materialLabels[materialTypeKey] ?: materialType.ifBlank { "Autre" },
            transactionTypeLabel = transactionLabels[transactionTypeKey] ?: transactionType.ifBlank { "-" },
            sellerId = sellerId,
            sellerLabel = sellerLabel.ifBlank { if (sellerId > 0) "Vendeur #$sellerId" else "Vendeur" },
            sellerPhotoBase64 = sellerPhotoBase64,
            sellerPhotoUrl = sellerPhotoUrl,
            postedAtLabel = formatServerDateTime(postedAt),
            previewImageBase64 = previewImage,
            imageGalleryBase64 = galleryImages,
        )
    }

    private data class ParsedMarketplaceSellerSummary(
        val id: Int,
        val label: String,
        val photoBase64: String,
        val photoUrl: String,
    )

    private fun parseMarketplaceSellerSummary(reader: JsonReader): ParsedMarketplaceSellerSummary {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                var id = 0
                var label = ""
                var photoBase64 = ""
                var photoUrl = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = nextIntSafely(reader)
                        "display_name", "username", "email", "name" -> if (label.isBlank()) label = nextStringSafely(reader) else reader.skipValue()
                        "google_profile_picture_url", "photo_url", "avatar_url" -> if (photoUrl.isBlank()) photoUrl = nextStringWithMaxLength(reader, 2_000) else reader.skipValue()
                        "profile_picture", "photo", "avatar", "image_base64" -> if (photoBase64.isBlank()) photoBase64 = nextStringWithMaxLength(reader, 900_000) else reader.skipValue()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                ParsedMarketplaceSellerSummary(id = id, label = label, photoBase64 = photoBase64, photoUrl = photoUrl)
            }
            JsonToken.NUMBER, JsonToken.STRING -> ParsedMarketplaceSellerSummary(
                id = nextIntSafely(reader),
                label = "",
                photoBase64 = "",
                photoUrl = "",
            )
            JsonToken.NULL -> {
                reader.nextNull()
                ParsedMarketplaceSellerSummary(0, "", "", "")
            }
            else -> {
                reader.skipValue()
                ParsedMarketplaceSellerSummary(0, "", "", "")
            }
        }
    }

    private fun parseImageArray(
        reader: JsonReader,
        target: MutableList<String>,
        maxItems: Int,
        maxChars: Int,
    ) {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return
        }

        reader.beginArray()
        while (reader.hasNext()) {
            if (target.size >= maxItems) {
                reader.skipValue()
                continue
            }
            val value = nextImageValueSafely(reader, maxChars)
            if (value.isNotBlank()) {
                target.add(value)
            }
        }
        reader.endArray()
    }

    private fun nextImageValueSafely(reader: JsonReader, maxChars: Int): String {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                var value = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "image", "image_base64", "preview_image", "preview_image_base64", "image_url", "url", "thumbnail", "thumbnail_url" -> {
                            if (value.isBlank()) {
                                value = nextStringWithMaxLength(reader, maxChars)
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                value
            }
            JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN, JsonToken.NULL -> nextStringWithMaxLength(reader, maxChars)
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun nextUserIdSafely(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                var id = 0
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = nextIntSafely(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                id
            }
            JsonToken.NULL -> {
                reader.nextNull()
                0
            }
            JsonToken.NUMBER, JsonToken.STRING -> nextIntSafely(reader)
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    private fun nextStringWithMaxLength(reader: JsonReader, maxChars: Int): String {
        val value = nextStringSafely(reader)
        if (value.length <= maxChars) {
            return value
        }
        return ""
    }

    private fun nextStringSafely(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER, JsonToken.BOOLEAN -> reader.nextString()
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun nextIntSafely(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                0
            }
            JsonToken.NUMBER, JsonToken.STRING -> nextStringSafely(reader).toIntOrNull() ?: 0
            else -> {
                reader.skipValue()
                0
            }
        }
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

    private fun parsePageFromUrl(url: String): Int? {
        if (url.isBlank()) return null
        val query = url.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        val pageRaw = query
            .split('&')
            .firstOrNull { it.startsWith("page=") }
            ?.substringAfter('=', "")
            ?.trim()
            ?: return null
        val decoded = runCatching { URLDecoder.decode(pageRaw, StandardCharsets.UTF_8.toString()) }.getOrDefault(pageRaw)
        return decoded.toIntOrNull()
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
