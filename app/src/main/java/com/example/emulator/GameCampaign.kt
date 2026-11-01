package com.example.emulator

/**
 * Original, self-contained campaign data for RetroForge's five built-in prototypes.
 * The visual renderer remains system-specific; this class only owns story and progression.
 */
data class CampaignStage(
    val title: String,
    val story: String,
    val objective: String,
    val bossName: String,
    val bossStory: String,
    val objectiveGoal: Int,
    val bossHealthMax: Int
)

data class CampaignView(
    val level: Int,
    val levelTitle: String,
    val story: String,
    val objective: String,
    val objectiveProgress: Int,
    val objectiveGoal: Int,
    val bossName: String,
    val bossStory: String,
    val bossHealth: Int,
    val bossHealthMax: Int,
    val bossActive: Boolean,
    val completed: Boolean,
    val message: String
)

class GameCampaign private constructor(
    private val gameTitle: String,
    private val stages: List<CampaignStage>,
    private val ending: String
) {
    private var stageIndex = 0
    private var progress = 0
    private var bossHealth = 0
    private var bossActive = false
    private var finished = false
    private var statusMessage = "The adventure begins!"

    val view: CampaignView
        get() {
            val stage = stages[stageIndex.coerceIn(stages.indices)]
            return CampaignView(
                level = stageIndex + 1,
                levelTitle = stage.title,
                story = stage.story,
                objective = stage.objective,
                objectiveProgress = progress,
                objectiveGoal = stage.objectiveGoal,
                bossName = stage.bossName,
                bossStory = stage.bossStory,
                bossHealth = bossHealth,
                bossHealthMax = stage.bossHealthMax,
                bossActive = bossActive,
                completed = finished,
                message = if (finished) ending else statusMessage
            )
        }

    fun reset() {
        stageIndex = 0
        progress = 0
        bossHealth = 0
        bossActive = false
        finished = false
        statusMessage = stages.first().story
    }

    /** Counts a meaningful gameplay objective, not raw frames or button-hold duration. */
    fun recordObjective(amount: Int = 1) {
        if (finished || bossActive || amount <= 0) return
        val stage = stages[stageIndex]
        progress = (progress + amount).coerceAtMost(stage.objectiveGoal)
        if (progress >= stage.objectiveGoal) {
            bossActive = true
            bossHealth = stage.bossHealthMax
            statusMessage = "${stage.bossName} has appeared!"
        }
    }

    /** Applies one deliberate player attack to the active boss. */
    fun damageBoss(amount: Int = 1) {
        if (finished || !bossActive || amount <= 0) return
        bossHealth = (bossHealth - amount).coerceAtLeast(0)
        if (bossHealth == 0) {
            if (stageIndex == stages.lastIndex) {
                finished = true
                bossActive = false
                statusMessage = ending
            } else {
                stageIndex++
                progress = 0
                bossActive = false
                bossHealth = 0
                statusMessage = stages[stageIndex].story
            }
        }
    }

    companion object {
        fun forSystem(system: RetroSystem): GameCampaign = when (system) {
            RetroSystem.GB -> GameCampaign(
                "Pocket Quest: 8-Bit Dungeon",
                listOf(
                    CampaignStage("The Moss Gate", "A young explorer enters the sealed Moss Gate to find the missing village lantern.", "Defeat the dungeon sentries", "Mossback Slime", "The gate guardian rises from the old stones.", 3, 3),
                    CampaignStage("Crypt of Echoes", "Beyond the gate, the lantern's light reveals a crypt where the echoes have become restless.", "Clear the crypt guardians", "Crypt Warden", "The Warden protects the key to the deepest hall.", 4, 4),
                    CampaignStage("The Lantern Keep", "The lantern waits in the keep, where the shadow that stole it has gathered its strength.", "Break through the keep defenders", "The Hollow Knight", "The Hollow Knight challenges the explorer for the lantern.", 5, 5)
                ),
                "The lantern returns to the village. Its warm light guides everyone home, and the dungeon finally falls silent."
            )
            RetroSystem.GBC -> GameCampaign(
                "Crystal Cavern Color",
                listOf(
                    CampaignStage("Sunlit Entrance", "Mira follows a trail of glowing crystals into a cavern that has lost its colour.", "Collect the entrance crystals", "Shardling", "A crystal guardian blocks the first passage.", 3, 3),
                    CampaignStage("Prism Falls", "The collected shards awaken the Prism Falls and reveal a path above the underground river.", "Gather the prism fragments", "Prism Golem", "The golem tests whether Mira can control the cavern's light.", 3, 4),
                    CampaignStage("Heart of the Cavern", "At the cavern's heart, Mira discovers the colour is fading because its prism core is trapped.", "Restore the heart crystals", "The Dusk Prism", "The Dusk Prism guards the core and the cavern's last light.", 3, 5)
                ),
                "The prism core shines again. Colour flows through every crystal, and Mira leaves the cavern brighter than she found it."
            )
            RetroSystem.GBA -> GameCampaign(
                "Advance Speedway 32-Bit",
                listOf(
                    CampaignStage("Rookie Circuit", "A rookie driver enters the regional series to earn a place on the championship grid.", "Complete the qualifying lap", "Rookie Rival", "Your rival challenges you to prove your pace.", 1, 2),
                    CampaignStage("Mountain Run", "The mountain stage brings tight bends and a rival team determined to take the lead.", "Complete the mountain lap", "Ridge Runner", "The Ridge Runner blocks the route to the final event.", 1, 3),
                    CampaignStage("Grand Prix", "At the Grand Prix, the season title comes down to one final race.", "Complete the championship lap", "Champion Vortex", "Vortex makes one last attempt to keep the trophy.", 1, 4)
                ),
                "The checkered flag falls. You have won the Advance Speedway championship through skill and clean driving."
            )
            RetroSystem.NDS -> GameCampaign(
                "Dual Touch Arena",
                listOf(
                    CampaignStage("Outer Ring", "The arena's defense network is failing. The cadet must protect the outer ring using the touch console.", "Clear the incoming contacts", "Ring Breaker", "A heavy signal appears on the upper radar.", 6, 3),
                    CampaignStage("Core Corridor", "With the outer ring secure, the cadet moves inward to defend the power corridor.", "Defend the corridor wave", "Pulse Engine", "The corridor's automated engine has turned hostile.", 8, 4),
                    CampaignStage("Central Nexus", "The central nexus is the source of the attack. The final defense must shut it down.", "Clear the nexus wave", "Nexus Overlord", "The source of the invasion emerges on the radar.", 10, 5)
                ),
                "The nexus powers down. The arena is safe, and the defense crew welcomes its newest guardian."
            )
            RetroSystem.N64 -> GameCampaign(
                "PolyStar 64",
                listOf(
                    CampaignStage("Orion Approach", "A pilot answers a distress beacon near Orion and finds a field of hostile drones.", "Destroy the approach drones", "Drone Marshal", "The drone formation converges into a command unit.", 3, 3),
                    CampaignStage("Nebula Frontier", "The beacon leads into a nebula where a raider fleet is harvesting star energy.", "Clear the raider formation", "Nebula Dreadnought", "The dreadnought emerges from the nebula.", 4, 4),
                    CampaignStage("PolyStar Core", "At the signal's origin, a rogue navigation core controls the remaining fleet.", "Disable the core defenses", "Astra Core", "Astra Core locks onto your ship for the final encounter.", 5, 5)
                ),
                "Astra Core shuts down and the distress beacon turns green. The frontier is free to navigate again."
            )
        }
    }
}
