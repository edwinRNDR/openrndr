package org.openrndr.draw

import org.openrndr.internal.Driver

interface AtomicCounterBuffer {
    companion object {
        fun create(counterCount: Int) = Driver.instance.createAtomicCounterBuffer(counterCount)
    }

    fun write(data: IntArray)
    fun read(): IntArray

    fun bind(index: Int)

    /**
     * Reset all the counters to 0
     */
    fun reset()

    fun destroy()

}