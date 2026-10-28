package pl.dakil.appanalyser.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import pl.dakil.appanalyser.data.PinnedNotificationsRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.service.PinnedNotificationsManager
import pl.dakil.appanalyser.ui.components.canPostNotifications
import pl.dakil.appanalyser.ui.components.filesystem.ShareSessionDialogHost
import pl.dakil.appanalyser.ui.screens.AppDetailsScreen
import pl.dakil.appanalyser.ui.screens.AppListScreen
import pl.dakil.appanalyser.ui.screens.DeviceInfoScreen
import pl.dakil.appanalyser.ui.screens.DiskUsageScreen
import pl.dakil.appanalyser.ui.screens.FileEditorScreen
import pl.dakil.appanalyser.ui.screens.FileSystemScreen
import pl.dakil.appanalyser.ui.screens.HomeScreen
import pl.dakil.appanalyser.ui.screens.SettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.AppearanceSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.DeviceInfoSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.FileSystemSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.HomeSettingsScreen
import pl.dakil.appanalyser.ui.screens.settings.NotificationsSettingsScreen
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel
import java.util.UUID

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object DeviceInfo : Screen("device?tab={tab}") {
        /** Without a tab the screen keeps (or restores) its current page. */
        fun createRoute(tab: Int = -1) = if (tab >= 0) "device?tab=$tab" else "device"
    }
    object AppList : Screen("apps")
    object FileSystem : Screen("files?path={path}") {
        /** Without a path the browser keeps (or restores) wherever it was. */
        fun createRoute(path: String? = null) =
            if (path != null) "files?path=${Uri.encode(path)}" else "files"
    }
    object Settings : Screen("settings")
    object DiskUsage : Screen("files/usage?path={path}&rescan={rescan}") {
        /**
         * Without a path the analyser opens on its volume list. [rescan] scans [path] afresh even
         * when an older scan covers it — set once the user confirmed that in the browser.
         */
        fun createRoute(path: String? = null, rescan: Boolean = false) =
            if (path != null) "files/usage?path=${Uri.encode(path)}&rescan=$rescan" else "files/usage"
    }
    object FileEditor : Screen("files/edit/{path}") {
        /** Encoded so the separators inside an absolute path don't split the route. */
        fun createRoute(path: String) = "files/edit/${Uri.encode(path)}"
    }
    object AppDetails : Screen("app_details/{packageName}") {
        fun createRoute(packageName: String) = "app_details/$packageName"
    }
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsDeviceInfo : Screen("settings/device_info")
    object SettingsHome : Screen("settings/home")
    object SettingsNotifications : Screen("settings/notifications")
    object SettingsFileSystem : Screen("settings/file_system")
}

/**
 * The bottom-bar destinations; every other route is a pushed screen without the bar. File system
 * is always a tab root — the experimental flag only decides whether its item is rendered, so
 * transitions and bar visibility stay correct while the user is standing on it.
 */
private val tabRoutes = setOf(
    Screen.Home.route,
    Screen.DeviceInfo.route,
    Screen.AppList.route,
    Screen.FileSystem.route,
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
    BottomNavItem(Screen.FileSystem, R.string.nav_files, Icons.Filled.Folder, Icons.Outlined.Folder),
    BottomNavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

private const val TRANSITION_DURATION_MS = 500
private const val FADE_THROUGH_DURATION_MS = 220
private const val FADE_THROUGH_OUT_MS = 90

private fun NavBackStackEntry.isTabRoot() = destination.route in tabRoutes

@Composable
fun AppNavigation(
    viewModel: AppAnalyzerViewModel,
    shareDialogRequested: Boolean,
    onShareDialogShown: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val animationSpec = tween<Float>(TRANSITION_DURATION_MS)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings = SettingsRepository.get(LocalContext.current)
    val fileSystemEnabled by settings.fileSystemEnabled.collectAsState()

    // Turning the experimental feature off while standing on its tab would strand the user on a
    // screen with no way back to it in the bar.
    LaunchedEffect(fileSystemEnabled, currentRoute) {
        if (!fileSystemEnabled && currentRoute?.startsWith("files") == true) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    // Above the NavHost rather than inside the Files tab, so a tap on the share notification can
    // bring it back over whichever screen is open.
    ShareSessionDialogHost(shareDialogRequested, onShareDialogShown)

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
                    bottomNavItems.filter {
                        it.screen != Screen.FileSystem || fileSystemEnabled
                    }.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = when (item.screen) {
                                    Screen.DeviceInfo -> Screen.DeviceInfo.createRoute()
                                    Screen.FileSystem -> Screen.FileSystem.createRoute()
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
                            // No restoreState: restoring would revive the entry with its old
                            // arguments and discard the requested tab.
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
                val pinnedMessage = stringResource(R.string.device_pinned_to_notifications)
                val unpinnedMessage = stringResource(R.string.device_unpinned_from_notifications)
                val permissionMessage =
                    stringResource(R.string.device_notification_permission_required)
                val permissionAction =
                    stringResource(R.string.device_notification_permission_settings)

                fun pin(type: HomeWidgetType, sensor: SensorInfo?) {
                    PinnedNotificationsManager.pin(context, type, sensor)
                    scope.launch { snackbarHostState.showSnackbar(pinnedMessage) }
                }

                // The card awaiting the permission result, so a granted dialog completes the pin
                // the user actually asked for.
                var pendingPin by remember { mutableStateOf<Pair<HomeWidgetType, SensorInfo?>?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    val pending = pendingPin
                    pendingPin = null
                    if (granted && pending != null) {
                        pin(pending.first, pending.second)
                    } else {
                        scope.launch {
                            // Android shows the system dialog at most twice, after which requests
                            // are auto-denied — offer the app's notification settings instead.
                            val result = snackbarHostState.showSnackbar(
                                message = permissionMessage,
                                actionLabel = permissionAction
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.openNotificationSettings()
                            }
                        }
                    }
                }

                DeviceInfoScreen(
                    initialTab = backStackEntry.arguments?.getInt("tab") ?: -1,
                    // Only offered while the File system feature (and so the analyser) exists.
                    onAnalyseStorage = if (fileSystemEnabled) {
                        { navController.navigate(Screen.DiskUsage.createRoute()) }
                    } else {
                        null
                    },
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
                    },
                    onTogglePin = { type, sensor ->
                        val repository = PinnedNotificationsRepository.get(context)
                        when {
                            repository.isPinned(type, sensor?.type) -> {
                                PinnedNotificationsManager.unpin(context, type, sensor?.type)
                                scope.launch { snackbarHostState.showSnackbar(unpinnedMessage) }
                            }
                            // Pinning without the permission would start a foreground service whose
                            // notifications the system silently drops — ask first, pin after.
                            context.canPostNotifications() -> pin(type, sensor)
                            else -> {
                                pendingPin = type to sensor
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
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
                    onNavigateToHomeSettings = { navController.navigate(Screen.SettingsHome.route) },
                    onNavigateToNotificationsSettings = {
                        navController.navigate(Screen.SettingsNotifications.route)
                    },
                    onNavigateToFileSystemSettings = {
                        navController.navigate(Screen.SettingsFileSystem.route)
                    }
                )
            }
            composable(
                route = Screen.FileSystem.route,
                arguments = listOf(navArgument("path") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                FileSystemScreen(
                    onOpenEditor = { path ->
                        navController.navigate(Screen.FileEditor.createRoute(path))
                    },
                    onOpenDiskUsage = { path, rescan ->
                        navController.navigate(Screen.DiskUsage.createRoute(path, rescan))
                    },
                    initialPath = backStackEntry.arguments?.getString("path"),
                )
            }
            composable(
                route = Screen.DiskUsage.route,
                arguments = listOf(
                    navArgument("path") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("rescan") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                )
            ) {
                DiskUsageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenEditor = { path ->
                        navController.navigate(Screen.FileEditor.createRoute(path))
                    },
                    onShowInBrowser = { path ->
                        navController.navigate(Screen.FileSystem.createRoute(path)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // No restoreState: restoring would revive the entry with its old
                            // arguments and discard the requested folder.
                        }
                    },
                )
            }
            composable(Screen.FileEditor.route) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: return@composable
                FileEditorScreen(
                    path = path,
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
            composable(Screen.SettingsAppearance.route) {
                AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsDeviceInfo.route) {
                DeviceInfoSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsHome.route) {
                HomeSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsNotifications.route) {
                NotificationsSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.SettingsFileSystem.route) {
                FileSystemSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

private fun Context.openNotificationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS)
        )

private fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_THROUGH_OUT_MS))
