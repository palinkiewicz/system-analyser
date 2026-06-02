package pl.dakil.appanalyser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.navigation.AppNavigation
import pl.dakil.appanalyser.ui.theme.AppAnalyserTheme
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppAnalyserTheme {
                val viewModel: AppAnalyzerViewModel = viewModel()
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}