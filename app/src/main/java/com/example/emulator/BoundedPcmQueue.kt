package com.example.emulator

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Bounded PCM chunk queue; on overload it keeps the newest audio instead of blocking emulation. */
internal class BoundedPcmQueue(capacityChunks: Int) {
    private val chunks: ArrayBlockingQueue<ShortArray>

    init {
        require(capacityChunks > 0) { "Audio queue capacity must be positive." }
        chunks = ArrayBlockingQueue(capacityChunks)
    }

    fun offerLatest(samples: ShortArray) {
        if (samples.isEmpty()) return
        if (!chunks.offer(samples)) {
            chunks.poll()
            if (!chunks.offer(samples)) {
                chunks.poll()
                chunks.offer(samples)
            }
        }
    }

    @Throws(InterruptedException::class)
    fun poll(timeout: Long, unit: TimeUnit): ShortArray? = chunks.poll(timeout, unit)

    fun clear() = chunks.clear()

    fun size(): Int = chunks.size
}
