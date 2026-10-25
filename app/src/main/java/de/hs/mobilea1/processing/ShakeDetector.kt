package de.hs.mobilea1.processing

/**
 * Meldet eine Erschütterung, wenn |a| den [thresholdMps2] überschreitet.
 * Nach einer Meldung wird [cooldownNanos] lang keine weitere ausgelöst.
 */
class ShakeDetector(
    private val thresholdMps2: Float = 25f,
    private val cooldownNanos: Long = 5_000_000_000L,
) {
    private var lastTriggerNanos: Long? = null

    fun check(magnitude: Float, timestampNanos: Long): Boolean {
        if (magnitude <= thresholdMps2) return false
        val last = lastTriggerNanos
        if (last != null && timestampNanos - last < cooldownNanos) return false
        lastTriggerNanos = timestampNanos
        return true
    }
}
