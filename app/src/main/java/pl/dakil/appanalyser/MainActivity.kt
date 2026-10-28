package pl.dakil.appanalyser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.navigation.AppNavigation
import pl.dakil.appanalyser.service.PinnedNotificationsManager
import pl.dakil.appanalyser.service.ShareServerService
import pl.dakil.appanalyser.ui.theme.AppAnalyserTheme
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

class MainActivity : ComponentActivity() {

    /** Set by a tap on the share notification, cleared once the session dialog has been re-shown. */
    private var shareDialogRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity gets its launch intent again; only a fresh launch should act on it.
        if (savedInstanceState == null) handleIntent(intent)
        // Restores pinned notifications if the service was killed from outside (task manager,
        // OEM battery manager, or a boot where the receiver was blocked). No-op when none exist.
        PinnedNotificationsManager.syncOnAppStart(this)
        val settings = SettingsRepository.get(this)
        setContent {
            val colorTheme by settings.colorTheme.collectAsState()
            val darkThemeOption by settings.darkThemeOption.collectAsState()
            val pureBlack by settings.pureBlack.collectAsState()
            AppAnalyserTheme(
                colorTheme = colorTheme,
                darkThemeOption = darkThemeOption,
                pureBlack = pureBlack
            ) {
                val viewModel: AppAnalyzerViewModel = viewModel()
                AppNavigation(
                    viewModel = viewModel,
                    shareDialogRequested = shareDialogRequested,
                    onShareDialogShown = { shareDialogRequested = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Reopening from Recents replays the intent that last launched the task.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent.action == ShareServerService.ACTION_SHOW_SESSION && !fromHistory) {
            shareDialogRequested = true
        }
    }
}
