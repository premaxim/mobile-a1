package de.hs.mobilea1.processing

/** Misst die tatsächliche Ereignisrate anhand der Sensor-Zeitstempel. */
class RateMeter(private val windowNanos: Long = 1_000_000_000L) {
    private var windowStart = -1L
    private var count = 0

    /** Zuletzt gemessene Rate in Hz, `null` solange noch kein volles Fenster vorliegt. */
    var rateHz: Float? = null
        private set

    fun onEvent(timestampNanos: Long) {
        if (windowStart < 0) {
            windowStart = timestampNanos
            count = 0
            return
        }
        count++
        val elapsed = timestampNanos - windowStart
        if (elapsed >= windowNanos) {
            rateHz = count * 1e9f / elapsed
            windowStart = timestampNanos
            count = 0
        }
    }

    fun reset() {
        windowStart = -1L
        count = 0
        rateHz = null
    }
}
