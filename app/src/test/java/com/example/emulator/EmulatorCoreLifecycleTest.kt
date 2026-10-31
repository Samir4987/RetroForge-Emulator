package com.example.emulator

import com.example.data.model.RetroGame
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmulatorCoreLifecycleTest {
    @Test
    fun loadingANewGameResumesEngineAfterReturningToLibrary() {
        val core = EmulatorCore(AudioSynthesizer())
        core.isPaused = true
        core.isRewinding = true

        core.loadGame(
            RetroGame(
                id = "gba-lifecycle-test",
                title = "Lifecycle Test",
                system = "GBA",
                developer = "RetroForge",
                description = "Test game"
            )
        )

        assertFalse(core.isPaused)
        assertFalse(core.isRewinding)
    }
}
