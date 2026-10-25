package de.hs.mobilea1

import de.hs.mobilea1.data.RingBuffer
import de.hs.mobilea1.processing.MovingAverage
import de.hs.mobilea1.processing.RateMeter
import de.hs.mobilea1.processing.ShakeDetector
import de.hs.mobilea1.ui.lastSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingTest {

    @Test
    fun ringBufferOverwritesOldest() {
        val buffer = RingBuffer<Int>(3)
        (1..5).forEach(buffer::add)
        assertEquals(listOf(3, 4, 5), buffer.toList())
        assertEquals(5, buffer.lastOrNull())
    }

    @Test
    fun movingAverageUsesLastTenValues() {
        val average = MovingAverage(10)
        repeat(10) { average.add(0f) }
        val result = (1..10).map { average.add(10f) }.last()
        assertEquals(10f, result, 1e-6f)
        assertEquals(5f, MovingAverage(10).run { add(0f); add(10f) }, 1e-6f)
    }

    @Test
    fun shakeDetectorRespectsThresholdAndCooldown() {
        val detector = ShakeDetector()
        val s = 1_000_000_000L
        assertFalse(detector.check(25f, 0))
        assertTrue(detector.check(26f, 1 * s))
        assertFalse(detector.check(30f, 5 * s))
        assertTrue(detector.check(30f, 6 * s))
    }

    @Test
    fun rateMeterMeasuresFrequency() {
        val meter = RateMeter()
        assertNull(meter.rateHz)
        for (i in 0..50) meter.onEvent(i * 20_000_000L)
        assertEquals(50f, meter.rateHz!!, 0.01f)
    }

    @Test
    fun chartWindowKeepsLast30sOnTenthGrid() {
        val buffer = RingBuffer<Long>(100)
        val s = 1_000_000_000L
        listOf(0L, 10 * s, 39 * s, 39 * s + 10_000_000L, 40 * s).forEach(buffer::add)
        val window = buffer.lastSeconds(40 * s) { it }
        assertEquals(listOf(-30.0, -1.0, 0.0), window.xs)
        assertEquals(listOf(10 * s, 39 * s + 10_000_000L, 40 * s), window.samples)
    }
}
