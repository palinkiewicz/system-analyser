package pl.dakil.appanalyser.domain

data class DetectedFramework(
    val techStack: TechStack,
    val probability: Int,
    val matchedFiles: List<String>
)

data class AppDetails(
    val appInfo: AppInfo,
    val sizeBytes: Long,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val detectedFrameworks: List<DetectedFramework>
)

