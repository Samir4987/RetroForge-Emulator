package com.example.emulator

data class FrameSnapshot(
    val frameIndex: Long,
    val timestampMs: Long,
    val stateJson: String,
    val displayBuffer: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameSnapshot
        return frameIndex == other.frameIndex
    }

    override fun hashCode(): Int = frameIndex.hashCode()
}

class RewindBuffer(val capacity: Int = 300) {
    private val buffer = ArrayList<FrameSnapshot>(capacity)
    private var writeHead = 0
    private var count = 0

    @Synchronized
    fun push(snapshot: FrameSnapshot) {
        if (buffer.size < capacity) {
            buffer.add(snapshot)
        } else {
            buffer[writeHead] = snapshot
        }
        writeHead = (writeHead + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun pop(): FrameSnapshot? {
        if (count == 0) return null
        writeHead = (writeHead - 1 + capacity) % capacity
        val item = buffer[writeHead]
        count--
        return item
    }

    @Synchronized
    fun canRewind(): Boolean = count > 0

    @Synchronized
    fun availableFrames(): Int = count

    @Synchronized
    fun clear() {
        buffer.clear()
        writeHead = 0
        count = 0
    }
}
