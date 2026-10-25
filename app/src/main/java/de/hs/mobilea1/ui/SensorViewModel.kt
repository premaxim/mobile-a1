package de.hs.mobilea1.ui

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import de.hs.mobilea1.data.AccelSample
import de.hs.mobilea1.data.GnssSample
import de.hs.mobilea1.data.PressureSample
import de.hs.mobilea1.data.RingBuffer
import de.hs.mobilea1.log.ChainLogger
import de.hs.mobilea1.notify.ShakeNotifier
import de.hs.mobilea1.processing.MeasurementResult
import de.hs.mobilea1.processing.MovingAverage
import de.hs.mobilea1.processing.PressureMeasurement
import de.hs.mobilea1.processing.RateMeter
import de.hs.mobilea1.processing.ShakeDetector
import de.hs.mobilea1.sensor.GnssSource
import de.hs.mobilea1.sensor.SensorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt

/**
 * Hält Sensorquellen, Ringpuffer und Verarbeitung. Sensor-Callbacks laufen auf dem Main-Thread;
 * die Oberfläche wird von einem Ticker mit höchstens [UI_INTERVAL_MS]⁻¹ = 10 Hz aktualisiert.
 */
class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val accelSource = SensorSource(sensorManager, Sensor.TYPE_ACCELEROMETER)
    private val baroSource = SensorSource(sensorManager, Sensor.TYPE_PRESSURE)
    private val gnssSource = GnssSource(application)
    private val notifier = ShakeNotifier(application)
    private val altitudeConverter =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) AltitudeConverter() else null

    private val accelBuffer = RingBuffer<AccelSample>(ACCEL_CAPACITY)
    private val pressureBuffer = RingBuffer<PressureSample>(PRESSURE_CAPACITY)
    private val gnssBuffer = RingBuffer<GnssSample>(GNSS_CAPACITY)

    private val accelRate = RateMeter()
    private val pressureAverage = MovingAverage(PRESSURE_WINDOW)
    private val shakeDetector = ShakeDetector()
    private var referencePressure: Float? = null
    private val pressureMeasurement = PressureMeasurement()
    private val measurementResults = ArrayList<MeasurementResult>()

    private val accelLog = ChainLogger("ACC", every = 50)
    private val baroLog = ChainLogger("BARO", every = 10)
    private val gnssLog = ChainLogger("GNSS", every = 5)

    private var gnssStatus: GnssUi = GnssUi.Waiting
    private var banner: String? = null
    private var bannerUntilNanos = 0L

    private val _uiState = MutableStateFlow(
        UiState(
            accel = AccelUi(accelSource.isAvailable, AccelSamplingRate.GAME),
            barometer = BarometerUi(baroSource.isAvailable),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val accelChart = CartesianChartModelProducer()
    val barometerChart = CartesianChartModelProducer()
    val gnssChart = CartesianChartModelProducer()

    private var tickerJob: Job? = null

    // region Lebenszyklus (aufgerufen aus MainActivity.onStart/onStop)

    fun onStart() {
        if (tickerJob != null) return
        startAccelerometer()
        pressureAverage.reset()
        baroSource.start(SensorManager.SENSOR_DELAY_NORMAL, ::onPressureEvent)
        startGnss()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(UI_INTERVAL_MS)
            }
        }
    }

    fun onStop() {
        pressureMeasurement.cancel()
        accelSource.stop()
        baroSource.stop()
        gnssSource.stop()
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onCleared() = onStop()

    // endregion

    // region Benutzeraktionen

    fun setMode(mode: DisplayMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setAccelSamplingRate(rate: AccelSamplingRate) {
        if (rate == _uiState.value.accel.samplingRate) return
        _uiState.update { it.copy(accel = it.accel.copy(samplingRate = rate)) }
        if (accelSource.isRunning) startAccelerometer()
    }

    /** Nach Rückkehr aus dem Berechtigungsdialog. */
    fun onPermissionsChanged() {
        if (tickerJob != null) startGnss()
    }

    /** Messmodus: Rohwerte des Barometers 20 s lang mitteln. */
    fun startPressureMeasurement() {
        if (!pressureMeasurement.isRunning) pressureMeasurement.start(SystemClock.elapsedRealtimeNanos())
    }

    fun dismissBanner() {
        banner = null
        _uiState.update { it.copy(banner = null) }
    }

    // endregion

    // region Sensorverarbeitung

    private fun startAccelerometer() {
        accelRate.reset()
        accelSource.start(_uiState.value.accel.samplingRate.sensorDelay, ::onAccelEvent)
    }

    private fun onAccelEvent(event: SensorEvent) {
        val (x, y, z) = event.values
        val t = event.timestamp
        val seq = accelLog.nextSeq()
        seq?.let { accelLog.raw(it, "t=$t ns x=$x y=$y z=$z") }

        val magnitude = sqrt(x * x + y * y + z * z)
        accelRate.onEvent(t)
        accelBuffer.add(AccelSample(t, x, y, z, magnitude))
        seq?.let { accelLog.processed(it, "|a|=$magnitude m/s² rate=${accelRate.rateHz} Hz") }

        if (shakeDetector.check(magnitude, t)) onShake(magnitude)
    }

    private fun onShake(magnitude: Float) {
        val text = magnitude.format(1)
        if (!notifier.notifyShake(text)) {
            banner = "Starke Erschütterung: |a| = $text m/s²"
            bannerUntilNanos = SystemClock.elapsedRealtimeNanos() + BANNER_DURATION_NANOS
        }
    }

    private fun onPressureEvent(event: SensorEvent) {
        val raw = event.values[0]
        val t = event.timestamp
        val seq = baroLog.nextSeq()
        seq?.let { baroLog.raw(it, "t=$t ns p=$raw hPa") }

        pressureMeasurement.add(raw)
        val smoothed = pressureAverage.add(raw)
        // p0 und Δh erst aus einem vollständig geglätteten Wert bestimmen (nicht aus einem Einzelwert).
        val settled = pressureAverage.isFull
        if (referencePressure == null && settled) referencePressure = smoothed
        val reference = referencePressure
        val deltaH = if (reference != null && settled) SensorManager.getAltitude(reference, smoothed) else null
        pressureBuffer.add(PressureSample(t, raw, smoothed, deltaH))
        seq?.let { baroLog.processed(it, "p̄=$smoothed hPa p0=$reference hPa Δh=$deltaH m") }
    }

    private fun startGnss() {
        gnssStatus = when (gnssSource.start(::onLocation, ::onGnssProviderChanged)) {
            GnssSource.StartResult.STARTED ->
                gnssBuffer.lastOrNull()?.let { gnssStatus as? GnssUi.Data } ?: GnssUi.Waiting
            GnssSource.StartResult.NO_PERMISSION -> GnssUi.NoPermission
            GnssSource.StartResult.UNAVAILABLE -> GnssUi.Unavailable
            GnssSource.StartResult.DISABLED -> GnssUi.Disabled
        }
    }

    private fun onGnssProviderChanged(enabled: Boolean) {
        gnssStatus = if (enabled) GnssUi.Waiting else GnssUi.Disabled
    }

    private fun onLocation(location: Location) {
        // Die Umrechnung lädt beim ersten Aufruf ein Geoid-Modell (I/O) und läuft daher nicht im Main-Thread.
        viewModelScope.launch {
            val mslAltitude = withContext(Dispatchers.IO) { altitudeAboveSeaLevel(location) }
            onLocation(location, mslAltitude)
        }
    }

    /**
     * Höhe über dem Meeresspiegel. [Location.getAltitude] bezieht sich auf das WGS84-Ellipsoid und
     * liegt in Deutschland rund 45–50 m darüber. Ohne Umrechnung (API < 34) wird keine Höhe angezeigt.
     */
    private fun altitudeAboveSeaLevel(location: Location): Double? {
        if (!location.hasAltitude() || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        if (!location.hasMslAltitude()) {
            try {
                altitudeConverter?.addMslAltitudeToLocation(getApplication(), location)
            } catch (_: IOException) {
                return null
            }
        }
        return if (location.hasMslAltitude()) location.mslAltitudeMeters else null
    }

    private fun onLocation(location: Location, mslAltitude: Double?) {
        val t = location.elapsedRealtimeNanos
        val sample = GnssSample(
            timestampNanos = t,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeM = mslAltitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
        )
        // Koordinaten werden bewusst nicht geloggt.
        val seq = gnssLog.nextSeq()
        seq?.let {
            gnssLog.raw(
                it,
                "t=$t ns alt=${location.altitude} (Ellipsoid) acc=${location.accuracy} speed=${location.speed}",
            )
        }
        gnssBuffer.add(sample)
        gnssStatus = GnssUi.Data(
            latitude = sample.latitude.format(6) + " °",
            longitude = sample.longitude.format(6) + " °",
            altitude = sample.altitudeM?.let { it.format(1) + " m" } ?: PLACEHOLDER,
            accuracy = sample.accuracyM?.let { "± " + it.format(1) + " m" } ?: PLACEHOLDER,
            speed = sample.speedMps?.let { it.format(2) + " m/s" } ?: PLACEHOLDER,
        )
        seq?.let {
            gnssLog.processed(it, "altMSL=${sample.altitudeM} acc=${sample.accuracyM} v=${sample.speedMps}")
        }
    }

    // endregion

    // region UI-Aktualisierung (≤ 10 Hz)

    private suspend fun tick() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (banner != null && now > bannerUntilNanos) banner = null
        pressureMeasurement.finishIfDue(now)?.let(::onMeasurementFinished)

        val state = _uiState.value
        val newState = state.copy(
            accel = buildAccelUi(state.accel),
            barometer = buildBarometerUi(state.barometer),
            gnss = withFixAge(gnssStatus, now),
            banner = banner,
        )
        _uiState.value = newState
        logDisplayed(newState)

        if (newState.mode == DisplayMode.CHART) updateCharts(now)
    }

    /** Ergänzt das Alter des letzten Fixes, damit veraltete Werte nicht als aktuell erscheinen. */
    private fun withFixAge(status: GnssUi, now: Long): GnssUi {
        val data = status as? GnssUi.Data ?: return status
        val last = gnssBuffer.lastOrNull() ?: return status
        val age = ((now - last.timestampNanos) / 1_000_000_000L).coerceAtLeast(0)
        return data.copy(ageSeconds = age, stale = age >= GNSS_STALE_SECONDS)
    }

    private fun buildAccelUi(previous: AccelUi): AccelUi {
        val sample = accelBuffer.lastOrNull() ?: return previous
        return previous.copy(
            x = sample.x.format(2) + " m/s²",
            y = sample.y.format(2) + " m/s²",
            z = sample.z.format(2) + " m/s²",
            magnitude = sample.magnitude.format(2) + " m/s²",
            eventRate = accelRate.rateHz?.let { it.format(1) + " Hz" } ?: PLACEHOLDER,
        )
    }

    private fun onMeasurementFinished(result: MeasurementResult) {
        measurementResults.add(result)
        val nr = measurementResults.size
        Log.i(TAG_MEASUREMENT, "#$nr p=${result.meanHpa} hPa s=${result.stdDevHpa} hPa n=${result.count}")
    }

    private fun formatMeasurement(index: Int): String {
        val r = measurementResults[index]
        val line = "#${index + 1}: ${r.meanHpa.format(3)} hPa ± ${r.stdDevHpa.format(3)} (n=${r.count})"
        if (index == 0) return line
        val previous = measurementResults[index - 1].meanHpa
        val deltaP = r.meanHpa - previous
        val deltaH = SensorManager.getAltitude(previous.toFloat(), r.meanHpa.toFloat())
        return "$line\n   Δp = ${deltaP.format(3)} hPa ≙ Δh = ${deltaH.format(2)} m"
    }

    private fun buildBarometerUi(previous: BarometerUi): BarometerUi {
        val now = SystemClock.elapsedRealtimeNanos()
        val withMeasurement = previous.copy(
            measurementRemainingS = if (pressureMeasurement.isRunning) pressureMeasurement.remainingSeconds(now) else null,
            measurements = measurementResults.indices.map(::formatMeasurement),
        )
        val sample = pressureBuffer.lastOrNull() ?: return withMeasurement
        return withMeasurement.copy(
            pressure = sample.smoothedHpa.format(2) + " hPa",
            relativeAltitude = sample.relativeAltitudeM?.let { it.format(2) + " m" } ?: PLACEHOLDER,
        )
    }

    private fun logDisplayed(state: UiState) {
        with(state.accel) { accelLog.displayed { "x=$x y=$y z=$z |a|=$magnitude rate=$eventRate" } }
        with(state.barometer) { baroLog.displayed { "p=$pressure Δh=$relativeAltitude" } }
        (state.gnss as? GnssUi.Data)?.let { gnss ->
            gnssLog.displayed { "h=${gnss.altitude} acc=${gnss.accuracy} v=${gnss.speed}" }
        }
    }

    private suspend fun updateCharts(now: Long) {
        val accel = accelBuffer.lastSeconds(now) { it.timestampNanos }
        accelChart.runTransaction {
            if (accel.isNotEmpty()) {
                lineModel {
                    series(accel.xs, accel.samples.map { it.x })
                    series(accel.xs, accel.samples.map { it.y })
                    series(accel.xs, accel.samples.map { it.z })
                    series(accel.xs, accel.samples.map { it.magnitude })
                }
            }
        }

        val pressure = pressureBuffer.lastSeconds(now) { it.timestampNanos }
            .filter { it.relativeAltitudeM != null }
        barometerChart.runTransaction {
            if (pressure.isNotEmpty()) {
                lineModel { series(pressure.xs, pressure.samples.map { it.relativeAltitudeM ?: 0f }) }
            }
        }

        val gnss = gnssBuffer.lastSeconds(now) { it.timestampNanos }
            .filter { it.speedMps != null }
        gnssChart.runTransaction {
            if (gnss.isNotEmpty()) {
                lineModel { series(gnss.xs, gnss.samples.map { it.speedMps ?: 0f }) }
            }
        }
    }

    // endregion

    private companion object {
        const val UI_INTERVAL_MS = 100L
        const val TAG_MEASUREMENT = "MESSUNG"
        const val PRESSURE_WINDOW = 10
        const val BANNER_DURATION_NANOS = 5_000_000_000L

        // Kapazitäten für ≥ 30 s: GAME liefert typ. ~50 Hz, manche Geräte bis ~200 Hz.
        const val ACCEL_CAPACITY = 6_000
        const val PRESSURE_CAPACITY = 1_000
        const val GNSS_CAPACITY = 64
    }
}

private fun Float.format(decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", this)
private fun Double.format(decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", this)
