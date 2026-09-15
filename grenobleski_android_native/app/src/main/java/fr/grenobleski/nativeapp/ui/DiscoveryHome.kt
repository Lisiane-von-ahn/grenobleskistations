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
import coil.compose.AsyncImage
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem
import com.google.gson.JsonParser
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DiscoveryHome(state: AppUiState, onOpenStations: () -> Unit, onOpenUrl: (String) -> Unit) {
    val french = LocalConfiguration.current.locales[0].language == "fr"
    var interest by rememberSaveable { mutableStateOf("all") }
    var stationSort by rememberSaveable { mutableStateOf("recommended") }
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
        item { AccommodationPreview(onOpenUrl) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.discovery_conditions), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = stationSort == "recommended", onClick = { stationSort = "recommended" }, label = { Text(stringResource(R.string.sort_recommended)) })
                    FilterChip(selected = stationSort == "distance", onClick = { stationSort = "distance" }, label = { Text(stringResource(R.string.sort_distance)) })
                }
            }
        }
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
        item {
            if (state.stationItems.isEmpty()) Text(stringResource(R.string.discovery_no_conditions))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items((if (stationSort == "distance") state.stationItems.sortedBy { distanceValue(it) } else state.stationItems.sortedWith(compareByDescending<StationItem> { stationScore(it) }.thenBy { distanceValue(it) })), key = { it.id }) { station ->
                    Card(Modifier.width(290.dp), shape = RoundedCornerShape(20.dp)) {
                        DiscoveryPhoto(station.imageBase64, station.name)
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium)
                            StationConditions(station, onOpenUrl)
                            StationRouteButtons(station, onOpenUrl)
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
                    OutlinedButton(onClick = { onOpenUrl("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(place.name + ", Grenoble")}&travelmode=walking") }) {
                        Text(stringResource(R.string.walk_to_place))
                    }
                }
            }
        }
        item { HikingTrailsSection(onOpenUrl) }
        }
    }
}

@Composable
private fun AccommodationPreview(onOpenUrl: (String) -> Unit) {
    val fallbackStays = listOf(
        AccommodationStay("OKKO Hotels Grenoble Centre", 118, "https://www.booking.com/hotel/fr/okko-hotels-grenoble-jardin-hoche.fr.html", listOf("https://hapi.mmcreation.com/hapidam/f8521bfa-5ad0-4688-85c8-b67836f820ac/okko-hotels-officielles-grenoble-jardin-hoche-029.jpg?w=960&h=960&mode=ratio&coi=50%2C50", "https://hapi.mmcreation.com/hapidam/4dd71f70-d360-4125-b8ca-158c92e20304/Grenoble_chambre_classique5.jpg.jpg?size=lg")),
        AccommodationStay("Joy Villard de Lans", 112, "https://www.booking.com/hotel/fr/roseraie.fr.html", emptyList()),
        AccommodationStay("Grandes Rousses Hotel & Spa", 189, "https://www.booking.com/hotel/fr/grandes-rousses.fr.html", listOf("https://www.hotelgrandesrousses.com/_novaimg/galleria/1534878.jpg", "https://media.grenoble-tourisme.com/photos/structure_33495/40506006.jpg")),
    )
    var stays by remember { mutableStateOf(fallbackStays) }
    var maximumPrice by rememberSaveable { mutableFloatStateOf(200f) }
    var accommodationPage by rememberSaveable { mutableIntStateOf(0) }
    var checkin by rememberSaveable { mutableStateOf("") }
    var checkout by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val payload = URL("https://www.grenobleski.fr/api/accommodations/").readText()
                JsonParser.parseString(payload).asJsonObject.getAsJsonArray("results").mapNotNull { element ->
                    val row = element.asJsonObject
                    val name = row.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val website = row.get("website_url")?.asString.orEmpty()
                    val url = website.ifBlank { "https://www.booking.com/searchresults.html?ss=${Uri.encode(name)}" }
                    val photos = row.getAsJsonArray("image_urls")?.mapNotNull { it.asString?.takeIf(String::isNotBlank) }.orEmpty()
                    AccommodationStay(name, 0, url, photos)
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { stays = it; accommodationPage = 0 }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.accommodation_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.accommodation_subtitle), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = checkin,
                onValueChange = { checkin = formatDayMonthYear(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.accommodation_checkin)) },
                placeholder = { Text("dd/mm/yyyy") },
            )
            OutlinedTextField(
                value = checkout,
                onValueChange = { checkout = formatDayMonthYear(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.accommodation_checkout)) },
                placeholder = { Text("dd/mm/yyyy") },
            )
        }
        Text(stringResource(R.string.accommodation_max_price, maximumPrice.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(value = maximumPrice, onValueChange = { maximumPrice = it }, valueRange = 60f..500f, steps = 43)
        val filteredStays = stays.filter { it.price == 0 || it.price <= maximumPrice.toInt() }
        val pageSize = 6
        val pageCount = maxOf(1, (filteredStays.size + pageSize - 1) / pageSize)
        val safePage = accommodationPage.coerceIn(0, pageCount - 1)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filteredStays.drop(safePage * pageSize).take(pageSize)) { stay ->
                Card(Modifier.width(250.dp), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stay.photos.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(stay.photos) { photo ->
                                    AsyncImage(model = photo, contentDescription = stay.name, modifier = Modifier.width(222.dp).height(140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                        Text(stay.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (stay.price > 0) stringResource(R.string.accommodation_from_price, stay.price) else stringResource(R.string.accommodation_check_price), style = MaterialTheme.typography.bodySmall)
                        Text("OpenStreetMap", style = MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick = { onOpenUrl(bookingSearchUrl(stay.name, checkin, checkout)) }) {
                            Text(stringResource(R.string.accommodation_view_establishment))
                        }
                    }
                }
            }
        }
        if (pageCount > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { accommodationPage = safePage - 1 }, enabled = safePage > 0) { Text(stringResource(R.string.previous_page)) }
                Text("${safePage + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { accommodationPage = safePage + 1 }, enabled = safePage + 1 < pageCount) { Text(stringResource(R.string.next_page)) }
            }
        }
    }
}

private data class AccommodationStay(val name: String, val price: Int, val url: String, val photos: List<String>)

private fun formatDayMonthYear(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(8)
    return listOf(digits.take(2), digits.drop(2).take(2), digits.drop(4).take(4))
        .filter(String::isNotEmpty)
        .joinToString("/")
}

private fun bookingSearchUrl(name: String, checkin: String, checkout: String): String {
    fun isoDate(value: String): String? {
        val parts = value.split('/')
        if (parts.size != 3 || parts[0].length != 2 || parts[1].length != 2 || parts[2].length != 4) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        if (day !in 1..31 || month !in 1..12) return null
        return "${parts[2]}-${parts[1]}-${parts[0]}"
    }
    val arrival = isoDate(checkin)
    val departure = isoDate(checkout)
    val params = mutableListOf("ss=${Uri.encode(name)}", "group_adults=2", "no_rooms=1")
    if (arrival != null) params += "checkin=$arrival"
    if (departure != null) params += "checkout=$departure"
    return "https://www.booking.com/searchresults.html?${params.joinToString("&")}"
}

@Composable
private fun HikingTrailsSection(onOpenUrl: (String) -> Unit) {
    val trails = listOf(
        Triple("Bastille – Fort de la Bastille", "Grenoble · facile · 5 km", "Fort de la Bastille, Grenoble"),
        Triple("Mont Jalla – mémorial", "Bastille · intermédiaire · 7 km", "Mont Jalla, Grenoble"),
        Triple("Bastille – Quais de l’Isère", "Grenoble · facile · 4 km", "Quai Stéphane Jay, Grenoble"),
        Triple("Le Moucherotte", "Vercors · difficile · vérifier les conditions", "Le Moucherotte, France"),
    )
    Text(stringResource(R.string.hiking_trails_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.hiking_trails_subtitle), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onOpenUrl("https://www.grenobleski.fr/trail-maps/") }) { Text(stringResource(R.string.open_trail_maps)) }
        OutlinedButton(onClick = { onOpenUrl("https://www.grenobleski.fr/mountain-tips/") }) { Text(stringResource(R.string.open_mountain_tips)) }
        OutlinedButton(onClick = { onOpenUrl("https://www.grenobleski.fr/accommodations/") }) { Text(stringResource(R.string.open_accommodations)) }
    }
    trails.forEach { (name, details, destination) ->
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpenUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}") }) { Text(stringResource(R.string.hiking_open_map)) }
                    TextButton(onClick = { onOpenUrl("https://www.openstreetmap.org/search?query=${Uri.encode(destination)}") }) { Text(stringResource(R.string.hiking_open_osm)) }
                }
            }
        }
    }
}

@Composable
private fun StationRouteButtons(station: StationItem, onOpenUrl: (String) -> Unit) {
    val lat = station.latitude ?: return
    val lon = station.longitude ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.getting_there_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.getting_there_subtitle), style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("driving" to R.string.route_car, "transit" to R.string.route_transit, "bicycling" to R.string.route_bike, "walking" to R.string.route_walk)) { (mode, label) ->
                OutlinedButton(onClick = { onOpenUrl("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=$mode") }) { Text(stringResource(label)) }
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
    if (station.skiAssessment != "closed" && station.skiAssessment != "stale") {
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.conditions_ready_today)) })
    }
    if (station.observedAt.isBlank() && station.weatherObservedAt.isBlank()) {
        Text(stringResource(R.string.conditions_update_unknown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (station.observedAt.isNotBlank()) Text(stringResource(R.string.conditions_observed, station.observedAt), style = MaterialTheme.typography.labelSmall)
    if (station.conditionSource.isNotBlank()) TextButton(onClick = { onOpenUrl(station.conditionSource) }) {
        Text(stringResource(R.string.conditions_bulletin))
    }
}

private fun distanceValue(station: StationItem): Double = station.distanceLabel.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: Double.MAX_VALUE

private fun stationScore(station: StationItem): Int = when (station.skiAssessment) {
    "closed" -> 0
    "stale" -> 1
    "unfavourable" -> 2
    "check_bulletin" -> 3
    else -> 4
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
