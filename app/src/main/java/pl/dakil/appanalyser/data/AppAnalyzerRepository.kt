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
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            if (!showSystemApps && isSystemApp) {
                return@mapNotNull null
            }
            
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (!showSystemApps && intent == null) {
                return@mapNotNull null
            }

            AppInfo(
                name = appInfo.loadLabel(packageManager).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(packageManager),
                isSystemApp = isSystemApp
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

            val techStack = determineTechStack(sourceDir)

            AppDetails(
                appInfo = basicInfo,
                sizeBytes = sizeBytes,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                techStack = techStack
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun determineTechStack(apkPath: String): TechStack {
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return TechStack.UNKNOWN

        var isFlutter = false
        var isReactNative = false
        var isCompose = false
        var isXamarin = false
        var isCordova = false
        var isNativeScript = false
        var isUnity = false
        var isUnreal = false
        var isGodot = false

        try {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name

                    if (name.contains("libflutter.so") || name.contains("libapp.so") || name.startsWith("assets/flutter_assets/")) {
                        isFlutter = true
                    }
                    if (name.contains("libreactnativejni.so") || name.contains("libhermes.so") || name == "assets/index.android.bundle") {
                        isReactNative = true
                    }
                    if (name.contains("META-INF/androidx.compose.ui_ui.version") || name.contains("META-INF/androidx.compose.runtime_runtime.version")) {
                        isCompose = true
                    }
                    if (name.contains("libmonosgen-2.0.so") || name.contains("libassemblies.arm64-v8a.blob.so")) {
                        isXamarin = true
                    }
                    if (name == "assets/www/index.html" || name == "assets/www/cordova.js") {
                        isCordova = true
                    }
                    if (name.contains("libNativeScript.so")) {
                        isNativeScript = true
                    }
                    if (name.contains("libunity.so") || name.startsWith("assets/bin/Data/")) {
                        isUnity = true
                    }
                    if (name.contains("libUnrealEngine.so") || name.contains("libUE4.so")) {
                        isUnreal = true
                    }
                    if (name.contains("libgodot_android.so") || name == "project.godot") {
                        isGodot = true
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return when {
            isUnity -> TechStack.UNITY
            isUnreal -> TechStack.UNREAL_ENGINE
            isGodot -> TechStack.GODOT
            isFlutter -> TechStack.FLUTTER
            isReactNative -> TechStack.REACT_NATIVE
            isXamarin -> TechStack.XAMARIN
            isCordova -> TechStack.CORDOVA
            isNativeScript -> TechStack.NATIVESCRIPT
            isCompose -> TechStack.JETPACK_COMPOSE
            else -> TechStack.NATIVE
        }
    }
}
