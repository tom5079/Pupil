package xyz.quaver.pupil.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _uiState
}

data class SearchState(val stub: String = "")