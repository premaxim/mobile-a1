package de.hs.mobilea1.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

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
        PressureMeasurementControls(baro, actions.onStartPressureMeasurement)
    }

    SensorCard("GNSS") {
        GnssStatusContent(state.gnss, actions) { gnss ->
            if (gnss.stale) {
                Text(
                    "Kein aktueller Fix – Werte veraltet",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ValueRow("Alter des Fixes", "${gnss.ageSeconds} s")
            ValueRow("Breite", gnss.latitude)
            ValueRow("Länge", gnss.longitude)
            ValueRow("Höhe ü. NN", gnss.altitude)
            ValueRow("Genauigkeit", gnss.accuracy)
            ValueRow("Geschwindigkeit", gnss.speed)
        }
        GnssMeasurementControls(state, actions.onToggleGnssMeasurement)
    }
}

/** Messmodus für Experimente: 20 s Rohwerte mitteln, Ergebnisse und Differenzen anzeigen. */
@Composable
private fun PressureMeasurementControls(baro: BarometerUi, onStart: () -> Unit) {
    val remaining = baro.measurementRemainingS
    if (remaining != null) {
        // Während der Messung darf der Bildschirm nicht ausgehen (sonst werden die Sensoren abgemeldet).
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }
    OutlinedButton(onClick = onStart, enabled = remaining == null) {
        Text(if (remaining == null) "20 s mitteln" else "Messung läuft … $remaining s")
    }
    baro.measurements.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

/** Messmodus für Experimente: zwischen Start und Stopp Dauer, Ø Geschwindigkeit und Strecke erfassen. */
@Composable
private fun GnssMeasurementControls(state: UiState, onToggle: () -> Unit) {
    val elapsed = state.gnssMeasurementElapsedS
    if (elapsed != null) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Button(onClick = onToggle) { Text("Messung stoppen ($elapsed s)") }
    } else {
        OutlinedButton(onClick = onToggle) { Text("Messung starten") }
    }
    state.gnssMeasurements.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
