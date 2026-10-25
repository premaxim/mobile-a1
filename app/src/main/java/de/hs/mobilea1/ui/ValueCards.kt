package de.hs.mobilea1.ui

import androidx.compose.runtime.Composable

@Composable
fun ValueCards(state: UiState, actions: SensorScreenActions) {
    SensorCard("Accelerometer") {
        val accel = state.accel
        if (!accel.available) {
            SensorUnavailable()
            return@SensorCard
        }
        ValueRow("a_x", accel.x)
        ValueRow("a_y", accel.y)
        ValueRow("a_z", accel.z)
        ValueRow("|a|", accel.magnitude)
        ValueRow("Gemessene Rate", accel.eventRate)
        SamplingRateSelector(accel.samplingRate, actions.onSamplingRateChange)
    }

    SensorCard("Barometer") {
        val baro = state.barometer
        if (!baro.available) {
            SensorUnavailable()
            return@SensorCard
        }
        ValueRow("Luftdruck p", baro.pressure)
        ValueRow("Relative Höhe Δh", baro.relativeAltitude)
    }

    SensorCard("GNSS") {
        GnssStatusContent(state.gnss, actions) { gnss ->
            ValueRow("Breite", gnss.latitude)
            ValueRow("Länge", gnss.longitude)
            ValueRow("Höhe", gnss.altitude)
            ValueRow("Genauigkeit", gnss.accuracy)
            ValueRow("Geschwindigkeit", gnss.speed)
        }
    }
}
