package de.hs.mobilea1.processing

/** Ergebnis einer GNSS-Messfahrt zwischen Start und Stopp. */
data class GnssMeasurementResult(
    val durationS: Double,
    /** Mittel der vom Empfänger gemeldeten Geschwindigkeiten (Doppler). */
    val meanSpeedMps: Double?,
    /** Aus den Positionen aufsummierte Strecke. */
    val distanceM: Double,
    val fixCount: Int,
)

/**
 * Messmodus für Experimente: sammelt zwischen Start und Stopp die Geschwindigkeiten und
 * Streckenabschnitte aller Fixes. Die Dauer ergibt sich aus den Zeitpunkten von Start und Stopp.
 */
class GnssMeasurement {
    private var startNanos: Long? = null
    private val speeds = ArrayList<Float>()
    private var distanceM = 0.0
    private var fixCount = 0

    val isRunning: Boolean get() = startNanos != null

    fun start(nowNanos: Long) {
        startNanos = nowNanos
        speeds.clear()
        distanceM = 0.0
        fixCount = 0
    }

    fun cancel() {
        startNanos = null
    }

    /** @param stepM Abstand zum vorherigen Fix (bzw. 0 beim ersten Fix der Messung). */
    fun addFix(speedMps: Float?, stepM: Double) {
        if (!isRunning) return
        fixCount++
        speedMps?.let(speeds::add)
        distanceM += stepM
    }

    fun elapsedSeconds(nowNanos: Long): Long = startNanos?.let { (nowNanos - it) / 1_000_000_000L } ?: 0

    fun stop(nowNanos: Long): GnssMeasurementResult? {
        val start = startNanos ?: return null
        startNanos = null
        return GnssMeasurementResult(
            durationS = (nowNanos - start) / 1e9,
            meanSpeedMps = if (speeds.isEmpty()) null else speeds.average(),
            distanceM = distanceM,
            fixCount = fixCount,
        )
    }
}
