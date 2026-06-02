package pl.dakil.appanalyser.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.domain.AppDetails
import pl.dakil.appanalyser.domain.AppInfo
import pl.dakil.appanalyser.domain.TechStack
import java.io.File
import java.util.zip.ZipInputStream

class AppAnalyzerRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledApps(showSystemApps: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_META_DATA
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(flags)
        }

        installedApps.mapNotNull { appInfo ->
            val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isSystemFlag = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            val isUserApp = !isSystemFlag || isUpdatedSystemApp
            val isPureSystemApp = isSystemFlag && !isUpdatedSystemApp
            
            // If showSystemApps is false, we want User Apps.
            // If showSystemApps is true, we want System Apps.
            if (showSystemApps && !isPureSystemApp) {
                return@mapNotNull null
            }
            if (!showSystemApps && !isUserApp) {
                return@mapNotNull null
            }

            AppInfo(
                name = appInfo.loadLabel(packageManager).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(packageManager),
                isSystemApp = isPureSystemApp
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun analyzeApp(packageName: String): AppDetails? = withContext(Dispatchers.IO) {
        try {
            val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val appInfo: ApplicationInfo = packageInfo.applicationInfo ?: return@withContext null
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val basicInfo = AppInfo(
                name = appInfo.loadLabel(packageManager).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(packageManager),
                isSystemApp = isSystemApp
            )

            val sourceDir = appInfo.sourceDir
            val file = File(sourceDir)
            val sizeBytes = if (file.exists()) file.length() else 0L

            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0
            val targetSdk = appInfo.targetSdkVersion

            val firstInstallTime = packageInfo.firstInstallTime
            val lastUpdateTime = packageInfo.lastUpdateTime

            val techStacks = determineTechStack(sourceDir)

            AppDetails(
                appInfo = basicInfo,
                sizeBytes = sizeBytes,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                techStacks = techStacks
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun determineTechStack(apkPath: String): List<TechStack> {
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return listOf(TechStack.UNKNOWN)

        val detectedStacks = mutableSetOf<TechStack>()

        try {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name

                    if (name.contains("libflutter.so") || name.contains("libapp.so") || name.startsWith("assets/flutter_assets/")) {
                        detectedStacks.add(TechStack.FLUTTER)
                    }
                    if (name.contains("libreactnativejni.so") || name.contains("libhermes.so") || name == "assets/index.android.bundle") {
                        detectedStacks.add(TechStack.REACT_NATIVE)
                    }
                    if (name.contains("META-INF/androidx.compose.ui_ui.version") || name.contains("META-INF/androidx.compose.runtime_runtime.version")) {
                        detectedStacks.add(TechStack.JETPACK_COMPOSE)
                    }
                    if (name.contains("libmonosgen-2.0.so") || name.contains("libassemblies.arm64-v8a.blob.so")) {
                        detectedStacks.add(TechStack.XAMARIN)
                    }
                    if (name == "assets/www/index.html" || name == "assets/www/cordova.js") {
                        detectedStacks.add(TechStack.CORDOVA)
                    }
                    if (name.contains("libNativeScript.so")) {
                        detectedStacks.add(TechStack.NATIVESCRIPT)
                    }
                    if (name.contains("libunity.so") || name.startsWith("assets/bin/Data/")) {
                        detectedStacks.add(TechStack.UNITY)
                    }
                    if (name.contains("libUnrealEngine.so") || name.contains("libUE4.so")) {
                        detectedStacks.add(TechStack.UNREAL_ENGINE)
                    }
                    if (name.contains("libgodot_android.so") || name == "project.godot") {
                        detectedStacks.add(TechStack.GODOT)
                    }
                    if (name.startsWith("assets/compose-resources/")) {
                        detectedStacks.add(TechStack.COMPOSE_MULTIPLATFORM)
                    }
                    if (name.startsWith("META-INF/") && name.endsWith(".kotlin_module")) {
                        detectedStacks.add(TechStack.KOTLIN_MULTIPLATFORM)
                    }
                    if (name.contains("libtauri.so") || name == "assets/public/index.html") {
                        detectedStacks.add(TechStack.TAURI)
                    }
                    if (name.contains("libv8android.so") || name == "assets/app/bundle.js") {
                        detectedStacks.add(TechStack.SVELTE_NATIVE)
                    }
                    if (name.contains("libtitanium.so") || name.contains("AssetCryptImpl.class")) {
                        detectedStacks.add(TechStack.TITANIUM)
                    }
                    if (name.contains("libQt6Core.so") || name.contains("libQt6Gui.so") || name == "assets/qt.conf") {
                        detectedStacks.add(TechStack.QT)
                    }
                    if (name.contains("libpython3") || name.contains("libmain.so") || name == "assets/private.mp3" || name == "assets/private.tar") {
                        detectedStacks.add(TechStack.KIVY)
                    }
                    if (name.contains("libpython3") || name == "assets/python/stdlib.zip") {
                        detectedStacks.add(TechStack.BEEWARE)
                    }
                    if (name.contains("librubymotion.so") || name.contains("libpayload.so") || name.contains("rb_")) {
                        detectedStacks.add(TechStack.RUBYMOTION)
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (detectedStacks.isEmpty()) {
            detectedStacks.add(TechStack.NATIVE)
        }

        return detectedStacks.toList()
    }
}
