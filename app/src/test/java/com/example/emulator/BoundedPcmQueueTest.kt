package com.example.emulator

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedPcmQueueTest {
    @Test
    fun dropsOldestChunkWhenQueueIsFull() {
        val queue = BoundedPcmQueue(capacityChunks = 2)
        queue.offerLatest(shortArrayOf(1, 2))
        queue.offerLatest(shortArrayOf(3, 4))
        queue.offerLatest(shortArrayOf(5, 6))

        assertEquals(2, queue.size())
        assertArrayEquals(shortArrayOf(3, 4), queue.poll(0, TimeUnit.MILLISECONDS)!!)
        assertArrayEquals(shortArrayOf(5, 6), queue.poll(0, TimeUnit.MILLISECONDS)!!)
        assertEquals(0, queue.size())
    }

    @Test
    fun ignoresEmptyChunksAndCanClearQueue() {
        val queue = BoundedPcmQueue(capacityChunks = 2)
        queue.offerLatest(shortArrayOf())
        queue.offerLatest(shortArrayOf(1))
        assertEquals(1, queue.size())

        queue.clear()
        assertEquals(0, queue.size())
    }
}
