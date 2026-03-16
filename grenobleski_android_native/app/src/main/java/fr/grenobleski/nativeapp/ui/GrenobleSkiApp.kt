package fr.grenobleski.nativeapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import android.graphics.BitmapFactory
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

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

private data class ChatThreadSummary(
    val userId: Int,
    val label: String,
    val photoBase64: String,
    val photoUrl: String,
    val lastMessage: String,
    val lastDateLabel: String,
    val unreadCount: Int,
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
            onSelectMessageRecipient = viewModel::selectMessageRecipient,
            onAddFriend = viewModel::addFriend,
            onRemoveFriend = viewModel::removeFriend,
            onMessageBodyChange = viewModel::updateMessageDraftBody,
            onSendMessage = viewModel::sendMessageDraft,
            onProfileFirstNameChange = viewModel::updateProfileEditFirstName,
            onProfileLastNameChange = viewModel::updateProfileEditLastName,
            onProfileEmailChange = viewModel::updateProfileEditEmail,
            onCurrentPasswordChange = viewModel::updateCurrentPasswordInput,
            onNewPasswordChange = viewModel::updateNewPasswordInput,
            onConfirmNewPasswordChange = viewModel::updateConfirmNewPasswordInput,
            onSaveProfile = viewModel::saveProfileChanges,
            onChangePassword = viewModel::changePassword,
            onUpdatePublishTitle = viewModel::updatePublishTitle,
            onUpdatePublishDescription = viewModel::updatePublishDescription,
            onUpdatePublishCity = viewModel::updatePublishCity,
            onUpdatePublishPrice = viewModel::updatePublishPrice,
            onAppendPublishImages = viewModel::appendPublishImagesBase64,
            onRemovePublishImageAt = viewModel::removePublishImageAt,
            onClearPublishImages = viewModel::clearPublishImages,
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
    onSelectMessageRecipient: (Int) -> Unit,
    onAddFriend: (Int) -> Unit,
    onRemoveFriend: (Int) -> Unit,
    onMessageBodyChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onProfileFirstNameChange: (String) -> Unit,
    onProfileLastNameChange: (String) -> Unit,
    onProfileEmailChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onUpdatePublishTitle: (String) -> Unit,
    onUpdatePublishDescription: (String) -> Unit,
    onUpdatePublishCity: (String) -> Unit,
    onUpdatePublishPrice: (String) -> Unit,
    onAppendPublishImages: (List<String>) -> Unit,
    onRemovePublishImageAt: (Int) -> Unit,
    onClearPublishImages: () -> Unit,
    onPublishArticle: () -> Unit,
) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    var quickMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var publishDialogOpen by remember { mutableStateOf(false) }
    val publishPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val encoded = uris.mapNotNull { uri -> uriToBase64(localContext, uri) }
        onAppendPublishImages(encoded)
    }
    val currentUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    val unreadMessages = state.messageItems.count { !it.isRead && it.recipientId == currentUserId }

    LaunchedEffect(
        state.isPublishingArticle,
        state.publishTitle,
        state.publishDescription,
        state.publishCity,
        state.selectedTab,
    ) {
        if (
            publishDialogOpen &&
            !state.isPublishingArticle &&
            state.selectedTab == NativeTab.MARKETPLACE &&
            state.publishTitle.isBlank() &&
            state.publishDescription.isBlank() &&
            state.publishCity.isBlank()
        ) {
            publishDialogOpen = false
        }
    }

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
                NativeTab.INSTRUCTORS -> InstructorsTab(state, onPrepareMessageToSeller)
                NativeTab.PISTES -> PistesTab(state)
                NativeTab.MESSAGES -> MessagesTab(
                    state = state,
                    onSelectRecipient = onSelectMessageRecipient,
                    onAddFriend = onAddFriend,
                    onRemoveFriend = onRemoveFriend,
                    onBodyChange = onMessageBodyChange,
                    onSend = onSendMessage,
                )
                NativeTab.PROFILE -> ProfileTab(
                    state = state,
                    onFirstNameChange = onProfileFirstNameChange,
                    onLastNameChange = onProfileLastNameChange,
                    onEmailChange = onProfileEmailChange,
                    onCurrentPasswordChange = onCurrentPasswordChange,
                    onNewPasswordChange = onNewPasswordChange,
                    onConfirmNewPasswordChange = onConfirmNewPasswordChange,
                    onSaveProfile = onSaveProfile,
                    onChangePassword = onChangePassword,
                )
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

            if (!state.statusMessage.isNullOrBlank()) {
                Text(
                    text = state.statusMessage,
                    color = Color(0xFF126E3A),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 34.dp),
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
            Dialog(onDismissRequest = { publishDialogOpen = false }) {
                val previews = remember(state.publishImagesBase64) {
                    state.publishImagesBase64.map { decodeBase64Image(it) }
                }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                        )
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(id = R.string.publish_article),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(id = R.string.publish_premium_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.86f),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
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

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { publishPhotoPicker.launch("image/*") }) {
                                    Text(stringResource(id = R.string.choose_photos))
                                }
                                if (state.publishImagesBase64.isNotEmpty()) {
                                    TextButton(onClick = onClearPublishImages) {
                                        Text(stringResource(id = R.string.clear_photos))
                                    }
                                }
                            }

                            if (previews.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.photo_upload_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.photo_count, previews.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(previews.size) { index ->
                                        val bitmap = previews[index]
                                        Box {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = stringResource(id = R.string.choose_photos),
                                                    modifier = Modifier
                                                        .width(104.dp)
                                                        .height(104.dp)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemovePublishImageAt(index) },
                                                modifier = Modifier.align(Alignment.TopEnd),
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.remove_photo))
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { publishDialogOpen = false },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(id = R.string.close))
                                }
                                Button(
                                    onClick = onPublishArticle,
                                    enabled = !state.isPublishingArticle,
                                    modifier = Modifier.weight(1f),
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
                            }
                        }
                    }
                }
            }
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf("") }
    val availableCities = remember(state.marketplaceItems) { state.marketplaceItems.map { it.city }.filter { it.isNotBlank() && it != "-" }.distinct().sorted() }
    val availableConditions = remember(state.marketplaceItems) { state.marketplaceItems.map { it.conditionLabel }.filter { it.isNotBlank() && it != "-" }.distinct().sorted() }
    val filteredItems = remember(state.marketplaceItems, searchQuery, selectedCity, selectedCondition) {
        state.marketplaceItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() || listOf(item.title, item.description, item.city).any {
                it.contains(searchQuery, ignoreCase = true)
            }
            val matchesCity = selectedCity.isBlank() || item.city == selectedCity
            val matchesCondition = selectedCondition.isBlank() || item.conditionLabel == selectedCondition
            matchesQuery && matchesCity && matchesCondition
        }
    }
    val listState = rememberLazyListState()
    val visibleCount = rememberProgressiveItemCount(
        totalCount = filteredItems.size,
        batchSize = 12,
        listState = listState,
        firstDataIndex = 1,
    )
    val visibleItems = remember(filteredItems, visibleCount) { filteredItems.take(visibleCount) }

    if (state.marketplaceItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_marketplace))
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(id = R.string.search_marketplace)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    text = stringResource(id = R.string.marketplace_filters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_cities),
                            selected = selectedCity.isBlank(),
                            onClick = { selectedCity = "" },
                        )
                    }
                    items(availableCities) { city ->
                        FilterChipButton(label = city, selected = selectedCity == city, onClick = { selectedCity = city })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_conditions),
                            selected = selectedCondition.isBlank(),
                            onClick = { selectedCondition = "" },
                        )
                    }
                    items(availableConditions) { condition ->
                        FilterChipButton(label = condition, selected = selectedCondition == condition, onClick = { selectedCondition = condition })
                    }
                }
                Text(
                    text = stringResource(id = R.string.showing_results, filteredItems.size, state.marketplaceItems.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filteredItems.isEmpty()) {
            item {
                EmptyTabMessage(text = stringResource(id = R.string.no_results))
            }
        }
        items(visibleItems) { item ->
            val previewImage = remember(item.previewImageBase64) { decodeBase64Image(item.previewImageBase64) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedItem = item },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(144.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${item.city}  •  ${item.priceLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(item.conditionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.postedAtLabel.isNotBlank()) {
                        Text(item.postedAtLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        if (visibleItems.size < filteredItems.size) {
            item {
                Text(
                    text = stringResource(id = R.string.loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    val details = selectedItem
    if (details != null) {
        val detailImage = remember(details.previewImageBase64) { decodeBase64Image(details.previewImageBase64) }
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(details.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (detailImage != null) {
                        Image(
                            bitmap = detailImage,
                            contentDescription = details.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
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
private fun InstructorsTab(
    state: AppUiState,
    onContactInstructor: (Int, String) -> Unit,
) {
    if (state.instructorItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_instructors))
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedInstructor by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.InstructorItem?>(null) }
    val filteredItems = remember(state.instructorItems, searchQuery) {
        state.instructorItems.filter { item ->
            searchQuery.isBlank() || item.displayName.contains(searchQuery, ignoreCase = true) || item.bio.contains(searchQuery, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()
    val visibleCount = rememberProgressiveItemCount(
        totalCount = filteredItems.size,
        batchSize = 10,
        listState = listState,
        firstDataIndex = 1,
    )
    val visibleItems = remember(filteredItems, visibleCount) { filteredItems.take(visibleCount) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(id = R.string.search_instructors)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (filteredItems.isEmpty()) {
            item { EmptyTabMessage(text = stringResource(id = R.string.no_results)) }
        }
        items(visibleItems) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            displayName = item.displayName,
                            photoBase64 = item.profilePhotoBase64,
                            photoUrl = "",
                            size = 56.dp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(id = R.string.years_experience_label, item.yearsExperience),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(item.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedInstructor = item }) {
                            Text(stringResource(id = R.string.view_details))
                        }
                        if (item.userId > 0) {
                            Button(onClick = { onContactInstructor(item.userId, item.displayName) }) {
                                Text(stringResource(id = R.string.contact_instructor))
                            }
                        }
                    }
                }
            }
        }
        if (visibleItems.size < filteredItems.size) {
            item {
                Text(
                    text = stringResource(id = R.string.loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    val details = selectedInstructor
    if (details != null) {
        Dialog(onDismissRequest = { selectedInstructor = null }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.86f),
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                displayName = details.displayName,
                                photoBase64 = details.profilePhotoBase64,
                                photoUrl = "",
                                size = 62.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(details.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    text = stringResource(id = R.string.years_experience_label, details.yearsExperience),
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (details.phone.isNotBlank()) {
                            Text("${stringResource(id = R.string.phone)}: ${details.phone}")
                        }
                        if (details.certifications.isNotBlank()) {
                            Text("${stringResource(id = R.string.certifications)}: ${details.certifications}")
                        }
                        Text(details.bio, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (details.userId > 0) {
                                Button(onClick = {
                                    selectedInstructor = null
                                    onContactInstructor(details.userId, details.displayName)
                                }) {
                                    Text(stringResource(id = R.string.contact_instructor))
                                }
                            }
                            OutlinedButton(onClick = { selectedInstructor = null }) {
                                Text(stringResource(id = R.string.close))
                            }
                        }
                    }
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

    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(state.pisteItems, searchQuery) {
        state.pisteItems.filter { item ->
            searchQuery.isBlank() || item.stationName.contains(searchQuery, ignoreCase = true) || item.weatherLabel.contains(searchQuery, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()
    val visibleCount = rememberProgressiveItemCount(
        totalCount = filteredItems.size,
        batchSize = 10,
        listState = listState,
        firstDataIndex = 1,
    )
    val visibleItems = remember(filteredItems, visibleCount) { filteredItems.take(visibleCount) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(id = R.string.search_conditions)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        items(visibleItems) { item ->
            val crowdLower = item.crowdLabel.lowercase()
            val crowdIcon = when {
                crowdLower.contains("bon") || crowdLower.contains("busy") -> "🔴"
                crowdLower.contains("peu") || crowdLower.contains("quiet") -> "🟢"
                else -> "🟡"
            }

            val weatherIcon = when {
                item.weatherLabel.contains("neige", ignoreCase = true) -> "❄"
                item.weatherLabel.contains("pluie", ignoreCase = true) -> "🌧"
                item.weatherLabel.contains("nuage", ignoreCase = true) -> "☁"
                item.weatherLabel.contains("soleil", ignoreCase = true) || item.weatherLabel.contains("clair", ignoreCase = true) -> "☀"
                else -> "🌤"
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.stationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (item.updatedAtLabel.isNotBlank()) {
                            Text(
                                text = "🕒 ${item.updatedAtLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PisteMetricPill(
                            label = stringResource(id = R.string.weather),
                            value = "${weatherIcon} ${item.weatherLabel}",
                            modifier = Modifier.weight(1f),
                        )
                        PisteMetricPill(
                            label = stringResource(id = R.string.temperature),
                            value = "🌡 ${item.temperatureLabel}°C",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PisteMetricPill(
                            label = stringResource(id = R.string.snow_depth),
                            value = "❄ ${item.snowDepthLabel} cm",
                            modifier = Modifier.weight(1f),
                        )
                        PisteMetricPill(
                            label = stringResource(id = R.string.piste_rating),
                            value = "⭐ ${item.ratingLabel}",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PisteMetricPill(
                            label = stringResource(id = R.string.altitude),
                            value = "⛰ ${item.altitudeLabel} m",
                            modifier = Modifier.weight(1f),
                        )
                        PisteMetricPill(
                            label = stringResource(id = R.string.distance_from_grenoble),
                            value = "📍 ${item.distanceLabel} km",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "$crowdIcon ${item.crowdLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (item.comment.isNotBlank() && item.comment != "-") {
                        Text(
                            text = "📝 ${item.comment}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (visibleItems.size < filteredItems.size) {
            item {
                Text(
                    text = stringResource(id = R.string.loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PisteMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun rememberProgressiveItemCount(
    totalCount: Int,
    batchSize: Int,
    listState: LazyListState,
    firstDataIndex: Int,
): Int {
    var visibleCount by remember(totalCount) { mutableStateOf(min(batchSize, totalCount)) }

    LaunchedEffect(totalCount) {
        visibleCount = min(batchSize, totalCount)
    }

    LaunchedEffect(listState, totalCount, visibleCount, firstDataIndex) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { index -> index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (visibleCount >= totalCount) return@collect
                val triggerIndex = max(firstDataIndex, firstDataIndex + visibleCount - 2)
                if (lastVisibleIndex >= triggerIndex) {
                    visibleCount = min(totalCount, visibleCount + batchSize)
                }
            }
    }

    return visibleCount
}

@Composable
private fun MessagesTab(
    state: AppUiState,
    onSelectRecipient: (Int) -> Unit,
    onAddFriend: (Int) -> Unit,
    onRemoveFriend: (Int) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val myUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    var chatSearch by remember { mutableStateOf("") }
    var addFriendDialogOpen by remember { mutableStateOf(false) }
    var addFriendSearch by remember { mutableStateOf("") }
    val friendIds = remember(state.friendLinks) { state.friendLinks.map { it.friendId }.toSet() }

    val threadSummaries = remember(state.messageItems, state.chatUsers, myUserId) {
        val map = linkedMapOf<Int, ChatThreadSummary>()

        state.messageItems.forEach { item ->
            val outgoing = myUserId > 0 && item.senderId == myUserId
            val otherId = if (outgoing) item.recipientId else item.senderId
            if (otherId <= 0 || otherId == myUserId) return@forEach

            val label = if (outgoing) item.recipientLabel else item.senderLabel
            val photoBase64 = if (outgoing) item.recipientPhotoBase64 else item.senderPhotoBase64
            val photoUrl = if (outgoing) item.recipientPhotoUrl else item.senderPhotoUrl
            val unreadIncrement = if (!outgoing && !item.isRead) 1 else 0

            val existing = map[otherId]
            if (existing == null) {
                map[otherId] = ChatThreadSummary(
                    userId = otherId,
                    label = label.ifBlank { "Utilisateur #$otherId" },
                    photoBase64 = photoBase64,
                    photoUrl = photoUrl,
                    lastMessage = item.body,
                    lastDateLabel = item.createdAtLabel,
                    unreadCount = unreadIncrement,
                )
            } else {
                map[otherId] = existing.copy(unreadCount = existing.unreadCount + unreadIncrement)
            }
        }

        state.chatUsers.forEach { user ->
            if (user.id <= 0 || user.id == myUserId) return@forEach
            val existing = map[user.id]
            if (existing == null) {
                map[user.id] = ChatThreadSummary(
                    userId = user.id,
                    label = user.label,
                    photoBase64 = user.photoBase64,
                    photoUrl = user.photoUrl,
                    lastMessage = "",
                    lastDateLabel = "",
                    unreadCount = 0,
                )
            } else if (existing.photoBase64.isBlank() && existing.photoUrl.isBlank()) {
                map[user.id] = existing.copy(
                    label = existing.label.ifBlank { user.label },
                    photoBase64 = user.photoBase64,
                    photoUrl = user.photoUrl,
                )
            }
        }

        map.values.toList()
    }

    val friendThreads = remember(threadSummaries, friendIds) {
        threadSummaries.filter { friendIds.contains(it.userId) }
    }

    val filteredThreads = remember(friendThreads, chatSearch) {
        friendThreads.filter {
            chatSearch.isBlank() || it.label.contains(chatSearch, ignoreCase = true) || it.lastMessage.contains(chatSearch, ignoreCase = true)
        }
    }

    val selectedRecipientId = state.messageRecipientId ?: filteredThreads.firstOrNull()?.userId ?: friendThreads.firstOrNull()?.userId
    val selectedThread = threadSummaries.firstOrNull { it.userId == selectedRecipientId }
        ?: state.chatUsers.firstOrNull { it.id == selectedRecipientId }?.let { user ->
            ChatThreadSummary(
                userId = user.id,
                label = user.label,
                photoBase64 = user.photoBase64,
                photoUrl = user.photoUrl,
                lastMessage = "",
                lastDateLabel = "",
                unreadCount = 0,
            )
        }

    LaunchedEffect(selectedRecipientId) {
        if (selectedRecipientId != null && selectedRecipientId != state.messageRecipientId) {
            onSelectRecipient(selectedRecipientId)
        }
    }

    val recipientOptions = remember(state.chatUsers, addFriendSearch, myUserId, friendIds) {
        state.chatUsers.filter { option ->
            option.id != myUserId &&
                !friendIds.contains(option.id) &&
                (addFriendSearch.length >= 2 && option.label.contains(addFriendSearch, ignoreCase = true))
        }
    }

    val orderedMessages = remember(state.messageItems, selectedRecipientId, myUserId) {
        state.messageItems.asReversed().filter { item ->
            selectedRecipientId != null && (
                (item.senderId == selectedRecipientId && item.recipientId == myUserId) ||
                    (item.senderId == myUserId && item.recipientId == selectedRecipientId)
                )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chatSearch,
                    onValueChange = { chatSearch = it },
                    label = { Text(stringResource(id = R.string.search_contacts)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { addFriendDialogOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.add_friend))
                }

                Text(
                    text = stringResource(id = R.string.friends),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredThreads.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.no_contacts_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredThreads) { thread ->
                            val selected = thread.userId == selectedRecipientId
                            OutlinedButton(
                                onClick = { onSelectRecipient(thread.userId) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    UserAvatar(
                                        displayName = thread.label,
                                        photoBase64 = thread.photoBase64,
                                        photoUrl = thread.photoUrl,
                                        size = 34.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            thread.label,
                                            maxLines = 1,
                                            fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                                        )
                                        if (thread.lastMessage.isNotBlank()) {
                                            Text(
                                                text = thread.lastMessage,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (thread.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (thread.unreadCount > 0) {
                                        Badge { Text(thread.unreadCount.toString()) }
                                    }
                                    TextButton(onClick = { onRemoveFriend(thread.userId) }) {
                                        Text(stringResource(id = R.string.remove_friend))
                                    }
                                    if (selected) {
                                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedRecipientId == null) {
                EmptyTabMessage(text = stringResource(id = R.string.choose_contact_first))
            } else if (orderedMessages.isEmpty()) {
                EmptyTabMessage(text = stringResource(id = R.string.empty_messages))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
                                            text = if (mine) stringResource(id = R.string.you) else item.senderLabel,
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
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = selectedThread?.label ?: stringResource(id = R.string.choose_contact_first),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    enabled = !state.isSendingMessage && selectedRecipientId != null,
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

        if (addFriendDialogOpen) {
            AlertDialog(
                onDismissRequest = { addFriendDialogOpen = false },
                title = { Text(stringResource(id = R.string.add_friend)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addFriendSearch,
                            onValueChange = { addFriendSearch = it },
                            label = { Text(stringResource(id = R.string.search_users_to_add)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (addFriendSearch.length < 2) {
                            Text(stringResource(id = R.string.type_min_two_chars))
                        } else if (recipientOptions.isEmpty()) {
                            Text(stringResource(id = R.string.no_contacts_available))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(220.dp)) {
                                items(recipientOptions) { option ->
                                    OutlinedButton(
                                        onClick = {
                                            onAddFriend(option.id)
                                            onSelectRecipient(option.id)
                                            addFriendDialogOpen = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            UserAvatar(
                                                displayName = option.label,
                                                photoBase64 = option.photoBase64,
                                                photoUrl = option.photoUrl,
                                                size = 34.dp,
                                            )
                                            Text(option.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { addFriendDialogOpen = false }) {
                        Text(stringResource(id = R.string.close))
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileTab(
    state: AppUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onChangePassword: () -> Unit,
) {
    val profile = state.profileInfo
    if (profile == null) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_profile))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(profile = profile, displayName = profile.displayName)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(profile.email, style = MaterialTheme.typography.bodyMedium)
                        if (profile.username.isNotBlank()) {
                            Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.personal_information),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = state.profileEditFirstName,
                        onValueChange = onFirstNameChange,
                        label = { Text(stringResource(id = R.string.first_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.profileEditLastName,
                        onValueChange = onLastNameChange,
                        label = { Text(stringResource(id = R.string.last_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.profileEditEmail,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(id = R.string.email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onSaveProfile,
                        enabled = !state.isSavingProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSavingProfile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(id = R.string.save_profile))
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.change_password),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = state.currentPasswordInput,
                        onValueChange = onCurrentPasswordChange,
                        label = { Text(stringResource(id = R.string.current_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.newPasswordInput,
                        onValueChange = onNewPasswordChange,
                        label = { Text(stringResource(id = R.string.new_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.confirmNewPasswordInput,
                        onValueChange = onConfirmNewPasswordChange,
                        label = { Text(stringResource(id = R.string.confirm_new_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onChangePassword,
                        enabled = !state.isChangingPassword,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isChangingPassword) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(id = R.string.update_password))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ProfileAvatar(
    profile: fr.grenobleski.nativeapp.data.model.ProfileInfo,
    displayName: String,
) {
    UserAvatar(
        displayName = displayName,
        photoBase64 = profile.profilePictureBase64,
        photoUrl = profile.googleProfilePictureUrl,
        size = 72.dp,
    )
}

@Composable
private fun UserAvatar(
    displayName: String,
    photoBase64: String,
    photoUrl: String,
    size: Dp,
) {
    val base64Image = remember(photoBase64) { decodeBase64Image(photoBase64) }
    var remoteImage by remember(photoUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUrl) {
        remoteImage = loadImageFromUrl(photoUrl)
    }

    val avatarBitmap = base64Image ?: remoteImage
    if (avatarBitmap != null) {
        Image(
            bitmap = avatarBitmap,
            contentDescription = displayName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(999.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName
                    .split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString("")
                    .take(2)
                    .ifBlank { "GS" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
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

private fun decodeBase64Image(data: String): ImageBitmap? {
    if (data.isBlank()) return null
    return try {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private suspend fun loadImageFromUrl(url: String): ImageBitmap? {
    if (url.isBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }.also {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun uriToBase64(context: Context, uri: Uri?): String? {
    uri ?: return null
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        }
    } catch (_: Exception) {
        null
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
