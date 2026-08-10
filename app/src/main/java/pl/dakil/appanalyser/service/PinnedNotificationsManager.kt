package pl.dakil.appanalyser.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import pl.dakil.appanalyser.data.PinnedNotificationsRepository
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.domain.SensorInfo

/**
 * In-process signals from the notification receivers to the running service. Only meaningful while
 * the service is alive — which is exactly when notifications exist.
 */
object PinnedNotificationBus {
    /** Card ids the user swiped away and that must be re-posted immediately. */
    val repostRequests = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

/**
 * Entry point for pinning and unpinning cards. Owns the service lifecycle so
 * [PinnedNotificationsRepository] can stay a pure data holder.
 *
 * Only [pin] and [syncOnAppStart] ever start the service, and both are called from a visible
 * Activity, so the Android 12+ background foreground-service-start restriction never applies.
 * Stopping is always the service's own decision, taken when the pinned list empties.
 */
object PinnedNotificationsManager {

    /** @return false when an equivalent card was already pinned. */
    fun pin(context: Context, type: HomeWidgetType, sensor: SensorInfo?): Boolean {
        PinnedNotificationsRepository.get(context)
            .pin(type, sensor?.type, sensor?.name) ?: return false
        ensureRunning(context)
        return true
    }

    fun unpin(context: Context, id: String) {
        // The service observes the repository and cancels the notification itself.
        PinnedNotificationsRepository.get(context).unpin(id)
    }

    fun unpin(context: Context, type: HomeWidgetType, sensorType: Int?) {
        val repo = PinnedNotificationsRepository.get(context)
        repo.pinnedIdFor(type, sensorType)?.let { repo.unpin(it) }
    }

    /**
     * Restores notifications after the service was killed from outside — the notification-shade
     * task manager, an OEM battery manager, or a boot where the receiver was blocked.
     */
    fun syncOnAppStart(context: Context) {
        if (PinnedNotificationsRepository.get(context).pinned.value.isEmpty()) return
        ensureRunning(context)
    }

    private fun ensureRunning(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PinnedNotificationsService::class.java)
            )
        }
    }
}
