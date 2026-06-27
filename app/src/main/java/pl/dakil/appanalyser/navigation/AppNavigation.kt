package pl.dakil.appanalyser.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.dakil.appanalyser.ui.screens.AppDetailsScreen
import pl.dakil.appanalyser.ui.screens.AppListScreen
import pl.dakil.appanalyser.ui.screens.DeviceInfoScreen
import pl.dakil.appanalyser.ui.screens.HomeScreen
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AppList : Screen("app_list")
    object AppDetails : Screen("app_details/{packageName}") {
        fun createRoute(packageName: String) = "app_details/$packageName"
    }
    object DeviceInfo : Screen("device_info")
}

private const val TRANSITION_DURATION_MS = 500

@Composable
fun AppNavigation(viewModel: AppAnalyzerViewModel, navController: NavHostController = rememberNavController()) {
    val animationSpec = tween<Float>(TRANSITION_DURATION_MS)

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        // Paint the container with the screen background so no white window flashes through the
        // cross-fade while both screens are partially transparent.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Material 3 shared-axis X: the outgoing screen slides toward the start while the incoming
        // one enters from the end; back navigation plays the same motion in reverse.
        enterTransition = {
            slideIntoContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                fadeIn(animationSpec)
        },
        exitTransition = {
            slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                fadeOut(animationSpec)
        },
        popEnterTransition = {
            slideIntoContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                fadeIn(animationSpec)
        },
        popExitTransition = {
            slideOutOfContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                fadeOut(animationSpec)
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAppList = { navController.navigate(Screen.AppList.route) },
                onNavigateToDeviceInfo = { navController.navigate(Screen.DeviceInfo.route) }
            )
        }
        composable(Screen.AppList.route) {
            AppListScreen(
                viewModel = viewModel,
                onNavigateToDetails = { packageName ->
                    navController.navigate(Screen.AppDetails.createRoute(packageName))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AppDetails.route) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
            AppDetailsScreen(
                viewModel = viewModel,
                packageName = packageName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.DeviceInfo.route) {
            DeviceInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
