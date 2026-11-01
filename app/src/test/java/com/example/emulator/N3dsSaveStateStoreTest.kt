package com.example.emulator

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class N3dsSaveStateStoreTest {
    @Test
    fun savesListsLoadsAndDeletesStateFiles() {
        val root = Files.createTempDirectory("retroforge-3ds-states").toFile()
        try {
            val store = N3dsSaveStateStore(root)
            val payload = byteArrayOf(1, 2, 3, 4, 5)

            store.save("game-123", 2, payload)

            assertEquals(listOf(2L), store.listSlots("game-123"))
            assertArrayEquals(payload, store.load("game-123", 2))
            assertTrue(store.delete("game-123", 2))
            assertTrue(store.listSlots("game-123").isEmpty())
            assertNull(store.load("game-123", 2))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sanitizesGameIdsAndRejectsInvalidSlots() {
        val root = Files.createTempDirectory("retroforge-3ds-paths").toFile()
        try {
            val store = N3dsSaveStateStore(root)
            store.save("../../outside", 0, byteArrayOf(7))
            assertEquals(listOf(0L), store.listSlots("../../outside"))
            assertEquals(1, root.listFiles().orEmpty().size)
            assertFalse(root.listFiles().orEmpty().single().canonicalPath.contains("/outside/"))
        } finally {
            root.deleteRecursively()
        }
    }
}
