package fr.grenobleski.nativeapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.AppViewModel
import fr.grenobleski.nativeapp.AppViewModelFactory
import fr.grenobleski.nativeapp.BuildConfig
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.NativeTab
import fr.grenobleski.nativeapp.data.network.GrenobleSkiApiClient
import fr.grenobleski.nativeapp.data.session.SessionStore
import fr.grenobleski.nativeapp.ui.components.MetricCard
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MOBILE_AUTH_COMPLETE_CANDIDATES = listOf(
    "/api/mobile/auth/complete/",
    "/api/mobile/auth/complete",
    "/mobile/auth/complete/",
    "/mobile/auth/complete",
)

private val tabItems = listOf(
    TabNavItem(NativeTab.HOME, R.string.nav_home, Icons.Filled.Home),
    TabNavItem(NativeTab.MARKETPLACE, R.string.marketplace, Icons.Filled.Storefront),
    TabNavItem(NativeTab.INSTRUCTORS, R.string.instructors, Icons.Filled.School),
    TabNavItem(NativeTab.PISTES, R.string.piste_status, Icons.Filled.Terrain),
    TabNavItem(NativeTab.MESSAGES, R.string.messages, Icons.Filled.Chat),
    TabNavItem(NativeTab.PROFILE, R.string.profile, Icons.Filled.Person),
)

private data class TabNavItem(
    val tab: NativeTab,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun GrenobleSkiApp(
    pendingAuthUri: Uri? = null,
    onAuthUriConsumed: () -> Unit = {},
) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    val appContext = localContext.applicationContext
    val uiScope = rememberCoroutineScope()

    val repository = remember {
        AuthRepository(
            service = GrenobleSkiApiClient.createService(BuildConfig.API_BASE_URL),
            siteBaseUrl = BuildConfig.API_BASE_URL,
        )
    }
    val sessionStore = remember { SessionStore(appContext) }
    val viewModel: AppViewModel = viewModel(
        factory = AppViewModelFactory(repository = repository, sessionStore = sessionStore)
    )

    val state = viewModel.state
    val siteBase = BuildConfig.API_BASE_URL.trimEnd('/')

    LaunchedEffect(pendingAuthUri) {
        val callback = pendingAuthUri ?: return@LaunchedEffect
        val token = callback.getQueryParameter("token").orEmpty()
        val email = callback.getQueryParameter("email").orEmpty()
        val name = callback.getQueryParameter("name").orEmpty()

        if (token.isBlank()) {
            viewModel.setError(appContext.getString(R.string.mobile_auth_invalid))
        } else {
            viewModel.loginWithToken(token = token, email = email, displayName = name)
        }
        onAuthUriConsumed()
    }

    if (state.session == null) {
        LoginScreen(
            state = state,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onFirstNameChange = viewModel::updateFirstName,
            onLastNameChange = viewModel::updateLastName,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onSwitchAuthMode = viewModel::switchAuthMode,
            onForgotPassword = {
                val ok = openExternalUrl(localContext, "$siteBase/password/reset/")
                if (!ok) {
                    viewModel.setError(appContext.getString(R.string.browser_error))
                } else {
                    viewModel.clearError()
                }
            },
            onGoogle = {
                uiScope.launch {
                    val callbackUrl = resolveFirstReachableEndpoint(
                        candidates = MOBILE_AUTH_COMPLETE_CANDIDATES.map { "$siteBase$it" },
                        fallbackUrl = "",
                    )
                    if (callbackUrl.isBlank()) {
                        viewModel.setError(appContext.getString(R.string.mobile_bridge_unavailable))
                        return@launch
                    }
                    val googleUrl = "$siteBase/accounts/google/login/?process=login&next=${Uri.encode(callbackUrl)}"
                    val ok = openExternalUrl(localContext, googleUrl)
                    if (!ok) {
                        viewModel.setError(appContext.getString(R.string.browser_error))
                    } else {
                        viewModel.clearError()
                    }
                }
            },
        )
    } else {
        NativeShell(
            state = state,
            onSelectTab = viewModel::selectTab,
            onRefresh = viewModel::refreshCurrentTab,
            onLogout = viewModel::logout,
        )
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onSwitchAuthMode: (Boolean) -> Unit,
    onForgotPassword: () -> Unit,
    onGoogle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(84.dp)
                            .height(84.dp),
                    )

                    Text(
                        text = stringResource(id = R.string.auth_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(id = R.string.auth_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(id = R.string.email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.isRegisterMode) {
                        OutlinedTextField(
                            value = state.firstName,
                            onValueChange = onFirstNameChange,
                            label = { Text(stringResource(id = R.string.first_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.lastName,
                            onValueChange = onLastNameChange,
                            label = { Text(stringResource(id = R.string.last_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(id = R.string.password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.isRegisterMode) {
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = { Text(stringResource(id = R.string.confirm_password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Button(
                        onClick = if (state.isRegisterMode) onRegister else onLogin,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            if (state.isRegisterMode) {
                                stringResource(id = R.string.create_account_in_app)
                            } else {
                                stringResource(id = R.string.sign_in)
                            }
                        )
                    }

                    if (!state.isRegisterMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onForgotPassword) {
                                Text(stringResource(id = R.string.forgot_password))
                            }
                        }

                        OutlinedButton(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.continue_google))
                        }
                    }

                    TextButton(
                        onClick = { onSwitchAuthMode(!state.isRegisterMode) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            if (state.isRegisterMode) {
                                stringResource(id = R.string.back_to_login)
                            } else {
                                stringResource(id = R.string.create_account)
                            }
                        )
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(
    state: AppUiState,
    onSelectTab: (NativeTab) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabTitle(state.selectedTab)) },
                actions = {
                    if (state.isTabLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.refresh))
                    }
                    IconButton(onClick = onLogout) {
                        Icon(imageVector = Icons.Filled.Logout, contentDescription = stringResource(id = R.string.logout))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabItems.forEach { item ->
                    NavigationBarItem(
                        selected = state.selectedTab == item.tab,
                        onClick = { onSelectTab(item.tab) },
                        icon = { Icon(imageVector = item.icon, contentDescription = stringResource(id = item.labelRes)) },
                        label = { Text(stringResource(id = item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.selectedTab) {
                NativeTab.HOME -> HomeTab(state)
                NativeTab.MARKETPLACE -> MarketplaceTab(state)
                NativeTab.INSTRUCTORS -> InstructorsTab(state)
                NativeTab.PISTES -> PistesTab(state)
                NativeTab.MESSAGES -> MessagesTab(state)
                NativeTab.PROFILE -> ProfileTab(state)
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTab(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.session?.displayName.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(id = R.string.stations),
                value = state.dashboardCounts.stations,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(id = R.string.bus_lines),
                value = state.dashboardCounts.busLines,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(id = R.string.services),
                value = state.dashboardCounts.services,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(id = R.string.marketplace),
                value = state.dashboardCounts.marketplace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MarketplaceTab(state: AppUiState) {
    if (state.marketplaceItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_marketplace))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.marketplaceItems) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${item.city}  •  ${item.priceLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(item.conditionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InstructorsTab(state: AppUiState) {
    if (state.instructorItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_instructors))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.instructorItems) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PistesTab(state: AppUiState) {
    if (state.pisteItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_pistes))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.pisteItems) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.stationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${stringResource(id = R.string.piste_rating)}: ${item.rating}  •  ${item.crowdLabel}", style = MaterialTheme.typography.bodySmall)
                    Text(item.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MessagesTab(state: AppUiState) {
    if (state.messageItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_messages))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.messageItems) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.fromLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.body, style = MaterialTheme.typography.bodySmall)
                    if (item.createdAtLabel.isNotBlank()) {
                        Text(item.createdAtLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(state: AppUiState) {
    val profile = state.profileInfo
    if (profile == null) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_profile))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(profile.email, style = MaterialTheme.typography.bodyMedium)
                if (profile.username.isNotBlank()) {
                    Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun tabTitle(tab: NativeTab): String {
    return when (tab) {
        NativeTab.HOME -> stringResource(id = R.string.nav_home)
        NativeTab.MARKETPLACE -> stringResource(id = R.string.marketplace)
        NativeTab.INSTRUCTORS -> stringResource(id = R.string.instructors)
        NativeTab.PISTES -> stringResource(id = R.string.piste_status)
        NativeTab.MESSAGES -> stringResource(id = R.string.messages)
        NativeTab.PROFILE -> stringResource(id = R.string.profile)
    }
}

private fun openExternalUrl(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)

    return try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        if (context !is Activity) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(context, uri)
        true
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

private suspend fun resolveFirstReachableEndpoint(
    candidates: List<String>,
    fallbackUrl: String,
): String {
    return withContext(Dispatchers.IO) {
        for (candidate in candidates) {
            if (isEndpointReachable(candidate)) {
                return@withContext candidate
            }
        }
        fallbackUrl
    }
}

private fun isEndpointReachable(url: String): Boolean {
    return try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 2500
            readTimeout = 2500
        }

        val status = connection.responseCode
        connection.disconnect()
        status in 200..399
    } catch (_: Exception) {
        false
    }
}
