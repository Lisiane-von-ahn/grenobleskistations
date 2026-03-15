package fr.grenobleski.nativeapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.DashboardCounts
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
    val dashboardCounts: DashboardCounts = DashboardCounts(),
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
            refreshDashboard()
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
                sessionStore.save(session)
                state = state.copy(
                    isLoading = false,
                    session = session,
                    password = "",
                )
                refreshDashboard()
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

        val session = UserSession(
            token = token,
            email = normalizedEmail,
            displayName = finalDisplayName,
        )
        sessionStore.save(session)
        state = state.copy(session = session, password = "", errorMessage = null)
        refreshDashboard()
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
                sessionStore.save(session)
                state = state.copy(
                    isLoading = false,
                    session = session,
                    password = "",
                    confirmPassword = "",
                    isRegisterMode = false,
                )
                refreshDashboard()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to create account"
                state = state.copy(isLoading = false, errorMessage = message)
            }
        }
    }

    fun refreshDashboard() {
        val session = state.session ?: return

        viewModelScope.launch {
            state = state.copy(isLoading = true)
            val result = repository.fetchDashboardCounts(session.token)
            if (result.isSuccess) {
                state = state.copy(
                    isLoading = false,
                    dashboardCounts = result.getOrNull()!!,
                )
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unable to refresh dashboard"
                state = state.copy(isLoading = false, errorMessage = message)
            }
        }
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
