package xyz.quaver.pupil.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.adaptive.calculateDisplayFeatures
import xyz.quaver.pupil.ui.compose.MainApp
import xyz.quaver.pupil.ui.theme.AppTheme
import xyz.quaver.pupil.ui.viewmodel.MainViewModel
import xyz.quaver.pupil.util.requestNotificationPermission
import xyz.quaver.pupil.util.showNotificationPermissionExplanationDialog

class MainComposeActivity : BaseComponentActivity() {
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                showNotificationPermissionExplanationDialog(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission(this, requestNotificationPermissionLauncher, false)

        val viewModel: MainViewModel by viewModels()

        setContent {
            val displayFeatures = calculateDisplayFeatures(this)

            val uiState by viewModel.searchState.collectAsStateWithLifecycle()

            AppTheme {
                MainApp(
                    uiState = uiState,
                    displayFeatures = displayFeatures
                )
            }
        }
    }
}