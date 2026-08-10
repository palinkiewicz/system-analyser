package pl.dakil.appanalyser.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import pl.dakil.appanalyser.data.PinnedNotificationsRepository

/**
 * Brings pinned notifications back after a reboot or an app update — a notification the user
 * pinned as permanent should not silently disappear overnight.
 *
 * Does nothing when nothing is pinned, so a device with the app installed but unused pays no boot
 * cost at all.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (PinnedNotificationsRepository.get(context).pinned.value.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        // specialUse is allowed to start from BOOT_COMPLETED, but OEM battery managers block it
        // routinely. If that happens, opening the app restores the notifications instead.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PinnedNotificationsService::class.java)
            )
        }
    }
}
