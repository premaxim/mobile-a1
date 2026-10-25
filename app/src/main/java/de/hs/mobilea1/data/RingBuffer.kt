package de.hs.mobilea1.data

/**
 * Ringpuffer fester Kapazität. Ist er voll, überschreibt jedes [add] das älteste Element.
 * Nicht thread-sicher – wird ausschließlich vom Main-Thread benutzt.
 */
class RingBuffer<T>(val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val items = arrayOfNulls<Any?>(capacity)
    private var next = 0

    var size = 0
        private set

    fun add(item: T) {
        items[next] = item
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    /** Index 0 ist das älteste, `size - 1` das neueste Element. */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index $index, size $size")
        return items[(next - size + index + capacity) % capacity] as T
    }

    fun lastOrNull(): T? = if (size == 0) null else get(size - 1)

    fun isEmpty(): Boolean = size == 0

    fun clear() {
        items.fill(null)
        next = 0
        size = 0
    }

    /** Kopie aller Elemente vom ältesten zum neuesten. */
    fun toList(): List<T> = List(size) { get(it) }
}
