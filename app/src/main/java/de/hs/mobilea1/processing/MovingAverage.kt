package de.hs.mobilea1.processing

import de.hs.mobilea1.data.RingBuffer

/** Gleitender Mittelwert über die letzten [window] Werte. */
class MovingAverage(window: Int) {
    private val values = RingBuffer<Float>(window)
    private var sum = 0.0

    /** `true`, sobald [window] Werte vorliegen und der Mittelwert voll geglättet ist. */
    val isFull: Boolean get() = values.size == values.capacity

    fun add(value: Float): Float {
        if (values.size == values.capacity) sum -= values[0]
        values.add(value)
        sum += value
        return (sum / values.size).toFloat()
    }

    fun reset() {
        values.clear()
        sum = 0.0
    }
}
