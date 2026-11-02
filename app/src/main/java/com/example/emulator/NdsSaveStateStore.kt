package com.example.emulator

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class NdsSaveStateEntry(
    val slot: Int,
    val updatedAtMillis: Long,
    val sizeBytes: Long
)

/**
 * App-private, atomic storage for large Nintendo DS save states.
 * Save-state payloads are intentionally not sent to Firestore (1 MiB document limit).
 */
class NdsSaveStateStore(private val filesDir: File) {
    companion object {
        const val MAX_STATE_BYTES = 256L * 1024L * 1024L
        const val MAX_SLOT = 99
    }

    private fun gameDirectory(gameId: String): File {
        require(gameId.isNotBlank()) { "Game ID must not be blank." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(gameId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(File(filesDir, "nds-save-states"), digest)
    }

    private fun slotFile(gameId: String, slot: Int): File {
        require(slot in 0..MAX_SLOT) { "Save slot must be between 0 and $MAX_SLOT." }
        return File(gameDirectory(gameId), "slot-$slot.state")
    }

    @Synchronized
    fun save(gameId: String, slot: Int, state: ByteArray): NdsSaveStateEntry {
        require(state.isNotEmpty()) { "Save-state data is empty." }
        require(state.size.toLong() <= MAX_STATE_BYTES) { "Save-state data exceeds 256 MiB." }
        val destination = slotFile(gameId, slot)
        val directory = destination.parentFile
            ?: throw IllegalStateException("Could not resolve save-state directory.")
        check(directory.exists() || directory.mkdirs()) { "Could not create save-state directory." }
        val temporary = File(directory, "${destination.name}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(state)
                stream.fd.sync()
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                check(temporary.delete()) { "Could not remove temporary save-state file." }
            }
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
        return NdsSaveStateEntry(slot, destination.lastModified(), destination.length())
    }

    @Synchronized
    fun load(gameId: String, slot: Int): ByteArray? {
        val file = slotFile(gameId, slot)
        if (!file.isFile) return null
        require(file.length() in 1..MAX_STATE_BYTES) { "Stored save-state file has an invalid size." }
        return file.readBytes()
    }

    @Synchronized
    fun list(gameId: String): List<NdsSaveStateEntry> {
        val directory = gameDirectory(gameId)
        val files = directory.listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            val slot = Regex("""slot-(\d+)\.state""").matchEntire(file.name)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null
            if (slot !in 0..MAX_SLOT || !file.isFile || file.length() !in 1..MAX_STATE_BYTES) {
                return@mapNotNull null
            }
            NdsSaveStateEntry(slot, file.lastModified(), file.length())
        }.sortedBy { it.slot }
    }

    @Synchronized
    fun delete(gameId: String, slot: Int): Boolean {
        val file = slotFile(gameId, slot)
        return !file.exists() || file.delete()
    }
}
