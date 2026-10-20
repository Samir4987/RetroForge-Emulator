package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SaveStateDto
import com.example.data.repository.SaveStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SaveStateRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveState_authenticatedUser_writesAndObservesSuccessfully() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = SaveStateRepository(firestore)

    val testSlot = 1L
    val stateId = "game_gb_test_${UUID.randomUUID()}"
    val dto = SaveStateDto(
      id = stateId,
      userId = uid,
      gameId = "pocket_quest",
      gameTitle = "Pocket Quest",
      system = "GB",
      slot = testSlot,
      label = "Before Dungeon Boss",
      stateData = "dGVzdF9zdGF0ZV9kYXRh",
      playTimeSeconds = 420L
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.saveState(dto)
    }
    assertTrue("Save state should succeed", result.isSuccess)

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeSaveStates("pocket_quest").first { list ->
        list.any { it.id == stateId }
      }
    }
    val savedItem = observed.first { it.id == stateId }
    assertEquals("pocket_quest", savedItem.gameId)
    assertEquals("GB", savedItem.system)
    assertEquals("Before Dungeon Boss", savedItem.label)
  }

  @Test
  fun getSaveState_unauthenticatedUser_fails() = runBlocking {
    auth.signOut()
    val repository = SaveStateRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.getSaveState("some_state_id")
    }
    assertTrue("Unauthenticated calls must return failure", result.isFailure)
    assertTrue("Failure exception should be IllegalStateException", result.exceptionOrNull() is IllegalStateException)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_gamer@test.com"
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 6000L
  }
}
