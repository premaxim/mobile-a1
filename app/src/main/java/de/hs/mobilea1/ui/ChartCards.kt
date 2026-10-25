package de.hs.mobilea1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

class ChartProducers(
    val accel: CartesianChartModelProducer,
    val barometer: CartesianChartModelProducer,
    val gnss: CartesianChartModelProducer,
)

private data class Series(val label: String, val color: Color, val dashed: Boolean = false)

private val accelSeries = listOf(
    Series("a_x", Color(0xFFE53935)),
    Series("a_y", Color(0xFF43A047)),
    Series("a_z", Color(0xFF1E88E5)),
    Series("|a| (gestrichelt)", Color(0xFF6D4C41), dashed = true),
)
private val barometerSeries = listOf(Series("Δh [m]", Color(0xFF8E24AA)))
private val gnssSeries = listOf(Series("v [m/s]", Color(0xFFFB8C00)))

@Composable
fun ChartCards(state: UiState, charts: ChartProducers, actions: SensorScreenActions) {
    SensorCard("Accelerometer – a [m/s²]") {
        if (!state.accel.available) {
            SensorUnavailable()
            return@SensorCard
        }
        SamplingRateSelector(state.accel.samplingRate, actions.onSamplingRateChange)
        Text("Gemessene Rate: ${state.accel.eventRate}", style = MaterialTheme.typography.bodySmall)
        LineChart(charts.accel, accelSeries)
    }

    SensorCard("Barometer – relative Höhe") {
        if (!state.barometer.available) {
            SensorUnavailable()
            return@SensorCard
        }
        LineChart(charts.barometer, barometerSeries)
    }

    SensorCard("GNSS – Geschwindigkeit") {
        GnssStatusContent(state.gnss, actions) { LineChart(charts.gnss, gnssSeries) }
    }
}

@Composable
private fun LineChart(producer: CartesianChartModelProducer, series: List<Series>) {
    val lines = series.map {
        val fill = LineCartesianLayer.LineFill.single(Fill(it.color))
        // Gestrichelt, damit eine deckungsgleiche Linie darunter (z. B. a_z bei flachem Gerät) sichtbar bleibt.
        if (it.dashed) {
            LineCartesianLayer.rememberLine(fill, stroke = LineCartesianLayer.LineStroke.Dashed())
        } else {
            LineCartesianLayer.rememberLine(fill)
        }
    }
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(lines),
            rangeProvider = remember { HoldingRangeProvider() },
        ),
        startAxis = VerticalAxis.rememberStart(
            valueFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 1, decimalSeparator = ",") },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = remember { CartesianValueFormatter { _, x, _ -> "${x.toInt()} s" } },
            itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 50 }) },
        ),
        getXStep = { _, _, _ -> X_STEP },
    )
    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        animationSpec = null,
        placeholder = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Daten in den letzten $CHART_WINDOW_SECONDS s")
            }
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { LegendItem(it) }
    }
}

@Composable
private fun LegendItem(series: Series) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(series.color, CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Text(series.label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Rasterweite der x-Werte, siehe [lastSeconds]. */
private const val X_STEP = 0.1

/**
 * Feste x-Achse (−30 … 0 s). Die y-Achse übernimmt das bisher kleinste bzw. größte Minimum/Maximum und
 * wächst damit nur, statt bei jeder Aktualisierung neu zu skalieren (sonst "zittert" das Diagramm).
 */
private class HoldingRangeProvider : CartesianLayerRangeProvider {
    private var heldMinY = Double.POSITIVE_INFINITY
    private var heldMaxY = Double.NEGATIVE_INFINITY

    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = -CHART_WINDOW_SECONDS.toDouble()

    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0

    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        heldMinY = minOf(heldMinY, minY)
        return heldMinY
    }

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        heldMaxY = maxOf(heldMaxY, maxY)
        return heldMaxY
    }
}
