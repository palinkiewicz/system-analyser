package pl.dakil.appanalyser.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** POST_NOTIFICATIONS is a runtime permission from Android 13; before that it is implicit. */
fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
