package de.hs.mobilea1.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Kapselt An- und Abmeldung eines Hardware-Sensors. Ist der Sensor nicht vorhanden,
 * sind [start] und [stop] wirkungslos.
 */
class SensorSource(private val sensorManager: SensorManager?, sensorType: Int) {

    private val sensor: Sensor? = sensorManager?.getDefaultSensor(sensorType)
    private var listener: SensorEventListener? = null

    val isAvailable: Boolean get() = sensor != null
    val isRunning: Boolean get() = listener != null

    /** @return `true`, wenn der Listener registriert wurde. */
    fun start(samplingPeriod: Int, onEvent: (SensorEvent) -> Unit): Boolean {
        val sensor = sensor ?: return false
        stop()
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager?.registerListener(newListener, sensor, samplingPeriod) == true
        if (registered) listener = newListener
        return registered
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }
}
