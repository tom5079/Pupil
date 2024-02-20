package xyz.quaver.pupil.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.quaver.pupil.R

data class MainDestination(
    val route: String,
    val icon: ImageVector,
    val textId: Int
)

val mainDestinations = listOf(
    MainDestination(
        "search",
        Icons.Default.Search,
        R.string.main_destination_search
    ),
    MainDestination(
        "history",
        Icons.Default.History,
        R.string.main_destination_history
    ),
    MainDestination(
        "downloads",
        Icons.Default.Download,
        R.string.main_destination_downloads
    ),
    MainDestination(
        "favorites",
        Icons.Default.Star,
        R.string.main_destination_favorites
    ),
)