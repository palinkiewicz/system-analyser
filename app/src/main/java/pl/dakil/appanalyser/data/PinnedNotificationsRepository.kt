package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.domain.PinnedCard
import java.util.UUID

/**
 * Persists the set of device-info cards pinned to the notification shade, as a JSON array in
 * SharedPreferences. Exposed as reactive state: the notification service collects it and posts,
 * updates or cancels notifications to match, and shuts itself down when the list empties.
 *
 * Deliberately a pure data holder — it never starts or stops the service, so a cold-started
 * broadcast receiver (the "Remove notification" action) can safely write to it.
 */
class PinnedNotificationsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pinned = MutableStateFlow(load())
    val pinned: StateFlow<List<PinnedCard>> = _pinned.asStateFlow()

    fun isPinned(type: HomeWidgetType, sensorType: Int?): Boolean {
        val key = PinnedCard.dedupeKeyOf(type, sensorType)
        return _pinned.value.any { it.dedupeKey == key }
    }

    fun pinnedIdFor(type: HomeWidgetType, sensorType: Int?): String? {
        val key = PinnedCard.dedupeKeyOf(type, sensorType)
        return _pinned.value.firstOrNull { it.dedupeKey == key }?.id
    }

    /** Adds a card unless an equivalent one is already pinned. Returns the new card, or null. */
    fun pin(type: HomeWidgetType, sensorType: Int?, sensorName: String?): PinnedCard? {
        val current = _pinned.value
        val key = PinnedCard.dedupeKeyOf(type, sensorType)
        if (current.any { it.dedupeKey == key }) return null

        val card = PinnedCard(
            id = UUID.randomUUID().toString(),
            // Monotonic within the list: never reuses an id still held by a live notification.
            notificationId = (current.maxOfOrNull { it.notificationId }
                ?: (PinnedCard.FIRST_NOTIFICATION_ID - 1)) + 1,
            type = type,
            sensorType = sensorType,
            sensorName = sensorName,
        )
        save(current + card)
        return card
    }

    fun unpin(id: String) = save(_pinned.value.filterNot { it.id == id })

    private fun save(cards: List<PinnedCard>) {
        prefs.edit().putString(KEY_PINNED, toJson(cards)).apply()
        _pinned.value = cards
    }

    private fun load(): List<PinnedCard> {
        val json = prefs.getString(KEY_PINNED, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    // Skip entries whose type is unknown (e.g. written by a newer app version).
                    val type = runCatching {
                        HomeWidgetType.valueOf(obj.getString(FIELD_TYPE))
                    }.getOrNull() ?: continue
                    add(
                        PinnedCard(
                            id = obj.optString(FIELD_ID, UUID.randomUUID().toString()),
                            notificationId = obj.optInt(
                                FIELD_NOTIFICATION_ID,
                                PinnedCard.FIRST_NOTIFICATION_ID + i
                            ),
                            type = type,
                            sensorType = if (obj.has(FIELD_SENSOR_TYPE)) obj.getInt(FIELD_SENSOR_TYPE) else null,
                            sensorName = if (obj.has(FIELD_SENSOR_NAME)) obj.getString(FIELD_SENSOR_NAME) else null,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun toJson(cards: List<PinnedCard>): String {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject().apply {
                    put(FIELD_ID, card.id)
                    put(FIELD_NOTIFICATION_ID, card.notificationId)
                    put(FIELD_TYPE, card.type.name)
                    card.sensorType?.let { put(FIELD_SENSOR_TYPE, it) }
                    card.sensorName?.let { put(FIELD_SENSOR_NAME, it) }
                }
            )
        }
        return array.toString()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_PINNED = "pinned_notifications"

        private const val FIELD_ID = "id"
        private const val FIELD_NOTIFICATION_ID = "notificationId"
        private const val FIELD_TYPE = "type"
        private const val FIELD_SENSOR_TYPE = "sensorType"
        private const val FIELD_SENSOR_NAME = "sensorName"

        @Volatile
        private var instance: PinnedNotificationsRepository? = null

        fun get(context: Context): PinnedNotificationsRepository =
            instance ?: synchronized(this) {
                instance ?: PinnedNotificationsRepository(context.applicationContext)
                    .also { instance = it }
            }
    }
}
