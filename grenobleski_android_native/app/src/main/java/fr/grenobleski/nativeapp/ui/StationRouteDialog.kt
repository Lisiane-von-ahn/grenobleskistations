package fr.grenobleski.nativeapp.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.grenobleski.nativeapp.BuildConfig
import fr.grenobleski.nativeapp.R
import kotlinx.coroutines.delay

@Composable
internal fun StationRouteDialog(stationId: Int, onDismiss: () -> Unit) {
    val base = Uri.parse(BuildConfig.API_BASE_URL)
    val path = "/ski-station/$stationId/route/"
    val url = base.buildUpon().path(path).clearQuery().appendQueryParameter("embedded", "1").build()
    var attempt by remember(stationId) { mutableIntStateOf(0) }
    var failed by remember(stationId, attempt) { mutableStateOf(false) }
    var loading by remember(stationId, attempt) { mutableStateOf(true) }
    LaunchedEffect(stationId, attempt, loading, failed) {
        if (loading && !failed) {
            delay(20_000)
            failed = true
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.getting_there_title), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                if (failed) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.route_load_error))
                        OutlinedButton(onClick = { attempt++ }) { Text(stringResource(R.string.refresh)) }
                    }
                } else {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    key(stationId, attempt) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val target = request.url
                                            return target.scheme != base.scheme || target.host != base.host ||
                                                target.port != base.port || target.path !in setOf(path, "/offline/")
                                        }

                                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                            loading = true
                                        }

                                        override fun onPageFinished(view: WebView, url: String) {
                                            loading = false
                                        }

                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                            if (request.isForMainFrame) {
                                                if (view.url?.contains("/offline/") == true) failed = true
                                                else view.loadUrl(base.buildUpon().path("/offline/").clearQuery().build().toString())
                                            }
                                        }

                                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                            if (request.isForMainFrame) failed = true
                                        }

                                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                            failed = true
                                            return true
                                        }
                                    }
                                    loadUrl(url.toString())
                                }
                            },
                            onRelease = { it.destroy() },
                        )
                    }
                }
            }
        }
    }
}
