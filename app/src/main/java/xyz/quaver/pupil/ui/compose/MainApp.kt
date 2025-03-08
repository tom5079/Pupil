package xyz.quaver.pupil.ui.compose

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import androidx.window.layout.DisplayFeature
import xyz.quaver.pupil.ui.viewmodel.SearchState

@Composable
fun MainApp(
    uiState: SearchState,
    displayFeatures: List<DisplayFeature>,
    navController: NavController = rememberNavController(),
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
) {
    Text("Hello, World!")
}