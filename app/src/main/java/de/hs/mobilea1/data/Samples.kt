package de.hs.mobilea1.data

/** Alle Zeitstempel in ns auf der elapsedRealtime-Zeitbasis. */
data class AccelSample(
    val timestampNanos: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float,
)

data class PressureSample(
    val timestampNanos: Long,
    val rawHpa: Float,
    val smoothedHpa: Float,
    val relativeAltitudeM: Float,
)

data class GnssSample(
    val timestampNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float?,
    val speedMps: Float?,
)
