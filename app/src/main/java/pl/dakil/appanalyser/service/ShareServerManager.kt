package pl.dakil.appanalyser.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Start/stop entry points for the share server.
 *
 * Both are only ever called from a visible screen or a notification action, which keeps them clear
 * of the Android 12+ ban on starting a foreground service from the background.
 */
object ShareServerManager {

    fun start(context: Context, path: String) {
        val intent = Intent(context, ShareServerService::class.java)
            .putExtra(ShareServerService.EXTRA_PATH, path)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    /**
     * Deliberately `stopService`, not a `startForegroundService` carrying a stop action: routing the
     * stop through a start would oblige a already-dead service to call `startForeground` before
     * quitting, which crashes. `stopService` is a no-op when nothing is running.
     */
    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, ShareServerService::class.java)) }
    }
}
