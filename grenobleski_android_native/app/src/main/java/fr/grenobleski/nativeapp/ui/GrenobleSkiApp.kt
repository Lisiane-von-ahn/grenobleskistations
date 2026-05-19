package fr.grenobleski.nativeapp.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.ads.MaxAdView
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.AppViewModel
import fr.grenobleski.nativeapp.AppViewModelFactory
import fr.grenobleski.nativeapp.BuildConfig
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.AuthRepository
import fr.grenobleski.nativeapp.data.model.PisteItem
import fr.grenobleski.nativeapp.data.model.NativeTab
import fr.grenobleski.nativeapp.data.network.GrenobleSkiApiClient
import fr.grenobleski.nativeapp.data.session.SessionStore
import fr.grenobleski.nativeapp.ui.components.MetricCard
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import android.graphics.BitmapFactory
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private object MarketplaceBitmapCache {
    // Keep a bounded in-memory bitmap cache to avoid repetitive decode spikes.
    private val maxKb = 16 * 1024
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

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

private val RHONE_ALPES_CITIES = listOf(
    "Grenoble",
    "Lyon",
    "Annecy",
    "Chambery",
    "Aix-les-Bains",
    "Albertville",
    "Annemasse",
    "Bourg-en-Bresse",
    "Chamonix",
    "Cluses",
    "Echirolles",
    "Evian-les-Bains",
    "Fontaine",
    "Meylan",
    "Romans-sur-Isere",
    "Saint-Etienne",
    "Sallanches",
    "Seynod",
    "Thonon-les-Bains",
    "Valence",
    "Vienne",
    "Villeurbanne",
    "Voiron",
)

private data class BottomNavItem(
    val action: BottomNavAction,
    val labelRes: Int,
    val icon: ImageVector,
)

private data class MoreMenuAction(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val action: () -> Unit,
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
    adsEnabled: Boolean = false,
    showAdsConsentPrompt: Boolean = false,
    onAcceptAdsConsent: (() -> Unit)? = null,
    onRejectAdsConsent: (() -> Unit)? = null,
    onOpenAdsPreferences: (() -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChange: (String) -> Unit = {},
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
            onRegisterTermsAcceptedChange = viewModel::updateRegisterTermsAccepted,
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
            showMobileAds = adsEnabled,
            adBannerUnitId = BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID,
            onOpenAdsPreferences = onOpenAdsPreferences,
            onDismissError = viewModel::clearError,
            onDismissStatus = viewModel::clearStatusMessage,
            onSelectTab = viewModel::selectTab,
            onRefresh = viewModel::refreshCurrentTab,
            onLogout = viewModel::logout,
            onPrepareMessageToSeller = viewModel::prepareMessageToSeller,
            onSelectMessageRecipient = viewModel::selectMessageRecipient,
            onAddFriend = viewModel::addFriend,
            onRemoveFriend = viewModel::removeFriend,
            onAcceptFriendInvitation = viewModel::acceptFriendInvitation,
            onDeclineFriendInvitation = viewModel::declineFriendInvitation,
            onCancelFriendInvitation = viewModel::cancelFriendInvitation,
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
            onUpdatePublishMaterialType = viewModel::updatePublishMaterialType,
            onUpdatePublishTransactionType = viewModel::updatePublishTransactionType,
            onAppendPublishImages = viewModel::appendPublishImagesBase64,
            onRemovePublishImageAt = viewModel::removePublishImageAt,
            onClearPublishImages = viewModel::clearPublishImages,
            onPublishArticle = viewModel::publishArticle,
            onUpdatePartnerTitle = viewModel::updatePublishPartnerTitle,
            onUpdatePartnerMessage = viewModel::updatePublishPartnerMessage,
            onUpdatePartnerCity = viewModel::updatePublishPartnerCity,
            onUpdatePartnerLevel = viewModel::updatePublishPartnerLevel,
            onUpdatePartnerDate = viewModel::updatePublishPartnerDate,
            onPublishPartnerPost = viewModel::publishPartnerPost,
            onRequestCarpoolReservation = viewModel::requestCarpoolReservation,
            onCancelCarpoolReservation = viewModel::cancelCarpoolReservation,
            onApproveCarpoolReservation = viewModel::approveCarpoolReservation,
            onRejectCarpoolReservation = viewModel::rejectCarpoolReservation,
            onSubmitSellerRating = viewModel::submitSellerRating,
            onSubmitStationRating = viewModel::submitStationRating,
            onStoriesSearchChange = viewModel::updateStoriesSearchQuery,
            onStoriesStationFilterChange = viewModel::updateStoriesStationFilter,
            onApplyStoriesFilters = viewModel::applyStoriesFilters,
            onLoadMoreStories = viewModel::loadMoreStories,
            onLoadMoreMarketplace = viewModel::loadMoreMarketplace,
            onToggleStoryLike = viewModel::toggleStoryLike,
            onStoryComment = viewModel::addStoryComment,
            onOpenUserActivity = viewModel::openUserActivity,
            onCloseUserActivity = viewModel::closeUserActivity,
            currentLanguage = currentLanguage,
            onLanguageChange = onLanguageChange,
        )
    }

    if (showAdsConsentPrompt && onAcceptAdsConsent != null && onRejectAdsConsent != null) {
        CenteredPanelDialog(
            title = stringResource(id = R.string.ads_consent_title),
            onDismiss = onRejectAdsConsent,
        ) {
            Text(
                text = stringResource(id = R.string.ads_consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onRejectAdsConsent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.ads_consent_reject))
                }
                Button(
                    onClick = onAcceptAdsConsent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.ads_consent_accept))
                }
            }
        }
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
    onRegisterTermsAcceptedChange: (Boolean) -> Unit,
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = state.registerTermsAccepted,
                                onCheckedChange = onRegisterTermsAcceptedChange,
                            )
                            Text(
                                text = stringResource(id = R.string.register_accept_terms_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = onOpenTerms) { Text(stringResource(id = R.string.terms)) }
                            TextButton(onClick = onOpenPrivacy) { Text(stringResource(id = R.string.privacy)) }
                        }
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

                    if (!state.isRegisterMode) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(
    state: AppUiState,
    siteBase: String,
    showMobileAds: Boolean,
    adBannerUnitId: String,
    onOpenAdsPreferences: (() -> Unit)?,
    onDismissError: () -> Unit,
    onDismissStatus: () -> Unit,
    onSelectTab: (NativeTab) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onPrepareMessageToSeller: (Int, String) -> Unit,
    onSelectMessageRecipient: (Int) -> Unit,
    onAddFriend: (Int) -> Unit,
    onRemoveFriend: (Int) -> Unit,
    onAcceptFriendInvitation: (Int) -> Unit,
    onDeclineFriendInvitation: (Int) -> Unit,
    onCancelFriendInvitation: (Int) -> Unit,
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
    onUpdatePublishMaterialType: (String) -> Unit,
    onUpdatePublishTransactionType: (String) -> Unit,
    onAppendPublishImages: (List<String>) -> Unit,
    onRemovePublishImageAt: (Int) -> Unit,
    onClearPublishImages: () -> Unit,
    onPublishArticle: () -> Unit,
    onUpdatePartnerTitle: (String) -> Unit,
    onUpdatePartnerMessage: (String) -> Unit,
    onUpdatePartnerCity: (String) -> Unit,
    onUpdatePartnerLevel: (String) -> Unit,
    onUpdatePartnerDate: (String) -> Unit,
    onPublishPartnerPost: () -> Unit,
    onRequestCarpoolReservation: (Int, Int) -> Unit,
    onCancelCarpoolReservation: (Int) -> Unit,
    onApproveCarpoolReservation: (Int, Int) -> Unit,
    onRejectCarpoolReservation: (Int, Int) -> Unit,
    onSubmitSellerRating: (Int, Int, Int, String) -> Unit,
    onSubmitStationRating: (Int, Int, String) -> Unit,
    onStoriesSearchChange: (String) -> Unit,
    onStoriesStationFilterChange: (Int?) -> Unit,
    onApplyStoriesFilters: () -> Unit,
    onLoadMoreStories: () -> Unit,
    onLoadMoreMarketplace: () -> Unit,
    onToggleStoryLike: (Int, Boolean) -> Unit,
    onStoryComment: (Int, String) -> Unit,
    onOpenUserActivity: (Int) -> Unit,
    onCloseUserActivity: () -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    var moreMenuOpen by remember { mutableStateOf(false) }
    var publishDialogOpen by remember { mutableStateOf(false) }
    var publishPartnerDialogOpen by remember { mutableStateOf(false) }
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

    LaunchedEffect(
        state.isPublishingPartner,
        state.publishPartnerTitle,
        state.publishPartnerMessage,
        state.publishPartnerCity,
        state.selectedTab,
    ) {
        if (
            publishPartnerDialogOpen &&
            !state.isPublishingPartner &&
            state.selectedTab == NativeTab.PARTNERS &&
            state.publishPartnerTitle.isBlank() &&
            state.publishPartnerMessage.isBlank() &&
            state.publishPartnerCity.isBlank()
        ) {
            publishPartnerDialogOpen = false
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
            when (state.selectedTab) {
                NativeTab.MARKETPLACE -> {
                    ExtendedFloatingActionButton(
                        onClick = { publishDialogOpen = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.publish_article)) },
                        text = { Text(stringResource(id = R.string.publish_article)) },
                    )
                }
                NativeTab.PARTNERS -> {
                    ExtendedFloatingActionButton(
                        onClick = { publishPartnerDialogOpen = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.publish_partner_post)) },
                        text = { Text(stringResource(id = R.string.publish_partner_post)) },
                    )
                }
                else -> {
                    // Keep other tabs visually clean: navigation is available from bottom bar + More panel.
                }
            }
        },
        bottomBar = {
            Column {
                if (showMobileAds && adBannerUnitId.isNotBlank()) {
                    MobileBannerAd(adUnitId = adBannerUnitId)
                }

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
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.selectedTab) {
                NativeTab.HOME -> HomeTab(
                    state = state,
                    onOpenStations = { onSelectTab(NativeTab.STATIONS) },
                    onOpenMarketplace = { onSelectTab(NativeTab.MARKETPLACE) },
                    onOpenBusLines = { onSelectTab(NativeTab.BUS_LINES) },
                    onOpenServices = { onSelectTab(NativeTab.SERVICES) },
                    onOpenCarpool = { onSelectTab(NativeTab.PARTNERS) },
                    onOpenStories = { onSelectTab(NativeTab.STORIES) },
                    onOpenCommunity = { onSelectTab(NativeTab.COMMUNITY) },
                    onOpenUrl = { url -> openExternalUrl(localContext, url) },
                )
                NativeTab.STORIES -> StoriesTab(
                    state = state,
                    onStoriesSearchChange = onStoriesSearchChange,
                    onStoriesStationFilterChange = onStoriesStationFilterChange,
                    onApplyStoriesFilters = onApplyStoriesFilters,
                    onLoadMoreStories = onLoadMoreStories,
                    onToggleStoryLike = onToggleStoryLike,
                    onStoryComment = onStoryComment,
                    onOpenUserActivity = onOpenUserActivity,
                    onAddFriend = onAddFriend,
                )
                NativeTab.COMMUNITY -> CommunityDashboardTab(state = state)
                NativeTab.STATIONS -> StationsTab(
                    state = state,
                    onSubmitStationRating = onSubmitStationRating,
                    onOpenUrl = { url -> openExternalUrl(localContext, url) },
                )
                NativeTab.BUS_LINES -> BusLinesTab(state)
                NativeTab.SERVICES -> ServicesTab(
                    state = state,
                    onOpenUrl = { url -> openExternalUrl(localContext, url) },
                )
                NativeTab.MARKETPLACE -> MarketplaceTab(
                    state = state,
                    siteBase = siteBase,
                    onPrepareMessageToSeller = onPrepareMessageToSeller,
                    onSubmitSellerRating = onSubmitSellerRating,
                    onOpenPublishMarketplace = { publishDialogOpen = true },
                    onLoadMoreMarketplace = onLoadMoreMarketplace,
                )
                NativeTab.PARTNERS -> PartnersTab(
                    state = state,
                    currentUserId = currentUserId,
                    onPrepareMessageToPartner = onPrepareMessageToSeller,
                    onOpenPublishPartner = { publishPartnerDialogOpen = true },
                    onRequestCarpoolReservation = onRequestCarpoolReservation,
                    onCancelCarpoolReservation = onCancelCarpoolReservation,
                    onApproveCarpoolReservation = onApproveCarpoolReservation,
                    onRejectCarpoolReservation = onRejectCarpoolReservation,
                )
                NativeTab.INSTRUCTORS -> InstructorsTab(state, onPrepareMessageToSeller)
                NativeTab.PISTES -> PistesTab(
                    state = state,
                    onOpenUrl = { url -> openExternalUrl(localContext, url) },
                )
                NativeTab.MESSAGES -> MessagesTab(
                    state = state,
                    onSelectRecipient = onSelectMessageRecipient,
                    onAddFriend = onAddFriend,
                    onRemoveFriend = onRemoveFriend,
                    onAcceptFriendInvitation = onAcceptFriendInvitation,
                    onDeclineFriendInvitation = onDeclineFriendInvitation,
                    onCancelFriendInvitation = onCancelFriendInvitation,
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
                    currentLanguage = currentLanguage,
                    onLanguageChange = onLanguageChange,
                )
            }

        }

        if (!state.errorMessage.isNullOrBlank()) {
            CenteredAlertPopup(
                title = "Alert",
                message = state.errorMessage,
                isError = true,
                onDismiss = onDismissError,
            )
        } else if (!state.statusMessage.isNullOrBlank()) {
            CenteredAlertPopup(
                title = "Success",
                message = state.statusMessage,
                isError = false,
                onDismiss = onDismissStatus,
            )
        }

        if (moreMenuOpen) {
            CenteredPanelDialog(
                title = stringResource(id = R.string.nav_more),
                onDismiss = { moreMenuOpen = false },
            ) {
                var compactMoreMode by rememberSaveable { mutableStateOf(true) }
                var exploreExpanded by rememberSaveable { mutableStateOf(true) }
                var createExpanded by rememberSaveable { mutableStateOf(true) }
                var accountExpanded by rememberSaveable { mutableStateOf(false) }

                val exploreActions = listOf(
                    MoreMenuAction(
                        label = stringResource(id = R.string.community_dashboard),
                        shortLabel = stringResource(id = R.string.menu_short_community),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        action = { onSelectTab(NativeTab.COMMUNITY) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.stories),
                        shortLabel = stringResource(id = R.string.menu_short_stories),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        action = { onSelectTab(NativeTab.STORIES) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.stations),
                        shortLabel = stringResource(id = R.string.menu_short_stations),
                        icon = Icons.Filled.Terrain,
                        action = { onSelectTab(NativeTab.STATIONS) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.bus_lines),
                        shortLabel = stringResource(id = R.string.menu_short_bus),
                        icon = Icons.Filled.Terrain,
                        action = { onSelectTab(NativeTab.BUS_LINES) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.services),
                        shortLabel = stringResource(id = R.string.menu_short_services),
                        icon = Icons.Filled.Storefront,
                        action = { onSelectTab(NativeTab.SERVICES) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.piste_status),
                        shortLabel = stringResource(id = R.string.menu_short_pistes),
                        icon = Icons.Filled.Terrain,
                        action = { onSelectTab(NativeTab.PISTES) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.instructors),
                        shortLabel = stringResource(id = R.string.menu_short_instructors),
                        icon = Icons.Filled.School,
                        action = { onSelectTab(NativeTab.INSTRUCTORS) },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.adventure_partners),
                        shortLabel = stringResource(id = R.string.menu_short_partners),
                        icon = Icons.Filled.School,
                        action = { onSelectTab(NativeTab.PARTNERS) },
                    ),
                )
                val createActions = listOf(
                    MoreMenuAction(
                        label = stringResource(id = R.string.publish_article),
                        shortLabel = stringResource(id = R.string.menu_short_article),
                        icon = Icons.Filled.LocalOffer,
                        action = { publishDialogOpen = true },
                    ),
                    MoreMenuAction(
                        label = stringResource(id = R.string.publish_partner_post),
                        shortLabel = stringResource(id = R.string.menu_short_partner_post),
                        icon = Icons.Filled.School,
                        action = { publishPartnerDialogOpen = true },
                    ),
                )
                val accountActions = buildList<MoreMenuAction> {
                    add(
                        MoreMenuAction(
                            label = stringResource(id = R.string.profile),
                            shortLabel = stringResource(id = R.string.menu_short_profile),
                            icon = Icons.Filled.Person,
                            action = { onSelectTab(NativeTab.PROFILE) },
                        )
                    )
                    add(
                        MoreMenuAction(
                            label = stringResource(id = R.string.terms),
                            shortLabel = stringResource(id = R.string.menu_short_terms),
                            icon = Icons.Filled.MoreHoriz,
                            action = { openExternalUrl(localContext, "$siteBase/terms/") },
                        )
                    )
                    add(
                        MoreMenuAction(
                            label = stringResource(id = R.string.privacy),
                            shortLabel = stringResource(id = R.string.menu_short_privacy),
                            icon = Icons.Filled.MoreHoriz,
                            action = { openExternalUrl(localContext, "$siteBase/privacy/") },
                        )
                    )
                    if (onOpenAdsPreferences != null) {
                        add(
                            MoreMenuAction(
                                label = stringResource(id = R.string.ad_preferences),
                                shortLabel = stringResource(id = R.string.menu_short_ads),
                                icon = Icons.Filled.MoreHoriz,
                                action = { onOpenAdsPreferences.invoke() },
                            )
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { compactMoreMode = !compactMoreMode },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (compactMoreMode) Icons.Filled.CheckCircle else Icons.Filled.MoreHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (compactMoreMode) {
                                stringResource(id = R.string.menu_compact_on)
                            } else {
                                stringResource(id = R.string.menu_compact_off)
                            }
                        )
                    }

                    CollapsibleMoreSection(
                        title = stringResource(id = R.string.menu_explore),
                        expanded = exploreExpanded,
                        onToggle = { exploreExpanded = !exploreExpanded },
                    ) {
                        MoreActionsGrid(
                            actions = exploreActions,
                            compactMode = compactMoreMode,
                            onActionClick = { action ->
                                moreMenuOpen = false
                                action.action()
                            },
                        )
                    }

                    CollapsibleMoreSection(
                        title = stringResource(id = R.string.menu_create),
                        expanded = createExpanded,
                        onToggle = { createExpanded = !createExpanded },
                    ) {
                        MoreActionsGrid(
                            actions = createActions,
                            compactMode = compactMoreMode,
                            onActionClick = { action ->
                                moreMenuOpen = false
                                action.action()
                            },
                        )
                    }

                    CollapsibleMoreSection(
                        title = stringResource(id = R.string.menu_account),
                        expanded = accountExpanded,
                        onToggle = { accountExpanded = !accountExpanded },
                    ) {
                        MoreActionsGrid(
                            actions = accountActions,
                            compactMode = compactMoreMode,
                            onActionClick = { action ->
                                moreMenuOpen = false
                                action.action()
                            },
                        )
                    }

                    OutlinedButton(
                        onClick = { moreMenuOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            }
        }

        if (publishDialogOpen) {
            Dialog(onDismissRequest = { publishDialogOpen = false }) {
                val previews = remember(state.publishImagesBase64) {
                    state.publishImagesBase64.map { decodeBase64Image(it) }
                }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 760.dp),
                    ) {
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
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val materialOptions = listOf(
                                "ski" to stringResource(id = R.string.market_type_ski),
                                "service" to stringResource(id = R.string.market_type_service),
                                "transport" to stringResource(id = R.string.market_type_transport),
                                "accommodation" to stringResource(id = R.string.market_type_accommodation),
                                "other" to stringResource(id = R.string.market_type_other),
                            )
                            val transactionOptions = listOf(
                                "sale" to stringResource(id = R.string.offer_sale),
                                "rent" to stringResource(id = R.string.offer_rent),
                                "lend" to stringResource(id = R.string.offer_lend),
                                "service" to stringResource(id = R.string.offer_service),
                            )

                            val citySuggestions = remember(state.publishCity) {
                                val query = state.publishCity.trim()
                                if (query.isBlank()) {
                                    RHONE_ALPES_CITIES.take(8)
                                } else {
                                    RHONE_ALPES_CITIES.filter { city ->
                                        city.contains(query, ignoreCase = true)
                                    }.take(8)
                                }
                            }

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
                            Text(
                                text = stringResource(id = R.string.marketplace_category),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(materialOptions) { option ->
                                    FilterChipButton(
                                        label = option.second,
                                        selected = state.publishMaterialType == option.first,
                                        onClick = { onUpdatePublishMaterialType(option.first) },
                                    )
                                }
                            }
                            Text(
                                text = stringResource(id = R.string.marketplace_offer_type),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(transactionOptions) { option ->
                                    FilterChipButton(
                                        label = option.second,
                                        selected = state.publishTransactionType == option.first,
                                        onClick = { onUpdatePublishTransactionType(option.first) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.publishCity,
                                onValueChange = onUpdatePublishCity,
                                label = { Text(stringResource(id = R.string.city)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (citySuggestions.isNotEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.city_autocomplete_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(citySuggestions) { city ->
                                        FilterChipButton(
                                            label = city,
                                            selected = state.publishCity.equals(city, ignoreCase = true),
                                            onClick = { onUpdatePublishCity(city) },
                                        )
                                    }
                                }
                            }
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

        if (publishPartnerDialogOpen) {
            Dialog(onDismissRequest = { publishPartnerDialogOpen = false }) {
                val levelOptions = listOf(
                    "beginner" to stringResource(id = R.string.partner_level_beginner),
                    "intermediate" to stringResource(id = R.string.partner_level_intermediate),
                    "advanced" to stringResource(id = R.string.partner_level_advanced),
                )
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
                                            Color(0xFF1E3A8A),
                                            Color(0xFF0F766E),
                                        )
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(id = R.string.publish_partner_post),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(id = R.string.partner_post_subtitle),
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
                                value = state.publishPartnerTitle,
                                onValueChange = onUpdatePartnerTitle,
                                label = { Text(stringResource(id = R.string.partner_post_title)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = state.publishPartnerMessage,
                                onValueChange = onUpdatePartnerMessage,
                                label = { Text(stringResource(id = R.string.partner_post_message)) },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = state.publishPartnerCity,
                                onValueChange = onUpdatePartnerCity,
                                label = { Text(stringResource(id = R.string.city)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(id = R.string.partner_skill_level),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(levelOptions) { option ->
                                    FilterChipButton(
                                        label = option.second,
                                        selected = state.publishPartnerLevel == option.first,
                                        onClick = { onUpdatePartnerLevel(option.first) },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.publishPartnerDate,
                                onValueChange = onUpdatePartnerDate,
                                label = { Text(stringResource(id = R.string.partner_preferred_date)) },
                                placeholder = { Text("2026-03-30") },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { publishPartnerDialogOpen = false },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(id = R.string.close))
                                }
                                Button(
                                    onClick = onPublishPartnerPost,
                                    enabled = !state.isPublishingPartner,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (state.isPublishingPartner) {
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

        val selectedActivity = state.selectedUserActivity
        if (selectedActivity != null) {
            CenteredPanelDialog(
                title = stringResource(id = R.string.user_public_activity),
                onDismiss = onCloseUserActivity,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedActivity.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("@${selectedActivity.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedActivity.organizationName.isNotBlank()) {
                        Text(selectedActivity.organizationName, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Stories: ${selectedActivity.storiesCount} • Comments: ${selectedActivity.commentsCount}")
                    Text("Public messages: ${selectedActivity.publicMessagesCount} • Friends: ${selectedActivity.friendsCount}")
                    if (selectedActivity.recentStoryCaptions.isNotEmpty()) {
                        Text(stringResource(id = R.string.recent_stories), fontWeight = FontWeight.SemiBold)
                        selectedActivity.recentStoryCaptions.take(5).forEach { caption ->
                            Text("• $caption", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (selectedActivity.recentComments.isNotEmpty()) {
                        Text(stringResource(id = R.string.recent_comments), fontWeight = FontWeight.SemiBold)
                        selectedActivity.recentComments.take(5).forEach { body ->
                            Text("• $body", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val viewerUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
                    if (selectedActivity.userId > 0 && selectedActivity.userId != viewerUserId) {
                        OutlinedButton(
                            onClick = { onAddFriend(selectedActivity.userId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.add_friend))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredAlertPopup(
    title: String,
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                                    if (isError) Color(0xFFB42318) else Color(0xFF0F766E),
                                    if (isError) Color(0xFFEF4444) else Color(0xFF22C55E),
                                )
                            )
                        )
                        .padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(id = R.string.close),
                                tint = Color.White,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileBannerAd(adUnitId: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            val activity = context as? Activity
            if (activity == null) {
                FrameLayout(context)
            } else {
                MaxAdView(adUnitId, MaxAdFormat.BANNER, activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                    loadAd()
                }
            }
        },
    )
}

@Composable
private fun HomeTab(
    state: AppUiState,
    onOpenStations: () -> Unit,
    onOpenMarketplace: () -> Unit,
    onOpenBusLines: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenCarpool: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val xpInLevel = state.xpPoints % 100
    val xpProgress = xpInLevel / 100f
    val listState = rememberLazyListState()
    val latestMarketplaceItems = remember(state.marketplaceItems) {
        state.marketplaceItems.sortedByDescending { it.id }.take(4)
    }

    LazyColumn(
        state = listState,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(id = R.string.home_signature_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(id = R.string.home_signature_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.dashboard_welcome),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UserAvatar(
                            displayName = state.profileInfo?.displayName?.ifBlank { state.session?.displayName.orEmpty() }
                                ?: state.session?.displayName.orEmpty(),
                            photoBase64 = state.profileInfo?.profilePictureBase64.orEmpty(),
                            photoUrl = state.profileInfo?.googleProfilePictureUrl.orEmpty(),
                            size = 40.dp,
                        )
                        Text(
                            text = state.session?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.dashboard_subtitle_premium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(progress = { xpProgress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(id = R.string.gamification_next_level_hint, 100 - xpInLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(id = R.string.highlighted_stories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.highlightedStoryItems) { story ->
                    val preview = remember(story.imageBase64) { decodeBase64Image(story.imageBase64) }
                    Card(modifier = Modifier.width(220.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (preview != null) {
                                Image(
                                    bitmap = preview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(128.dp)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                )
                            }
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(story.userLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = story.caption.ifBlank { stringResource(id = R.string.story_no_caption) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                )
                                Text(
                                    text = "${story.stationName} • ${story.likeCount} ♥ • ${story.commentCount} 💬",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenStories, modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.stories))
                }
                OutlinedButton(onClick = onOpenCommunity, modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.community_dashboard))
                }
            }
        }

        if (state.highlightedSkiNewsItems.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.highlighted_ski_news),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.highlightedSkiNewsItems) { news ->
                        Card(modifier = Modifier.width(280.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(news.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3)
                                Text(
                                    text = "${news.sourceName} • ${news.publishedAtLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (news.summary.isNotBlank()) {
                                    Text(news.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                                OutlinedButton(onClick = { onOpenUrl(news.link) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(id = R.string.open_news))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(id = R.string.stations), state.dashboardCounts.stations, modifier = Modifier.weight(1f), onClick = onOpenStations)
                MetricCard(stringResource(id = R.string.bus_lines), state.dashboardCounts.busLines, modifier = Modifier.weight(1f), onClick = onOpenBusLines)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(id = R.string.services), state.dashboardCounts.services, modifier = Modifier.weight(1f), onClick = onOpenServices)
                MetricCard(stringResource(id = R.string.marketplace), state.dashboardCounts.marketplace, modifier = Modifier.weight(1f), onClick = onOpenMarketplace)
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(id = R.string.home_latest_marketplace), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (latestMarketplaceItems.isEmpty()) {
                        Text(text = stringResource(id = R.string.empty_marketplace), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        latestMarketplaceItems.forEach { item ->
                            Text("${item.title} • ${item.city} • ${item.priceLabel}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = onOpenCarpool, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.carpool))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoriesTab(
    state: AppUiState,
    onStoriesSearchChange: (String) -> Unit,
    onStoriesStationFilterChange: (Int?) -> Unit,
    onApplyStoriesFilters: () -> Unit,
    onLoadMoreStories: () -> Unit,
    onToggleStoryLike: (Int, Boolean) -> Unit,
    onStoryComment: (Int, String) -> Unit,
    onOpenUserActivity: (Int) -> Unit,
    onAddFriend: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val commentDrafts = remember { mutableStateMapOf<Int, String>() }
    val stationOptions = remember(state.stationItems) { state.stationItems.sortedBy { it.name } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(id = R.string.story_filters), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.storiesSearchQuery,
                        onValueChange = onStoriesSearchChange,
                        label = { Text(stringResource(id = R.string.story_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(
                                onClick = { onStoriesStationFilterChange(null) },
                                label = { Text(stringResource(id = R.string.all_stations)) },
                            )
                        }
                        items(stationOptions.take(20)) { station ->
                            AssistChip(
                                onClick = { onStoriesStationFilterChange(station.id) },
                                label = { Text(station.name) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApplyStoriesFilters, modifier = Modifier.weight(1f)) { Text(stringResource(id = R.string.apply_filters)) }
                        OutlinedButton(onClick = {
                            onStoriesSearchChange("")
                            onStoriesStationFilterChange(null)
                            onApplyStoriesFilters()
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(id = R.string.clear_filters)) }
                    }
                }
            }
        }

        items(state.storyItems) { story ->
            val previewImage = remember(story.imageBase64) { decodeBase64Image(story.imageBase64) }
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onOpenUserActivity(story.userId) }) { Text(story.userLabel, fontWeight = FontWeight.SemiBold) }
                        Text(story.createdAtLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${story.stationName} • ${story.caption.ifBlank { stringResource(id = R.string.story_no_caption) }}")
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onToggleStoryLike(story.id, story.likedByMe) }) {
                            Text(if (story.likedByMe) stringResource(id = R.string.unlike_story) else stringResource(id = R.string.like_story))
                        }
                        OutlinedButton(onClick = { onAddFriend(story.userId) }) { Text(stringResource(id = R.string.add_friend)) }
                        Text("${story.likeCount} ♥ • ${story.commentCount} 💬", style = MaterialTheme.typography.bodySmall)
                    }
                    story.recentComments.forEach { comment ->
                        Text("${comment.userLabel}: ${comment.body}", style = MaterialTheme.typography.bodySmall)
                    }
                    val draft = commentDrafts[story.id].orEmpty()
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { commentDrafts[story.id] = it },
                        label = { Text(stringResource(id = R.string.add_comment)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(onClick = {
                        onStoryComment(story.id, draft)
                        commentDrafts[story.id] = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(id = R.string.post_comment))
                    }
                }
            }
        }

        if (state.isStoriesLoadingMore) {
            item {
                Text(
                    text = stringResource(id = R.string.loading_more_stories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    LaunchedEffect(listState, state.storyItems.size, state.storiesHasNextPage, state.isStoriesLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { index ->
                val trigger = max(0, listState.layoutInfo.totalItemsCount - 3)
                if (index >= trigger && state.storiesHasNextPage && !state.isStoriesLoadingMore) {
                    onLoadMoreStories()
                }
            }
    }
}

@Composable
private fun CommunityDashboardTab(state: AppUiState) {
    val stats = state.storyStats
    var compactMode by rememberSaveable { mutableStateOf(false) }
    val isNightMood = remember {
        val hour = LocalTime.now().hour
        hour < 7 || hour >= 19
    }
    val moodGradient = if (isNightMood) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF1B2A4A),
                Color(0xFF243B64),
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
            )
        )
    }
    val moodLabel = if (isNightMood) {
        stringResource(id = R.string.community_mood_night)
    } else {
        stringResource(id = R.string.community_mood_day)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .background(moodGradient)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(id = R.string.community_dashboard),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = moodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isNightMood) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AnimatedVisibility(
                        visible = !compactMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            text = stringResource(id = R.string.community_live_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { compactMode = !compactMode },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (compactMode) {
                                stringResource(id = R.string.community_switch_detailed)
                            } else {
                                stringResource(id = R.string.community_switch_compact)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CommunityStatPill(
                            label = stringResource(id = R.string.community_vibe_label),
                            value = stats.momentVibe,
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                        )
                        CommunityStatPill(
                            label = stringResource(id = R.string.community_active_stories),
                            value = stats.totalActiveStories.toString(),
                            icon = Icons.Filled.CheckCircle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CommunityStatPill(
                        label = stringResource(id = R.string.community_avg_fun_score),
                        value = String.format("%.1f", stats.avgFunScore),
                        icon = Icons.Filled.LocalOffer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.highlightedStoryItems.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.community_trending_stories),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.highlightedStoryItems.take(5)) { story ->
                val preview = remember(story.imageBase64) { decodeBase64Image(story.imageBase64) }
                Card(
                    modifier = Modifier.animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (preview != null && !compactMode) {
                            Image(
                                bitmap = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(story.userLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            AnimatedVisibility(
                                visible = !compactMode,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Text(
                                    story.caption.ifBlank { stringResource(id = R.string.story_no_caption) },
                                    maxLines = 2,
                                )
                            }
                            Text(
                                text = stringResource(
                                    id = R.string.community_story_meta,
                                    story.stationName,
                                    story.likeCount,
                                    story.commentCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityStatPill(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StationsTab(
    state: AppUiState,
    onSubmitStationRating: (Int, Int, String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    if (state.stationItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_stations))
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStation by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.StationItem?>(null) }
    var selectedStationForRating by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.StationItem?>(null) }
    var stationScore by remember { mutableStateOf(5) }
    var stationComment by remember { mutableStateOf("") }
    val filteredItems = remember(state.stationItems, searchQuery) {
        state.stationItems.filter { item ->
            searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
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
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(id = R.string.search_stations)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (filteredItems.isEmpty()) {
            item {
                EmptyTabMessage(text = stringResource(id = R.string.no_results))
            }
        }
        items(visibleItems) { item ->
            val previewImage = remember(item.imageBase64) { decodeBase64Image(item.imageBase64) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { selectedStation = item },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        PisteMetricPill(
                            label = stringResource(id = R.string.station_capacity),
                            value = "🎿 ${item.capacityLabel}",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (item.cameras.isNotEmpty()) {
                            PisteMetricPill(
                                label = stringResource(id = R.string.live_cameras),
                                value = "📹 ${item.cameras.size}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedButton(
                            onClick = { selectedStation = item },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.view_station_details))
                        }
                        OutlinedButton(
                            onClick = {
                                selectedStationForRating = item
                                stationScore = 5
                                stationComment = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.rate_station))
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

    val stationDetails = selectedStation
    if (stationDetails != null) {
        val previewImage = remember(stationDetails.imageBase64) { decodeBase64Image(stationDetails.imageBase64) }
        val stationNewsItems = remember(
            stationDetails.id,
            stationDetails.name,
            state.skiNewsItems,
            state.highlightedSkiNewsItems,
        ) {
            val stationName = stationDetails.name.trim().lowercase()
            val highlighted = state.highlightedSkiNewsItems
                .filter {
                    it.stationId == stationDetails.id ||
                        it.stationName.trim().lowercase() == stationName
                }
            if (highlighted.isNotEmpty()) {
                highlighted.take(3)
            } else {
                state.skiNewsItems
                    .filter {
                        it.stationId == stationDetails.id ||
                            it.stationName.trim().lowercase() == stationName
                    }
                    .take(3)
            }
        }
        Dialog(onDismissRequest = { selectedStation = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 680.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (previewImage != null) {
                            Image(
                                bitmap = previewImage,
                                contentDescription = stationDetails.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stationDetails.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PisteMetricPill(
                                    label = stringResource(id = R.string.altitude),
                                    value = "⛰ ${stationDetails.altitudeLabel} m",
                                    modifier = Modifier.weight(1f),
                                )
                                PisteMetricPill(
                                    label = stringResource(id = R.string.distance_from_grenoble),
                                    value = "📍 ${stationDetails.distanceLabel} km",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            PisteMetricPill(
                                label = stringResource(id = R.string.station_capacity),
                                value = "🎿 ${stationDetails.capacityLabel}",
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Text(
                                text = stringResource(id = R.string.live_cameras),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            if (stationDetails.cameras.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.no_live_cameras),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                stationDetails.cameras.forEach { camera ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(camera.name, fontWeight = FontWeight.SemiBold)
                                            if (camera.description.isNotBlank()) {
                                                Text(
                                                    camera.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (camera.cameraType.isNotBlank()) {
                                                Text(
                                                    camera.cameraType,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            Button(
                                                onClick = { onOpenUrl(camera.cameraUrl) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(stringResource(id = R.string.open_camera))
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = stringResource(id = R.string.station_rss_news),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            if (stationNewsItems.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.no_station_rss_news),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                stationNewsItems.forEach { news ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "${news.sourceName} • ${news.publishedAtLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(news.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            if (news.summary.isNotBlank()) {
                                                Text(news.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                            }
                                            OutlinedButton(
                                                onClick = { onOpenUrl(news.link) },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(stringResource(id = R.string.open_news))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { selectedStation = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.close))
                        }
                        Button(
                            onClick = {
                                selectedStation = null
                                selectedStationForRating = stationDetails
                                stationScore = 5
                                stationComment = ""
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.rate_station))
                        }
                    }
                }
            }
        }
    }

    val stationToRate = selectedStationForRating
    if (stationToRate != null) {
        Dialog(onDismissRequest = { selectedStationForRating = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.rate_station_title, stationToRate.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..5).toList()) { score ->
                            FilterChipButton(
                                label = "$score",
                                selected = stationScore == score,
                                onClick = { stationScore = score },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = stationComment,
                        onValueChange = { stationComment = it },
                        label = { Text(stringResource(id = R.string.optional_comment)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { selectedStationForRating = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.close))
                        }
                        Button(
                            onClick = {
                                onSubmitStationRating(stationToRate.id, stationScore, stationComment)
                                selectedStationForRating = null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.submit_rating))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusLinesTab(state: AppUiState) {
    if (state.busLineItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_bus_lines))
        return
    }

    val stationNamesById = remember(state.stationItems) {
        state.stationItems.associate { it.id to it.name }
    }
    val stationFilters = remember(state.busLineItems, stationNamesById) {
        state.busLineItems.map { it.stationId }
            .distinct()
            .sortedBy { id -> stationNamesById[id] ?: "Station #$id" }
    }

    var searchQuery by remember { mutableStateOf("") }
    var seatsByPost by remember { mutableStateOf(mapOf<Int, String>()) }
    var selectedStationId by remember { mutableStateOf<Int?>(null) }

    val filteredItems = remember(state.busLineItems, searchQuery, selectedStationId, stationNamesById) {
        state.busLineItems.filter { item ->
            val stationName = item.stationName.ifBlank { stationNamesById[item.stationId].orEmpty() }
            val matchesStation = selectedStationId == null || item.stationId == selectedStationId
            val matchesQuery = searchQuery.isBlank() || listOf(
                item.busNumber,
                stationName,
                item.departureStop,
                item.arrivalStop,
                item.routePoints,
            ).any { it.contains(searchQuery, ignoreCase = true) }
            matchesStation && matchesQuery
        }
    }

    val listState = rememberLazyListState()
    val visibleCount = rememberProgressiveItemCount(
        totalCount = filteredItems.size,
        batchSize = 12,
        listState = listState,
        firstDataIndex = 2,
    )
    val visibleItems = remember(filteredItems, visibleCount) { filteredItems.take(visibleCount) }

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
                    label = { Text(stringResource(id = R.string.search_bus_lines)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_stations),
                            selected = selectedStationId == null,
                            onClick = { selectedStationId = null },
                        )
                    }
                    items(stationFilters) { stationId ->
                        val stationLabel = stationNamesById[stationId] ?: "Station #$stationId"
                        FilterChipButton(
                            label = stationLabel,
                            selected = selectedStationId == stationId,
                            onClick = { selectedStationId = stationId },
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item { EmptyTabMessage(text = stringResource(id = R.string.no_results)) }
        }

        items(visibleItems) { item ->
            val stationLabel = item.stationName.ifBlank { stationNamesById[item.stationId] ?: "Station #${item.stationId}" }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(item.busNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${stringResource(id = R.string.station)}: $stationLabel", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(id = R.string.departure)}: ${item.departureStop}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(id = R.string.arrival)}: ${item.arrivalStop}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(id = R.string.frequency)}: ${item.frequency}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(id = R.string.travel_time)}: ${item.travelTime}", style = MaterialTheme.typography.bodySmall)
                    if (item.routePoints.isNotBlank() && item.routePoints != "-") {
                        Text("${stringResource(id = R.string.route_points)}: ${item.routePoints}", style = MaterialTheme.typography.bodySmall)
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
private fun ServicesTab(
    state: AppUiState,
    onOpenUrl: (String) -> Unit,
) {
    if (state.serviceStoreItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_services))
        return
    }

    val stationNamesById = remember(state.stationItems) {
        state.stationItems.associate { it.id to it.name }
    }

    val stationFilters = remember(state.serviceStoreItems, stationNamesById) {
        state.serviceStoreItems.map { it.stationId }
            .distinct()
            .sortedBy { id -> stationNamesById[id] ?: "Station #$id" }
    }
    val typeFilters = remember(state.serviceStoreItems) {
        state.serviceStoreItems.map { it.type }
            .filter { it.isNotBlank() && it != "-" }
            .distinct()
            .sorted()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStationId by remember { mutableStateOf<Int?>(null) }
    var selectedType by remember { mutableStateOf("") }

    val filteredItems = remember(state.serviceStoreItems, searchQuery, selectedStationId, selectedType, stationNamesById) {
        state.serviceStoreItems.filter { item ->
            val stationName = item.stationName.ifBlank { stationNamesById[item.stationId].orEmpty() }
            val matchesStation = selectedStationId == null || item.stationId == selectedStationId
            val matchesType = selectedType.isBlank() || item.type == selectedType
            val matchesQuery = searchQuery.isBlank() || listOf(
                item.name,
                item.type,
                stationName,
                item.address,
            ).any { it.contains(searchQuery, ignoreCase = true) }
            matchesStation && matchesType && matchesQuery
        }
    }

    val listState = rememberLazyListState()
    val visibleCount = rememberProgressiveItemCount(
        totalCount = filteredItems.size,
        batchSize = 12,
        listState = listState,
        firstDataIndex = 3,
    )
    val visibleItems = remember(filteredItems, visibleCount) { filteredItems.take(visibleCount) }

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
                    label = { Text(stringResource(id = R.string.search_services)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_stations),
                            selected = selectedStationId == null,
                            onClick = { selectedStationId = null },
                        )
                    }
                    items(stationFilters) { stationId ->
                        val stationLabel = stationNamesById[stationId] ?: "Station #$stationId"
                        FilterChipButton(
                            label = stationLabel,
                            selected = selectedStationId == stationId,
                            onClick = { selectedStationId = stationId },
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_types),
                            selected = selectedType.isBlank(),
                            onClick = { selectedType = "" },
                        )
                    }
                    items(typeFilters) { type ->
                        FilterChipButton(
                            label = type,
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item { EmptyTabMessage(text = stringResource(id = R.string.no_results)) }
        }

        items(visibleItems) { item ->
            val stationLabel = item.stationName.ifBlank { stationNamesById[item.stationId] ?: "Station #${item.stationId}" }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("${stringResource(id = R.string.station)}: $stationLabel", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(id = R.string.opening_hours)}: ${item.openingHours}", style = MaterialTheme.typography.bodySmall)
                    if (item.address.isNotBlank() && item.address != "-") {
                        Text("${stringResource(id = R.string.address)}: ${item.address}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.phone.isNotBlank()) {
                        Text("${stringResource(id = R.string.phone)}: ${item.phone}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.websiteUrl.isNotBlank()) {
                        TextButton(onClick = { onOpenUrl(item.websiteUrl) }) {
                            Text(item.websiteUrl)
                        }
                    }
                    if (item.sourceNote.isNotBlank()) {
                        Text(item.sourceNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MarketplaceTab(
    state: AppUiState,
    siteBase: String,
    onPrepareMessageToSeller: (Int, String) -> Unit,
    onSubmitSellerRating: (Int, Int, Int, String) -> Unit,
    onOpenPublishMarketplace: () -> Unit,
    onLoadMoreMarketplace: () -> Unit,
) {
    var selectedItem by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.MarketplaceItem?>(null) }
    var rateSellerDialogItem by remember { mutableStateOf<fr.grenobleski.nativeapp.data.model.MarketplaceItem?>(null) }
    var sellerScore by remember { mutableStateOf(5) }
    var sellerComment by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedOfferType by remember { mutableStateOf("") }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedSort by rememberSaveable { mutableStateOf("new") }
    val availableCities = remember(state.marketplaceItems) {
        (state.marketplaceItems.map { it.city }.filter { it.isNotBlank() && it != "-" } + RHONE_ALPES_CITIES)
            .distinct()
            .sorted()
    }
    val availableConditions = remember(state.marketplaceItems) { state.marketplaceItems.map { it.conditionLabel }.filter { it.isNotBlank() && it != "-" }.distinct().sorted() }
    val availableCategories = remember(state.marketplaceItems) {
        state.marketplaceItems.map { it.materialTypeLabel }.filter { it.isNotBlank() && it != "-" }.distinct().sorted()
    }
    val availableOfferTypes = remember(state.marketplaceItems) {
        state.marketplaceItems.map { it.transactionTypeLabel }.filter { it.isNotBlank() && it != "-" }.distinct().sorted()
    }
    val filteredItems = remember(state.marketplaceItems, searchQuery, selectedCity, selectedCondition, selectedCategory, selectedOfferType) {
        state.marketplaceItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() || listOf(item.title, item.description, item.city, item.materialTypeLabel, item.transactionTypeLabel).any {
                it.contains(searchQuery, ignoreCase = true)
            }
            val matchesCity = selectedCity.isBlank() || item.city == selectedCity
            val matchesCondition = selectedCondition.isBlank() || item.conditionLabel == selectedCondition
            val matchesCategory = selectedCategory.isBlank() || item.materialTypeLabel == selectedCategory
            val matchesOfferType = selectedOfferType.isBlank() || item.transactionTypeLabel == selectedOfferType
            matchesQuery && matchesCity && matchesCondition && matchesCategory && matchesOfferType
        }
    }

    val activeFilterCount = remember(searchQuery, selectedCity, selectedCondition, selectedCategory, selectedOfferType) {
        listOf(searchQuery, selectedCity, selectedCondition, selectedCategory, selectedOfferType).count { it.isNotBlank() }
    }

    fun parsePriceValue(priceLabel: String): Double {
        val normalized = priceLabel
            .replace("€", "")
            .replace(" ", "")
            .replace(",", ".")
        val firstNumber = """-?\d+(?:\.\d+)?""".toRegex().find(normalized)?.value
        return firstNumber?.toDoubleOrNull() ?: Double.MAX_VALUE
    }

    val sortedItems = remember(filteredItems, selectedSort) {
        when (selectedSort) {
            "price_asc" -> filteredItems.sortedBy { parsePriceValue(it.priceLabel) }
            "price_desc" -> filteredItems.sortedByDescending { parsePriceValue(it.priceLabel) }
            else -> filteredItems.sortedByDescending { it.id }
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore = remember(state.marketplaceHasNextPage, state.isMarketplaceLoadingMore, sortedItems.size) {
        derivedStateOf {
            if (!state.marketplaceHasNextPage || state.isMarketplaceLoadingMore || sortedItems.isEmpty()) {
                return@derivedStateOf false
            }
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMoreMarketplace()
        }
    }

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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.showing_results, sortedItems.size, state.marketplaceItems.size),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (activeFilterCount > 0) {
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedCity = ""
                                    selectedCondition = ""
                                    selectedCategory = ""
                                    selectedOfferType = ""
                                },
                            ) {
                                Text(stringResource(id = R.string.marketplace_clear_filters))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(id = R.string.search_marketplace)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.marketplace_filters),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenPublishMarketplace,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.add_article), fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipButton(
                        label = stringResource(id = R.string.market_sort_newest),
                        selected = selectedSort == "new",
                        onClick = { selectedSort = "new" },
                    )
                    SortIconToggleButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(id = R.string.market_sort_price_low),
                        selected = selectedSort == "price_asc",
                        onClick = { selectedSort = "price_asc" },
                    )
                    SortIconToggleButton(
                        icon = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(id = R.string.market_sort_price_high),
                        selected = selectedSort == "price_desc",
                        onClick = { selectedSort = "price_desc" },
                    )
                }
                OutlinedButton(
                    onClick = { filtersExpanded = !filtersExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (filtersExpanded) {
                            stringResource(id = R.string.marketplace_hide_advanced_filters)
                        } else {
                            stringResource(id = R.string.marketplace_show_advanced_filters)
                        }
                    )
                }
                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_cities),
                            selected = selectedCity.isBlank(),
                            onClick = { selectedCity = "" },
                        )
                    }
                    items(availableCities) { city ->
                        FilterChipButton(
                            label = city,
                            selected = selectedCity == city,
                            onClick = { selectedCity = city },
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_conditions),
                            selected = selectedCondition.isBlank(),
                            onClick = { selectedCondition = "" },
                        )
                    }
                    items(availableConditions) { condition ->
                        FilterChipButton(
                            label = condition,
                            selected = selectedCondition == condition,
                            onClick = { selectedCondition = condition },
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_categories),
                            selected = selectedCategory.isBlank(),
                            onClick = { selectedCategory = "" },
                        )
                    }
                    items(availableCategories) { category ->
                        FilterChipButton(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                        )
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    item {
                        FilterChipButton(
                            label = stringResource(id = R.string.all_offer_types),
                            selected = selectedOfferType.isBlank(),
                            onClick = { selectedOfferType = "" },
                        )
                    }
                    items(availableOfferTypes) { offerType ->
                        FilterChipButton(
                            label = offerType,
                            selected = selectedOfferType == offerType,
                            onClick = { selectedOfferType = offerType },
                        )
                    }
                }
                    }
                }
            }
        }

        if (sortedItems.isEmpty()) {
            item {
                EmptyTabMessage(text = stringResource(id = R.string.no_results))
            }
        }

        items(sortedItems) { listing ->
            val previewSourceRaw = if (listing.previewImageBase64.isNotBlank()) {
                listing.previewImageBase64
            } else {
                listing.imageGalleryBase64.firstOrNull().orEmpty()
            }
            val previewSource = remember(previewSourceRaw, siteBase) {
                normalizeMarketplaceImageSource(previewSourceRaw, siteBase)
            }
            val previewImage = rememberMarketplaceImage(
                source = previewSource,
                cacheKey = "market:${listing.id}:preview",
                reqWidth = 960,
                reqHeight = 640,
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedItem = listing },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box {
                        MarketplaceProductImage(
                            bitmap = previewImage,
                            title = listing.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = listing.conditionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = listing.priceLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(listing.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = listing.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarketplaceMiniStat(
                                icon = "📍",
                                value = listing.city,
                                modifier = Modifier.weight(1f),
                            )
                            MarketplaceMiniStat(
                                icon = "🕒",
                                value = if (listing.postedAtLabel.isNotBlank()) listing.postedAtLabel else "-",
                                modifier = Modifier.weight(1f),
                            )
                            MarketplaceMiniStat(
                                icon = "🖼",
                                value = stringResource(
                                    id = R.string.marketplace_stat_photos,
                                    max(1, listing.imageGalleryBase64.size),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(listing.city, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (listing.postedAtLabel.isNotBlank()) {
                                Text(
                                    listing.postedAtLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UserAvatar(
                                displayName = listing.sellerLabel,
                                photoBase64 = listing.sellerPhotoBase64,
                                photoUrl = listing.sellerPhotoUrl,
                                size = 28.dp,
                            )
                            Text(listing.sellerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(id = R.string.marketplace_seller_trust_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChipButton(
                                label = listing.materialTypeLabel,
                                selected = false,
                                onClick = { selectedCategory = listing.materialTypeLabel },
                            )
                            FilterChipButton(
                                label = listing.transactionTypeLabel,
                                selected = false,
                                onClick = { selectedOfferType = listing.transactionTypeLabel },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selectedItem = listing }) {
                                Text(stringResource(id = R.string.view_details))
                            }
                            Button(onClick = { onPrepareMessageToSeller(listing.sellerId, listing.title) }) {
                                Text(stringResource(id = R.string.contact_seller))
                            }
                        }
                    }
                }
            }
        }

        if (state.isMarketplaceLoadingMore) {
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
        val gallerySources = remember(details.id, details.imageGalleryBase64, details.previewImageBase64, siteBase) {
            details.imageGalleryBase64.ifEmpty {
                if (details.previewImageBase64.isNotBlank()) listOf(details.previewImageBase64) else emptyList()
            }.map { raw -> normalizeMarketplaceImageSource(raw, siteBase) }
        }
        val galleryImages = gallerySources.mapIndexedNotNull { index, value ->
            rememberMarketplaceImage(
                source = value,
                cacheKey = "market:${details.id}:gallery:$index",
                reqWidth = 1280,
                reqHeight = 960,
            )
        }
        var selectedImageIndex by remember(details.id) { mutableStateOf(0) }
        val selectedImage = galleryImages.getOrNull(selectedImageIndex)

        Dialog(onDismissRequest = { selectedItem = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Box {
                        MarketplaceProductImage(
                            bitmap = selectedImage,
                            title = details.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = details.priceLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(
                            onClick = { selectedItem = null },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Text(stringResource(id = R.string.close), color = Color.White)
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(details.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(details.conditionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChipButton(
                                label = details.materialTypeLabel,
                                selected = false,
                                onClick = { selectedCategory = details.materialTypeLabel },
                            )
                            FilterChipButton(
                                label = details.transactionTypeLabel,
                                selected = false,
                                onClick = { selectedOfferType = details.transactionTypeLabel },
                            )
                        }

                        if (galleryImages.size > 1) {
                            Text(
                                text = stringResource(id = R.string.listing_photos),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(galleryImages.size) { index ->
                                    Image(
                                        bitmap = galleryImages[index],
                                        contentDescription = details.title,
                                        modifier = Modifier
                                            .width(78.dp)
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                width = if (selectedImageIndex == index) 2.dp else 1.dp,
                                                color = if (selectedImageIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(14.dp),
                                            )
                                            .clickable { selectedImageIndex = index },
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }

                        Text("${stringResource(id = R.string.city)}: ${details.city}")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UserAvatar(
                                displayName = details.sellerLabel,
                                photoBase64 = details.sellerPhotoBase64,
                                photoUrl = details.sellerPhotoUrl,
                                size = 30.dp,
                            )
                            Text("${stringResource(id = R.string.seller)}: ${details.sellerLabel}")
                        }
                        if (details.postedAtLabel.isNotBlank()) {
                            Text("${stringResource(id = R.string.posted_at)}: ${details.postedAtLabel}")
                        }
                        Text(details.description, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { selectedItem = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(id = R.string.close))
                            }
                            Button(
                                onClick = {
                                    selectedItem = null
                                    onPrepareMessageToSeller(details.sellerId, details.title)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(id = R.string.contact_seller))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                selectedItem = null
                                sellerScore = 5
                                sellerComment = ""
                                rateSellerDialogItem = details
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.rate_seller))
                        }
                    }
                }
            }
        }
    }

    val sellerListing = rateSellerDialogItem
    if (sellerListing != null) {
        Dialog(onDismissRequest = { rateSellerDialogItem = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.rate_seller_title, sellerListing.title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(id = R.string.rate_seller),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        MarketplaceStarRatingSelector(
                            selectedScore = sellerScore,
                            onScoreSelected = { sellerScore = it },
                        )

                        Text(
                            text = when (sellerScore) {
                                1 -> "Tres decu"
                                2 -> "Peut mieux faire"
                                3 -> "Correct"
                                4 -> "Tres bien"
                                else -> "Excellent"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )

                        OutlinedTextField(
                            value = sellerComment,
                            onValueChange = { sellerComment = it },
                            label = { Text(stringResource(id = R.string.optional_comment)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { rateSellerDialogItem = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.close))
                        }
                        Button(
                            onClick = {
                                onSubmitSellerRating(sellerListing.id, sellerListing.sellerId, sellerScore, sellerComment)
                                rateSellerDialogItem = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = sellerListing.sellerId > 0,
                        ) {
                            Text(stringResource(id = R.string.submit_rating))
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeMarketplaceImageSource(source: String, siteBase: String): String {
    if (source.isBlank()) return ""
    if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
        return source
    }
    if (source.startsWith("/")) {
        return siteBase.trimEnd('/') + source
    }
    return source
}

@Composable
private fun MarketplaceMiniStat(
    icon: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = "$icon $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun MarketplaceStarRatingSelector(
    selectedScore: Int,
    onScoreSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (1..5).forEach { score ->
            val isFilled = score <= selectedScore
            TextButton(
                onClick = { onScoreSelected(score) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isFilled) Color(0xFFFFF3D6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isFilled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Text(
                    text = if (isFilled) "★" else "☆",
                    fontSize = 23.sp,
                    color = if (isFilled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MarketplaceProductImage(
    bitmap: ImageBitmap?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    )
                )
            )
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = stringResource(id = R.string.no_photo_available),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.no_photo_available),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
private fun PistesTab(
    state: AppUiState,
    onOpenUrl: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(state.pisteItems, searchQuery) {
        state.pisteItems.filter { item ->
            searchQuery.isBlank() || item.stationName.contains(searchQuery, ignoreCase = true) || item.weatherLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    val fallbackMapItems = remember(state.stationItems) {
        state.stationItems
            .filter { it.latitude != null && it.longitude != null }
            .map { station ->
                fr.grenobleski.nativeapp.data.model.PisteItem(
                    id = station.id,
                    stationName = station.name,
                    altitudeLabel = station.altitudeLabel,
                    distanceLabel = station.distanceLabel,
                    pisteMapUrl = station.pisteMapUrl,
                    pisteMapThumbnailUrl = station.pisteMapThumbnailUrl,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    ratingLabel = "-",
                    crowdLabel = "normal",
                    weatherLabel = "indisponible",
                    temperatureLabel = "-",
                    snowDepthLabel = "-",
                    comment = "-",
                    updatedAtLabel = "",
                )
            }
    }

    val mapItems = remember(filteredItems, fallbackMapItems) {
        val pisteWithCoords = filteredItems.filter { it.latitude != null && it.longitude != null }
        if (pisteWithCoords.isNotEmpty()) pisteWithCoords else fallbackMapItems
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
        item {
            PisteStationsMapCard(
                items = mapItems,
                onOpenUrl = onOpenUrl,
            )
        }
        if (filteredItems.isEmpty()) {
            item {
                EmptyTabMessage(text = stringResource(id = R.string.empty_pistes))
            }
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

                    val pisteMapUrl = item.pisteMapUrl
                    if (!pisteMapUrl.isNullOrBlank()) {
                        OutlinedButton(onClick = { onOpenUrl(pisteMapUrl) }) {
                            Text(stringResource(id = R.string.open_piste_map))
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
}

@Composable
private fun PisteStationsMapCard(
    items: List<PisteItem>,
    onOpenUrl: (String) -> Unit,
) {
    val mapItems = remember(items) {
        items.filter { it.latitude != null && it.longitude != null }
    }
    val firstOfficialMapUrl = remember(items) {
        items.firstOrNull { it.pisteMapUrl.isNotBlank() }?.pisteMapUrl
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.stations_live_map),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = R.string.piste_map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (mapItems.isNotEmpty()) {
                val mapHtml = remember(mapItems) { buildStationsMapHtml(mapItems) }
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(
                                "https://www.openstreetmap.org",
                                mapHtml,
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(
                            "https://www.openstreetmap.org",
                            mapHtml,
                            "text/html",
                            "utf-8",
                            null,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }

            if (!firstOfficialMapUrl.isNullOrBlank()) {
                OutlinedButton(onClick = { onOpenUrl(firstOfficialMapUrl) }) {
                    Text(stringResource(id = R.string.interactive_station_map))
                }
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

private fun buildStationsMapHtml(items: List<PisteItem>): String {
    val markers = items.joinToString(separator = ",\n") { item ->
        val latitude = item.latitude ?: 0.0
        val longitude = item.longitude ?: 0.0
        val popup = buildString {
            append("<strong>")
            append(escapeHtml(item.stationName))
            append("</strong><br/>")
            append(escapeHtml(item.weatherLabel))
            append("<br/>")
            append("Note: ")
            append(escapeHtml(item.ratingLabel))
            append(" | Neige: ")
            append(escapeHtml(item.snowDepthLabel))
            append(" cm")
        }
        """{ lat: $latitude, lng: $longitude, title: \"${escapeForJs(item.stationName)}\", popup: \"${escapeForJs(popup)}\" }"""
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />
            <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />
            <style>
                html, body, #map {
                    height: 100%;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background: #eef3f8;
                }
                .leaflet-container {
                    font-family: sans-serif;
                }
            </style>
        </head>
        <body>
            <div id=\"map\"></div>
            <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>
            <script>
                const map = L.map('map', { zoomControl: false, attributionControl: true });
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 18,
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(map);

                const markers = [$markers];
                const bounds = [];

                markers.forEach((item) => {
                    const marker = L.marker([item.lat, item.lng]).addTo(map);
                    marker.bindPopup(item.popup);
                    bounds.push([item.lat, item.lng]);
                });

                if (bounds.length === 1) {
                    map.setView(bounds[0], 10);
                } else if (bounds.length > 1) {
                    map.fitBounds(bounds, { padding: [24, 24] });
                } else {
                    map.setView([45.1885, 5.7245], 8);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeForJs(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "<br/>")
    .replace("\r", "")

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

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
    onAcceptFriendInvitation: (Int) -> Unit,
    onDeclineFriendInvitation: (Int) -> Unit,
    onCancelFriendInvitation: (Int) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val myUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    var chatSearch by remember { mutableStateOf("") }
    var addFriendDialogOpen by remember { mutableStateOf(false) }
    var addFriendSearch by remember { mutableStateOf("") }
    val currentUserId = state.profileInfo?.userId?.takeIf { it > 0 } ?: state.session?.userId ?: 0
    val friendIds = remember(state.friendLinks) { state.friendLinks.map { it.friendId }.toSet() }
    val incomingInvitationFromIds = remember(state.friendInvitations, currentUserId) {
        state.friendInvitations.filter { it.status == "pending" && it.toUserId == currentUserId }.map { it.fromUserId }.toSet()
    }
    val outgoingInvitationToIds = remember(state.friendInvitations, currentUserId) {
        state.friendInvitations.filter { it.status == "pending" && it.fromUserId == currentUserId }.map { it.toUserId }.toSet()
    }

    val threadSummaries = remember(state.messageItems, myUserId) {
        val map = linkedMapOf<Int, ChatThreadSummary>()

        state.messageItems.forEach { item ->
            if (myUserId > 0 && item.senderId != myUserId && item.recipientId != myUserId) {
                return@forEach
            }
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

        map.values.toList()
    }

    val chatUsersById = remember(state.chatUsers) { state.chatUsers.associateBy { it.id } }

    val friendThreads = remember(state.friendLinks, threadSummaries, chatUsersById) {
        state.friendLinks.mapNotNull { link ->
            val friendId = link.friendId
            if (friendId <= 0) {
                null
            } else {
                threadSummaries.firstOrNull { it.userId == friendId } ?: run {
                    val fallback = chatUsersById[friendId]
                    ChatThreadSummary(
                        userId = friendId,
                        label = fallback?.label ?: "Utilisateur #$friendId",
                        photoBase64 = fallback?.photoBase64.orEmpty(),
                        photoUrl = fallback?.photoUrl.orEmpty(),
                        lastMessage = "",
                        lastDateLabel = "",
                        unreadCount = 0,
                    )
                }
            }
        }
    }

    val conversationThreads = remember(threadSummaries) {
        threadSummaries.filter { it.lastMessage.isNotBlank() }
    }

    val contactThreads = remember(conversationThreads, friendThreads) {
        val ordered = conversationThreads.toMutableList()
        friendThreads.forEach { candidate ->
            if (ordered.none { it.userId == candidate.userId }) {
                ordered.add(candidate)
            }
        }
        ordered
    }

    val filteredThreads = remember(contactThreads, chatSearch) {
        contactThreads.filter {
            chatSearch.isBlank() || it.label.contains(chatSearch, ignoreCase = true) || it.lastMessage.contains(chatSearch, ignoreCase = true)
        }
    }

    val selectedRecipientId = state.messageRecipientId ?: filteredThreads.firstOrNull()?.userId ?: contactThreads.firstOrNull()?.userId
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
    var showThreadView by remember { mutableStateOf(selectedRecipientId != null) }

    LaunchedEffect(selectedRecipientId) {
        if (selectedRecipientId != null && selectedRecipientId != state.messageRecipientId) {
            onSelectRecipient(selectedRecipientId)
        }
        if (selectedRecipientId != null) {
            showThreadView = true
        }
    }

    val recipientOptions = remember(state.chatUsers, addFriendSearch, myUserId, friendIds, incomingInvitationFromIds, outgoingInvitationToIds) {
        state.chatUsers.filter { option ->
            option.id != myUserId &&
                !friendIds.contains(option.id) &&
                !incomingInvitationFromIds.contains(option.id) &&
                !outgoingInvitationToIds.contains(option.id) &&
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
    val messageListState = rememberLazyListState()

    LaunchedEffect(selectedRecipientId, orderedMessages.size) {
        if (orderedMessages.isNotEmpty()) {
            messageListState.scrollToItem(orderedMessages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!showThreadView) {
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
                    if (friendThreads.isNotEmpty()) {
                        Text(
                            text = stringResource(id = R.string.friends_list_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(friendThreads) { friend ->
                                OutlinedButton(
                                    onClick = {
                                        onSelectRecipient(friend.userId)
                                        showThreadView = true
                                    }
                                ) {
                                    Text(friend.label, maxLines = 1)
                                }
                            }
                        }
                    }
                    val incomingInvites = state.chatUsers.filter { incomingInvitationFromIds.contains(it.id) }
                    if (incomingInvites.isNotEmpty()) {
                        Text(
                            text = stringResource(id = R.string.invites_received_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(incomingInvites) { inviteUser ->
                                OutlinedButton(onClick = { onAcceptFriendInvitation(inviteUser.id) }) {
                                    Text(inviteUser.label, maxLines = 1)
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.conversations),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (filteredThreads.isEmpty()) {
                EmptyTabMessage(text = stringResource(id = R.string.no_contacts_available))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredThreads) { thread ->
                        val selected = thread.userId == selectedRecipientId
                        val isFriend = friendIds.contains(thread.userId)
                        val hasIncomingInvitation = incomingInvitationFromIds.contains(thread.userId)
                        val hasOutgoingInvitation = outgoingInvitationToIds.contains(thread.userId)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectRecipient(thread.userId)
                                    showThreadView = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(
                                    displayName = thread.label,
                                    photoBase64 = thread.photoBase64,
                                    photoUrl = thread.photoUrl,
                                    size = 36.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
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
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (thread.lastDateLabel.isNotBlank()) {
                                        Text(
                                            text = thread.lastDateLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    if (thread.unreadCount > 0) {
                                        Badge { Text(thread.unreadCount.toString()) }
                                    }
                                    if (isFriend) {
                                        TextButton(onClick = { onRemoveFriend(thread.userId) }) {
                                            Text(stringResource(id = R.string.remove_friend))
                                        }
                                    } else if (hasIncomingInvitation) {
                                        TextButton(onClick = { onAcceptFriendInvitation(thread.userId) }) {
                                            Text(stringResource(id = R.string.accept_invite))
                                        }
                                        TextButton(onClick = { onDeclineFriendInvitation(thread.userId) }) {
                                            Text(stringResource(id = R.string.decline_invite))
                                        }
                                    } else if (hasOutgoingInvitation) {
                                        TextButton(onClick = { onCancelFriendInvitation(thread.userId) }) {
                                            Text(stringResource(id = R.string.cancel_invite))
                                        }
                                    } else if (thread.lastMessage.isNotBlank()) {
                                        Text(
                                            text = stringResource(id = R.string.new_contact_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = { showThreadView = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.conversations),
                        )
                    }
                    if (selectedThread != null) {
                        UserAvatar(
                            displayName = selectedThread.label,
                            photoBase64 = selectedThread.photoBase64,
                            photoUrl = selectedThread.photoUrl,
                            size = 34.dp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = selectedThread?.label ?: stringResource(id = R.string.choose_contact_first),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(id = R.string.conversations),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val selectedUserId = selectedThread?.userId
                    val selectedIsFriend = selectedUserId != null && friendIds.contains(selectedUserId)
                    val selectedIncomingInvitation = selectedUserId != null && incomingInvitationFromIds.contains(selectedUserId)
                    val selectedOutgoingInvitation = selectedUserId != null && outgoingInvitationToIds.contains(selectedUserId)
                    if (selectedUserId != null && !selectedIsFriend && !selectedIncomingInvitation && !selectedOutgoingInvitation) {
                        TextButton(onClick = { onAddFriend(selectedUserId) }) {
                            Text(stringResource(id = R.string.add_friend))
                        }
                    } else if (selectedIncomingInvitation && selectedUserId != null) {
                        TextButton(onClick = { onAcceptFriendInvitation(selectedUserId) }) {
                            Text(stringResource(id = R.string.accept_invite))
                        }
                    } else if (selectedOutgoingInvitation && selectedUserId != null) {
                        TextButton(onClick = { onCancelFriendInvitation(selectedUserId) }) {
                            Text(stringResource(id = R.string.cancel_invite))
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
                        state = messageListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(orderedMessages) { item ->
                            val mine = myUserId > 0 && item.senderId == myUserId
                            val avatarName = if (mine) {
                                state.profileInfo?.displayName?.ifBlank { stringResource(id = R.string.you) }
                                    ?: stringResource(id = R.string.you)
                            } else {
                                item.senderLabel
                            }
                            val avatarBase64 = if (mine) {
                                state.profileInfo?.profilePictureBase64.orEmpty()
                            } else {
                                item.senderPhotoBase64
                            }
                            val avatarUrl = if (mine) {
                                state.profileInfo?.googleProfilePictureUrl.orEmpty()
                            } else {
                                item.senderPhotoUrl
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                if (!mine) {
                                    UserAvatar(
                                        displayName = avatarName,
                                        photoBase64 = avatarBase64,
                                        photoUrl = avatarUrl,
                                        size = 30.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 300.dp)
                                        .background(
                                            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(18.dp),
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
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
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
                                if (mine) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    UserAvatar(
                                        displayName = avatarName,
                                        photoBase64 = avatarBase64,
                                        photoUrl = avatarUrl,
                                        size = 30.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.messageDraftBody,
                        onValueChange = onBodyChange,
                        label = { Text(stringResource(id = R.string.message)) },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                    )
                    Button(
                        onClick = onSend,
                        enabled = !state.isSendingMessage && selectedRecipientId != null && state.messageDraftBody.isNotBlank(),
                    ) {
                        if (state.isSendingMessage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(id = R.string.send_message),
                            )
                        }
                    }
                }
            }
        }

        if (addFriendDialogOpen) {
            CenteredPanelDialog(
                title = stringResource(id = R.string.add_friend),
                onDismiss = { addFriendDialogOpen = false },
            ) {
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

                    OutlinedButton(
                        onClick = { addFriendDialogOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredPanelDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
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
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                )
                            )
                        )
                        .padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(id = R.string.close),
                                tint = Color.White,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
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
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
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
                        text = stringResource(id = R.string.language_preference),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipButton(
                            label = stringResource(id = R.string.language_system),
                            selected = currentLanguage.lowercase() !in setOf("fr", "en"),
                            onClick = { onLanguageChange("system") },
                        )
                        FilterChipButton(
                            label = stringResource(id = R.string.language_french),
                            selected = currentLanguage.lowercase() == "fr",
                            onClick = { onLanguageChange("fr") },
                        )
                        FilterChipButton(
                            label = stringResource(id = R.string.language_english),
                            selected = currentLanguage.lowercase() == "en",
                            onClick = { onLanguageChange("en") },
                        )
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
private fun SortIconToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val buttonModifier = Modifier
        .height(40.dp)
        .width(64.dp)

    if (selected) {
        Button(onClick = onClick, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp))
                Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp))
            }
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, contentPadding = PaddingValues(0.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp))
                Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CollapsibleMoreSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun MoreActionsGrid(
    actions: List<MoreMenuAction>,
    compactMode: Boolean,
    onActionClick: (MoreMenuAction) -> Unit,
) {
    actions.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowItems.forEach { action ->
                OutlinedButton(
                    onClick = { onActionClick(action) },
                    modifier = Modifier.weight(1f),
                ) {
                    if (compactMode) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(action.shortLabel, maxLines = 1)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(action.label, maxLines = 1)
                        }
                    }
                }
            }
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
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
private fun PartnersTab(
    state: AppUiState,
    currentUserId: Int,
    onPrepareMessageToPartner: (Int, String) -> Unit,
    onOpenPublishPartner: () -> Unit,
    onRequestCarpoolReservation: (Int, Int) -> Unit,
    onCancelCarpoolReservation: (Int) -> Unit,
    onApproveCarpoolReservation: (Int, Int) -> Unit,
    onRejectCarpoolReservation: (Int, Int) -> Unit,
) {
    if (state.partnerItems.isEmpty()) {
        EmptyTabMessage(text = stringResource(id = R.string.empty_partners))
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var seatsByPost by remember { mutableStateOf(mapOf<Int, String>()) }
    val filteredItems = remember(state.partnerItems, searchQuery) {
        state.partnerItems.filter { item ->
            searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.city.contains(searchQuery, ignoreCase = true) ||
                item.stationLabel.contains(searchQuery, ignoreCase = true) ||
                item.organizerLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFE0F2FE),
                                    Color(0xFFD1FAE5),
                                )
                            )
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.partner_cta_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = R.string.partner_cta_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenPublishPartner) {
                        Text(stringResource(id = R.string.publish_partner_post))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(id = R.string.search_partners)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (filteredItems.isEmpty()) {
            item {
                EmptyTabMessage(text = stringResource(id = R.string.no_results))
            }
        }

        items(filteredItems) { item ->
            val requestedSeatsRaw = seatsByPost[item.id].orEmpty().ifBlank { "1" }
            val requestedSeats = requestedSeatsRaw.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val isOrganizer = currentUserId > 0 && currentUserId == item.organizerId
            val hasMyPendingOrActive = item.myReservationStatus == "pending" || item.myReservationStatus == "active"

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(id = R.string.partner_meta_row, item.organizerLabel, item.city),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(id = R.string.partner_level_date_row, item.levelLabel, item.preferredDateLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (item.isCarpool) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(text = stringResource(id = R.string.carpool_seats_left, item.seatsRemaining, item.totalSeats)) },
                            )
                            if (item.departureCity.isNotBlank()) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(text = stringResource(id = R.string.carpool_departure_city_chip, item.departureCity)) },
                                )
                            }
                        }

                        if (item.departureDateTimeLabel.isNotBlank() && item.departureDateTimeLabel != "-") {
                            Text(
                                text = stringResource(id = R.string.carpool_departure_time_line, item.departureDateTimeLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (!isOrganizer) {
                            when {
                                hasMyPendingOrActive -> {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(
                                                text = if (item.myReservationStatus == "pending") {
                                                    stringResource(id = R.string.carpool_status_pending_chip, item.myReservedSeats)
                                                } else {
                                                    stringResource(id = R.string.carpool_status_active_chip, item.myReservedSeats)
                                                }
                                            )
                                        },
                                    )
                                    OutlinedButton(onClick = { onCancelCarpoolReservation(item.id) }) {
                                        Text(stringResource(id = R.string.carpool_cancel_request))
                                    }
                                }

                                item.seatsRemaining > 0 -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = requestedSeatsRaw,
                                            onValueChange = { value ->
                                                if (value.isBlank() || value.all(Char::isDigit)) {
                                                    seatsByPost = seatsByPost + (item.id to value)
                                                }
                                            },
                                            label = { Text(stringResource(id = R.string.carpool_seats_label)) },
                                            modifier = Modifier.width(120.dp),
                                            singleLine = true,
                                        )
                                        Button(onClick = {
                                            onRequestCarpoolReservation(
                                                item.id,
                                                requestedSeats.coerceAtMost(maxOf(1, item.seatsRemaining)),
                                            )
                                        }) {
                                            Text(stringResource(id = R.string.carpool_request_button))
                                        }
                                    }
                                }

                                else -> {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text(stringResource(id = R.string.carpool_full_chip)) },
                                    )
                                }
                            }
                        } else {
                            if (item.pendingReservations.isNotEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.carpool_pending_requests_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                item.pendingReservations.forEach { pending ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(
                                                id = R.string.carpool_pending_request_line,
                                                pending.userLabel,
                                                pending.seatsReserved,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(onClick = { onApproveCarpoolReservation(item.id, pending.reservationId) }) {
                                                Text(stringResource(id = R.string.carpool_approve))
                                            }
                                            OutlinedButton(onClick = { onRejectCarpoolReservation(item.id, pending.reservationId) }) {
                                                Text(stringResource(id = R.string.carpool_reject))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onPrepareMessageToPartner(item.organizerId, item.title) },
                        enabled = item.organizerId > 0,
                    ) {
                        Text(stringResource(id = R.string.contact_partner))
                    }
                }
            }
        }
    }
}

@Composable
private fun tabTitle(tab: NativeTab): String {
    return when (tab) {
        NativeTab.HOME -> stringResource(id = R.string.nav_home)
        NativeTab.STORIES -> stringResource(id = R.string.stories)
        NativeTab.COMMUNITY -> stringResource(id = R.string.community_dashboard)
        NativeTab.STATIONS -> stringResource(id = R.string.stations)
        NativeTab.BUS_LINES -> stringResource(id = R.string.bus_lines)
        NativeTab.SERVICES -> stringResource(id = R.string.services)
        NativeTab.MARKETPLACE -> stringResource(id = R.string.marketplace)
        NativeTab.PARTNERS -> stringResource(id = R.string.adventure_partners)
        NativeTab.INSTRUCTORS -> stringResource(id = R.string.instructors)
        NativeTab.PISTES -> stringResource(id = R.string.piste_status)
        NativeTab.MESSAGES -> stringResource(id = R.string.messages)
        NativeTab.PROFILE -> stringResource(id = R.string.profile)
    }
}

private fun decodeBase64Image(data: String): ImageBitmap? {
    return decodeBase64ImageCached(
        data = data,
        cacheKey = "generic:${data.hashCode()}",
        reqWidth = 960,
        reqHeight = 640,
    )
}

@Composable
private fun rememberMarketplaceImage(
    source: String,
    cacheKey: String,
    reqWidth: Int,
    reqHeight: Int,
): ImageBitmap? {
    if (source.isBlank()) return null

    val isRemote = source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)
    if (!isRemote) {
        return remember(source, cacheKey, reqWidth, reqHeight) {
            decodeBase64ImageCached(
                data = source,
                cacheKey = cacheKey,
                reqWidth = reqWidth,
                reqHeight = reqHeight,
            )
        }
    }

    var remoteImage by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source) {
        remoteImage = loadImageFromUrl(source)
    }
    return remoteImage
}

private fun decodeBase64ImageCached(
    data: String,
    cacheKey: String,
    reqWidth: Int,
    reqHeight: Int,
): ImageBitmap? {
    if (data.isBlank()) return null

    val normalizedData = normalizeBase64Payload(data)
    if (normalizedData.isBlank()) return null

    MarketplaceBitmapCache.get(cacheKey)?.let { cached ->
        return cached.asImageBitmap()
    }

    return try {
        val bytes = try {
            Base64.decode(normalizedData, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            Base64.decode(normalizedData, Base64.URL_SAFE or Base64.NO_WRAP)
        }
        val bitmap = decodeSampledBitmap(bytes, reqWidth, reqHeight) ?: return null
        MarketplaceBitmapCache.put(cacheKey, bitmap)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun normalizeBase64Payload(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("data:", ignoreCase = true)) {
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex >= 0 && commaIndex + 1 < trimmed.length) {
            return trimmed.substring(commaIndex + 1).trim()
        }
    }
    return trimmed
}

private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
    val safeWidth = reqWidth.coerceAtLeast(200)
    val safeHeight = reqHeight.coerceAtLeast(200)

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds, safeWidth, safeHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
        inDither = true
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

private suspend fun loadImageFromUrl(url: String): ImageBitmap? {
    if (url.isBlank()) return null
    return withContext(Dispatchers.IO) {
        try {
            val requestWidth = 960
            val requestHeight = 640

            val boundsConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                doInput = true
                setRequestProperty("User-Agent", "GrenobleSkiAndroid")
            }

            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            boundsConnection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            boundsConnection.disconnect()

            val decodeConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                doInput = true
                setRequestProperty("User-Agent", "GrenobleSkiAndroid")
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, requestWidth, requestHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }
            decodeConnection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }.also {
                decodeConnection.disconnect()
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
