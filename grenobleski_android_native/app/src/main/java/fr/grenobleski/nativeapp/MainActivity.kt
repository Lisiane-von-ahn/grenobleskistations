package fr.grenobleski.nativeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import fr.grenobleski.nativeapp.ui.GrenobleSkiApp
import fr.grenobleski.nativeapp.ui.theme.GrenobleSkiNativeTheme

class MainActivity : ComponentActivity() {
    private var pendingAuthUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (uri.scheme != "grenobleski" || uri.host != "auth") {
            return null
        }
        return uri
    }
}
