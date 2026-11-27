package fr.grenobleski.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.grenobleski.nativeapp.AppUiState
import fr.grenobleski.nativeapp.BuildConfig
import fr.grenobleski.nativeapp.R
import fr.grenobleski.nativeapp.data.model.StationItem
import fr.grenobleski.nativeapp.data.network.GrenobleSkiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import com.google.gson.JsonObject

@Composable
internal fun StationBulletin(station: StationItem, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (station.offline) {
            val date = if (station.savedAt > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(station.savedAt)) else ""
            Text(stringResource(R.string.planning_offline) + " " + date, style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.planning_snow_base) + " " + formatStationMeasurement(station.snowBase, "cm") + station.baseAltitude.takeIf { it.isNotBlank() }?.let { " ($it m)" }.orEmpty(), Modifier.weight(1f))
            Text(stringResource(R.string.planning_snow_top) + " " + formatStationMeasurement(station.snowTop, "cm") + station.topAltitude.takeIf { it.isNotBlank() }?.let { " ($it m)" }.orEmpty(), Modifier.weight(1f))
        }
        Text(stringResource(R.string.planning_lifts) + " " + station.liftsOpen.ifBlank { "—" } + " · " +
            stringResource(R.string.planning_pistes) + " " + station.pistesOpen.ifBlank { "—" })
        if (station.observedAt.isNotBlank()) Text(station.observedAt, style = MaterialTheme.typography.labelSmall)
        if (station.conditionSource.startsWith("https://")) TextButton(onClick = { onOpenUrl(station.conditionSource) }) {
            Text(stringResource(R.string.station_access_bulletin))
        }
        if (station.operationalNotice.isNotBlank()) Text(station.operationalNotice)
    }
}

@Composable
internal fun StationPlanner(state: AppUiState, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val service = remember { GrenobleSkiApiClient.createService(BuildConfig.API_BASE_URL) }
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val token = state.session?.token.orEmpty()
    val scope = rememberCoroutineScope()
    var favorites by remember { mutableStateOf(setOf<Int>()) }
    var alerts by remember { mutableStateOf(listOf<JsonObject>()) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var openOnly by remember { mutableStateOf(false) }
    var beginnerOnly by remember { mutableStateOf(false) }
    var busOnly by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf("") }
    var order by remember { mutableIntStateOf(0) }
    var routeId by remember { mutableStateOf<Int?>(null) }
    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("station-planner", android.content.Context.MODE_PRIVATE)
    val accountKey = state.session?.userId?.toString() ?: "guest"
    LaunchedEffect(token) {
        favorites = prefs.getStringSet("favorites-$accountKey", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        if (token.isNotBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    val response = service.listResource("$base/api/station-favorites/", "Token $token")
                    if (!response.isSuccessful) error = true
                    if (response.isSuccessful) {
                        favorites = response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("station_ids")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asString.toIntOrNull() }?.toSet().orEmpty()
                        prefs.edit().putStringSet("favorites-$accountKey", favorites.map { it.toString() }.toSet()).apply()
                    }
                    val alertResponse = service.listResource("$base/api/station-alerts/", "Token $token")
                    if (alertResponse.isSuccessful) alerts = alertResponse.body()?.takeIf { it.isJsonArray }?.asJsonArray?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
                }
            } catch (_: IOException) { error = true }
            catch (_: com.google.gson.JsonParseException) { error = true }
        }
    }
    fun price(station: StationItem): Double =
        Regex("[0-9]+(?:,[0-9]+)?").find(station.access?.adultDayPrice.orEmpty())?.value?.replace(',', '.')?.toDoubleOrNull() ?: Double.MAX_VALUE
    val items = state.stationItems.filter {
        it.name.contains(query, ignoreCase = true) && (!favoritesOnly || it.id in favorites) &&
            (!openOnly || (!it.offline && it.access?.status == "open")) &&
            (!busOnly || it.hasBus) && (!beginnerOnly || it.beginnerPistes > 0) && (budget.toDoubleOrNull()?.let { limit -> price(it) <= limit } ?: true)
    }.sortedWith(compareBy {
        when (order) {
            1 -> Regex("[0-9]+(?:,[0-9]+)?").find(it.access?.adultDayPrice.orEmpty())?.value?.replace(',', '.')?.toDoubleOrNull() ?: Double.MAX_VALUE
            2 -> -(it.snowDepth.toDoubleOrNull() ?: -1.0)
            else -> it.distanceLabel.toDoubleOrNull() ?: Double.MAX_VALUE
        }
    })
    routeId?.let { id -> StationRouteDialog(id) { routeId = null } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    Text(stringResource(R.string.planning_compare), style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_stations)) }, modifier = Modifier.fillMaxWidth())
                    Row {
                        FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, label = { Text(stringResource(R.string.planning_favorites)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(openOnly, { openOnly = !openOnly }, label = { Text(stringResource(R.string.station_access_open)) })
                    }
                    FilterChip(beginnerOnly, { beginnerOnly = !beginnerOnly }, label = { Text(stringResource(R.string.partner_level_beginner)) })
                    FilterChip(busOnly, { busOnly = !busOnly }, label = { Text(stringResource(R.string.planning_bus)) })
                    OutlinedTextField(budget, { budget = it.filter { char -> char.isDigit() || char == '.' }.take(7) },
                        label = { Text(stringResource(R.string.planning_budget)) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { order = (order + 1) % 3 }) {
                        Text(stringResource(listOf(R.string.distance_from_grenoble, R.string.planning_price, R.string.snow_depth)[order]))
                    }
                    if (error) Text(stringResource(R.string.planning_sync_error))
                    alerts.filter { it.get("read")?.asBoolean != true }.take(10).forEach { alert ->
                        val kind = when (alert.get("kind")?.asString) {
                            "opening" -> R.string.planning_alert_open
                            "snow" -> R.string.planning_alert_snow
                            else -> R.string.planning_alert_notice
                        }
                        Text(alert.get("station_name").asString + " · " + stringResource(kind), style = MaterialTheme.typography.bodySmall)
                    }
                    if (alerts.any { it.get("read")?.asBoolean != true }) {
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val ids = alerts.mapNotNull { it.get("id")?.asInt }
                                    val response = withContext(Dispatchers.IO) {
                                        service.postResource("$base/api/station-alerts/", "Token $token", mapOf("ids" to ids))
                                    }
                                    if (response.isSuccessful) alerts = emptyList() else error = true
                                } catch (_: IOException) { error = true }
                                finally { busy = false }
                            }
                        }) { Text(stringResource(R.string.planning_read)) }
                    }
                }
                items(items, key = { it.id }) { station ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(station.name, style = MaterialTheme.typography.titleLarge)
                            Text(station.distanceLabel + " km")
                            if (token.isNotBlank()) {
                                OutlinedButton(enabled = !busy, onClick = {
                                    val enabled = station.id !in favorites
                                    scope.launch {
                                        busy = true
                                        error = false
                                        try {
                                            val response = withContext(Dispatchers.IO) {
                                                service.postResource("$base/api/station-favorites/", "Token $token", mapOf("station_id" to station.id, "enabled" to enabled))
                                            }
                                            if (response.isSuccessful) {
                                                favorites = response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("station_ids")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asString.toIntOrNull() }?.toSet().orEmpty()
                                                prefs.edit().putStringSet("favorites-$accountKey", favorites.map { it.toString() }.toSet()).apply()
                                            } else error = true
                                        } catch (_: IOException) { error = true }
                                        catch (_: com.google.gson.JsonParseException) { error = true }
                                        finally { busy = false }
                                    }
                                }) {
                                    Text(stringResource(if (station.id in favorites) R.string.planning_unfavorite else R.string.planning_favorite))
                                }
                            }
                            StationWeatherMetrics(station.temperature, station.snowDepth, station.snowDepthEstimated)
                            StationBulletin(station, onOpenUrl)
                            StationAccessSummary(if (station.offline) station.access?.copy(status = "unknown") else station.access, onOpenUrl)
                            StationCrowdSummary(station.crowd)
                            Button(onClick = { routeId = station.id }) { Text(stringResource(R.string.planning_roundtrip)) }
                        }
                    }
                }
            }
        }
    }
}
