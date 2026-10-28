package pl.dakil.appanalyser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.MainActivity
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ShareEndpoint
import pl.dakil.appanalyser.domain.ShareNetwork
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.net.HttpFileServer
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the local HTTP share server for exactly as long as the user wants it up.
 *
 * Battery is the design constraint. There is **no polling loop** — the server is a thread parked in
 * `accept()`, the notification is posted once and only re-posted when something visible changes,
 * and the CPU wake lock is ref-counted against live requests so an idle session holds nothing. A
 * Wi-Fi lock is held for the session so the radio does not power-save the server out of reach.
 */
class ShareServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val settings by lazy { SettingsRepository.get(this) }
    private val networkInfo by lazy { NetworkInfoProvider(this) }

    private var server: HttpFileServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val activeRequests = AtomicInteger(0)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNotificationText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = intent?.getStringExtra(EXTRA_PATH)
        val target = path?.let { File(it) }
        if (target == null || !target.exists()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val network = networkInfo.resolve()
        if (network == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Foreground must be attached synchronously, before anything can suspend, or the platform
        // kills us inside ~10s. Sticky restart is deliberately not requested: a share session must
        // never silently come back to life.
        if (!attachForeground(target.name)) return START_NOT_STICKY

        startServer(target, network)
        return START_NOT_STICKY
    }

    private fun startServer(target: File, network: ShareNetwork) {
        // Sharing a second item replaces the first rather than silently keeping the old one up.
        server?.stop()

        val instance = HttpFileServer(
            root = target,
            accessMode = settings.shareAccessMode.value,
            onRequestStart = { acquireWakeLock() },
            onRequestEnd = { releaseWakeLockIfIdle() },
        )
        val port = instance.start()
        if (port == null) {
            shutdown()
            return
        }
        server = instance
        lastNotificationText = null
        acquireWifiLock()
        registerNetworkCallback()

        publish(target, network, instance, port)
        startIdleWatchdog()
    }

    private fun publish(target: File, network: ShareNetwork, instance: HttpFileServer, port: Int) {
        val running = ShareState.Running(
            endpoint = ShareEndpoint(network.ipAddress, port, instance.basePath),
            network = network,
            sharedPath = target.absolutePath,
            sharedName = target.name,
            pin = instance.pin,
        )
        ShareServerRepository.setRunning(running)
        updateNotification(running)
    }

    // -----------------------------------------------------------------------
    // Idle shutdown & network changes
    // -----------------------------------------------------------------------

    /**
     * A single coarse timer, not a poll: it wakes about once a minute, compares a timestamp, and
     * otherwise costs nothing. `delay` stalls naturally in Doze, which is exactly what we want.
     */
    private fun startIdleWatchdog() {
        scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                val timeoutMinutes = settings.shareIdleTimeoutMinutes.value
                if (timeoutMinutes <= 0) continue
                val idleFor = server?.millisSinceLastRequest() ?: break
                if (activeRequests.get() == 0 && idleFor >= timeoutMinutes * 60_000L) {
                    shutdown()
                    break
                }
            }
        }
    }

    /** Re-resolves the address when the network changes, and gives up if it stays gone. */
    private fun registerNetworkCallback() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshNetwork()
            override fun onLost(network: Network) = refreshNetwork()
        }
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                callback,
            )
            networkCallback = callback
        }
    }

    private fun refreshNetwork() {
        scope.launch {
            val current = ShareServerRepository.state.value as? ShareState.Running ?: return@launch
            val resolved = networkInfo.resolve()
            if (resolved == null) {
                // Give the device a moment to hand over between Wi-Fi and hotspot before quitting.
                delay(NETWORK_GRACE_MS)
                if (networkInfo.resolve() == null) shutdown()
                return@launch
            }
            if (resolved == current.network) return@launch
            val updated = current.copy(
                endpoint = current.endpoint.copy(ipAddress = resolved.ipAddress),
                network = resolved,
            )
            ShareServerRepository.setRunning(updated)
            updateNotification(updated)
        }
    }

    // -----------------------------------------------------------------------
    // Locks
    // -----------------------------------------------------------------------

    /** Keeps the Wi-Fi radio from power-saving the server out of reach while the screen is off. */
    private fun acquireWifiLock() {
        if (wifiLock != null) return
        val manager = applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock = runCatching {
            manager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WIFI_LOCK_TAG)
                .apply { acquire() }
        }.getOrNull()
    }

    /** Held only while bytes are actually moving — an idle server holds no CPU lock at all. */
    private fun acquireWakeLock() {
        if (activeRequests.incrementAndGet() != 1) return
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = runCatching {
            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()
    }

    private fun releaseWakeLockIfIdle() {
        if (activeRequests.decrementAndGet() > 0) return
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.share_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.share_notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun attachForeground(name: String): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.share_notification_starting), name),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        true
    } catch (e: Exception) {
        stopSelf()
        false
    }

    private fun updateNotification(running: ShareState.Running) {
        val details = buildString {
            append(getString(R.string.share_notification_network, networkInfo.label(running.network)))
            running.pin?.let {
                append('\n')
                append(getString(R.string.share_notification_pin, it))
            }
        }
        // Keyed on everything visible, so a changed address or connection kind re-renders.
        val text = running.endpoint.url
        val signature = "$text|$details"
        if (signature == lastNotificationText) return
        lastNotificationText = signature
        post(buildNotification(text, running.sharedName, details))
    }

    @Suppress("MissingPermission")
    private fun post(notification: Notification) {
        if (!notificationManager.areNotificationsEnabled()) return
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(text: String, name: String, details: String? = null): Notification {
        val stopIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ShareServerActionReceiver::class.java)
                .setAction(ShareServerActionReceiver.ACTION_STOP_SHARING)
                .setData("analyser://share/stop".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_SHOW_SESSION)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.share_notification_title, name))
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (details != null) "$text\n$details" else text)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.share_stop), stopIntent)
            .build()
            .apply { flags = flags or Notification.FLAG_NO_CLEAR }
    }

    // -----------------------------------------------------------------------
    // Teardown
    // -----------------------------------------------------------------------

    /**
     * Swiping the app out of Recents must kill the session. `stopWithTask="true"` already covers
     * this; the explicit stop is here because a few OEM ROMs are unreliable about it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdown()
        super.onTaskRemoved(rootIntent)
    }

    private fun shutdown() {
        server?.stop()
        server = null
        ShareServerRepository.setStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            networkCallback?.let {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
            }
        }
        networkCallback = null
        server?.stop()
        server = null
        releaseLocks()
        ShareServerRepository.setStopped()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "pl.dakil.appanalyser.extra.SHARE_PATH"

        /** Tapping the notification opens the app with this action to bring the session dialog back. */
        const val ACTION_SHOW_SESSION = "pl.dakil.appanalyser.action.SHOW_SHARE_SESSION"

        private const val CHANNEL_ID = "share_server"
        private const val NOTIFICATION_ID = 3000
        private const val IDLE_CHECK_INTERVAL_MS = 60_000L
        private const val NETWORK_GRACE_MS = 10_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WIFI_LOCK_TAG = "Analyser:share"
        private const val WAKE_LOCK_TAG = "Analyser:share-transfer"
    }
}
