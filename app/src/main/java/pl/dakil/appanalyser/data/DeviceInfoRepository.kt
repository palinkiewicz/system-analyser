package pl.dakil.appanalyser.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.UptimeInfo

/**
 * Live device telemetry for the UI: each metric is a cold [Flow] that samples once a second while
 * collected and stops as soon as nobody is listening.
 *
 * The actual reads live in [DeviceMetricsReader] — this class only owns the flow plumbing and the
 * per-collection tick state, so the pinned-notification service can sample the very same readers
 * on its own schedule without duplicating the logic.
 */
class DeviceInfoRepository(private val context: Context) {

    private val refreshIntervalMs = 1000L

    private val reader = DeviceMetricsReader(context)

    // ---------------------------------------------------------------------
    // Static info
    // ---------------------------------------------------------------------

    fun getSystemInfo(): SystemInfo = reader.systemInfo()

    fun getDisplayInfo(): DisplayInfo = reader.displayInfo()

    // ---------------------------------------------------------------------
    // Battery (live)
    // ---------------------------------------------------------------------

    fun batteryFlow(): Flow<BatteryInfo> = callbackFlow {
        var lastIntent: Intent? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent != null) lastIntent = intent
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // registerReceiver for a sticky broadcast returns the current battery state immediately.
        lastIntent = context.registerReceiver(receiver, filter)

        // Smoothing window and session min/max live for the lifetime of this collection.
        val state = BatterySampleState()

        val ticker = launch {
            while (isActive) {
                trySend(reader.battery(state, lastIntent))
                delay(refreshIntervalMs)
            }
        }

        awaitClose {
            ticker.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------
    // Sensors (live)
    // ---------------------------------------------------------------------

    fun sensorsFlow(): Flow<List<SensorInfo>> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            .distinctBy { it.type }
            .sortedBy { it.name }

        // The listener only stores the most recent value; emission is throttled to a fixed cadence
        // by the ticker below, so high-rate sensors can't flood the UI with recompositions.
        val latest = ConcurrentHashMap<Int, FloatArray>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latest[event.sensor.type] = event.values.copyOf()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensors.forEach { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        val ticker = launch {
            while (isActive) {
                trySend(sensors.map { sensor ->
                    SensorInfo(
                        name = sensor.name,
                        vendor = sensor.vendor,
                        type = sensor.type,
                        typeName = reader.sensorTypeLabel(sensor.type),
                        values = latest[sensor.type]?.let { reader.formatSensorValues(it) } ?: "—"
                    )
                })
                delay(refreshIntervalMs)
            }
        }

        awaitClose {
            ticker.cancel()
            sm.unregisterListener(listener)
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------
    // Uptime (live)
    // ---------------------------------------------------------------------

    /** Ticks once a second so the System tab's uptime readout counts up while it is visible. */
    fun uptimeFlow(): Flow<UptimeInfo> = flow {
        while (true) {
            emit(reader.uptime())
            delay(refreshIntervalMs)
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------
    // CPU (live)
    // ---------------------------------------------------------------------

    fun cpuFlow(): Flow<CpuInfo> = callbackFlow {
        val state = CpuSampleState()

        val ticker = launch {
            while (isActive) {
                trySend(reader.cpu(state))
                delay(refreshIntervalMs)
            }
        }

        awaitClose { ticker.cancel() }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------------
    // Memory (live)
    // ---------------------------------------------------------------------

    fun memoryFlow(): Flow<MemoryInfo> = callbackFlow {
        val ticker = launch {
            while (isActive) {
                trySend(reader.memory())
                delay(refreshIntervalMs)
            }
        }

        awaitClose { ticker.cancel() }
    }.flowOn(Dispatchers.IO)
}
