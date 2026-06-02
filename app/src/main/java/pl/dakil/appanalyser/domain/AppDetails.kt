package pl.dakil.appanalyser.domain

data class AppDetails(
    val appInfo: AppInfo,
    val sizeBytes: Long,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val techStack: TechStack
)
