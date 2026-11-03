package com.example.emulator

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Stores large Azahar states in app-private files instead of Firestore documents.
 * Firestore's document-size limit is too small for typical 3DS state blobs.
 */
class N3dsSaveStateStore(private val rootDirectory: File) {
    @Synchronized
    fun save(gameId: String, slot: Long, state: ByteArray): File {
        require(gameId.isNotBlank()) { "Game ID is required." }
        require(slot in 0L..99L) { "Save slot is outside the supported range." }
        require(state.isNotEmpty() && state.size <= MAX_STATE_BYTES) {
            "Save state is empty or exceeds the 256 MiB safety limit."
        }

        val directory = gameDirectory(gameId)
        if (!directory.exists()) {
            check(directory.mkdirs()) { "Could not create the local 3DS save-state directory." }
        }
        val target = File(directory, "slot-$slot.state")
        val temporary = File.createTempFile("slot-$slot-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(state)
                output.fd.sync()
            }
            check(temporary.renameTo(target)) {
                "Could not atomically replace the 3DS save state; the previous slot was preserved."
            }
            return target
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    @Synchronized
    fun load(gameId: String, slot: Long): ByteArray? {
        require(slot in 0L..99L) { "Save slot is outside the supported range." }
        val file = File(gameDirectory(gameId), "slot-$slot.state")
        if (!file.isFile) return null
        require(file.length() in 1..MAX_STATE_BYTES.toLong()) {
            "Local 3DS save state is empty or exceeds the 256 MiB safety limit."
        }
        return file.readBytes()
    }

    @Synchronized
    fun listSlots(gameId: String): List<Long> {
        val directory = gameDirectory(gameId)
        if (!directory.isDirectory) return emptyList()
        val pattern = Regex("^slot-(\\d+)\\.state$")
        return directory.listFiles().orEmpty()
            .mapNotNull { file -> pattern.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull() }
            .filter { it in 0L..99L }
            .sorted()
    }

    @Synchronized
    fun delete(gameId: String, slot: Long): Boolean {
        require(slot in 0L..99L) { "Save slot is outside the supported range." }
        val directory = gameDirectory(gameId)
        val file = File(directory, "slot-$slot.state")
        val deleted = !file.exists() || file.delete()
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
        return deleted
    }

    private fun gameDirectory(gameId: String): File {
        require(gameId.isNotBlank()) { "Game ID is required." }
        val safePrefix = gameId.map { char ->
            if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_'
        }.joinToString("").take(48).ifBlank { "game" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(gameId.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(rootDirectory, "$safePrefix-$digest")
    }

    companion object {
        const val MAX_STATE_BYTES = 256 * 1024 * 1024
    }
}
