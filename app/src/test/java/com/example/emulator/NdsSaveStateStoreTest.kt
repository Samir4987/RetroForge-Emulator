package com.example.emulator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdsSaveStateStoreTest {
    @Test
    fun savesListsLoadsAndDeletesSlots() {
        val root = createTempDir(prefix = "nds-save-store-test-")
        try {
            val store = NdsSaveStateStore(root)
            val payload = byteArrayOf(1, 2, 3, 4, 5)

            val entry = store.save("game-one", 2, payload)

            assertEquals(2, entry.slot)
            assertEquals(payload.size.toLong(), entry.sizeBytes)
            assertArrayEquals(payload, store.load("game-one", 2))
            assertEquals(listOf(2), store.list("game-one").map { it.slot })
            assertTrue(store.delete("game-one", 2))
            assertNull(store.load("game-one", 2))
            assertTrue(store.list("game-one").isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun isolatesGamesAndReplacesSlotsAtomically() {
        val root = createTempDir(prefix = "nds-save-store-test-")
        try {
            val store = NdsSaveStateStore(root)
            store.save("game-one", 0, byteArrayOf(1))
            store.save("game-one", 0, byteArrayOf(7, 8))
            store.save("game-two", 0, byteArrayOf(9))

            assertArrayEquals(byteArrayOf(7, 8), store.load("game-one", 0))
            assertArrayEquals(byteArrayOf(9), store.load("game-two", 0))
            assertEquals(1, store.list("game-one").size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidSlotsAndEmptyPayloads() {
        val root = createTempDir(prefix = "nds-save-store-test-")
        try {
            val store = NdsSaveStateStore(root)
            assertThrows(IllegalArgumentException::class.java) {
                store.save("game", -1, byteArrayOf(1))
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.save("game", 0, byteArrayOf())
            }
            assertTrue(store.delete("game", 0))
        } finally {
            root.deleteRecursively()
        }
    }
}
