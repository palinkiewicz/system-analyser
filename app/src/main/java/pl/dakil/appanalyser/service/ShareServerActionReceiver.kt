package pl.dakil.appanalyser.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Backs the notification's "Stop sharing" action. A receiver rather than a service PendingIntent so
 * it still works if the process was cold-started to deliver the tap.
 */
class ShareServerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_SHARING) return
        ShareServerManager.stop(context)
    }

    companion object {
        const val ACTION_STOP_SHARING = "pl.dakil.appanalyser.action.STOP_SHARING"
    }
}
