package pl.dakil.appanalyser.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.dakil.appanalyser.data.PinnedNotificationsRepository

/**
 * Handles the notification's own buttons.
 *
 * A receiver rather than a `PendingIntent.getService`: starting a service from a background
 * process throws on Android 8+, and a receiver can cold-start the app, so "Remove notification"
 * works even if the service was already killed and the notification is a leftover.
 */
class PinnedNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        when (intent.action) {
            // Unpinning only writes to the repository — the service is collecting it and cancels
            // the matching notification (or shuts down if that was the last card). Doing both here
            // would race with the service.
            ACTION_UNPIN -> PinnedNotificationsRepository.get(context).unpin(id)

            // The user swiped the notification away. Ask the service to put it back; for static
            // cards there is no next tick, so this signal is the only thing that restores them.
            ACTION_REPOST -> PinnedNotificationBus.repostRequests.tryEmit(id)
        }
    }

    companion object {
        const val ACTION_UNPIN = "pl.dakil.appanalyser.action.UNPIN"
        const val ACTION_REPOST = "pl.dakil.appanalyser.action.REPOST"
        const val EXTRA_ITEM_ID = "pl.dakil.appanalyser.extra.ITEM_ID"
    }
}
