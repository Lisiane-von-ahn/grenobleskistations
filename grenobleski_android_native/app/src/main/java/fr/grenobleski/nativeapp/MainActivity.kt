package fr.grenobleski.nativeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.core.os.LocaleListCompat
import com.applovin.sdk.AppLovinSdk
import fr.grenobleski.nativeapp.ads.AdsConsentManager
import fr.grenobleski.nativeapp.data.session.LanguageStore
import fr.grenobleski.nativeapp.ui.GrenobleSkiApp
import fr.grenobleski.nativeapp.ui.theme.GrenobleSkiNativeTheme

class MainActivity : ComponentActivity() {
    private var pendingAuthUri by mutableStateOf<Uri?>(null)
    private var mobileAdsEnabled by mutableStateOf(false)
    private var showAdsConsentPrompt by mutableStateOf(false)
    private lateinit var languageStore: LanguageStore
    private lateinit var adsConsentManager: AdsConsentManager
    private var appLovinInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageStore = LanguageStore(this)
        adsConsentManager = AdsConsentManager(this)
        applySavedLanguage(languageStore.loadLanguage())
        pendingAuthUri = extractAuthUri(intent)

        adsConsentManager.syncPrivacyStateWithSdk()
        showAdsConsentPrompt = BuildConfig.ENABLE_MOBILE_ADS && adsConsentManager.shouldPromptConsent()
        refreshMobileAdsState()

        setContent {
            GrenobleSkiNativeTheme {
                Surface {
                    GrenobleSkiApp(
                        pendingAuthUri = pendingAuthUri,
                        onAuthUriConsumed = { pendingAuthUri = null },
                        adsEnabled = mobileAdsEnabled,
                        showAdsConsentPrompt = showAdsConsentPrompt,
                        onAcceptAdsConsent = {
                            adsConsentManager.setConsentAccepted()
                            showAdsConsentPrompt = false
                            refreshMobileAdsState()
                        },
                        onRejectAdsConsent = {
                            adsConsentManager.setConsentRejected()
                            showAdsConsentPrompt = false
                            refreshMobileAdsState()
                        },
                        onOpenAdsPreferences = {
                            showAdsConsentPrompt = true
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = extractAuthUri(intent)
        if (uri != null) {
            pendingAuthUri = uri
        }
    }

    private fun extractAuthUri(sourceIntent: Intent?): Uri? {
        val uri = sourceIntent?.data ?: return null
        if (sourceIntent.action != Intent.ACTION_VIEW) {
            return null
        }
        val isSupportedScheme = uri.scheme == "grenobleski"
        val isSupportedHost = uri.host == "auth"
        if (!isSupportedScheme || !isSupportedHost) {
            return null
        }
        return uri
    }

    private fun applySavedLanguage(language: String) {
        val locales = when (language.lowercase()) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "fr" -> LocaleListCompat.forLanguageTags("fr")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun refreshMobileAdsState() {
        val canEnableAds =
            BuildConfig.ENABLE_MOBILE_ADS &&
            adsConsentManager.canRequestAds() &&
            BuildConfig.APPLOVIN_SDK_KEY.isNotBlank() &&
            BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID.isNotBlank()

        mobileAdsEnabled = canEnableAds
        if (canEnableAds && !appLovinInitialized) {
            val sdk = AppLovinSdk.getInstance(this)
            sdk.mediationProvider = "max"
            sdk.initializeSdk {}
            appLovinInitialized = true
        }
    }
}
