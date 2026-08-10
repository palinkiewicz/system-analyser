package pl.dakil.appanalyser.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.MainActivity
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.BatterySampleState
import pl.dakil.appanalyser.data.CpuSampleState
import pl.dakil.appanalyser.data.DeviceMetricsReader
import pl.dakil.appanalyser.data.PinnedNotificationsRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.domain.PinnedCard
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.TemperatureUnit

/**
 * Keeps one notification per pinned device-info card up to date.
 *
 * Cost discipline, in order of importance:
 * - The service exists only while at least one card is pinned. Unpinning the last one tears
 *   everything down ([shutdown]) — no process, no timers, no sensor listeners.
 * - Each tick samples only the [MetricGroup]s the pinned cards actually display, derived once per
 *   list change rather than per tick.
 * - A set of purely static cards never enters the sampling loop at all: they are posted once and
 *   the coroutine parks on [awaitCancellation].
 * - The loop parks while the screen is off, and takes no wake lock, so `delay` stalls naturally
 *   in Doze.
 * - Notifications whose rendered text is unchanged are not re-posted, and posts are paced below
 *   the platform's per-package enqueue limit.
 */
class PinnedNotificationsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private val repository by lazy { PinnedNotificationsRepository.get(this) }
    private val settings by lazy { SettingsRepository.get(this) }
    private val reader by lazy { DeviceMetricsReader(this) }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }

    // Sampling state that must survive a loop restart (interval change, list change): resetting it
    // would zero the CPU delta and throw away the battery smoothing window and session min/max.
    private val cpuState = CpuSampleState()
    private val batteryState = BatterySampleState()
    private val sensorSampler by lazy { SensorSampler(this) }

    private var cachedSystemInfo: SystemInfo? = null
    private var cachedDisplayInfo: DisplayInfo? = null

    /** Content currently on screen, per notification id — the basis for skipping no-op re-posts. */
    private val lastRendered = mutableMapOf<Int, RenderedCard>()
    private val postedIds = mutableSetOf<Int>()

    /** Which notification is currently attached to the foreground service. */
    private var fgsNotificationId: Int? = null

    /** Timestamps of recent posts, used to stay under the platform enqueue rate limit. */
    private val recentPostTimes = ArrayDeque<Long>()

    /** Card id -> timestamps of recent user dismissals, to stop an endless swipe/re-post fight. */
    private val dismissals = mutableMapOf<String, MutableList<Long>>()
    private val abandoned = mutableSetOf<String>()

    private val screenOn = MutableStateFlow(true)
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerScreenReceiver()

        scope.launch {
            combine(
                repository.pinned,
                settings.notificationIntervalSeconds,
                settings.temperatureUnit,
                settings.showSensorUnits,
            ) { pinned, interval, unit, showUnits -> Config(pinned, interval, unit, showUnits) }
                // collectLatest restarts the loop on any change, so a settings slider or a new pin
                // takes effect immediately instead of after up to 15 seconds.
                .collectLatest { config ->
                    if (config.cards.isEmpty()) {
                        shutdown()
                        return@collectLatest
                    }
                    reconcile(config.cards)
                    runSamplingLoop(config)
                }
        }

        scope.launch {
            PinnedNotificationBus.repostRequests.collect { onDismissed(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cards = repository.pinned.value
        if (cards.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Must happen synchronously, before any suspension: Android gives ~10 s from
        // startForegroundService() to startForeground(), and missing it crashes the app.
        return if (attachForeground(cards.first())) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        sensorSampler.release()
        scope.cancel()
        super.onDestroy()
    }

    private data class Config(
        val cards: List<PinnedCard>,
        val intervalSeconds: Int,
        val temperatureUnit: TemperatureUnit,
        val showSensorUnits: Boolean,
    )

    // ---------------------------------------------------------------------
    // Sampling
    // ---------------------------------------------------------------------

    private suspend fun runSamplingLoop(config: Config) {
        val cards = config.cards
        val groups = cards.mapNotNull { it.type.metricGroup() }.toSet()
        val intervalMs = config.intervalSeconds * 1000L

        val sensorTypes = cards
            .filter { it.type == HomeWidgetType.SENSOR }
            .mapNotNull { it.sensorType }
            .toSet()
        // Never sample faster than we render; values in between would be discarded anyway.
        sensorSampler.setRequired(sensorTypes, (intervalMs * 1000).toInt().coerceAtLeast(200_000))

        // Everything pinned is static: post once, then hold the service open doing nothing at all.
        if (groups.isEmpty()) {
            postAll(cards, sample(emptySet(), cards), config)
            awaitCancellation()
        }

        while (true) {
            // Nobody reads a notification on a dark screen, and this lets the CPU idle in Doze.
            screenOn.first { it }
            postAll(cards, sample(groups, cards), config)
            delay(intervalMs)
        }
    }

    private fun sample(groups: Set<MetricGroup>, cards: List<PinnedCard>): MetricSnapshot {
        val needsSystem = cards.any {
            it.type == HomeWidgetType.SYSTEM_IDENTITY || it.type == HomeWidgetType.SYSTEM_SOFTWARE
        }
        val needsDisplay = cards.any { it.type == HomeWidgetType.DISPLAY }

        val needsCpuClocks = MetricGroup.CPU_CLOCKS in groups
        val needsCpuUsage = MetricGroup.CPU_USAGE in groups
        val needsCpuTemps = MetricGroup.CPU_TEMPS in groups
        val needsRam = MetricGroup.RAM in groups
        val needsStorage = MetricGroup.STORAGE in groups

        return MetricSnapshot(
            system = if (needsSystem) {
                cachedSystemInfo ?: reader.systemInfo().also { cachedSystemInfo = it }
            } else null,
            display = if (needsDisplay) {
                cachedDisplayInfo ?: reader.displayInfo().also { cachedDisplayInfo = it }
            } else null,
            uptime = if (MetricGroup.UPTIME in groups) reader.uptime() else null,
            cpu = if (needsCpuClocks || needsCpuUsage || needsCpuTemps) {
                reader.cpu(
                    state = cpuState,
                    needCores = needsCpuClocks,
                    needUsage = needsCpuUsage,
                    needTemps = needsCpuTemps
                )
            } else null,
            battery = if (MetricGroup.BATTERY in groups) {
                reader.battery(batteryState, reader.stickyBatteryIntent())
            } else null,
            memory = if (needsRam || needsStorage) {
                reader.memory(needRam = needsRam, needStorage = needsStorage)
            } else null,
            sensors = if (MetricGroup.SENSORS in groups) sampleSensors(cards) else emptyMap(),
        )
    }

    private fun sampleSensors(cards: List<PinnedCard>): Map<Int, SensorInfo> = buildMap {
        cards.filter { it.type == HomeWidgetType.SENSOR }.forEach { card ->
            val type = card.sensorType ?: return@forEach
            val sensor = sensorSampler.sensorFor(type) ?: return@forEach
            put(
                type,
                SensorInfo(
                    name = sensor.name,
                    vendor = sensor.vendor,
                    type = sensor.type,
                    typeName = reader.sensorTypeLabel(sensor.type),
                    values = sensorSampler.valuesFor(type)
                        ?.let { reader.formatSensorValues(it) } ?: "—"
                )
            )
        }
    }

    // ---------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------

    private suspend fun postAll(
        cards: List<PinnedCard>,
        snapshot: MetricSnapshot,
        config: Config,
    ) {
        for (card in cards) {
            if (card.id in abandoned) continue
            val rendered = renderCard(
                context = this,
                card = card,
                snapshot = snapshot,
                temperatureUnit = config.temperatureUnit,
                showSensorUnits = config.showSensorUnits,
            ) ?: continue
            if (lastRendered[card.notificationId] == rendered) continue

            pace()
            post(card, rendered)
        }
    }

    /**
     * Keeps posts under the platform's per-package enqueue rate limit (~5/s, beyond which
     * notifications are silently dropped). At a 1 s interval with many pinned cards, the surplus
     * simply lands in the next slot.
     */
    private suspend fun pace() {
        val now = SystemClock.elapsedRealtime()
        while (recentPostTimes.isNotEmpty() && now - recentPostTimes.first() >= 1000L) {
            recentPostTimes.removeFirst()
        }
        if (recentPostTimes.size >= MAX_POSTS_PER_SECOND) {
            delay(1000L - (now - recentPostTimes.first()))
            recentPostTimes.removeFirst()
        }
        recentPostTimes.addLast(SystemClock.elapsedRealtime())
    }

    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled() below.
    private fun post(card: PinnedCard, rendered: RenderedCard) {
        // The cache is only updated on a post that actually happened, so revoking and re-granting
        // the notification permission doesn't leave every card stuck as "unchanged".
        if (!notificationManager.areNotificationsEnabled()) return
        notificationManager.notify(card.notificationId, buildNotification(card, rendered))
        lastRendered[card.notificationId] = rendered
        postedIds.add(card.notificationId)
    }

    private fun buildNotification(card: PinnedCard, rendered: RenderedCard) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(rendered.title)
            .setContentText(rendered.summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(rendered.title)
                    .bigText(rendered.bigText())
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSortKey(card.notificationId.toString())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(card))
            .setDeleteIntent(actionIntent(card, PinnedNotificationActionReceiver.ACTION_REPOST, 1))
            .addAction(
                0,
                getString(R.string.notification_remove),
                actionIntent(card, PinnedNotificationActionReceiver.ACTION_UNPIN, 0)
            )
            .build()
            .apply {
                // No NotificationCompat setter for this: keeps the card out of "Clear all".
                flags = flags or android.app.Notification.FLAG_NO_CLEAR
            }

    /**
     * PendingIntent equality ignores extras — it keys on request code, action, data and component.
     * Without a per-card request code *and* data uri, every card's "Remove" button would resolve
     * to the same intent and remove whichever card was pinned first.
     */
    private fun actionIntent(card: PinnedCard, action: String, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            card.notificationId * REQUEST_CODE_SLOTS + slot,
            Intent(this, PinnedNotificationActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("analyser://pinned/${card.id}"))
                .putExtra(PinnedNotificationActionReceiver.EXTRA_ITEM_ID, card.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openAppIntent(card: PinnedCard): PendingIntent =
        PendingIntent.getActivity(
            this,
            card.notificationId * REQUEST_CODE_SLOTS + 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

    // ---------------------------------------------------------------------
    // Lifecycle plumbing
    // ---------------------------------------------------------------------

    /** Drops notifications for cards that are gone and keeps the FGS attached to a live one. */
    private fun reconcile(cards: List<PinnedCard>) {
        val wanted = cards.map { it.notificationId }.toSet()
        (postedIds - wanted).forEach { id ->
            notificationManager.cancel(id)
            lastRendered.remove(id)
            postedIds.remove(id)
        }
        abandoned.retainAll(cards.map { it.id }.toSet())

        val designated = cards.first()
        if (fgsNotificationId != designated.notificationId) {
            val previous = fgsNotificationId
            // Re-attach first, then drop the old one: the service must never be left without a
            // foreground notification, even momentarily.
            attachForeground(designated)
            if (previous != null && previous !in wanted) {
                notificationManager.cancel(previous)
                lastRendered.remove(previous)
                postedIds.remove(previous)
            }
        }
    }

    /**
     * Promotes a pinned card to be *the* foreground-service notification, rather than posting a
     * separate "service is running" row the user never asked for.
     */
    private fun attachForeground(card: PinnedCard): Boolean {
        val rendered = lastRendered[card.notificationId] ?: RenderedCard(
            title = cardTitle(this, card),
            summary = getString(R.string.notification_loading),
            lines = emptyList()
        )
        return try {
            ServiceCompat.startForeground(
                this,
                card.notificationId,
                buildNotification(card, rendered),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else 0
            )
            fgsNotificationId = card.notificationId
            postedIds.add(card.notificationId)
            true
        } catch (e: Exception) {
            // Typically ForegroundServiceStartNotAllowedException on a sticky restart from the
            // background. Nothing to do but stand down; the app restores state when next opened.
            stopSelf()
            false
        }
    }

    /**
     * The user swiped a notification away. Android 14+ allows that even for ongoing and
     * foreground-service notifications with no opt-out, so the only way to honour "these stay
     * until you press Remove" is to put it back.
     */
    private fun onDismissed(cardId: String) {
        val card = repository.pinned.value.firstOrNull { it.id == cardId } ?: return
        val now = SystemClock.elapsedRealtime()
        val history = dismissals.getOrPut(cardId) { mutableListOf() }
        history.removeAll { now - it > DISMISS_WINDOW_MS }
        history.add(now)
        if (history.size >= MAX_DISMISSALS) {
            // The user clearly wants it gone. Stop fighting them: the card stays pinned and can be
            // restored from Settings, but we no longer re-post it this session.
            abandoned.add(cardId)
            return
        }
        // Force the next render to count as a change, and put static cards back immediately —
        // they have no next tick to restore them.
        lastRendered.remove(card.notificationId)
        if (card.type.metricGroup() == null) {
            scope.launch {
                val config = Config(
                    cards = repository.pinned.value,
                    intervalSeconds = settings.notificationIntervalSeconds.value,
                    temperatureUnit = settings.temperatureUnit.value,
                    showSensorUnits = settings.showSensorUnits.value,
                )
                postAll(listOf(card), sample(emptySet(), listOf(card)), config)
            }
        }
    }

    private fun shutdown() {
        postedIds.toList().forEach { notificationManager.cancel(it) }
        postedIds.clear()
        lastRendered.clear()
        sensorSampler.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        fgsNotificationId = null
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_pinned),
            // LOW keeps them silent and out of the way while still showing a status-bar icon;
            // MIN would hide the icon and collapse the rows.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_pinned_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun registerScreenReceiver() {
        val pm = getSystemService(PowerManager::class.java)
        screenOn.value = pm?.isInteractive ?: true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                screenOn.value = intent?.action == Intent.ACTION_SCREEN_ON
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiver = receiver
    }

    companion object {
        private const val CHANNEL_ID = "pinned_cards"

        /** Request-code slots reserved per card: unpin, repost, open app. */
        private const val REQUEST_CODE_SLOTS = 4

        private const val MAX_POSTS_PER_SECOND = 4

        private const val MAX_DISMISSALS = 3
        private const val DISMISS_WINDOW_MS = 10_000L
    }
}
