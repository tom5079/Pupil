package xyz.quaver.pupil.ui.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import xyz.quaver.pupil.R

sealed interface MainDestination {
    val route: String
    val icon: ImageVector
    val textId: Int

    object Search: MainDestination {
        override val route = "search"
        override val icon = Icons.Default.Search
        override val textId = R.string.main_destination_search
    }

    object History: MainDestination {
        override val route = "history"
        override val icon = Icons.Default.History
        override val textId = R.string.main_destination_history
    }

    object Downloads: MainDestination {
        override val route = "downloads"
        override val icon = Icons.Default.Download
        override val textId = R.string.main_destination_downloads
    }

    object Favorites: MainDestination {
        override val route = "favorites"
        override val icon = Icons.Default.Favorite
        override val textId = R.string.main_destination_favorites
    }
}

val mainDestinations = listOf(
    MainDestination.Search,
    MainDestination.History,
    MainDestination.Downloads,
    MainDestination.Favorites
)