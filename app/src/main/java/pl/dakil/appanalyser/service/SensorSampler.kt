package pl.dakil.appanalyser.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps listeners registered for exactly the sensors that pinned notifications display — no more.
 * Values land in a map from the listener callback and are read once per tick, so a high-rate
 * sensor costs one map write per event instead of a notification post.
 *
 * Registrations are diffed on every [setRequired] call, so pinning or unpinning one sensor never
 * churns the others.
 */
class SensorSampler(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val latest = ConcurrentHashMap<Int, FloatArray>()
    private val registered = mutableMapOf<Int, Sensor>()

    /**
     * @param types [Sensor.getType] values that must be sampled; anything else is unregistered.
     * @param samplingPeriodUs delivery rate hint — never faster than the notification refresh,
     *   since values in between are thrown away anyway.
     */
    fun setRequired(types: Set<Int>, samplingPeriodUs: Int) {
        (registered.keys - types).toList().forEach { type ->
            registered.remove(type)?.let { sensorManager.unregisterListener(this, it) }
            latest.remove(type)
        }

        val missing = types - registered.keys
        if (missing.isEmpty()) return

        // Resolved the same way the Sensors tab does, so a pinned card keeps the name the user
        // saw. getDefaultSensor may return a different (e.g. wake-up) instance with another name.
        val available = sensorManager.getSensorList(Sensor.TYPE_ALL).distinctBy { it.type }
        missing.forEach { type ->
            val sensor = available.firstOrNull { it.type == type } ?: return@forEach
            sensorManager.registerListener(this, sensor, samplingPeriodUs)
            registered[type] = sensor
        }
    }

    fun sensorFor(type: Int): Sensor? = registered[type]

    fun valuesFor(type: Int): FloatArray? = latest[type]

    fun release() {
        registered.values.forEach { sensorManager.unregisterListener(this, it) }
        registered.clear()
        latest.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        latest[event.sensor.type] = event.values.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
