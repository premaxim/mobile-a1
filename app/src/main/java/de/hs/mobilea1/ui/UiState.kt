package de.hs.mobilea1.ui

import android.hardware.SensorManager

enum class DisplayMode(val label: String) { VALUES("Werte"), CHART("Diagramm") }

enum class AccelSamplingRate(val sensorDelay: Int, val label: String) {
    NORMAL(SensorManager.SENSOR_DELAY_NORMAL, "NORMAL"),
    GAME(SensorManager.SENSOR_DELAY_GAME, "GAME"),
}

/** Alle Messwerte sind bereits für die Anzeige formatiert. */
data class AccelUi(
    val available: Boolean,
    val samplingRate: AccelSamplingRate,
    val x: String = PLACEHOLDER,
    val y: String = PLACEHOLDER,
    val z: String = PLACEHOLDER,
    val magnitude: String = PLACEHOLDER,
    val eventRate: String = PLACEHOLDER,
)

data class BarometerUi(
    val available: Boolean,
    val pressure: String = PLACEHOLDER,
    val relativeAltitude: String = PLACEHOLDER,
    /** Messmodus: verbleibende Sekunden, `null` wenn keine Messung läuft. */
    val measurementRemainingS: Int? = null,
    /** Formatierte Ergebnisse der bisherigen Messungen (älteste zuerst). */
    val measurements: List<String> = emptyList(),
)

sealed interface GnssUi {
    data object Unavailable : GnssUi
    data object NoPermission : GnssUi
    data object Disabled : GnssUi
    data object Waiting : GnssUi
    data class Data(
        val latitude: String,
        val longitude: String,
        val altitude: String,
        val accuracy: String,
        val speed: String,
        /** Alter des letzten Fixes in Sekunden. */
        val ageSeconds: Long = 0,
        /** `true`, wenn seit [GNSS_STALE_SECONDS] kein neuer Fix kam – die Werte sind dann veraltet. */
        val stale: Boolean = false,
    ) : GnssUi
}

data class UiState(
    val mode: DisplayMode = DisplayMode.VALUES,
    val accel: AccelUi = AccelUi(available = true, samplingRate = AccelSamplingRate.GAME),
    val barometer: BarometerUi = BarometerUi(available = true),
    val gnss: GnssUi = GnssUi.Waiting,
    /** In-App-Warnung, falls keine System-Benachrichtigung möglich ist. */
    val banner: String? = null,
)

const val PLACEHOLDER = "–"

const val GNSS_STALE_SECONDS = 10L
