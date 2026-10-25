package de.hs.mobilea1.processing

import kotlin.math.sqrt

/** Ergebnis einer Mittelung: Mittelwert und Standardabweichung der Rohwerte. */
data class MeasurementResult(val meanHpa: Double, val stdDevHpa: Double, val count: Int)

/**
 * Sammelt Roh-Druckwerte über eine feste Dauer (Messmodus für Experimente).
 * Gemittelt werden die ungeglätteten Werte, damit Mittelwert und Streuung unverfälscht sind.
 */
class PressureMeasurement(private val durationNanos: Long = 20_000_000_000L) {
    private val values = ArrayList<Float>()
    private var endNanos: Long? = null

    val isRunning: Boolean get() = endNanos != null

    fun start(nowNanos: Long) {
        values.clear()
        endNanos = nowNanos + durationNanos
    }

    fun cancel() {
        values.clear()
        endNanos = null
    }

    fun add(rawHpa: Float) {
        if (isRunning) values.add(rawHpa)
    }

    fun remainingSeconds(nowNanos: Long): Int =
        endNanos?.let { ((it - nowNanos + 999_999_999L) / 1_000_000_000L).toInt().coerceAtLeast(0) } ?: 0

    /** Liefert das Ergebnis, sobald die Dauer abgelaufen ist, sonst `null`. */
    fun finishIfDue(nowNanos: Long): MeasurementResult? {
        val end = endNanos ?: return null
        if (nowNanos < end) return null
        endNanos = null
        if (values.isEmpty()) return null
        val mean = values.sumOf { it.toDouble() } / values.size
        val variance = if (values.size > 1) values.sumOf { (it - mean) * (it - mean) } / (values.size - 1) else 0.0
        return MeasurementResult(mean, sqrt(variance), values.size)
    }
}
