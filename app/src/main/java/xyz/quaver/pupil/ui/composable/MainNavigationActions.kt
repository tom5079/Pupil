package xyz.quaver.pupil.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.quaver.pupil.R

sealed interface MainDestination {
    val route: String
    val icon: ImageVector
    val textId: Int

    data object Search: MainDestination {
        override val route = "search"
        override val icon = Icons.Default.Search
        override val textId = R.string.main_destination_search
    }

    data object History: MainDestination {
        override val route = "history"
        override val icon = Icons.Default.History
        override val textId = R.string.main_destination_history
    }

    data object Downloads: MainDestination {
        override val route = "downloads"
        override val icon = Icons.Default.Download
        override val textId = R.string.main_destination_downloads
    }

    data object Favorites: MainDestination {
        override val route = "favorites"
        override val icon = Icons.Default.Favorite
        override val textId = R.string.main_destination_favorites
    }

    data object Settings: MainDestination {
        override val route = "settings"
        override val icon = Icons.Default.Settings
        override val textId = R.string.main_destination_settings
    }

    class ImageViewer(galleryID: String): MainDestination {
        override val route = "image_viewer/$galleryID"
        override val icon = Icons.AutoMirrored.Filled.MenuBook
        override val textId = R.string.main_destination_image_viewer

        companion object {
            val commonRoute = "image_viewer/{galleryID}"
        }
    }
}

val mainDestinations = listOf(
    MainDestination.Search,
    MainDestination.History,
    MainDestination.Downloads,
    MainDestination.Favorites,
    MainDestination.Settings
)