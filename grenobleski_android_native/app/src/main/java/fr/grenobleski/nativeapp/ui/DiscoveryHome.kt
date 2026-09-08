package fr.grenobleski.nativeapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem

@Composable
internal fun DiscoveryHome(state: AppUiState, onOpenStations: () -> Unit, onOpenUrl: (String) -> Unit) {
    val french = LocalConfiguration.current.locales[0].language == "fr"
    var interest by rememberSaveable { mutableStateOf("all") }
    val cameras = state.stationItems.flatMap { station -> station.cameras.map { station to it } }
    var selectedCamera by remember { mutableStateOf<Int?>(null) }
    val selected = cameras.firstOrNull { it.second.id == selectedCamera } ?: cameras.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.discovery_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.discovery_subtitle), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.discovery_everyone), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to R.string.interest_all, "mountains" to R.string.interest_mountains,
                    "walks" to R.string.interest_walks, "culture" to R.string.interest_culture)) { (key, label) ->
                    FilterChip(selected = interest == key, onClick = { interest = key }, label = { Text(stringResource(label)) })
                }
            }
        }
        if (interest == "all" || interest == "culture") {
            item {
                Text(stringResource(R.string.culture_news_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.culture_news_subtitle), style = MaterialTheme.typography.bodyMedium)
                if (state.cultureNewsItems.isEmpty()) Text(stringResource(R.string.culture_news_empty))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.cultureNewsItems, key = { it.id }) { news ->
                        CultureNewsCard(news, onOpenUrl)
                    }
                }
            }
        }
        if (interest == "all" || interest == "mountains") {
        item {
            Text(stringResource(R.string.discovery_cameras), style = MaterialTheme.typography.titleLarge)
            if (cameras.isEmpty()) Text(stringResource(R.string.no_live_cameras))
            else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cameras, key = { it.second.id }) { (station, camera) ->
                        FilterChip(selected = camera.id == selected?.second?.id, onClick = { selectedCamera = camera.id },
                            label = { Text("${station.name} · ${camera.name}") })
                    }
                }
                selected?.let { (station, camera) ->
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            key(camera.id) { CameraFrame(camera) }
                            Text(stringResource(R.string.camera_provider_note), style = MaterialTheme.typography.bodySmall)
                            StationConditions(station, onOpenUrl)
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.discovery_conditions), style = MaterialTheme.typography.titleLarge) }
        item {
            if (state.stationItems.isEmpty()) Text(stringResource(R.string.discovery_no_conditions))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.stationItems, key = { it.id }) { station ->
                    Card(Modifier.width(290.dp), shape = RoundedCornerShape(20.dp)) {
                        DiscoveryPhoto(station.imageBase64, station.name)
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            StationConditions(station, onOpenUrl)
                            PhotoCredit(station.photoCredit, station.photoSourceUrl, onOpenUrl)
                        }
                    }
                }
            }
            TextButton(onClick = onOpenStations) { Text(stringResource(R.string.featured_stations)) }
        }
        }
        if (interest != "mountains") {
        item {
            Text(stringResource(R.string.discovery_year_round), style = MaterialTheme.typography.titleLarge)
            if (state.grenoblePlaces.isEmpty()) Text(stringResource(R.string.discovery_no_places))
        }
        items(state.grenoblePlaces.filter {
            interest == "all" || (interest == "culture" && it.slug != "paul-mistral") ||
                (interest == "walks" && it.slug != "dauphinois")
        }, key = { "place-${it.id}" }) { place ->
            Card(shape = RoundedCornerShape(22.dp)) {
                DiscoveryPhoto(place.imageBase64, place.name)
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge)
                    Text(if (french) place.descriptionFr else place.descriptionEn)
                    Text(if (french) place.walkFr else place.walkEn, style = MaterialTheme.typography.bodyMedium)
                    PhotoCredit(place.photoCredit, place.photoSourceUrl, onOpenUrl)
                    OutlinedButton(onClick = { onOpenUrl(place.sourceUrl) }) { Text(stringResource(R.string.discovery_visit_info)) }
                }
            }
        }
        }
    }
}

@Composable
private fun CultureNewsCard(news: fr.grenobleski.nativeapp.data.model.SkiNewsItem, onOpenUrl: (String) -> Unit) {
    val image = rememberMarketplaceImage(news.imageUrl, "culture-${news.id}-${news.imageUrl}", 640, 360)
    Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        if (image != null) Image(image, null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(news.sourceName, style = MaterialTheme.typography.labelLarge)
            Text(news.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.culture_published, news.publishedAtLabel, news.language.uppercase()),
                style = MaterialTheme.typography.labelSmall)
            if (news.summary.isNotBlank()) Text(news.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            Button(onClick = { onOpenUrl(news.link) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.culture_read_article))
            }
        }
    }
}

@Composable
private fun DiscoveryPhoto(data: String, name: String) {
    val bitmap = remember(data) { runCatching {
        val bytes = Base64.decode(data.substringAfter("base64,", data), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull() }
    if (bitmap != null) Image(bitmap, name, Modifier.fillMaxWidth().height(185.dp), contentScale = ContentScale.Crop)
}

@Composable
internal fun PhotoCredit(credit: String, source: String, onOpenUrl: (String) -> Unit) {
    if (credit.isNotBlank()) TextButton(onClick = { onOpenUrl(source) }) {
        Text(credit, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StationConditions(station: StationItem, onOpenUrl: (String) -> Unit) {
    Text(if (station.temperature.isBlank()) stringResource(R.string.discovery_no_temperature)
         else "${station.temperature} °C · ${station.weather}", style = MaterialTheme.typography.titleMedium)
    if (station.weatherObservedAt.isNotBlank()) Text(stringResource(R.string.conditions_observed, station.weatherObservedAt), style = MaterialTheme.typography.labelSmall)
    if (station.weatherSource.isNotBlank()) Text("OpenWeather", style = MaterialTheme.typography.labelSmall)
    val label = when (station.skiAssessment) {
        "closed" -> R.string.ski_closed
        "stale" -> R.string.ski_stale
        "unfavourable" -> R.string.ski_unfavourable
        "check_bulletin" -> R.string.ski_check_bulletin
        else -> R.string.ski_unknown
    }
    Text(stringResource(label), color = MaterialTheme.colorScheme.primary)
    if (station.observedAt.isNotBlank()) Text(stringResource(R.string.conditions_observed, station.observedAt), style = MaterialTheme.typography.labelSmall)
    if (station.conditionSource.isNotBlank()) TextButton(onClick = { onOpenUrl(station.conditionSource) }) {
        Text(stringResource(R.string.conditions_bulletin))
    }
}

@Composable
internal fun CameraFrame(camera: StationCameraItem) {
    var failed by remember(camera.cameraUrl) { mutableStateOf(false) }
    var loading by remember(camera.cameraUrl) { mutableStateOf(true) }
    var attempt by remember(camera.cameraUrl) { mutableIntStateOf(0) }
    val uri = Uri.parse(camera.cameraUrl)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
        Text(stringResource(R.string.camera_unavailable)); return
    }
    if (failed) {
        Text(stringResource(R.string.camera_unavailable))
        OutlinedButton(onClick = { failed = false; loading = true; attempt++ }) { Text(stringResource(R.string.refresh)) }
    } else key(camera.cameraUrl, attempt) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (camera.cameraType == "hls_stream" || uri.path.orEmpty().endsWith(".m3u8")) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                factory = { context -> android.widget.VideoView(context).apply {
                    setMediaController(android.widget.MediaController(context).also { it.setAnchorView(this) })
                    setVideoURI(uri)
                    setOnPreparedListener { loading = false; start() }
                    setOnErrorListener { _, _, _ -> failed = true; loading = false; true }
                } },
                onRelease = { it.stopPlayback() },
            )
        } else {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme != "https"
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                            if (request.isForMainFrame) { failed = true; loading = false }
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) { failed = true; loading = false }
                        }
                    }
                    val path = uri.path.orEmpty().lowercase()
                    if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")) {
                        // Snapshot timestamp belongs to the provider image, not to our refresh time.
                        val escaped = org.json.JSONObject.quote(camera.cameraUrl)
                        loadDataWithBaseURL(camera.cameraUrl, """
                            <html><meta name="viewport" content="width=device-width,initial-scale=1">
                            <body style="margin:0;background:#112735"><img id="cam" alt="Webcam" style="width:100%;height:100vh;object-fit:contain">
                            <div id="status" hidden style="position:absolute;top:8px;color:white">Webcam unavailable / indisponible</div>
                            <script>const u=$escaped;const i=document.getElementById('cam');
                            function refresh(){i.src=u+(u.includes('?')?'&':'?')+'refresh='+Date.now();}
                            i.onerror=()=>{document.getElementById('status').hidden=false;};
                            i.onload=()=>{document.getElementById('status').hidden=true;};
                            refresh();setInterval(refresh,60000);</script>
                            </body></html>
                        """.trimIndent(), "text/html", "UTF-8", null)
                    } else loadUrl(camera.cameraUrl)
                }
            },
            onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
        )
        }
    }
}
