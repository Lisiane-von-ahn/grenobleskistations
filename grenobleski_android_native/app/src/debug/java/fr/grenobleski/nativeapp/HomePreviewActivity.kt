package fr.grenobleski.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import fr.grenobleski.nativeapp.data.model.SkiNewsItem
import fr.grenobleski.nativeapp.data.model.StationCameraItem
import fr.grenobleski.nativeapp.data.model.StationItem
import fr.grenobleski.nativeapp.data.model.ProfileInfo
import fr.grenobleski.nativeapp.ui.DiscoveryHome
import fr.grenobleski.nativeapp.ui.theme.GrenobleSkiNativeTheme

/** Debug-only screen used to validate the home layout on a real Android emulator. */
class HomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
        setContent {
            GrenobleSkiNativeTheme {
                Surface {
                    HomePreview()
                }
            }
        }
    }
}

private data class PreviewNavigationItem(
    val label: Int,
    val icon: ImageVector,
)

private val previewNavigation = listOf(
    PreviewNavigationItem(R.string.nav_home, Icons.Filled.Home),
    PreviewNavigationItem(R.string.stations, Icons.Filled.Terrain),
    PreviewNavigationItem(R.string.nav_webcams, Icons.Filled.CameraAlt),
    PreviewNavigationItem(R.string.nav_news_short, Icons.Filled.Article),
    PreviewNavigationItem(R.string.nav_menu, Icons.Filled.MoreHoriz),
)

@Composable
private fun HomePreview() {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White, tonalElevation = 10.dp) {
                previewNavigation.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == 0,
                        onClick = {},
                        icon = { Icon(item.icon, contentDescription = stringResource(item.label)) },
                        label = { Text(stringResource(item.label)) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color(0xFF0B6FE8),
                            selectedTextColor = androidx.compose.ui.graphics.Color(0xFF0B6FE8),
                            indicatorColor = androidx.compose.ui.graphics.Color(0xFFE2F0FF),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            DiscoveryHome(
                state = previewState,
                onOpenStations = {},
                onOpenProfile = {},
                onOpenUrl = {},
            )
        }
    }
}

private val previewStations = listOf(
    StationItem(
        id = 1,
        name = "Chamrousse",
        altitudeLabel = "1 650 – 2 250 m",
        distanceLabel = "30 km",
        capacityLabel = "90 km de pistes",
        imageBase64 = "",
        temperature = "-3",
        weather = "Ciel dégagé",
        skiAssessment = "excellent",
        cameras = listOf(StationCameraItem(11, "Croix de Chamrousse", "https://www.skaping.com/chamrousse/la-croix", cameraType = "embedded")),
    ),
    StationItem(
        id = 2,
        name = "Les 2 Alpes",
        altitudeLabel = "1 650 – 3 600 m",
        distanceLabel = "70 km",
        capacityLabel = "200 km de pistes",
        imageBase64 = "",
        temperature = "-6",
        weather = "Ensoleillé",
        skiAssessment = "très bon",
        cameras = listOf(StationCameraItem(21, "Secteur Glacier", "https://www.skaping.com/les2alpes/3200m", cameraType = "embedded")),
    ),
    StationItem(
        id = 3,
        name = "Alpe d’Huez",
        altitudeLabel = "1 860 – 3 330 m",
        distanceLabel = "63 km",
        capacityLabel = "250 km de pistes",
        imageBase64 = "",
        temperature = "-4",
        weather = "Peu nuageux",
        skiAssessment = "excellent",
        cameras = listOf(StationCameraItem(31, "Pic Blanc · 3 330 m", "https://www.skaping.com/alpedhuez/pic-blanc", cameraType = "embedded")),
    ),
    StationItem(
        id = 4,
        name = "Villard-de-Lans",
        altitudeLabel = "1 050 – 2 050 m",
        distanceLabel = "35 km",
        capacityLabel = "125 km de pistes",
        imageBase64 = "",
        temperature = "-1",
        weather = "Nuageux",
        skiAssessment = "bon",
    ),
)

private val previewNews = listOf(
    SkiNewsItem(1, "Excellent enneigement dans le massif de Belledonne", "", "", "Enneigement", "fr", null, "", "Aujourd’hui", "", true),
    SkiNewsItem(2, "Ouverture complète du domaine des 2 Alpes", "", "", "Domaines", "fr", 2, "Les 2 Alpes", "Hier", "", true),
    SkiNewsItem(3, "Les plus belles descentes autour de Grenoble", "", "", "Ski", "fr", null, "", "Cette semaine", "", false),
)

private val previewEvents = listOf(
    SkiNewsItem(101, "Une nouvelle exposition à découvrir au cœur de Grenoble", "Visites, rencontres et activités pour découvrir les collections.", "https://www.museedegrenoble.fr/", "Musée de Grenoble", "fr", null, "", "Aujourd’hui", "", false),
    SkiNewsItem(102, "Spectacles et rendez-vous de la semaine à la MC2", "La programmation culturelle grenobloise à ne pas manquer.", "https://www.mc2grenoble.fr/", "MC2 Grenoble", "fr", null, "", "Hier", "", false),
    SkiNewsItem(103, "Ateliers scientifiques pour toute la famille", "Expériences et découvertes proposées par La Casemate.", "https://lacasemate.fr/", "La Casemate", "fr", null, "", "Cette semaine", "", false),
)

private val previewState = AppUiState(
    stationItems = previewStations,
    skiNewsItems = previewNews,
    cultureNewsItems = previewEvents,
    profileInfo = ProfileInfo(
        userId = 1,
        displayName = "Luc Martin",
        email = "luc@grenobleski.fr",
        username = "luc.martin",
    ),
)
