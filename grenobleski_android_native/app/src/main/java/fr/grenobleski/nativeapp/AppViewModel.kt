package fr.grenobleski.nativeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.BusLineItem
import fr.grenobleski.nativeapp.data.model.ChatUserOption
import fr.grenobleski.nativeapp.data.model.FriendLink
import fr.grenobleski.nativeapp.data.model.InstructorItem
import fr.grenobleski.nativeapp.data.model.MarketplaceItem
import fr.grenobleski.nativeapp.data.model.MessageItem
import fr.grenobleski.nativeapp.data.model.NativeTab
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
import fr.grenobleski.nativeapp.data.model.ServiceStoreItem
import fr.grenobleski.nativeapp.data.model.SkiNewsItem
import fr.grenobleski.nativeapp.data.model.SkiPartnerItem
import fr.grenobleski.nativeapp.data.model.StationItem
import fr.grenobleski.nativeapp.data.model.StoryItem
import fr.grenobleski.nativeapp.data.model.StoryStats
import fr.grenobleski.nativeapp.data.model.UserActivitySummary
import fr.grenobleski.nativeapp.data.model.UserSession
import fr.grenobleski.nativeapp.data.session.SessionStore
import kotlinx.coroutines.launch

data class AppUiState(
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val confirmPassword: String = "",
    val registerTermsAccepted: Boolean = false,
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: UserSession? = null,
    val selectedTab: NativeTab = NativeTab.HOME,
    val isTabLoading: Boolean = false,
    val dashboardCounts: DashboardCounts = DashboardCounts(),
    val storyItems: List<StoryItem> = emptyList(),
    val highlightedStoryItems: List<StoryItem> = emptyList(),
    val skiNewsItems: List<SkiNewsItem> = emptyList(),
    val highlightedSkiNewsItems: List<SkiNewsItem> = emptyList(),
    val storyStats: StoryStats = StoryStats(),
    val selectedUserActivity: UserActivitySummary? = null,
    val isUserActivityLoading: Boolean = false,
    val storiesPage: Int = 1,
    val storiesHasNextPage: Boolean = true,
    val isStoriesLoadingMore: Boolean = false,
    val storiesStationFilterId: Int? = null,
    val storiesSearchQuery: String = "",
    val stationItems: List<StationItem> = emptyList(),
    val busLineItems: List<BusLineItem> = emptyList(),
    val serviceStoreItems: List<ServiceStoreItem> = emptyList(),
    val favoriteBusLineIds: Set<Int> = emptySet(),
    val favoriteServiceIds: Set<Int> = emptySet(),
    val marketplaceItems: List<MarketplaceItem> = emptyList(),
    val partnerItems: List<SkiPartnerItem> = emptyList(),
    val instructorItems: List<InstructorItem> = emptyList(),
    val pisteItems: List<PisteItem> = emptyList(),
    val messageItems: List<MessageItem> = emptyList(),
    val chatUsers: List<ChatUserOption> = emptyList(),
    val friendLinks: List<FriendLink> = emptyList(),
    val messageRecipientId: Int? = null,
    val messageDraftBody: String = "",
    val isSendingMessage: Boolean = false,
    val publishTitle: String = "",
    val publishDescription: String = "",
    val publishCity: String = "",
    val publishPrice: String = "",
    val publishMaterialType: String = "ski",
    val publishTransactionType: String = "sale",
    val publishImagesBase64: List<String> = emptyList(),
    val isPublishingArticle: Boolean = false,
    val publishPartnerTitle: String = "",
    val publishPartnerMessage: String = "",
    val publishPartnerCity: String = "",
    val publishPartnerLevel: String = "intermediate",
    val publishPartnerDate: String = "",
    val isPublishingPartner: Boolean = false,
    val profileInfo: ProfileInfo? = null,
    val profileEditFirstName: String = "",
    val profileEditLastName: String = "",
    val profileEditEmail: String = "",
    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
    val confirmNewPasswordInput: String = "",
    val isSavingProfile: Boolean = false,
    val isChangingPassword: Boolean = false,
    val statusMessage: String? = null,
    val xpPoints: Int = 0,
    val xpLevel: Int = 1,
)

class AppViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    companion object {
        private const val XP_SEND_MESSAGE = 5
        private const val XP_PUBLISH_MARKET = 20
        private const val XP_PUBLISH_PARTNER = 20
        private const val XP_RATE_SELLER = 10
        private const val XP_RATE_STATION = 10
    }

    var state by mutableStateOf(AppUiState())
        private set

    init {
        val cachedSession = sessionStore.load()
        if (cachedSession != null) {
            state = state.copy(session = cachedSession)
            refreshAllNativeData()
        }
    }

    fun updateEmail(value: String) {
        state = state.copy(email = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        state = state.copy(password = value, errorMessage = null)
    }

    fun updateFirstName(value: String) {
        state = state.copy(firstName = value, errorMessage = null)
    }

    fun updateLastName(value: String) {
        state = state.copy(lastName = value, errorMessage = null)
    }

    fun updateConfirmPassword(value: String) {
        state = state.copy(confirmPassword = value, errorMessage = null)
    }

    fun updateRegisterTermsAccepted(accepted: Boolean) {
        state = state.copy(registerTermsAccepted = accepted, errorMessage = null)
    }

    fun updateProfileEditFirstName(value: String) {
        state = state.copy(profileEditFirstName = value, errorMessage = null, statusMessage = null)
    }

    fun updateProfileEditLastName(value: String) {
        state = state.copy(profileEditLastName = value, errorMessage = null, statusMessage = null)
    }

    fun updateProfileEditEmail(value: String) {
        state = state.copy(profileEditEmail = value, errorMessage = null, statusMessage = null)
    }

    fun updateCurrentPasswordInput(value: String) {
        state = state.copy(currentPasswordInput = value, errorMessage = null, statusMessage = null)
    }

    fun updateNewPasswordInput(value: String) {
        state = state.copy(newPasswordInput = value, errorMessage = null, statusMessage = null)
    }

    fun updateConfirmNewPasswordInput(value: String) {
        state = state.copy(confirmNewPasswordInput = value, errorMessage = null, statusMessage = null)
    }

    fun switchAuthMode(registerMode: Boolean) {
        state = state.copy(
            isRegisterMode = registerMode,
            errorMessage = null,
            registerTermsAccepted = if (registerMode) state.registerTermsAccepted else false,
        )
    }

    fun login() {
        if (state.email.isBlank() || state.password.isBlank()) {
            state = state.copy(errorMessage = "Email and password are required.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)

            val result = repository.login(state.email.trim(), state.password)
            if (result.isSuccess) {
                val session = result.getOrNull()!!
                state = state.copy(isLoading = false, password = "")
                establishSession(session)
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to login"
                state = state.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    fun loginWithToken(token: String, email: String, displayName: String) {
        if (token.isBlank()) {
            state = state.copy(errorMessage = "Invalid mobile authentication payload.")
            return
        }

        val normalizedEmail = email.trim()
        val fallbackName = normalizedEmail.ifBlank { "Utilisateur" }
        val finalDisplayName = displayName.trim().ifBlank { fallbackName }

        establishSession(
            UserSession(
            token = token,
            email = normalizedEmail,
            displayName = finalDisplayName,
            )
        )
    }

    fun register() {
        if (state.email.isBlank() || state.password.isBlank()) {
            state = state.copy(errorMessage = "Email and password are required.")
            return
        }
        if (!state.registerTermsAccepted) {
            state = state.copy(errorMessage = "You must accept Terms and Privacy Policy.")
            return
        }
        if (state.password != state.confirmPassword) {
            state = state.copy(errorMessage = "Passwords do not match.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)

            val result = repository.register(
                email = state.email.trim(),
                password = state.password,
                firstName = state.firstName.trim(),
                lastName = state.lastName.trim(),
                acceptTerms = state.registerTermsAccepted,
            )

            if (result.isSuccess) {
                val session = result.getOrNull()!!
                state = state.copy(
                    isLoading = false,
                    password = "",
                    confirmPassword = "",
                    registerTermsAccepted = false,
                    isRegisterMode = false,
                )
                establishSession(session)
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to create account"
                state = state.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    fun selectTab(tab: NativeTab) {
        state = state.copy(selectedTab = tab, errorMessage = null, statusMessage = null)
        if (!hasDataForTab(tab)) {
            refreshCurrentTab()
        }
    }

    fun saveProfileChanges() {
        val session = state.session ?: return
        val email = state.profileEditEmail.trim()
        if (email.isBlank()) {
            state = state.copy(errorMessage = "Email is required.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isSavingProfile = true, errorMessage = null, statusMessage = null)

            val result = repository.updateProfile(
                token = session.token,
                firstName = state.profileEditFirstName,
                lastName = state.profileEditLastName,
                email = email,
            )

            if (result.isSuccess) {
                val updatedProfile = result.getOrNull()!!
                val updatedSession = session.copy(
                    email = updatedProfile.email,
                    displayName = updatedProfile.displayName.ifBlank { session.displayName },
                    userId = updatedProfile.userId.takeIf { it > 0 } ?: session.userId,
                )
                sessionStore.save(updatedSession)
                state = state.copy(
                    session = updatedSession,
                    profileInfo = updatedProfile,
                    isSavingProfile = false,
                    statusMessage = "Profil mis a jour.",
                )
                applyProfileToEditor(updatedProfile)
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to update profile"
                state = state.copy(isSavingProfile = false, errorMessage = message)
            }
        }
    }

    fun changePassword() {
        val session = state.session ?: return
        val currentPassword = state.currentPasswordInput
        val newPassword = state.newPasswordInput
        val confirmPassword = state.confirmNewPasswordInput

        if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            state = state.copy(errorMessage = "All password fields are required.")
            return
        }

        if (newPassword != confirmPassword) {
            state = state.copy(errorMessage = "Passwords do not match.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isChangingPassword = true, errorMessage = null, statusMessage = null)

            val result = repository.changePassword(
                token = session.token,
                currentPassword = currentPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword,
            )

            if (result.isSuccess) {
                val refreshedToken = result.getOrNull().orEmpty()
                val updatedSession = session.copy(token = refreshedToken)
                sessionStore.save(updatedSession)
                state = state.copy(
                    session = updatedSession,
                    isChangingPassword = false,
                    currentPasswordInput = "",
                    newPasswordInput = "",
                    confirmNewPasswordInput = "",
                    statusMessage = "Mot de passe mis a jour.",
                )
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to update password"
                state = state.copy(isChangingPassword = false, errorMessage = message)
            }
        }
    }

    fun addFriend(friendId: Int) {
        val session = state.session ?: return
        if (friendId <= 0) return

        viewModelScope.launch {
            state = state.copy(errorMessage = null, statusMessage = null)
            val result = repository.addFriend(session.token, friendId)
            if (result.isSuccess) {
                refreshMessagesData(session, preserveSelection = friendId)
                state = state.copy(statusMessage = "Contact ajoute a votre liste.")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to add friend"
                state = state.copy(errorMessage = message)
            }
        }
    }

    fun removeFriend(friendId: Int) {
        val session = state.session ?: return
        if (friendId <= 0) return

        val linkId = state.friendLinks.firstOrNull { it.friendId == friendId }?.id
        if (linkId == null) {
            state = state.copy(errorMessage = "Contact introuvable dans votre liste.")
            return
        }

        viewModelScope.launch {
            state = state.copy(errorMessage = null, statusMessage = null)
            val result = repository.removeFriend(session.token, linkId)
            if (result.isSuccess) {
                val newRecipient = if (state.messageRecipientId == friendId) null else state.messageRecipientId
                refreshMessagesData(session, preserveSelection = newRecipient)
                state = state.copy(messageRecipientId = newRecipient, statusMessage = "Contact retire.")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to remove friend"
                state = state.copy(errorMessage = message)
            }
        }
    }

    fun toggleBusLineFavorite(lineId: Int) {
        if (lineId <= 0) return
        val updated = state.favoriteBusLineIds.toMutableSet().apply {
            if (contains(lineId)) remove(lineId) else add(lineId)
        }
        state = state.copy(favoriteBusLineIds = updated)
    }

    fun toggleServiceFavorite(serviceId: Int) {
        if (serviceId <= 0) return
        val updated = state.favoriteServiceIds.toMutableSet().apply {
            if (contains(serviceId)) remove(serviceId) else add(serviceId)
        }
        state = state.copy(favoriteServiceIds = updated)
    }

    fun refreshCurrentTab() {
        val session = state.session ?: return
        val tab = state.selectedTab

        viewModelScope.launch {
            state = state.copy(isTabLoading = true)

            when (tab) {
                NativeTab.HOME -> {
                    val result = repository.fetchDashboardCounts(session.token)
                    val storiesResult = repository.fetchStoriesPage(
                        token = session.token,
                        page = 1,
                        pageSize = 5,
                        stationId = state.storiesStationFilterId,
                        query = state.storiesSearchQuery,
                    )
                    val newsResult = repository.fetchSkiNews(session.token)
                    if (result.isSuccess) {
                        val page = storiesResult.getOrNull()
                        val stories = page?.items ?: state.storyItems
                        val highlighted = stories.sortedWith(
                            compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                                .thenByDescending { it.createdAtRaw }
                        ).take(5)
                        state = state.copy(
                            dashboardCounts = result.getOrNull()!!,
                            storyItems = stories,
                            highlightedStoryItems = highlighted,
                            skiNewsItems = newsResult.getOrDefault(state.skiNewsItems),
                            highlightedSkiNewsItems = newsResult.getOrDefault(state.skiNewsItems).filter { it.highlighted }.take(5),
                            storiesPage = 1,
                            storiesHasNextPage = page?.hasNextPage ?: false,
                        )
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to refresh")
                    }
                }

                NativeTab.COMMUNITY -> {
                    val storiesResult = repository.fetchStoriesPage(
                        token = session.token,
                        page = 1,
                        pageSize = 5,
                        stationId = state.storiesStationFilterId,
                        query = state.storiesSearchQuery,
                    )
                    val statsResult = repository.fetchStoryStats(session.token)
                    val newsResult = repository.fetchSkiNews(session.token)
                    if (storiesResult.isSuccess) {
                        val page = storiesResult.getOrNull()!!
                        val highlighted = page.items.sortedWith(
                            compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                                .thenByDescending { it.createdAtRaw }
                        ).take(5)
                        state = state.copy(
                            storyItems = page.items,
                            highlightedStoryItems = highlighted,
                            storiesPage = 1,
                            storiesHasNextPage = page.hasNextPage,
                            skiNewsItems = newsResult.getOrDefault(state.skiNewsItems),
                            highlightedSkiNewsItems = newsResult.getOrDefault(state.skiNewsItems).filter { it.highlighted }.take(5),
                            storyStats = statsResult.getOrDefault(state.storyStats),
                        )
                    } else {
                        state = state.copy(errorMessage = storiesResult.exceptionOrNull()?.message ?: "Unable to load community")
                    }
                }

                NativeTab.STATIONS -> {
                    val result = repository.fetchStationItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(stationItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load stations")
                    }
                }

                NativeTab.BUS_LINES -> {
                    val result = repository.fetchBusLineItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(busLineItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load bus lines")
                    }
                }

                NativeTab.SERVICES -> {
                    val result = repository.fetchServiceStoreItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(serviceStoreItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load services")
                    }
                }

                NativeTab.MARKETPLACE -> {
                    val result = repository.fetchMarketplaceItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(marketplaceItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load marketplace")
                    }
                }

                NativeTab.PARTNERS -> {
                    val result = repository.fetchPartnerItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(partnerItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load partner posts")
                    }
                }

                NativeTab.INSTRUCTORS -> {
                    val result = repository.fetchInstructorItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(instructorItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load instructors")
                    }
                }

                NativeTab.PISTES -> {
                    val result = repository.fetchPisteItems(session.token)
                    if (result.isSuccess) {
                        state = state.copy(pisteItems = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load piste status")
                    }
                }

                NativeTab.MESSAGES -> {
                    val result = repository.fetchMessageItems(session.token)
                    val usersResult = repository.fetchChatUsers(session.token)
                    val friendsResult = repository.fetchFriendLinks(session.token)
                    if (result.isSuccess) {
                        state = state.copy(
                            messageItems = result.getOrNull()!!,
                            chatUsers = usersResult.getOrDefault(state.chatUsers),
                            friendLinks = friendsResult.getOrDefault(state.friendLinks),
                        )
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load messages")
                    }
                }

                NativeTab.PROFILE -> {
                    val result = repository.fetchProfileInfo(session.token)
                    if (result.isSuccess) {
                        val profile = result.getOrNull()
                        state = state.copy(profileInfo = profile)
                        applyProfileToEditor(profile)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load profile")
                    }
                }
            }

            state = state.copy(isTabLoading = false)
        }
    }

    fun refreshDashboard() {
        refreshCurrentTab()
    }

    fun updateStoriesSearchQuery(value: String) {
        state = state.copy(storiesSearchQuery = value)
    }

    fun updateStoriesStationFilter(stationId: Int?) {
        state = state.copy(storiesStationFilterId = stationId)
    }

    fun applyStoriesFilters() {
        val session = state.session ?: return
        viewModelScope.launch {
            state = state.copy(isTabLoading = true, errorMessage = null)
            val result = repository.fetchStoriesPage(
                token = session.token,
                page = 1,
                pageSize = 5,
                stationId = state.storiesStationFilterId,
                query = state.storiesSearchQuery,
            )
            if (result.isSuccess) {
                val page = result.getOrNull()!!
                val highlighted = page.items.sortedWith(
                    compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                        .thenByDescending { it.createdAtRaw }
                ).take(5)
                state = state.copy(
                    storyItems = page.items,
                    highlightedStoryItems = highlighted,
                    storiesPage = 1,
                    storiesHasNextPage = page.hasNextPage,
                    isTabLoading = false,
                )
            } else {
                state = state.copy(
                    isTabLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to filter stories",
                )
            }
        }
    }

    fun loadMoreStories() {
        val session = state.session ?: return
        if (state.isStoriesLoadingMore || !state.storiesHasNextPage) return
        val nextPage = state.storiesPage + 1

        viewModelScope.launch {
            state = state.copy(isStoriesLoadingMore = true, errorMessage = null)
            val result = repository.fetchStoriesPage(
                token = session.token,
                page = nextPage,
                pageSize = 5,
                stationId = state.storiesStationFilterId,
                query = state.storiesSearchQuery,
            )
            if (result.isSuccess) {
                val page = result.getOrNull()!!
                val merged = (state.storyItems + page.items).distinctBy { it.id }
                val highlighted = merged.sortedWith(
                    compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                        .thenByDescending { it.createdAtRaw }
                ).take(5)
                state = state.copy(
                    storyItems = merged,
                    highlightedStoryItems = highlighted,
                    storiesPage = nextPage,
                    storiesHasNextPage = page.hasNextPage,
                    isStoriesLoadingMore = false,
                )
            } else {
                state = state.copy(
                    isStoriesLoadingMore = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to load more stories",
                )
            }
        }
    }

    fun toggleStoryLike(storyId: Int, currentlyLiked: Boolean) {
        val session = state.session ?: return
        if (storyId <= 0) return

        viewModelScope.launch {
            val result = if (currentlyLiked) {
                repository.unlikeStory(session.token, storyId)
            } else {
                repository.likeStory(session.token, storyId)
            }
            if (result.isSuccess) {
                val updated = state.storyItems.map { item ->
                    if (item.id != storyId) item
                    else {
                        val newLiked = !currentlyLiked
                        val newLikeCount = if (newLiked) item.likeCount + 1 else (item.likeCount - 1).coerceAtLeast(0)
                        item.copy(likeCount = newLikeCount, likedByMe = newLiked)
                    }
                }
                val highlighted = updated.sortedWith(
                    compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                        .thenByDescending { it.createdAtRaw }
                ).take(5)
                state = state.copy(storyItems = updated, highlightedStoryItems = highlighted)
            } else {
                state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to update like")
            }
        }
    }

    fun addStoryComment(storyId: Int, body: String) {
        val session = state.session ?: return
        if (storyId <= 0) return
        if (body.trim().isBlank()) {
            state = state.copy(errorMessage = "Comment cannot be empty.")
            return
        }

        viewModelScope.launch {
            val result = repository.commentStory(session.token, storyId, body)
            if (result.isSuccess) {
                val refreshed = repository.fetchStoriesPage(
                    token = session.token,
                    page = 1,
                    pageSize = maxOf(5, state.storyItems.size.coerceAtLeast(5)),
                    stationId = state.storiesStationFilterId,
                    query = state.storiesSearchQuery,
                ).getOrNull()
                if (refreshed != null) {
                    val highlighted = refreshed.items.sortedWith(
                        compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                            .thenByDescending { it.createdAtRaw }
                    ).take(5)
                    state = state.copy(
                        storyItems = refreshed.items,
                        highlightedStoryItems = highlighted,
                        statusMessage = "Comment added.",
                    )
                }
            } else {
                state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to comment")
            }
        }
    }

    fun openUserActivity(userId: Int) {
        val session = state.session ?: return
        if (userId <= 0) return
        viewModelScope.launch {
            state = state.copy(isUserActivityLoading = true, errorMessage = null)
            val result = repository.fetchUserActivity(session.token, userId)
            if (result.isSuccess) {
                state = state.copy(
                    isUserActivityLoading = false,
                    selectedUserActivity = result.getOrNull(),
                )
            } else {
                state = state.copy(
                    isUserActivityLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Unable to load user profile",
                )
            }
        }
    }

    fun closeUserActivity() {
        state = state.copy(selectedUserActivity = null, isUserActivityLoading = false)
    }

    fun logout() {
        sessionStore.clear()
        state = AppUiState()
    }

    fun updateMessageRecipientId(raw: String) {
        val parsed = raw.trim().toIntOrNull()
        state = state.copy(messageRecipientId = parsed)
    }

    fun selectMessageRecipient(id: Int) {
        if (id <= 0) return
        state = state.copy(messageRecipientId = id, errorMessage = null, statusMessage = null)

        val session = state.session ?: return
        viewModelScope.launch {
            repository.markThreadAsRead(session.token, id)
            refreshMessagesData(session, preserveSelection = id)
        }
    }

    fun updateMessageDraftBody(value: String) {
        state = state.copy(messageDraftBody = value)
    }

    fun prepareMessageToSeller(recipientId: Int, listingTitle: String) {
        if (recipientId <= 0) {
            state = state.copy(errorMessage = "Seller contact is unavailable for this listing.")
            return
        }

        val prefill = "Bonjour, votre annonce '$listingTitle' est-elle toujours disponible ?"
        state = state.copy(
            selectedTab = NativeTab.MESSAGES,
            messageRecipientId = recipientId,
            messageDraftBody = prefill,
            errorMessage = null,
            statusMessage = null,
        )
        refreshCurrentTab()

        val session = state.session
        if (session != null) {
            viewModelScope.launch {
                repository.addFriend(session.token, recipientId)
                refreshMessagesData(session, preserveSelection = recipientId)
            }
        }
    }

    fun sendMessageDraft() {
        val session = state.session ?: return
        val recipientId = state.messageRecipientId
        val body = state.messageDraftBody.trim()

        if (recipientId == null || recipientId <= 0) {
            state = state.copy(errorMessage = "Recipient is required.")
            return
        }
        if (body.isBlank()) {
            state = state.copy(errorMessage = "Message cannot be empty.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isSendingMessage = true, errorMessage = null)

            val result = repository.sendMessage(
                token = session.token,
                recipientId = recipientId,
                subject = "Message chat",
                body = body,
            )

            if (result.isSuccess) {
                state = state.copy(isSendingMessage = false, messageDraftBody = "", statusMessage = null)
                repository.addFriend(session.token, recipientId)
                refreshMessagesData(session, preserveSelection = recipientId)
                awardXp(XP_SEND_MESSAGE, "Message envoye")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unable to send message"
                state = state.copy(isSendingMessage = false, errorMessage = msg)
            }
        }
    }

    fun updatePublishTitle(value: String) {
        state = state.copy(publishTitle = value)
    }

    fun updatePublishDescription(value: String) {
        state = state.copy(publishDescription = value)
    }

    fun updatePublishCity(value: String) {
        state = state.copy(publishCity = value)
    }

    fun updatePublishPrice(value: String) {
        state = state.copy(publishPrice = value)
    }

    fun updatePublishMaterialType(value: String) {
        state = state.copy(publishMaterialType = value)
    }

    fun updatePublishTransactionType(value: String) {
        state = state.copy(publishTransactionType = value)
    }

    fun updatePublishImageBase64(value: String) {
        state = if (value.isBlank()) {
            state.copy(publishImagesBase64 = emptyList())
        } else {
            state.copy(publishImagesBase64 = listOf(value))
        }
    }

    fun appendPublishImagesBase64(values: List<String>) {
        if (values.isEmpty()) return
        val cleaned = values.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        val merged = (state.publishImagesBase64 + cleaned).distinct().take(8)
        state = state.copy(publishImagesBase64 = merged)
    }

    fun removePublishImageAt(index: Int) {
        if (index !in state.publishImagesBase64.indices) return
        val updated = state.publishImagesBase64.toMutableList().also { it.removeAt(index) }
        state = state.copy(publishImagesBase64 = updated)
    }

    fun clearPublishImages() {
        state = state.copy(publishImagesBase64 = emptyList())
    }

    fun updatePublishPartnerTitle(value: String) {
        state = state.copy(publishPartnerTitle = value)
    }

    fun updatePublishPartnerMessage(value: String) {
        state = state.copy(publishPartnerMessage = value)
    }

    fun updatePublishPartnerCity(value: String) {
        state = state.copy(publishPartnerCity = value)
    }

    fun updatePublishPartnerLevel(value: String) {
        state = state.copy(publishPartnerLevel = value)
    }

    fun updatePublishPartnerDate(value: String) {
        state = state.copy(publishPartnerDate = value)
    }

    fun publishArticle() {
        val session = state.session ?: return
        val title = state.publishTitle.trim()
        val description = state.publishDescription.trim()
        val city = state.publishCity.trim()

        if (title.isBlank() || description.isBlank() || city.isBlank()) {
            state = state.copy(errorMessage = "Title, description and city are required.")
            return
        }

        val userId = state.profileInfo?.userId?.takeIf { it > 0 } ?: session.userId
        if (userId <= 0) {
            state = state.copy(errorMessage = "Unable to detect current user for publishing.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isPublishingArticle = true, errorMessage = null)
            val result = repository.publishMarketplaceListing(
                token = session.token,
                userId = userId,
                title = title,
                description = description,
                city = city,
                price = state.publishPrice.trim(),
                imagesBase64 = state.publishImagesBase64,
                materialType = state.publishMaterialType,
                transactionType = state.publishTransactionType,
            )

            if (result.isSuccess) {
                state = state.copy(
                    isPublishingArticle = false,
                    publishTitle = "",
                    publishDescription = "",
                    publishCity = "",
                    publishPrice = "",
                    publishMaterialType = "ski",
                    publishTransactionType = "sale",
                    publishImagesBase64 = emptyList(),
                    selectedTab = NativeTab.MARKETPLACE,
                )
                refreshCurrentTab()
                awardXp(XP_PUBLISH_MARKET, "Annonce marketplace publiee")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to publish article"
                state = state.copy(isPublishingArticle = false, errorMessage = message)
            }
        }
    }

    fun publishPartnerPost() {
        val session = state.session ?: return
        val title = state.publishPartnerTitle.trim()
        val message = state.publishPartnerMessage.trim()
        val city = state.publishPartnerCity.trim()
        val level = state.publishPartnerLevel.trim().ifBlank { "intermediate" }
        val preferredDate = state.publishPartnerDate.trim()

        if (title.isBlank() || message.isBlank() || city.isBlank()) {
            state = state.copy(errorMessage = "Title, message and city are required.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isPublishingPartner = true, errorMessage = null)
            val result = repository.publishPartnerPost(
                token = session.token,
                title = title,
                message = message,
                city = city,
                skillLevel = level,
                preferredDate = preferredDate,
            )

            if (result.isSuccess) {
                state = state.copy(
                    isPublishingPartner = false,
                    publishPartnerTitle = "",
                    publishPartnerMessage = "",
                    publishPartnerCity = "",
                    publishPartnerLevel = "intermediate",
                    publishPartnerDate = "",
                    selectedTab = NativeTab.PARTNERS,
                    statusMessage = "Annonce partenaire publiee.",
                )
                refreshCurrentTab()
                awardXp(XP_PUBLISH_PARTNER, "Annonce partenaire publiee")
            } else {
                val messageText = result.exceptionOrNull()?.message ?: "Unable to publish partner post"
                state = state.copy(isPublishingPartner = false, errorMessage = messageText)
            }
        }
    }

    fun requestCarpoolReservation(postId: Int, seats: Int) {
        val session = state.session ?: return
        if (postId <= 0) return

        viewModelScope.launch {
            state = state.copy(isTabLoading = true, errorMessage = null)
            val result = repository.requestCarpoolReservation(
                token = session.token,
                postId = postId,
                seats = seats.coerceAtLeast(1),
            )
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Demande de reservation envoyee.")
                refreshCurrentTab()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to request reservation"
                state = state.copy(errorMessage = message, isTabLoading = false)
            }
        }
    }

    fun cancelCarpoolReservation(postId: Int) {
        val session = state.session ?: return
        if (postId <= 0) return

        viewModelScope.launch {
            state = state.copy(isTabLoading = true, errorMessage = null)
            val result = repository.cancelCarpoolReservation(session.token, postId)
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Reservation/demande annulee.")
                refreshCurrentTab()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to cancel reservation"
                state = state.copy(errorMessage = message, isTabLoading = false)
            }
        }
    }

    fun approveCarpoolReservation(postId: Int, reservationId: Int) {
        val session = state.session ?: return
        if (postId <= 0 || reservationId <= 0) return

        viewModelScope.launch {
            state = state.copy(isTabLoading = true, errorMessage = null)
            val result = repository.approveCarpoolReservation(session.token, postId, reservationId)
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Demande approuvee.")
                refreshCurrentTab()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to approve reservation"
                state = state.copy(errorMessage = message, isTabLoading = false)
            }
        }
    }

    fun rejectCarpoolReservation(postId: Int, reservationId: Int) {
        val session = state.session ?: return
        if (postId <= 0 || reservationId <= 0) return

        viewModelScope.launch {
            state = state.copy(isTabLoading = true, errorMessage = null)
            val result = repository.rejectCarpoolReservation(session.token, postId, reservationId)
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Demande refusee.")
                refreshCurrentTab()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to reject reservation"
                state = state.copy(errorMessage = message, isTabLoading = false)
            }
        }
    }

    fun submitSellerRating(listingId: Int, sellerId: Int, score: Int, comment: String) {
        val session = state.session ?: return
        viewModelScope.launch {
            state = state.copy(errorMessage = null)
            val result = repository.rateSeller(
                token = session.token,
                listingId = listingId,
                ratedUserId = sellerId,
                score = score,
                comment = comment,
                raterUserId = session.userId,
            )
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Evaluation vendeur enregistree.")
                awardXp(XP_RATE_SELLER, "Evaluation vendeur")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to rate seller"
                state = state.copy(errorMessage = message)
            }
        }
    }

    fun submitStationRating(stationId: Int, score: Int, comment: String) {
        val session = state.session ?: return
        viewModelScope.launch {
            state = state.copy(errorMessage = null)
            val result = repository.rateStation(
                token = session.token,
                stationId = stationId,
                score = score,
                comment = comment,
            )
            if (result.isSuccess) {
                state = state.copy(statusMessage = "Evaluation station enregistree.")
                refreshCurrentTab()
                awardXp(XP_RATE_STATION, "Evaluation station")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to rate station"
                state = state.copy(errorMessage = message)
            }
        }
    }

    fun clearError() {
        state = state.copy(errorMessage = null)
    }

    fun clearStatusMessage() {
        state = state.copy(statusMessage = null)
    }

    fun setError(message: String) {
        state = state.copy(errorMessage = message, statusMessage = null)
    }

    private fun establishSession(session: UserSession) {
        sessionStore.save(session)
        state = state.copy(session = session, errorMessage = null)
        refreshAllNativeData()
    }

    private fun hasDataForTab(tab: NativeTab): Boolean {
        return when (tab) {
            NativeTab.HOME -> state.storyItems.isNotEmpty() || state.skiNewsItems.isNotEmpty()
            NativeTab.COMMUNITY -> state.storyItems.isNotEmpty() || state.skiNewsItems.isNotEmpty()
            NativeTab.STATIONS -> state.stationItems.isNotEmpty()
            NativeTab.BUS_LINES -> state.busLineItems.isNotEmpty()
            NativeTab.SERVICES -> state.serviceStoreItems.isNotEmpty()
            NativeTab.MARKETPLACE -> state.marketplaceItems.isNotEmpty()
            NativeTab.PARTNERS -> state.partnerItems.isNotEmpty()
            NativeTab.INSTRUCTORS -> state.instructorItems.isNotEmpty()
            NativeTab.PISTES -> state.pisteItems.isNotEmpty()
            NativeTab.MESSAGES -> state.messageItems.isNotEmpty() || state.chatUsers.isNotEmpty() || state.friendLinks.isNotEmpty()
            NativeTab.PROFILE -> state.profileInfo != null
        }
    }

    private fun refreshAllNativeData() {
        val session = state.session ?: return

        viewModelScope.launch {
            state = state.copy(isTabLoading = true)

            val current = state
            var firstError: String? = null

            val dashboardCounts = repository.fetchDashboardCounts(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load dashboard")
                current.dashboardCounts
            }

            val storyPage = repository.fetchStoriesPage(
                token = session.token,
                page = 1,
                pageSize = 5,
                stationId = current.storiesStationFilterId,
                query = current.storiesSearchQuery,
            ).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load stories")
                fr.grenobleski.nativeapp.data.model.StoryPage(
                    items = current.storyItems,
                    hasNextPage = current.storiesHasNextPage,
                    nextPage = null,
                )
            }

            val storyStats = repository.fetchStoryStats(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load community stats")
                current.storyStats
            }

            val skiNewsItems = repository.fetchSkiNews(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load ski news")
                current.skiNewsItems
            }

            val stationItems = repository.fetchStationItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load stations")
                current.stationItems
            }

            val busLineItems = repository.fetchBusLineItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load bus lines")
                current.busLineItems
            }

            val serviceStoreItems = repository.fetchServiceStoreItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load services")
                current.serviceStoreItems
            }

            val marketplaceItems = repository.fetchMarketplaceItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load marketplace")
                current.marketplaceItems
            }

            val partnerItems = repository.fetchPartnerItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load partner posts")
                current.partnerItems
            }

            val instructorItems = repository.fetchInstructorItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load instructors")
                current.instructorItems
            }

            val pisteItems = repository.fetchPisteItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load piste state")
                current.pisteItems
            }

            val messageItems = repository.fetchMessageItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load messages")
                current.messageItems
            }

            val chatUsers = repository.fetchChatUsers(session.token).getOrElse {
                current.chatUsers
            }

            val friendLinks = repository.fetchFriendLinks(session.token).getOrElse {
                current.friendLinks
            }

            val profileInfo = repository.fetchProfileInfo(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load profile")
                current.profileInfo
            }

            state = state.copy(
                isTabLoading = false,
                dashboardCounts = dashboardCounts,
                storyItems = storyPage.items,
                highlightedStoryItems = storyPage.items.sortedWith(
                    compareByDescending<StoryItem> { it.likeCount + it.commentCount }
                        .thenByDescending { it.createdAtRaw }
                ).take(5),
                skiNewsItems = skiNewsItems,
                highlightedSkiNewsItems = skiNewsItems.filter { it.highlighted }.take(5),
                storyStats = storyStats,
                storiesPage = 1,
                storiesHasNextPage = storyPage.hasNextPage,
                stationItems = stationItems,
                busLineItems = busLineItems,
                serviceStoreItems = serviceStoreItems,
                marketplaceItems = marketplaceItems,
                partnerItems = partnerItems,
                instructorItems = instructorItems,
                pisteItems = pisteItems,
                messageItems = messageItems,
                chatUsers = chatUsers,
                friendLinks = friendLinks,
                profileInfo = profileInfo,
                // Avoid noisy global errors at startup; errors are surfaced on explicit tab refresh/actions.
                errorMessage = null,
            )

            applyProfileToEditor(profileInfo)
        }
    }

    private suspend fun refreshMessagesData(session: UserSession, preserveSelection: Int?) {
        val messages = repository.fetchMessageItems(session.token).getOrElse { state.messageItems }
        val users = repository.fetchChatUsers(session.token).getOrElse { state.chatUsers }
        val links = repository.fetchFriendLinks(session.token).getOrElse { state.friendLinks }

        state = state.copy(
            messageItems = messages,
            chatUsers = users,
            friendLinks = links,
            messageRecipientId = preserveSelection,
        )
    }

    private fun applyProfileToEditor(profile: ProfileInfo?) {
        if (profile == null) return

        val current = state
        val firstName = if (current.profileEditFirstName.isBlank()) profile.firstName else current.profileEditFirstName
        val lastName = if (current.profileEditLastName.isBlank()) profile.lastName else current.profileEditLastName
        val email = if (current.profileEditEmail.isBlank()) profile.email else current.profileEditEmail

        state = current.copy(
            profileEditFirstName = firstName,
            profileEditLastName = lastName,
            profileEditEmail = email,
        )
    }

    private fun awardXp(points: Int, reason: String) {
        if (points <= 0) return
        val newXp = state.xpPoints + points
        val newLevel = (newXp / 100) + 1
        state = state.copy(
            xpPoints = newXp,
            xpLevel = newLevel,
            statusMessage = "+$points XP - $reason",
        )
    }
}

class AppViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(repository, sessionStore) as T
    }
}
