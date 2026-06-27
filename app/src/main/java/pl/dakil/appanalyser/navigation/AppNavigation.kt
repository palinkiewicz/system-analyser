package pl.dakil.appanalyser.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun AppNavigation(viewModel: AppAnalyzerViewModel, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
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
