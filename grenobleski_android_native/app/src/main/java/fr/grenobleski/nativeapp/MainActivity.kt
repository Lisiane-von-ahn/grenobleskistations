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
import fr.grenobleski.nativeapp.data.session.LanguageStore
import fr.grenobleski.nativeapp.ui.GrenobleSkiApp
import fr.grenobleski.nativeapp.ui.theme.GrenobleSkiNativeTheme

class MainActivity : ComponentActivity() {
    private var pendingAuthUri by mutableStateOf<Uri?>(null)
    private lateinit var languageStore: LanguageStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageStore = LanguageStore(this)
        applySavedLanguage(languageStore.loadLanguage())
        pendingAuthUri = extractAuthUri(intent)

        setContent {
            GrenobleSkiNativeTheme {
                Surface {
                    GrenobleSkiApp(
                        pendingAuthUri = pendingAuthUri,
                        onAuthUriConsumed = { pendingAuthUri = null },
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
}
