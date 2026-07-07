package pl.dakil.appanalyser.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.HomeLayoutRepository
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.ui.screens.AppDetailsScreen
import pl.dakil.appanalyser.ui.screens.AppListScreen
import pl.dakil.appanalyser.ui.screens.DeviceInfoScreen
import pl.dakil.appanalyser.ui.screens.HomeScreen
import pl.dakil.appanalyser.ui.screens.SettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.AppearanceSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.DeviceInfoSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.HomeSettingsScreen
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel
import java.util.UUID

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceInfo : Screen("device?tab={tab}") {
        /** Without a tab the screen keeps (or restores) its current page. */
        fun createRoute(tab: Int = -1) = if (tab >= 0) "device?tab=$tab" else "device"
    }
    object AppList : Screen("apps")
    object Settings : Screen("settings")
    object AppDetails : Screen("app_details/{packageName}") {
        fun createRoute(packageName: String) = "app_details/$packageName"
    }
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsDeviceInfo : Screen("settings/device_info")
    object SettingsHome : Screen("settings/home")
}

/** The four bottom-bar destinations; every other route is a pushed screen without the bar. */
private val tabRoutes = setOf(
    Screen.Home.route,
    Screen.DeviceInfo.route,
    Screen.AppList.route,
    Screen.Settings.route,
)

private data class BottomNavItem(
    val screen: Screen,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.DeviceInfo, R.string.nav_device, Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid),
    BottomNavItem(Screen.AppList, R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
    BottomNavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

private const val TRANSITION_DURATION_MS = 500
private const val FADE_THROUGH_DURATION_MS = 220
private const val FADE_THROUGH_OUT_MS = 90

private fun NavBackStackEntry.isTabRoot() = destination.route in tabRoutes

@Composable
fun AppNavigation(viewModel: AppAnalyzerViewModel, navController: NavHostController = rememberNavController()) {
    val animationSpec = tween<Float>(TRANSITION_DURATION_MS)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen owns its top app bar and insets; the outer scaffold only
        // contributes the bottom bar, so zero its content insets to avoid
        // double status-bar/navigation-bar padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in tabRoutes,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = when (item.screen) {
                                    Screen.DeviceInfo -> Screen.DeviceInfo.createRoute()
                                    else -> item.screen.route
                                }
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // Paint the container with the screen background so no white window flashes through the
            // cross-fade while both screens are partially transparent.
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The bottom bar already covers the navigation-bar area; consume its padding
                // so screens don't add their own bottom inset on top of it.
                .consumeWindowInsets(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            // Switching between bottom-bar tabs plays Material fade-through (no direction);
            // pushed destinations keep the Material 3 shared-axis X used before.
            enterTransition = {
                if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughEnter()
                else slideIntoContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                    fadeIn(animationSpec)
            },
            exitTransition = {
                if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughExit()
                else slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                    fadeOut(animationSpec)
            },
            popEnterTransition = {
                if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughEnter()
                else slideIntoContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                    fadeIn(animationSpec)
            },
            popExitTransition = {
                if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughExit()
                else slideOutOfContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                    fadeOut(animationSpec)
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenDeviceTab = { tab ->
                        navController.navigate(Screen.DeviceInfo.createRoute(tab)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.DeviceInfo.route,
                arguments = listOf(navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = -1
                })
            ) { backStackEntry ->
                val context = LocalContext.current
                val addedMessage = stringResource(R.string.device_added_to_home)
                DeviceInfoScreen(
                    initialTab = backStackEntry.arguments?.getInt("tab") ?: -1,
                    onAddToHome = { type, sensor ->
                        HomeLayoutRepository.get(context).addWidget(
                            HomeWidget(
                                id = UUID.randomUUID().toString(),
                                type = type,
                                sensorType = sensor?.type,
                                sensorName = sensor?.name,
                                columnSpan = if (type == HomeWidgetType.BATTERY_POWER) 2 else 1,
                                rowSpan = if (type == HomeWidgetType.BATTERY_INFO) 2 else 1,
                            )
                        )
                        scope.launch { snackbarHostState.showSnackbar(addedMessage) }
                    }
                )
            }
            composable(Screen.AppList.route) {
                AppListScreen(
                    viewModel = viewModel,
                    onNavigateToDetails = { packageName ->
                        navController.navigate(Screen.AppDetails.createRoute(packageName))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                    onNavigateToDeviceInfoSettings = { navController.navigate(Screen.SettingsDeviceInfo.route) },
                    onNavigateToHomeSettings = { navController.navigate(Screen.SettingsHome.route) }
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
            composable(Screen.SettingsAppearance.route) {
                AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsDeviceInfo.route) {
                DeviceInfoSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsHome.route) {
                HomeSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

private fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS)
        )

private fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_THROUGH_OUT_MS))
