package com.example.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCampaignTest {
    @Test
    fun everyBuiltInSystemHasThreeStoryStagesAndACompleteEnding() {
        RetroSystem.entries.forEach { system ->
            val campaign = GameCampaign.forSystem(system)
            campaign.reset()
            assertEquals(1, campaign.view.level)
            assertFalse(campaign.view.completed)

            repeat(3) { stageIndex ->
                val stage = campaign.view
                campaign.recordObjective(stage.objectiveGoal)
                assertTrue("${system.code} stage ${stage.level} should activate its boss", campaign.view.bossActive)
                repeat(campaign.view.bossHealth) { campaign.damageBoss() }

                if (stageIndex < 2) {
                    assertEquals(stageIndex + 2, campaign.view.level)
                    assertFalse(campaign.view.completed)
                }
            }

            assertTrue("${system.code} campaign should reach its ending", campaign.view.completed)
            assertFalse(campaign.view.bossActive)
        }
    }

    @Test
    fun saveSnapshotRestoresObjectiveAndBossProgress() {
        val campaign = GameCampaign.forSystem(RetroSystem.GB)
        campaign.reset()
        campaign.recordObjective(campaign.view.objectiveGoal)
        campaign.damageBoss()
        val expected = campaign.view
        val restored = GameCampaign.forSystem(RetroSystem.GB)
        restored.restore(campaign.snapshot())

        assertEquals(expected.level, restored.view.level)
        assertEquals(expected.bossHealth, restored.view.bossHealth)
        assertEquals(expected.bossActive, restored.view.bossActive)
    }

    @Test
    fun objectiveProgressCannotExceedGoalOrChangeDuringBossFight() {
        val campaign = GameCampaign.forSystem(RetroSystem.GBC)
        campaign.reset()
        campaign.recordObjective(100)
        assertEquals(campaign.view.objectiveGoal, campaign.view.objectiveProgress)
        campaign.recordObjective(1)
        assertEquals(campaign.view.objectiveGoal, campaign.view.objectiveProgress)
    }
}
