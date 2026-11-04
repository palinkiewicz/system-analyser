package pl.dakil.appanalyser.domain

import java.io.File

/**
 * A folder or file pinned to the Files home. [colorIndex] picks one of the theme-derived favourite
 * colours, or [AUTO_COLOR] for the plain theme tint.
 */
data class FileFavorite(
    val path: String,
    val isDirectory: Boolean,
    val colorIndex: Int = AUTO_COLOR,
) {
    val name: String get() = File(path).name

    companion object {
        const val AUTO_COLOR = -1

        /** How many colours there are to pick from besides the theme tint (two even rows of four). */
        const val COLOR_COUNT = 7
    }
}

/**
 * Follows a rename of [from] to [to]: a favourite at that path moves with it, and so does every
 * favourite inside it when it was a folder. Everything else is left alone.
 */
fun List<FileFavorite>.renamed(from: String, to: String): List<FileFavorite> = map { favorite ->
    when {
        favorite.path == from -> favorite.copy(path = to)
        favorite.path.startsWith("$from/") -> favorite.copy(path = to + favorite.path.removePrefix(from))
        else -> favorite
    }
}
