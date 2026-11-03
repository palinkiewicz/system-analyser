package pl.dakil.appanalyser.domain

/**
 * The well-known folders Android keeps at the top of every shared volume (`Environment.DIRECTORY_*`),
 * plus the camera's own subfolders, which the browser badges with their own icon the way desktop
 * file managers do for their known folders.
 */
enum class StandardFolder {
    ALARMS, ANDROID, AUDIOBOOKS, CAMERA, DCIM, DOCUMENTS, DOWNLOAD, MOVIES, MUSIC, NOTIFICATIONS,
    PICTURES, PODCASTS, RECORDINGS, RINGTONES, SCREENSHOTS;

    companion object {
        private val TOP_LEVEL = mapOf(
            "alarms" to ALARMS,
            "android" to ANDROID,
            "audiobooks" to AUDIOBOOKS,
            "dcim" to DCIM,
            "documents" to DOCUMENTS,
            "download" to DOWNLOAD,
            "movies" to MOVIES,
            "music" to MUSIC,
            "notifications" to NOTIFICATIONS,
            "pictures" to PICTURES,
            "podcasts" to PODCASTS,
            "recordings" to RECORDINGS,
            "ringtones" to RINGTONES,
        )
        private val NESTED = mapOf(
            "dcim/camera" to CAMERA,
            "dcim/screenshots" to SCREENSHOTS,
            "pictures/screenshots" to SCREENSHOTS,
        )

        /** A folder one or two levels under a volume root, `/storage/emulated/<user>` or `/storage/<uuid>`. */
        private val VOLUME_CHILD = Regex("^/storage/(?:emulated/\\d+|(?!emulated/)[^/]+)/([^/]+)(?:/([^/]+))?$")

        /**
         * The standard folder at [path], or null for anything else. Only the volume's own copy counts:
         * a `Music` folder buried in some app's data is just a folder. Case is ignored, since FAT
         * formatted cards are often written by tools that don't keep it.
         */
        fun of(path: String): StandardFolder? {
            val (top, nested) = VOLUME_CHILD.matchEntire(path)?.destructured ?: return null
            return if (nested.isEmpty()) TOP_LEVEL[top.lowercase()] else NESTED["$top/$nested".lowercase()]
        }
    }
}
