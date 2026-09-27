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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Composable
internal fun DiscoveryHome(
    state: AppUiState,
    onOpenStations: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val french = LocalConfiguration.current.locales[0].language == "fr"
    val bestStation = remember(state.stationItems) {
        state.stationItems.maxWithOrNull(
            compareBy<StationItem> { stationScore(it) }.thenBy { -distanceValue(it) }
        )
    }
    val cameras = state.stationItems.flatMap { station -> station.cameras.map { station to it } }
    var selectedCameraId by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().clipToBounds().background(Color(0xFFF7FBFF)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(392.dp)
                    .clip(RoundedCornerShape(30.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.grenoble_alps_hero),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x4D05233B), Color(0xE8072D50)))),
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.weight(1f).height(92.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                        )
                        Column(
                            modifier = Modifier.widthIn(max = 136.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            HeroUserBadge(state = state, onClick = onOpenProfile)
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xB3175791))) {
                                Text(stringResource(R.string.home_today), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xBD083D70)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF92D8FF), modifier = Modifier.size(22.dp))
                                Text("Grenoble · Isère", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("☀", style = MaterialTheme.typography.displayMedium)
                                Spacer(Modifier.width(10.dp))
                                Text(bestStation?.temperature?.takeIf { it.isNotBlank() }?.let { "$it°" } ?: "—°", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        bestStation?.weather?.ifBlank { stringResource(R.string.home_mountain_subtitle) } ?: stringResource(R.string.home_mountain_subtitle),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        bestStation?.name ?: stringResource(R.string.discovery_no_conditions),
                                        color = Color.White.copy(alpha = .8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WeatherMetric("❄", stringResource(R.string.home_condition), bestStation?.skiAssessment?.replace('_', ' ').orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                                WeatherMetric("↝", stringResource(R.string.home_distance), bestStation?.distanceLabel.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                                WeatherMetric("▲", stringResource(R.string.home_altitude), bestStation?.altitudeLabel.orEmpty().ifBlank { "—" }, Modifier.weight(1.25f))
                            }
                        }
                    }
                }
            }
        }
        item {
            HomeSectionHeader(Icons.Filled.Landscape, stringResource(R.string.home_conditions_title), onOpenStations)
        }
        item {
            if (state.stationItems.isEmpty()) Text(stringResource(R.string.discovery_no_conditions))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.stationItems.sortedWith(compareByDescending<StationItem> { stationScore(it) }.thenBy { distanceValue(it) }).take(8), key = { it.id }) { station ->
                    Card(Modifier.width(188.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                        DiscoveryPhoto(station.imageBase64, station.name, 108.dp)
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(station.altitudeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("☀", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(7.dp))
                                Text(station.temperature.takeIf { it.isNotBlank() }?.let { "$it°" } ?: "—°", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text("❄ ${station.skiAssessment.replace('_', ' ')} · ${station.distanceLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            HomeSectionHeader(Icons.Filled.CameraAlt, stringResource(R.string.home_webcams_title), onOpenStations)
        }
        item {
            if (cameras.isEmpty()) Text(stringResource(R.string.no_live_cameras))
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cameras.take(8), key = { it.second.id }) { (station, camera) ->
                    HomeCameraCard(station.name, station.imageBase64, camera) {
                        selectedCameraId = camera.id
                    }
                }
            }
        }
        item {
            HomeSectionHeader(Icons.Filled.Article, stringResource(R.string.home_news_title)) { }
        }
        item {
            if (state.skiNewsItems.isEmpty()) Text(stringResource(R.string.no_station_rss_news))
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.skiNewsItems.take(8), key = { it.id }) { news -> HomeNewsCard(news, onOpenUrl) }
            }
        }
        item {
            HomeSectionHeader(Icons.Filled.Event, stringResource(R.string.home_grenoble_events_title)) { }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.home_grenoble_events_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.cultureNewsItems.isEmpty()) {
                    Text(stringResource(R.string.home_grenoble_events_empty))
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.cultureNewsItems.take(8), key = { it.id }) { event ->
                            HomeEventCard(event, onOpenUrl)
                        }
                    }
                }
            }
        }
        item {
            HomeSectionHeader(Icons.Filled.Apartment, stringResource(R.string.home_grenoble_info_title)) { }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { GrenobleInfoCard("●", stringResource(R.string.home_traffic), stringResource(R.string.home_traffic_status), "A48, A41") }
                item { GrenobleInfoCard("▣", stringResource(R.string.home_transport), "Réseau TAG", stringResource(R.string.home_normal_service)) }
                item { GrenobleInfoCard("▦", stringResource(R.string.home_events), stringResource(R.string.home_events_now), stringResource(R.string.home_see_agenda)) }
            }
        }
    }
    selectedCameraId?.let { cameraId ->
        CameraBrowserDialog(
            cameras = cameras,
            initialCameraId = cameraId,
            onDismiss = { selectedCameraId = null },
        )
    }
}

@Composable
private fun HeroUserBadge(state: AppUiState, onClick: () -> Unit) {
    val profile = state.profileInfo
    val displayName = profile?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: state.session?.displayName?.takeIf { it.isNotBlank() }
        ?: state.session?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.profile)
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 136.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD9083D70)),
    ) {
        Row(
            modifier = Modifier.padding(start = 7.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                UserAvatar(
                    displayName = displayName,
                    photoBase64 = profile?.profilePictureBase64.orEmpty(),
                    photoUrl = profile?.googleProfilePictureUrl.orEmpty(),
                    size = 40.dp,
                )
            }
            Text(
                text = displayName.substringBefore(' ').take(14),
                modifier = Modifier.weight(1f, fill = false),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(icon: ImageVector, title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = Color(0xFF0A4E91), modifier = Modifier.size(26.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color(0xFF082E68), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all) + "  ›", maxLines = 1) }
    }
}

@Composable
private fun HomeCameraCard(
    stationName: String,
    stationImageBase64: String,
    camera: StationCameraItem,
    onClick: () -> Unit,
) {
    var resolvedPreviewUrl by remember(camera.cameraUrl, camera.thumbnailUrl) {
        mutableStateOf(camera.thumbnailUrl.takeIf { it.isNotBlank() })
    }
    LaunchedEffect(camera.cameraUrl, camera.thumbnailUrl) {
        if (resolvedPreviewUrl.isNullOrBlank()) {
            resolvedPreviewUrl = resolveCameraPreviewUrl(camera.cameraUrl)
        }
    }
    Card(
        modifier = Modifier.width(276.dp).height(172.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            DiscoveryPhoto(stationImageBase64, camera.name, 172.dp)
            resolvedPreviewUrl?.takeIf { it.isNotBlank() }?.let { previewUrl ->
                AsyncImage(
                    model = previewUrl,
                    contentDescription = camera.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9001428)))))
            Text("● LIVE", modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xFFFF4D55), RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(stationName, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(camera.name, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun CamerasTab(state: AppUiState) {
    val cameras = state.stationItems.flatMap { station -> station.cameras.map { station to it } }
    var selectedCameraId by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.home_webcams_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(cameras, key = { it.second.id }) { (station, camera) ->
            HomeCameraCard(station.name, station.imageBase64, camera) {
                selectedCameraId = camera.id
            }
        }
    }
    selectedCameraId?.let { cameraId ->
        CameraBrowserDialog(
            cameras = cameras,
            initialCameraId = cameraId,
            onDismiss = { selectedCameraId = null },
        )
    }
}

@Composable
private fun CameraBrowserDialog(
    cameras: List<Pair<StationItem, StationCameraItem>>,
    initialCameraId: Int,
    onDismiss: () -> Unit,
) {
    if (cameras.isEmpty()) return
    var currentCameraId by remember(initialCameraId, cameras) { mutableIntStateOf(initialCameraId) }
    val currentIndex = cameras.indexOfFirst { it.second.id == currentCameraId }.takeIf { it >= 0 } ?: 0
    val (station, camera) = cameras[currentIndex]
    var viewerPreviewUrl by remember(camera.id, camera.cameraUrl, camera.thumbnailUrl) {
        mutableStateOf(camera.thumbnailUrl.takeIf { it.isNotBlank() })
    }
    val stationBitmap = remember(station.imageBase64) {
        runCatching {
            val bytes = Base64.decode(station.imageBase64.substringAfter("base64,", station.imageBase64), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    LaunchedEffect(camera.id, camera.cameraUrl, camera.thumbnailUrl) {
        viewerPreviewUrl = camera.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: resolveCameraPreviewUrl(camera.cameraUrl)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5FAFF)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF063A70)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(station.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(camera.name, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text(
                        text = "${currentIndex + 1}/${cameras.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF071B2D))) {
                    viewerPreviewUrl?.let { previewUrl ->
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = camera.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } ?: run {
                        if (stationBitmap != null) {
                            Image(
                                bitmap = stationBitmap,
                                contentDescription = station.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.grenoble_alps_hero),
                                contentDescription = station.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "● LIVE",
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp).background(Color(0xFFFF4D55), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().height(205.dp).background(Color.White).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { currentCameraId = cameras[(currentIndex - 1 + cameras.size) % cameras.size].second.id },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.previous_page))
                        }
                        Button(
                            onClick = { currentCameraId = cameras[(currentIndex + 1) % cameras.size].second.id },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.next_page))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                    Text(stringResource(R.string.camera_choose), style = MaterialTheme.typography.labelLarge, color = Color(0xFF063A70))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cameras, key = { it.second.id }) { (candidateStation, candidateCamera) ->
                            FilterChip(
                                selected = candidateCamera.id == camera.id,
                                onClick = { currentCameraId = candidateCamera.id },
                                label = { Text(candidateStation.name, maxLines = 1) },
                            )
                        }
                    }
                    Text(stringResource(R.string.camera_provider_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private suspend fun resolveCameraPreviewUrl(cameraUrl: String): String? = withContext(Dispatchers.IO) {
    val source = runCatching { URI(cameraUrl) }.getOrNull() ?: return@withContext null
    if (source.scheme != "https" || source.host.isNullOrBlank()) return@withContext null
    val lowerPath = source.path.orEmpty().lowercase()
    if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".png") || lowerPath.endsWith(".webp")) {
        return@withContext cameraUrl
    }

    runCatching {
        val connection = URL(cameraUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 GrenobleSki/1.0")
        connection.inputStream.bufferedReader().use { reader ->
            val html = buildString {
                val buffer = CharArray(8_192)
                while (length < 512_000) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    append(buffer, 0, read)
                    if (contains("</head>", ignoreCase = true)) break
                }
            }
            val propertyFirst = Regex(
                """<meta[^>]+(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["'][^>]+content\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )
            val contentFirst = Regex(
                """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["'](?:og:image|twitter:image)["']""",
                RegexOption.IGNORE_CASE,
            )
            val candidate = propertyFirst.find(html)?.groupValues?.getOrNull(1)
                ?: contentFirst.find(html)?.groupValues?.getOrNull(1)
                ?: return@use null
            val clean = candidate.replace("&amp;", "&").trim()
            source.resolve(clean).toString().takeIf { it.startsWith("https://") }
        }.also { connection.disconnect() }
    }.getOrNull()
}

@Composable
private fun HomeNewsCard(news: fr.grenobleski.nativeapp.data.model.SkiNewsItem, onOpenUrl: (String) -> Unit) {
    Card(modifier = Modifier.width(246.dp).clickable { onOpenUrl(news.link) }, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        AsyncImage(
            model = news.imageUrl,
            contentDescription = news.title,
            modifier = Modifier.fillMaxWidth().height(112.dp),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.grenoble_alps_hero),
            error = painterResource(R.drawable.grenoble_alps_hero),
            fallback = painterResource(R.drawable.grenoble_alps_hero),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(news.sourceName.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(news.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3)
            Text(news.publishedAtLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomeEventCard(event: fr.grenobleski.nativeapp.data.model.SkiNewsItem, onOpenUrl: (String) -> Unit) {
    Card(
        modifier = Modifier.width(286.dp).clickable { onOpenUrl(event.link) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Box {
            AsyncImage(
                model = event.imageUrl,
                contentDescription = event.title,
                modifier = Modifier.fillMaxWidth().height(126.dp),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.grenoble_alps_hero),
                error = painterResource(R.drawable.grenoble_alps_hero),
                fallback = painterResource(R.drawable.grenoble_alps_hero),
            )
            Text(
                text = event.sourceName,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0xE60A4E91), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
            if (event.summary.isNotBlank()) {
                Text(event.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(event.publishedAtLabel, color = Color(0xFF0A4E91), style = MaterialTheme.typography.labelMedium)
                Text("›", color = Color(0xFF0A4E91), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GrenobleInfoCard(icon: String, title: String, status: String, detail: String) {
    Card(modifier = Modifier.width(208.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text(icon, color = Color(0xFF082E68), style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(status, color = Color(0xFF169B45), style = MaterialTheme.typography.bodySmall)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WeatherMetric(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("$icon  $label", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
private fun DiscoveryPhoto(data: String, name: String, height: Dp = 185.dp) {
    val bitmap = remember(data) { runCatching {
        val bytes = Base64.decode(data.substringAfter("base64,", data), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull() }
    if (bitmap != null) {
        Image(bitmap, name, Modifier.fillMaxWidth().height(height), contentScale = ContentScale.Crop)
    } else {
        Image(
            painter = painterResource(R.drawable.grenoble_alps_hero),
            contentDescription = name,
            modifier = Modifier.fillMaxWidth().height(height),
            contentScale = ContentScale.Crop,
        )
    }
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
internal fun CameraFrame(
    camera: StationCameraItem,
    modifier: Modifier = Modifier.fillMaxWidth().height(240.dp),
) {
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
                modifier = modifier,
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
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
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
