package pl.dakil.appanalyser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.navigation.AppNavigation
import pl.dakil.appanalyser.ui.theme.AppAnalyserTheme
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}