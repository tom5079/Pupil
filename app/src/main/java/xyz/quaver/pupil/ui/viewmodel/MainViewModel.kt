package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.quaver.pupil.networking.SearchQuery
import xyz.quaver.pupil.ui.composable.MainDestination
import xyz.quaver.pupil.ui.composable.mainDestinations

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUIState())
    val uiState: StateFlow<MainUIState> = _uiState

    fun navigateToDestination(destination: MainDestination) {
        _uiState.value = MainUIState(
            currentDestination = destination
        )
    }
}

data class MainUIState(
    val currentDestination: MainDestination = mainDestinations.first(),
    val query: SearchQuery? = null,
    val loading: Boolean = true
)