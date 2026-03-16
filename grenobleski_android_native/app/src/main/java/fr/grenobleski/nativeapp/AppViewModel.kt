package fr.grenobleski.nativeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.DashboardCounts
import fr.grenobleski.nativeapp.data.model.ChatUserOption
import fr.grenobleski.nativeapp.data.model.InstructorItem
import fr.grenobleski.nativeapp.data.model.MarketplaceItem
import fr.grenobleski.nativeapp.data.model.MessageItem
import fr.grenobleski.nativeapp.data.model.NativeTab
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
import fr.grenobleski.nativeapp.data.model.UserSession
import fr.grenobleski.nativeapp.data.session.SessionStore
import kotlinx.coroutines.launch

data class AppUiState(
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val confirmPassword: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: UserSession? = null,
    val selectedTab: NativeTab = NativeTab.HOME,
    val isTabLoading: Boolean = false,
    val dashboardCounts: DashboardCounts = DashboardCounts(),
    val marketplaceItems: List<MarketplaceItem> = emptyList(),
    val instructorItems: List<InstructorItem> = emptyList(),
    val pisteItems: List<PisteItem> = emptyList(),
    val messageItems: List<MessageItem> = emptyList(),
    val chatUsers: List<ChatUserOption> = emptyList(),
    val messageRecipientId: Int? = null,
    val messageDraftBody: String = "",
    val isSendingMessage: Boolean = false,
    val publishTitle: String = "",
    val publishDescription: String = "",
    val publishCity: String = "",
    val publishPrice: String = "",
    val publishImagesBase64: List<String> = emptyList(),
    val isPublishingArticle: Boolean = false,
    val profileInfo: ProfileInfo? = null,
)

class AppViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
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

    fun switchAuthMode(registerMode: Boolean) {
        state = state.copy(
            isRegisterMode = registerMode,
            errorMessage = null,
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
            )

            if (result.isSuccess) {
                val session = result.getOrNull()!!
                state = state.copy(
                    isLoading = false,
                    password = "",
                    confirmPassword = "",
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
        state = state.copy(selectedTab = tab, errorMessage = null)
        if (!hasDataForTab(tab)) {
            refreshCurrentTab()
        }
    }

    fun refreshCurrentTab() {
        val session = state.session ?: return
        val tab = state.selectedTab

        viewModelScope.launch {
            state = state.copy(isTabLoading = true)

            when (tab) {
                NativeTab.HOME -> {
                    val result = repository.fetchDashboardCounts(session.token)
                    if (result.isSuccess) {
                        state = state.copy(dashboardCounts = result.getOrNull()!!)
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to refresh")
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
                    if (result.isSuccess) {
                        state = state.copy(
                            messageItems = result.getOrNull()!!,
                            chatUsers = usersResult.getOrDefault(state.chatUsers),
                        )
                    } else {
                        state = state.copy(errorMessage = result.exceptionOrNull()?.message ?: "Unable to load messages")
                    }
                }

                NativeTab.PROFILE -> {
                    val result = repository.fetchProfileInfo(session.token)
                    if (result.isSuccess) {
                        state = state.copy(profileInfo = result.getOrNull())
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
        state = state.copy(messageRecipientId = id)
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
        )
        refreshCurrentTab()
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
                state = state.copy(isSendingMessage = false, messageDraftBody = "")
                refreshCurrentTab()
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
            )

            if (result.isSuccess) {
                state = state.copy(
                    isPublishingArticle = false,
                    publishTitle = "",
                    publishDescription = "",
                    publishCity = "",
                    publishPrice = "",
                    publishImagesBase64 = emptyList(),
                    selectedTab = NativeTab.MARKETPLACE,
                )
                refreshCurrentTab()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to publish article"
                state = state.copy(isPublishingArticle = false, errorMessage = message)
            }
        }
    }

    fun clearError() {
        state = state.copy(errorMessage = null)
    }

    fun setError(message: String) {
        state = state.copy(errorMessage = message)
    }

    private fun establishSession(session: UserSession) {
        sessionStore.save(session)
        state = state.copy(session = session, errorMessage = null)
        refreshAllNativeData()
    }

    private fun hasDataForTab(tab: NativeTab): Boolean {
        return when (tab) {
            NativeTab.HOME -> true
            NativeTab.MARKETPLACE -> state.marketplaceItems.isNotEmpty()
            NativeTab.INSTRUCTORS -> state.instructorItems.isNotEmpty()
            NativeTab.PISTES -> state.pisteItems.isNotEmpty()
            NativeTab.MESSAGES -> state.messageItems.isNotEmpty() || state.chatUsers.isNotEmpty()
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

            val marketplaceItems = repository.fetchMarketplaceItems(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load marketplace")
                current.marketplaceItems
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

            val profileInfo = repository.fetchProfileInfo(session.token).getOrElse {
                firstError = firstError ?: (it.message ?: "Unable to load profile")
                current.profileInfo
            }

            state = state.copy(
                isTabLoading = false,
                dashboardCounts = dashboardCounts,
                marketplaceItems = marketplaceItems,
                instructorItems = instructorItems,
                pisteItems = pisteItems,
                messageItems = messageItems,
                chatUsers = chatUsers,
                profileInfo = profileInfo,
                // Avoid noisy global errors at startup; errors are surfaced on explicit tab refresh/actions.
                errorMessage = null,
            )
        }
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
