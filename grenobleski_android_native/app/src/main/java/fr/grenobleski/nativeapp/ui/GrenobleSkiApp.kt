package fr.grenobleski.nativeapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val GOOGLE_LOGIN_CANDIDATES = listOf(
    "/accounts/google/login/",
    "/accounts/google/login",
)

private enum class BottomNavAction {
    HOME,
    MARKETPLACE,
    MESSAGES,
    MORE,
}

private val bottomNavItems = listOf(
    BottomNavItem(BottomNavAction.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomNavItem(BottomNavAction.MARKETPLACE, R.string.nav_market_short, Icons.Filled.Storefront),
    BottomNavItem(BottomNavAction.MESSAGES, R.string.nav_chat_short, Icons.AutoMirrored.Filled.Chat),
    BottomNavItem(BottomNavAction.MORE, R.string.nav_more, Icons.Filled.MoreHoriz),
)

private data class BottomNavItem(
    val action: BottomNavAction,
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
        val token = callback.queryParamOrFragment("token").orEmpty()
        val email = callback.queryParamOrFragment("email").orEmpty()
        val name = callback.queryParamOrFragment("name").orEmpty()

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
            onOpenWebLogin = {
                val ok = openExternalUrl(localContext, "$siteBase/accounts/login/")
                if (!ok) {
                    viewModel.setError(appContext.getString(R.string.browser_error))
                }
            },
            onOpenTerms = {
                val ok = openExternalUrl(localContext, "$siteBase/terms/")
                if (!ok) viewModel.setError(appContext.getString(R.string.browser_error))
            },
            onOpenPrivacy = {
                val ok = openExternalUrl(localContext, "$siteBase/privacy/")
                if (!ok) viewModel.setError(appContext.getString(R.string.browser_error))
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
                    val loginBase = resolveFirstReachableEndpoint(
                        candidates = GOOGLE_LOGIN_CANDIDATES.map { "$siteBase$it" },
                        fallbackUrl = "$siteBase/accounts/google/login/",
                    )
                    val separator = if (loginBase.contains("?")) "&" else "?"
                    val googleUrl = "$loginBase${separator}process=login&next=${Uri.encode(callbackUrl)}"
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
            siteBase = siteBase,
            onSelectTab = viewModel::selectTab,
            onRefresh = viewModel::refreshCurrentTab,
            onLogout = viewModel::logout,
            onPrepareMessageToSeller = viewModel::prepareMessageToSeller,
            onMessageRecipientChange = viewModel::updateMessageRecipientId,
            onMessageBodyChange = viewModel::updateMessageDraftBody,
            onSendMessage = viewModel::sendMessageDraft,
            onUpdatePublishTitle = viewModel::updatePublishTitle,
            onUpdatePublishDescription = viewModel::updatePublishDescription,
            onUpdatePublishCity = viewModel::updatePublishCity,
            onUpdatePublishPrice = viewModel::updatePublishPrice,
            onPublishArticle = viewModel::publishArticle,
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
    onOpenWebLogin: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
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

                        if (state.errorMessage.contains("callback", ignoreCase = true)) {
                            OutlinedButton(
                                onClick = onOpenWebLogin,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(id = R.string.open_web_login))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = onOpenTerms) { Text(stringResource(id = R.string.terms)) }
                        TextButton(onClick = onOpenPrivacy) { Text(stringResource(id = R.string.privacy)) }
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
    siteBase: String,
    onSelectTab: (NativeTab) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPrepareMessageToSeller: (Int, String) -> Unit,
    onMessageRecipientChange: (String) -> Unit,
    onMessageBodyChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onUpdatePublishTitle: (String) -> Unit,
    onUpdatePublishDescription: (String) -> Unit,
    onUpdatePublishCity: (String) -> Unit,
    onUpdatePublishPrice: (String) -> Unit,
    onPublishArticle: () -> Unit,
) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    var quickMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var publishDialogOpen by remember { mutableStateOf(false) }
    val currentUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    val unreadMessages = state.messageItems.count { !it.isRead && it.recipientId == currentUserId }

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
                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(id = R.string.logout))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (quickMenuOpen) {
                    SmallFloatingActionButton(onClick = { onSelectTab(NativeTab.MARKETPLACE) }) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = stringResource(id = R.string.marketplace))
                    }
                    SmallFloatingActionButton(onClick = { onSelectTab(NativeTab.MESSAGES) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(id = R.string.messages))
                    }
                    SmallFloatingActionButton(onClick = { onSelectTab(NativeTab.PROFILE) }) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(id = R.string.profile))
                    }
                }

                FloatingActionButton(onClick = { quickMenuOpen = !quickMenuOpen }) {
                    if (quickMenuOpen) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.close_menu))
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.open_quick_menu))
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = when (item.action) {
                        BottomNavAction.HOME -> state.selectedTab == NativeTab.HOME
                        BottomNavAction.MARKETPLACE -> state.selectedTab == NativeTab.MARKETPLACE
                        BottomNavAction.MESSAGES -> state.selectedTab == NativeTab.MESSAGES
                        BottomNavAction.MORE -> false
                    }
                    NavigationBarItem(
                        selected = selected,
                        alwaysShowLabel = false,
                        onClick = {
                            when (item.action) {
                                BottomNavAction.HOME -> onSelectTab(NativeTab.HOME)
                                BottomNavAction.MARKETPLACE -> onSelectTab(NativeTab.MARKETPLACE)
                                BottomNavAction.MESSAGES -> onSelectTab(NativeTab.MESSAGES)
                                BottomNavAction.MORE -> moreMenuOpen = true
                            }
                        },
                        icon = {
                            if (item.action == BottomNavAction.MESSAGES && unreadMessages > 0) {
                                BadgedBox(badge = { Badge { Text(unreadMessages.toString()) } }) {
                                    Icon(imageVector = item.icon, contentDescription = stringResource(id = item.labelRes))
                                }
                            } else {
                                Icon(imageVector = item.icon, contentDescription = stringResource(id = item.labelRes))
                            }
                        },
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
                NativeTab.MARKETPLACE -> MarketplaceTab(state, onPrepareMessageToSeller)
                NativeTab.INSTRUCTORS -> InstructorsTab(state)
                NativeTab.PISTES -> PistesTab(state)
                NativeTab.MESSAGES -> MessagesTab(
                    state = state,
                    onRecipientChange = onMessageRecipientChange,
                    onBodyChange = onMessageBodyChange,
                    onSend = onSendMessage,
                )
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

        if (moreMenuOpen) {
            AlertDialog(
                onDismissRequest = { moreMenuOpen = false },
                title = { Text(stringResource(id = R.string.nav_more)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            onSelectTab(NativeTab.PROFILE)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.profile))
                        }
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            onSelectTab(NativeTab.INSTRUCTORS)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.instructors))
                        }
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            onSelectTab(NativeTab.PISTES)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.piste_status))
                        }
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            publishDialogOpen = true
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.publish_article))
                        }
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            openExternalUrl(localContext, "$siteBase/terms/")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.terms))
                        }
                        OutlinedButton(onClick = {
                            moreMenuOpen = false
                            openExternalUrl(localContext, "$siteBase/privacy/")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(id = R.string.privacy))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { moreMenuOpen = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                },
            )
        }

        if (publishDialogOpen) {
            AlertDialog(
                onDismissRequest = { publishDialogOpen = false },
                title = { Text(stringResource(id = R.string.publish_article)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.publishTitle,
                            onValueChange = onUpdatePublishTitle,
                            label = { Text(stringResource(id = R.string.article_title)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.publishDescription,
                            onValueChange = onUpdatePublishDescription,
                            label = { Text(stringResource(id = R.string.article_description)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                        )
                        OutlinedTextField(
                            value = state.publishCity,
                            onValueChange = onUpdatePublishCity,
                            label = { Text(stringResource(id = R.string.city)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.publishPrice,
                            onValueChange = onUpdatePublishPrice,
                            label = { Text(stringResource(id = R.string.price)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onPublishArticle()
                        },
                        enabled = !state.isPublishingArticle,
                    ) {
                        if (state.isPublishingArticle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(id = R.string.publish))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { publishDialogOpen = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeTab(state: AppUiState) {
    val stories = listOf(
        stringResource(id = R.string.story_powder_alert),
        stringResource(id = R.string.story_last_minute_deals),
        stringResource(id = R.string.story_bus_live_updates),
        stringResource(id = R.string.story_local_guides),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = stringResource(id = R.string.app_name),
                            modifier = Modifier
                                .size(52.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .padding(6.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.dashboard_welcome),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = state.session?.displayName.orEmpty(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.dashboard_subtitle_premium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(id = R.string.stories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stories) { story ->
                    Card(
                        modifier = Modifier.width(172.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(story, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item {
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
        }

        item {
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

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.recent_activities),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ActivityLine(stringResource(id = R.string.activity_market_updated, state.dashboardCounts.marketplace))
                    ActivityLine(stringResource(id = R.string.activity_instructors_ready, state.instructorItems.size))
                    ActivityLine(stringResource(id = R.string.activity_piste_reports, state.pisteItems.size))
                }
            }
        }
    }
}

@Composable
private fun MarketplaceTab(
    state: AppUiState,
    onPrepareMessageToSeller: (Int, String) -> Unit,
) {
    var selectedItem by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.MarketplaceItem?>(null) }

    if (state.marketplaceItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_marketplace))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.marketplaceItems) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedItem = item },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${item.city}  •  ${item.priceLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(item.conditionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.sellerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedItem = item }) {
                            Text(stringResource(id = R.string.view_details))
                        }
                        Button(onClick = { onPrepareMessageToSeller(item.sellerId, item.title) }) {
                            Text(stringResource(id = R.string.contact_seller))
                        }
                    }
                }
            }
        }
    }

    val details = selectedItem
    if (details != null) {
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(details.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${stringResource(id = R.string.city)}: ${details.city}")
                    Text("${stringResource(id = R.string.price)}: ${details.priceLabel}")
                    Text("${stringResource(id = R.string.condition)}: ${details.conditionLabel}")
                    Text("${stringResource(id = R.string.seller)}: ${details.sellerLabel}")
                    if (details.postedAtLabel.isNotBlank()) {
                        Text("${stringResource(id = R.string.posted_at)}: ${details.postedAtLabel}")
                    }
                    Text(details.description)
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedItem = null
                    onPrepareMessageToSeller(details.sellerId, details.title)
                }) {
                    Text(stringResource(id = R.string.contact_seller))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedItem = null }) {
                    Text(stringResource(id = R.string.close))
                }
            },
        )
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
private fun MessagesTab(
    state: AppUiState,
    onRecipientChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val myUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    val orderedMessages = state.messageItems.asReversed()

    Column(modifier = Modifier.fillMaxSize()) {
        if (orderedMessages.isEmpty()) {
            EmptyTabMessage(text = stringResource(id = R.string.empty_messages))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orderedMessages) { item ->
                    val mine = myUserId > 0 && item.senderId == myUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .background(
                                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (mine) stringResource(id = R.string.you) else item.fromLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mine) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!mine && !item.isRead) {
                                        Badge { Text(stringResource(id = R.string.new_short)) }
                                    }
                                    if (item.createdAtLabel.isNotBlank()) {
                                        Text(
                                            item.createdAtLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (mine) Color.White.copy(alpha = 0.74f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.messageRecipientId?.toString().orEmpty(),
                    onValueChange = onRecipientChange,
                    label = { Text(stringResource(id = R.string.recipient_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.messageDraftBody,
                    onValueChange = onBodyChange,
                    label = { Text(stringResource(id = R.string.message)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Button(
                    onClick = onSend,
                    enabled = !state.isSendingMessage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSendingMessage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(id = R.string.send_message))
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
private fun ActivityLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun Uri.queryParamOrFragment(name: String): String? {
    val fromQuery = getQueryParameter(name)
    if (!fromQuery.isNullOrBlank()) {
        return fromQuery
    }

    val fragmentPart = fragment.orEmpty()
    if (fragmentPart.isBlank()) {
        return null
    }

    val pairs = fragmentPart.split("&")
    for (pair in pairs) {
        val keyValue = pair.split("=", limit = 2)
        if (keyValue.size != 2) continue
        if (keyValue[0] == name) {
            return Uri.decode(keyValue[1])
        }
    }
    return null
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
