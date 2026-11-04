package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.dakil.appanalyser.domain.FileFavorite
import pl.dakil.appanalyser.domain.renamed

/**
 * The folders and files pinned to the Files home, in the order they were added, persisted as a JSON
 * array in the "settings" SharedPreferences. One process-wide instance, so every pane and screen
 * sees a change at once.
 */
class FavoritesRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<FileFavorite>> = _favorites.asStateFlow()

    fun isFavorite(path: String) = _favorites.value.any { it.path == path }

    /** Adds whichever of [items] (path to is-directory) aren't favourites yet, at the end. */
    fun add(items: List<Pair<String, Boolean>>) {
        val present = _favorites.value.mapTo(mutableSetOf()) { it.path }
        val added = items.filter { it.first !in present }.distinctBy { it.first }
            .map { (path, isDirectory) -> FileFavorite(path, isDirectory) }
        if (added.isNotEmpty()) save(_favorites.value + added)
    }

    fun remove(paths: Collection<String>) {
        val gone = paths.toSet()
        save(_favorites.value.filterNot { it.path in gone })
    }

    fun setColor(path: String, colorIndex: Int) =
        save(_favorites.value.map { if (it.path == path) it.copy(colorIndex = colorIndex) else it })

    /** Keeps favourites pointing at the right place after a rename in the browser. */
    fun onRenamed(from: String, to: String) {
        val updated = _favorites.value.renamed(from, to)
        if (updated != _favorites.value) save(updated)
    }

    private fun save(favorites: List<FileFavorite>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            array.put(
                JSONObject().apply {
                    put(FIELD_PATH, favorite.path)
                    put(FIELD_DIRECTORY, favorite.isDirectory)
                    put(FIELD_COLOR, favorite.colorIndex)
                }
            )
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
        _favorites.value = favorites
    }

    private fun load(): List<FileFavorite> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                FileFavorite(
                    path = obj.getString(FIELD_PATH),
                    isDirectory = obj.optBoolean(FIELD_DIRECTORY, true),
                    colorIndex = obj.optInt(FIELD_COLOR, FileFavorite.AUTO_COLOR)
                        .takeIf { it in 0 until FileFavorite.COLOR_COUNT } ?: FileFavorite.AUTO_COLOR,
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_FAVORITES = "file_favorites"
        private const val FIELD_PATH = "path"
        private const val FIELD_DIRECTORY = "dir"
        private const val FIELD_COLOR = "color"

        @Volatile
        private var instance: FavoritesRepository? = null

        fun get(context: Context): FavoritesRepository =
            instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context.applicationContext).also { instance = it }
            }
    }
}
