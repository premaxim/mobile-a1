package de.hs.mobilea1.ui

import de.hs.mobilea1.data.RingBuffer
import kotlin.math.roundToLong

/** Diagrammfenster in Sekunden (x-Achse: −30 … 0 s). */
const val CHART_WINDOW_SECONDS = 30

/**
 * Ausschnitt der letzten [CHART_WINDOW_SECONDS] eines Puffers, auf 0,1 s gerastert
 * (je Raster das jüngste Sample). x ist das Alter in Sekunden, negativ, aufsteigend sortiert.
 */
class ChartWindow<T>(val xs: List<Double>, val samples: List<T>) {
    fun isNotEmpty() = samples.isNotEmpty()

    fun filter(predicate: (T) -> Boolean): ChartWindow<T> {
        val keep = samples.indices.filter { predicate(samples[it]) }
        return ChartWindow(keep.map { xs[it] }, keep.map { samples[it] })
    }
}

fun <T> RingBuffer<T>.lastSeconds(nowNanos: Long, timestamp: (T) -> Long): ChartWindow<T> {
    val xs = ArrayList<Double>()
    val samples = ArrayList<T>()
    var lastBucket = Long.MIN_VALUE
    for (i in 0 until size) {
        val sample = get(i)
        val ageTenths = ((nowNanos - timestamp(sample)) / 1e8).roundToLong().coerceAtLeast(0)
        if (ageTenths > CHART_WINDOW_SECONDS * 10L) continue
        val bucket = -ageTenths
        if (bucket == lastBucket) {
            samples[samples.lastIndex] = sample
        } else {
            xs.add(bucket / 10.0)
            samples.add(sample)
            lastBucket = bucket
        }
    }
    return ChartWindow(xs, samples)
}
