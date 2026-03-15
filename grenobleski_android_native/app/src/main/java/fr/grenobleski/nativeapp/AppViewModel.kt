package fr.grenobleski.nativeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.DashboardCounts
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
                    if (result.isSuccess) {
                        state = state.copy(messageItems = result.getOrNull()!!)
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
            NativeTab.MESSAGES -> state.messageItems.isNotEmpty()
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
                profileInfo = profileInfo,
                errorMessage = firstError,
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
