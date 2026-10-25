package de.hs.mobilea1.log

import android.util.Log

/**
 * Loggt die Verarbeitungskette SENSOR_EVENT → PROCESS → UI für jedes [every]-te Event
 * eines Sensors. Die laufende Nummer verknüpft die drei Einträge miteinander.
 */
class ChainLogger(private val sensorName: String, private val every: Int) {
    private var count = 0L
    private var pendingUiSeq: Long? = null

    /** Zählt ein Event und liefert dessen Nummer, falls es geloggt werden soll. */
    fun nextSeq(): Long? {
        count++
        return if (count % every == 0L) count else null
    }

    fun raw(seq: Long, message: String) {
        Log.d(TAG_SENSOR_EVENT, "$sensorName #$seq $message")
    }

    fun processed(seq: Long, message: String) {
        Log.d(TAG_PROCESS, "$sensorName #$seq $message")
        pendingUiSeq = seq
    }

    /** Loggt den angezeigten Wert, sofern seit dem letzten Aufruf ein Event geloggt wurde. */
    fun displayed(message: () -> String) {
        val seq = pendingUiSeq ?: return
        Log.d(TAG_UI, "$sensorName #$seq ${message()}")
        pendingUiSeq = null
    }

    companion object {
        const val TAG_SENSOR_EVENT = "SENSOR_EVENT"
        const val TAG_PROCESS = "PROCESS"
        const val TAG_UI = "UI"
    }
}
