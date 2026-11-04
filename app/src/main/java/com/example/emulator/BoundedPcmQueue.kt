package com.example.emulator

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Bounded queue for PCM chunks. When producers outrun playback, the oldest
 * chunk is discarded so audio stays close to the current emulation frame.
 *
 * Chunks are copied on insertion because callers commonly reuse their buffers.
 */
class BoundedPcmQueue(capacityChunks: Int) {
    private val queue: ArrayBlockingQueue<ShortArray>

    init {
        require(capacityChunks > 0) { "Queue capacity must be positive." }
        queue = ArrayBlockingQueue(capacityChunks)
    }

    fun offerLatest(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return false
        val chunk = samples.copyOf()
        while (!queue.offer(chunk)) {
            queue.poll()
        }
        return true
    }

    @Throws(InterruptedException::class)
    fun poll(timeout: Long, unit: TimeUnit): ShortArray? = queue.poll(timeout, unit)

    fun size(): Int = queue.size

    fun clear() = queue.clear()
}
