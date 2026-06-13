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
import pl.dakil.appanalyser.domain.DetectedFramework
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

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

            val detectedFrameworks = determineTechStack(sourceDir)

            AppDetails(
                appInfo = basicInfo,
                sizeBytes = sizeBytes,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                firstInstallTime = firstInstallTime,
                lastUpdateTime = lastUpdateTime,
                detectedFrameworks = detectedFrameworks
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun determineTechStack(apkPath: String): List<DetectedFramework> {
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return listOf(DetectedFramework(TechStack.UNKNOWN, 100, emptyList()))

        val detectors = createDetectors()

        try {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name

                    for (detector in detectors) {
                        detector.check(name)
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return calculateFrameworkProbabilities(detectors)
    }

    private fun calculateFrameworkProbabilities(detectors: List<FrameworkDetector>): List<DetectedFramework> {
        val activeDetectors = detectors.filter { it.hasMatches() }

        if (activeDetectors.isEmpty()) {
            return listOf(DetectedFramework(TechStack.NATIVE, 100, emptyList()))
        }

        val rawScores = activeDetectors.associate { it.techStack to it.calculateRawScore() }
        val maxCertainty = activeDetectors.maxOf { it.getMaxCertainty() }

        val nativeScore = (1.0f - maxCertainty).coerceAtLeast(0f)

        var totalScore = rawScores.values.sum()
        if (nativeScore > 0f) {
            totalScore += nativeScore
        }

        val results = mutableListOf<DetectedFramework>()

        for (detector in activeDetectors) {
            val rawScore = rawScores[detector.techStack] ?: 0f
            val probability = if (totalScore > 0f) (rawScore / totalScore * 100f) else 0f
            results.add(
                DetectedFramework(
                    techStack = detector.techStack,
                    probability = probability.roundToInt(),
                    matchedFiles = detector.getMatchedFiles()
                )
            )
        }

        if (nativeScore > 0f) {
            val probability = (nativeScore / totalScore * 100f)
            val probInt = probability.roundToInt()
            if (probInt > 0) {
                results.add(
                    DetectedFramework(
                        techStack = TechStack.NATIVE,
                        probability = probInt,
                        matchedFiles = emptyList()
                    )
                )
            }
        }

        results.sortByDescending { it.probability }

        val sum = results.sumOf { it.probability }
        if (sum != 100 && results.isNotEmpty()) {
            val diff = 100 - sum
            val highest = results.maxByOrNull { it.probability }
            if (highest != null) {
                val idx = results.indexOf(highest)
                results[idx] = highest.copy(probability = (highest.probability + diff).coerceAtLeast(0))
            }
        }

        return results
    }

    private fun createDetectors(): List<FrameworkDetector> {
        return listOf(
            FrameworkDetector(TechStack.FLUTTER, listOf(
                SignatureRule.Contains("libflutter.so", 1.0f),
                SignatureRule.Contains("libapp.so", 0.9f),
                SignatureRule.StartsWith("assets/flutter_assets/", 0.95f)
            )),
            FrameworkDetector(TechStack.REACT_NATIVE, listOf(
                SignatureRule.Contains("libreactnativejni.so", 1.0f),
                SignatureRule.Contains("libhermes.so", 1.0f),
                SignatureRule.Equals("assets/index.android.bundle", 0.95f)
            )),
            FrameworkDetector(TechStack.JETPACK_COMPOSE, listOf(
                SignatureRule.Contains("META-INF/androidx.compose.ui_ui.version", 0.95f),
                SignatureRule.Contains("META-INF/androidx.compose.runtime_runtime.version", 0.95f)
            )),
            FrameworkDetector(TechStack.XAMARIN, listOf(
                SignatureRule.Contains("libmonosgen-2.0.so", 1.0f),
                SignatureRule.Contains("libassemblies.arm64-v8a.blob.so", 1.0f)
            )),
            FrameworkDetector(TechStack.CORDOVA, listOf(
                SignatureRule.Equals("assets/www/index.html", 0.2f),
                SignatureRule.Equals("assets/www/cordova.js", 0.95f)
            )),
            FrameworkDetector(TechStack.NATIVESCRIPT, listOf(
                SignatureRule.Contains("libNativeScript.so", 1.0f)
            )),
            FrameworkDetector(TechStack.UNITY, listOf(
                SignatureRule.Contains("libunity.so", 1.0f),
                SignatureRule.StartsWith("assets/bin/Data/", 0.95f)
            )),
            FrameworkDetector(TechStack.UNREAL_ENGINE, listOf(
                SignatureRule.Contains("libUnrealEngine.so", 1.0f),
                SignatureRule.Contains("libUE4.so", 1.0f)
            )),
            FrameworkDetector(TechStack.GODOT, listOf(
                SignatureRule.Contains("libgodot_android.so", 1.0f),
                SignatureRule.Equals("project.godot", 0.95f)
            )),
            FrameworkDetector(TechStack.COMPOSE_MULTIPLATFORM, listOf(
                SignatureRule.StartsWith("assets/compose-resources/", 0.95f)
            )),
            FrameworkDetector(TechStack.KOTLIN_MULTIPLATFORM, listOf(
                SignatureRule.Custom({ it.startsWith("META-INF/") && it.endsWith(".kotlin_module") }, 0.9f)
            )),
            FrameworkDetector(TechStack.TAURI, listOf(
                SignatureRule.Contains("libtauri.so", 1.0f),
                SignatureRule.Equals("assets/public/index.html", 0.2f)
            )),
            FrameworkDetector(TechStack.SVELTE_NATIVE, listOf(
                SignatureRule.Contains("libv8android.so", 0.8f),
                SignatureRule.Equals("assets/app/bundle.js", 0.95f)
            )),
            FrameworkDetector(TechStack.TITANIUM, listOf(
                SignatureRule.Contains("libtitanium.so", 1.0f),
                SignatureRule.Contains("AssetCryptImpl.class", 0.9f)
            )),
            FrameworkDetector(TechStack.QT, listOf(
                SignatureRule.Contains("libQt6Core.so", 1.0f),
                SignatureRule.Contains("libQt6Gui.so", 1.0f),
                SignatureRule.Equals("assets/qt.conf", 0.9f)
            )),
            FrameworkDetector(TechStack.KIVY, listOf(
                SignatureRule.Contains("libpython3", 0.5f),
                SignatureRule.Contains("libmain.so", 0.4f),
                SignatureRule.Equals("assets/private.mp3", 0.9f),
                SignatureRule.Equals("assets/private.tar", 0.9f)
            )),
            FrameworkDetector(TechStack.BEEWARE, listOf(
                SignatureRule.Contains("libpython3", 0.5f),
                SignatureRule.Equals("assets/python/stdlib.zip", 0.95f)
            )),
            FrameworkDetector(TechStack.RUBYMOTION, listOf(
                SignatureRule.Contains("librubymotion.so", 1.0f),
                SignatureRule.Contains("libpayload.so", 0.9f),
                SignatureRule.Contains("rb_", 0.8f)
            ))
        )
    }
}

sealed class SignatureRule {
    abstract val weight: Float
    abstract fun matches(name: String): Boolean

    data class Equals(val path: String, override val weight: Float) : SignatureRule() {
        override fun matches(name: String) = name == path
    }

    data class Contains(val substring: String, override val weight: Float) : SignatureRule() {
        override fun matches(name: String) = name.contains(substring)
    }

    data class StartsWith(val prefix: String, override val weight: Float) : SignatureRule() {
        override fun matches(name: String) = name.startsWith(prefix)
    }

    data class Custom(val predicate: (String) -> Boolean, override val weight: Float) : SignatureRule() {
        override fun matches(name: String) = predicate(name)
    }
}

class FrameworkDetector(val techStack: TechStack, val rules: List<SignatureRule>) {
    val matchedFilesByRule = mutableMapOf<SignatureRule, MutableSet<String>>()

    fun check(name: String) {
        for (rule in rules) {
            if (rule.matches(name)) {
                matchedFilesByRule.getOrPut(rule) { mutableSetOf() }.add(name)
            }
        }
    }

    fun hasMatches(): Boolean = matchedFilesByRule.isNotEmpty()

    fun getMatchedFiles(): List<String> = matchedFilesByRule.values.flatten().sorted()

    fun calculateRawScore(): Float {
        if (!hasMatches()) return 0f
        val sumOfMatchedWeights = matchedFilesByRule.keys.sumOf { it.weight.toDouble() }.toFloat()
        return sumOfMatchedWeights / rules.size
    }

    fun getMaxCertainty(): Float {
        if (!hasMatches()) return 0f
        return matchedFilesByRule.keys.maxOf { it.weight }
    }
}

