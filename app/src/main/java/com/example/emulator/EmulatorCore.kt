package com.example.emulator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import com.example.data.model.RetroGame
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class EmulatorCore(
    val audioSynthesizer: AudioSynthesizer
) {
    var currentSystem: RetroSystem = RetroSystem.GB
        private set
    var currentGame: RetroGame? = null
        private set

    // Primary display buffer
    var screenBitmap: Bitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888)
        private set
    // Secondary display buffer (for NDS dual screen bottom)
    var bottomScreenBitmap: Bitmap? = null
        private set

    val inputState = EmulatorInputState()
    val rewindBuffer = RewindBuffer(capacity = 360)

    var isPaused = false
    var isRewinding = false
    var fastForwardMultiplier = 1.0f // 1x, 1.5x, 2x, 4x, 8x
    var frameCounter = 0L
    var playTimeSeconds = 0L

    // In-game entity simulation state
    private var playerX = 80f
    private var playerY = 72f
    private var playerVx = 0f
    private var playerVy = 0f
    private var playerHealth = 100
    private var score = 0
    private var gameTick = 0
    private lateinit var campaign: GameCampaign
    val activeCampaignView: CampaignView?
        get() = if (::campaign.isInitialized) campaign.view else null
    private val collectedGems = mutableSetOf<Int>()
    private val defeatedDungeonSentries = mutableSetOf<Int>()
    private var touchWasActive = false

    // N64 3D engine state
    private var pitch = 0f
    private var yaw = 0f
    private var roll = 0f
    private val starfield = FloatArray(180) { (it % 100).toFloat() - 50f }
    private val lasers = mutableListOf<FloatArray>() // [x, y, z, vx, vy, vz]

    // GBA Racer state
    private var trackCurve = 0f
    private var trackPosition = 0f
    private var speed = 0f
    private var lap = 1

    // NDS Touch state
    private var shieldCharge = 100
    private val touchTargets = mutableListOf<FloatArray>() // [x, y, radius, alive]

    init {
        // Pre-populate touch targets
        repeat(6) {
            touchTargets.add(floatArrayOf(40f + (it % 3) * 40f, 36f + (it / 3) * 60f, 12f, 1f))
        }
    }

    fun loadGame(game: RetroGame) {
        currentGame = game
        currentSystem = RetroSystem.fromCode(game.system)
        campaign = GameCampaign.forSystem(currentSystem)
        campaign.reset()
        collectedGems.clear()
        defeatedDungeonSentries.clear()
        touchWasActive = false
        recreateBuffers()
        resetGameState()
        rewindBuffer.clear()
        audioSynthesizer.playCoin()
    }

    private fun recreateBuffers() {
        screenBitmap = Bitmap.createBitmap(
            currentSystem.screenWidth,
            currentSystem.screenHeight,
            Bitmap.Config.ARGB_8888
        )
        if (currentSystem.isDualScreen) {
            bottomScreenBitmap = Bitmap.createBitmap(
                currentSystem.screenWidth,
                currentSystem.screenHeight,
                Bitmap.Config.ARGB_8888
            )
        } else {
            bottomScreenBitmap = null
        }
    }

    private fun resetGameState() {
        playerX = currentSystem.screenWidth / 2f
        playerY = currentSystem.screenHeight / 2f
        playerVx = 0f
        playerVy = 0f
        playerHealth = 100
        score = 0
        speed = 0f
        trackPosition = 0f
        trackCurve = 0f
        lap = 1
        pitch = 0f
        yaw = 0f
        lasers.clear()
        shieldCharge = 100
        collectedGems.clear()
        gameTick = 0
        frameCounter = 0L
        playTimeSeconds = 0L
        touchWasActive = false
        touchTargets.forEachIndexed { index, target ->
            target[0] = 40f + (index % 3) * 40f
            target[1] = 36f + (index / 3) * 60f
            target[3] = 1f
        }
    }

    fun tick() {
        if (isPaused || !::campaign.isInitialized) return

        if (isRewinding) {
            val pastFrame = rewindBuffer.pop()
            if (pastFrame != null) {
                restoreFromSnapshot(pastFrame)
                audioSynthesizer.playBeep(180f, 25, AudioSynthesizer.WaveType.SQUARE)
            } else {
                isRewinding = false
            }
            return
        }

        // Fast-forward step execution
        val steps = max(1, fastForwardMultiplier.toInt())
        for (s in 0 until steps) {
            val previousLevel = campaign.view.level
            updateSimulation()
            if (campaign.view.level != previousLevel) {
                collectedGems.clear()
                defeatedDungeonSentries.clear()
                lasers.clear()
                touchWasActive = false
                touchTargets.forEachIndexed { index, target ->
                    target[0] = 40f + (index % 3) * 40f
                    target[1] = 36f + (index / 3) * 60f
                    target[3] = 1f
                }
            }
            frameCounter++
            if (frameCounter % 60 == 0L) {
                playTimeSeconds++
            }
        }

        renderFrame()
        drawCampaignOverlay()

        // Push state snapshot into rewind buffer (sampling every 2-3 frames to maximize rewind length)
        if (frameCounter % 2 == 0L) {
            val snapshot = createSnapshot()
            rewindBuffer.push(snapshot)
        }
    }

    private fun updateSimulation() {
        gameTick++

        when (currentSystem) {
            RetroSystem.GB -> updateGbSimulation()
            RetroSystem.GBC -> updateGbcSimulation()
            RetroSystem.GBA -> updateGbaSimulation()
            RetroSystem.NDS -> updateNdsSimulation()
            RetroSystem.N64 -> updateN64Simulation()
        }
    }

    private fun updateGbSimulation() {
        val moveSpeed = 1.6f
        if (inputState.dpadUp) playerY = (playerY - moveSpeed).coerceAtLeast(16f)
        if (inputState.dpadDown) playerY = (playerY + moveSpeed).coerceAtMost(128f)
        if (inputState.dpadLeft) playerX = (playerX - moveSpeed).coerceAtLeast(16f)
        if (inputState.dpadRight) playerX = (playerX + moveSpeed).coerceAtMost(144f)

        if (inputState.buttonA) {
            audioSynthesizer.playLaser()
            if (campaign.view.bossActive) {
                campaign.damageBoss()
                score += 10
            } else {
                val sentries = listOf(36f to 36f, 120f to 44f, 80f to 108f, 40f to 104f, 120f to 104f)
                val target = sentries.indices.firstOrNull { index ->
                    index !in defeatedDungeonSentries && kotlin.math.hypot(playerX - sentries[index].first, playerY - sentries[index].second) < 19f
                }
                if (target != null) {
                    defeatedDungeonSentries.add(target)
                    campaign.recordObjective()
                    score += 10
                }
            }
            inputState.buttonA = false
        }
        if (inputState.buttonB) {
            audioSynthesizer.playHit()
            inputState.buttonB = false
        }
    }

    private fun updateGbcSimulation() {
        // Platformer gravity and jump
        playerVy += 0.40f // gravity
        if (playerVy > 6f) playerVy = 6f
        playerY += playerVy

        val floorY = 110f
        if (playerY >= floorY) {
            playerY = floorY
            playerVy = 0f
        }

        if (inputState.dpadLeft) playerX = (playerX - 2.2f).coerceAtLeast(10f)
        if (inputState.dpadRight) playerX = (playerX + 2.2f).coerceAtMost(150f)

        if (inputState.buttonA && playerY >= floorY) {
            playerVy = -8.0f
            audioSynthesizer.playJump()
            score += 5
            inputState.buttonA = false
        }
        val gemPositions = listOf(52f to 75f, 118f to 55f, 84f to 42f)
        gemPositions.forEachIndexed { index, gem ->
            if (index !in collectedGems && kotlin.math.hypot(playerX - gem.first, playerY - gem.second) < 13f) {
                collectedGems.add(index)
                score += 25
                audioSynthesizer.playCoin()
                campaign.recordObjective()
            }
        }
        if (inputState.buttonB) {
            if (campaign.view.bossActive) campaign.damageBoss()
            inputState.buttonB = false
        }
    }

    private fun updateGbaSimulation() {
        // Mode 7 Racer
        val maxSpeed = 160f
        if (inputState.buttonA) {
            speed = (speed + 2.5f).coerceAtMost(maxSpeed)
        } else if (inputState.buttonB) {
            speed = (speed - 4f).coerceAtLeast(0f)
        } else {
            speed = (speed - 0.8f).coerceAtLeast(0f)
        }

        if (speed > 10f) {
            if (inputState.dpadLeft) {
                playerX = (playerX - 1.8f).coerceAtLeast(30f)
                trackCurve -= 0.02f
            }
            if (inputState.dpadRight) {
                playerX = (playerX + 1.8f).coerceAtLeast(30f).coerceAtMost(210f)
                trackCurve += 0.02f
            }
        }

        trackPosition += speed * 0.05f
        if (trackPosition > 1000f) {
            trackPosition = 0f
            lap++
            audioSynthesizer.playCoin()
            if (campaign.view.bossActive) campaign.damageBoss() else campaign.recordObjective()
        }
    }

    private fun updateNdsSimulation() {
        // Touch coordinates are normalized by the UI; convert to the target model's 160x144 space.
        val touchPx = inputState.touchX * 160f
        val touchPy = inputState.touchY * 144f
        val justTouched = inputState.isTouching && !touchWasActive

        if (inputState.isTouching) {
            shieldCharge = (shieldCharge - 1).coerceAtLeast(0)
        } else {
            shieldCharge = (shieldCharge + 1).coerceAtMost(100)
        }

        // Count one deliberate tap, not every frame that a finger remains on the screen.
        if (justTouched && campaign.view.bossActive) {
            val dx = touchPx - 80f
            val dy = touchPy - 72f
            if (kotlin.math.hypot(dx, dy) < 18f) campaign.damageBoss()
        } else if (justTouched && !campaign.view.completed) {
            val target = touchTargets.firstOrNull {
                it[3] > 0.5f && kotlin.math.hypot(touchPx - it[0], touchPy - it[1]) < it[2] * 1.5f
            }
            if (target != null) {
                target[3] = 0f
                score += 50
                audioSynthesizer.playLaser()
                campaign.recordObjective()
            }
        }

        // Respawn targets inside the visible touch area while objectives remain.
        if (gameTick % 90 == 0 && !campaign.view.bossActive && !campaign.view.completed) {
            touchTargets.forEach {
                it[3] = 1f
                it[0] = 24f + (Math.random() * 112f).toFloat()
                it[1] = 24f + (Math.random() * 96f).toFloat()
            }
        }
        touchWasActive = inputState.isTouching
    }

    private fun updateN64Simulation() {
        // 3D Pitch / Yaw / Roll
        val stickScale = 0.05f
        pitch += inputState.stickY * stickScale
        yaw += inputState.stickX * stickScale

        if (inputState.dpadUp) pitch += 0.03f
        if (inputState.dpadDown) pitch -= 0.03f
        if (inputState.dpadLeft) yaw -= 0.03f
        if (inputState.dpadRight) yaw += 0.03f

        if (inputState.buttonA || inputState.triggerZ) {
            // Fire 3D Laser
            lasers.add(floatArrayOf(0f, -2f, 10f, sin(yaw) * 100f, -sin(pitch) * 100f, cos(yaw).coerceAtLeast(0.25f) * 15f))
            audioSynthesizer.playLaser()
            inputState.buttonA = false
            inputState.triggerZ = false
        }

        // Resolve shots against the moving polygon target at its depth.
        val iter = lasers.iterator()
        while (iter.hasNext()) {
            val laser = iter.next()
            laser[0] += laser[3]
            laser[1] += laser[4]
            laser[2] += laser[5]

            if (laser[2] >= 50f) {
                val shotX = 160f + (laser[0] / (laser[2] * 0.1f)).coerceIn(-150f, 150f)
                val shotY = 120f + (laser[1] / (laser[2] * 0.1f)).coerceIn(-100f, 100f)
                val targetX = 160f + (cos(gameTick * 0.03) * 60f).toFloat()
                val targetY = 120f + (sin(gameTick * 0.02) * 40f).toFloat()
                if (kotlin.math.hypot(shotX - targetX, shotY - targetY) < 24f) {
                    iter.remove()
                    score += 20
                    if (campaign.view.bossActive) campaign.damageBoss() else campaign.recordObjective()
                    continue
                }
            }
            if (laser[2] > 250f) iter.remove()
        }
    }

    private fun renderFrame() {
        when (currentSystem) {
            RetroSystem.GB -> renderGbFrame()
            RetroSystem.GBC -> renderGbcFrame()
            RetroSystem.GBA -> renderGbaFrame()
            RetroSystem.NDS -> renderNdsFrame()
            RetroSystem.N64 -> renderN64Frame()
        }
    }

    private fun renderGbFrame() {
        val canvas = Canvas(screenBitmap)
        // Authentic Game Boy 4-shade greenish palette
        val cBg = Color.rgb(155, 188, 15) // Lightest
        val cTile = Color.rgb(139, 172, 15)
        val cSprite = Color.rgb(48, 98, 48)
        val cDark = Color.rgb(15, 56, 15) // Darkest

        canvas.drawColor(cBg)
        val paint = Paint().apply { isAntiAlias = false }

        // Draw Dungeon Grid
        paint.color = cTile
        for (x in 0 until 160 step 16) {
            for (y in 0 until 144 step 16) {
                if ((x / 16 + y / 16) % 2 == 0) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + 16).toFloat(), (y + 16).toFloat(), paint)
                }
            }
        }

        // Border walls
        paint.color = cDark
        canvas.drawRect(0f, 0f, 160f, 12f, paint)
        canvas.drawRect(0f, 132f, 160f, 144f, paint)
        canvas.drawRect(0f, 0f, 12f, 144f, paint)
        canvas.drawRect(148f, 0f, 160f, 144f, paint)

        // Draw Player Sprite (Knight with sword)
        paint.color = cSprite
        canvas.drawRect(playerX - 6, playerY - 6, playerX + 6, playerY + 6, paint)
        paint.color = cDark
        canvas.drawCircle(playerX, playerY - 2, 3f, paint)
        canvas.drawRect(playerX + 5, playerY - 4, playerX + 9, playerY + 2, paint) // Sword

        // HUD Text
        paint.color = cDark
        paint.textSize = 8f
        canvas.drawText("SCORE: $score", 16f, 10f, paint)
        canvas.drawText("HP: $playerHealth", 100f, 10f, paint)
    }

    private fun renderGbcFrame() {
        val canvas = Canvas(screenBitmap)
        // Vibrant 15-bit color Game Boy Color platformer
        canvas.drawColor(Color.rgb(40, 80, 160)) // Deep Sky Blue

        val paint = Paint().apply { isAntiAlias = false }

        // Clouds
        paint.color = Color.rgb(240, 240, 255)
        val cloudX = (gameTick * 0.4f) % 200 - 20
        canvas.drawCircle(cloudX, 30f, 12f, paint)
        canvas.drawCircle(cloudX + 10, 28f, 14f, paint)
        canvas.drawCircle(cloudX + 22, 30f, 10f, paint)

        // Cavern Crystals & Ground
        paint.color = Color.rgb(120, 60, 180)
        canvas.drawRect(0f, 110f, 160f, 144f, paint)
        paint.color = Color.rgb(180, 90, 240)
        canvas.drawRect(0f, 110f, 160f, 114f, paint)

        // Floating Platforms
        paint.color = Color.rgb(0, 200, 150)
        canvas.drawRect(30f, 85f, 75f, 92f, paint)
        canvas.drawRect(95f, 65f, 140f, 72f, paint)

        // Gems (crystals)
        paint.color = Color.rgb(255, 215, 0)
        val gemBounce = (sin(gameTick * 0.1) * 3).toFloat()
        canvas.drawCircle(52f, 75f + gemBounce, 4f, paint)
        canvas.drawCircle(118f, 55f + gemBounce, 4f, paint)
        canvas.drawCircle(84f, 42f - gemBounce, 4f, paint)

        // Player (Red & Yellow cap)
        paint.color = Color.rgb(240, 50, 50)
        canvas.drawRect(playerX - 6, playerY - 12, playerX + 6, playerY, paint)
        paint.color = Color.rgb(255, 220, 80)
        canvas.drawCircle(playerX, playerY - 8, 4f, paint)

        // HUD
        paint.color = Color.WHITE
        paint.textSize = 9f
        canvas.drawText("💎 $score", 8f, 14f, paint)
        canvas.drawText("GBC 15-BIT", 100f, 14f, paint)
    }

    private fun renderGbaFrame() {
        val canvas = Canvas(screenBitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // Mode 7 Pseudo-3D Racing
        // Horizon sky
        paint.color = Color.rgb(20, 30, 80)
        canvas.drawRect(0f, 0f, 240f, 60f, paint)

        // Mountain silhouettes
        paint.color = Color.rgb(50, 60, 110)
        val mPath = Path().apply {
            moveTo(0f, 60f)
            lineTo(40f, 35f)
            lineTo(90f, 55f)
            lineTo(150f, 25f)
            lineTo(200f, 50f)
            lineTo(240f, 35f)
            lineTo(240f, 60f)
            close()
        }
        canvas.drawPath(mPath, paint)

        // Scaling perspective road (Mode 7)
        val horizonY = 60f
        for (y in 60 until 160) {
            val progress = (y - horizonY) / (160f - horizonY) // 0 to 1
            val roadWidth = 20f + progress * progress * 190f
            val roadCenter = 120f + trackCurve * (1f - progress) * 80f
            val isStripe = ((y + (trackPosition * 0.8f).toInt()) / 8) % 2 == 0

            // Grass
            paint.color = if (isStripe) Color.rgb(30, 130, 40) else Color.rgb(25, 110, 35)
            canvas.drawRect(0f, y.toFloat(), 240f, (y + 1).toFloat(), paint)

            // Asphalt
            paint.color = if (isStripe) Color.rgb(90, 90, 95) else Color.rgb(75, 75, 80)
            canvas.drawRect(roadCenter - roadWidth / 2, y.toFloat(), roadCenter + roadWidth / 2, (y + 1).toFloat(), paint)

            // Red/White Curbs
            paint.color = if (isStripe) Color.RED else Color.WHITE
            canvas.drawRect(roadCenter - roadWidth / 2 - 4, y.toFloat(), roadCenter - roadWidth / 2, (y + 1).toFloat(), paint)
            canvas.drawRect(roadCenter + roadWidth / 2, y.toFloat(), roadCenter + roadWidth / 2 + 4, (y + 1).toFloat(), paint)
        }

        // Player 32-Bit Racecar Sprite
        val carX = playerX
        val carY = 135f
        paint.color = Color.rgb(220, 20, 60) // Crimson racer body
        canvas.drawRoundRect(carX - 16, carY - 8, carX + 16, carY + 8, 4f, 4f, paint)
        paint.color = Color.rgb(20, 20, 20) // Tires
        canvas.drawRect(carX - 18, carY - 6, carX - 14, carY + 4, paint)
        canvas.drawRect(carX + 14, carY - 6, carX + 18, carY + 4, paint)
        paint.color = Color.CYAN // Windshield
        canvas.drawRect(carX - 8, carY - 4, carX + 8, carY + 2, paint)

        // HUD overlay
        paint.color = Color.YELLOW
        paint.textSize = 10f
        canvas.drawText("SPD: ${speed.toInt()} MPH", 8f, 15f, paint)
        canvas.drawText("LAP: $lap/3", 190f, 15f, paint)
    }

    private fun renderNdsFrame() {
        // TOP SCREEN: Radar & Boss Health
        val topCanvas = Canvas(screenBitmap)
        topCanvas.drawColor(Color.rgb(10, 15, 30))
        val paint = Paint().apply { isAntiAlias = true }

        // Radar grid
        paint.color = Color.rgb(0, 100, 60)
        paint.style = Paint.Style.STROKE
        topCanvas.drawCircle(128f, 96f, 70f, paint)
        topCanvas.drawCircle(128f, 96f, 45f, paint)
        topCanvas.drawLine(128f, 26f, 128f, 166f, paint)
        topCanvas.drawLine(58f, 96f, 198f, 96f, paint)

        // Sweeping radar beam
        paint.color = Color.rgb(0, 255, 128)
        val radarAngle = gameTick * 0.05
        topCanvas.drawLine(
            128f, 96f,
            (128f + cos(radarAngle) * 70f).toFloat(),
            (96f + sin(radarAngle) * 70f).toFloat(),
            paint
        )

        // Boss / Targets
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        touchTargets.forEach {
            if (it[3] > 0.5f) {
                topCanvas.drawCircle(it[0] * (256f / 160f), it[1] * (192f / 144f), 5f, paint)
            }
        }

        // Boss Health Bar
        paint.color = Color.DKGRAY
        topCanvas.drawRect(20f, 12f, 236f, 22f, paint)
        paint.color = Color.rgb(255, 80, 80)
        topCanvas.drawRect(22f, 14f, 22f + 210f * 0.75f, 20f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        topCanvas.drawText("NDS DUAL SCREEN - ARENA RADAR", 45f, 180f, paint)

        // BOTTOM SCREEN: Touch Screen Matrix
        bottomScreenBitmap?.let { bBmp ->
            val bCanvas = Canvas(bBmp)
            bCanvas.drawColor(Color.rgb(25, 30, 45))

            // Touch Grid lines
            paint.color = Color.rgb(50, 60, 90)
            paint.style = Paint.Style.STROKE
            for (x in 0 until 256 step 32) bCanvas.drawLine(x.toFloat(), 0f, x.toFloat(), 192f, paint)
            for (y in 0 until 192 step 32) bCanvas.drawLine(0f, y.toFloat(), 256f, y.toFloat(), paint)

            // Touch Targets to hit with stylus
            touchTargets.forEach {
                if (it[3] > 0.5f) {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(0, 200, 255)
                    bCanvas.drawCircle(it[0] * (256f / 160f), it[1] * (192f / 144f), it[2], paint)
                    paint.color = Color.WHITE
                    bCanvas.drawCircle(it[0] * (256f / 160f), it[1] * (192f / 144f), it[2] * 0.4f, paint)
                }
            }

            // Stylus Touch Ripple
            if (inputState.isTouching) {
                paint.color = Color.rgb(255, 230, 0)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                bCanvas.drawCircle(inputState.touchX * 256f, inputState.touchY * 192f, 16f, paint)
                paint.strokeWidth = 1f
            }

            // Shield status
            paint.style = Paint.Style.FILL
            paint.color = Color.CYAN
            paint.textSize = 10f
            bCanvas.drawText("STYLUS TOUCH SCREEN - DEFENSE: $shieldCharge%", 16f, 180f, paint)
        }
    }

    private fun renderN64Frame() {
        val canvas = Canvas(screenBitmap)
        // Dark 3D space cosmos
        canvas.drawColor(Color.rgb(5, 5, 15))
        val paint = Paint().apply { isAntiAlias = true }

        // 3D Starfield projection
        paint.color = Color.WHITE
        for (i in 0 until starfield.size step 3) {
            val sx = starfield[i]
            val sy = starfield[i + 1]
            var sz = starfield[i + 2] - (gameTick * 0.8f) % 80f
            if (sz <= 2f) sz += 80f

            val px = 160f + (sx / sz) * 160f
            val py = 120f + (sy / sz) * 120f
            if (px in 0f..320f && py in 0f..240f) {
                val size = (3f / (sz * 0.1f)).coerceIn(1f, 4f)
                canvas.drawCircle(px, py, size, paint)
            }
        }

        // Draw 3D Lasers
        paint.color = Color.GREEN
        paint.strokeWidth = 3f
        lasers.forEach {
            val sx = 160f + (it[0] / (it[2] * 0.1f)).coerceIn(-150f, 150f)
            val sy = 120f + (it[1] / (it[2] * 0.1f)).coerceIn(-100f, 100f)
            canvas.drawCircle(sx, sy, 4f, paint)
        }
        paint.strokeWidth = 1f

        // Draw 3D Polygon Ship Cockpit Reticle
        paint.color = Color.rgb(255, 200, 0)
        paint.style = Paint.Style.STROKE
        val cx = 160f + sin(yaw) * 40f
        val cy = 120f + sin(pitch) * 30f
        canvas.drawCircle(cx, cy, 18f, paint)
        canvas.drawLine(cx - 24f, cy, cx - 18f, cy, paint)
        canvas.drawLine(cx + 18f, cy, cx + 24f, cy, paint)
        canvas.drawLine(cx, cy - 24f, cx, cy - 18f, paint)
        canvas.drawLine(cx, cy + 18f, cx, cy + 24f, paint)

        // 3D Polygon Asteroid Target
        val targetZ = 50f
        val tx = 160f + cos(gameTick * 0.03) * 60f
        val ty = 120f + sin(gameTick * 0.02) * 40f
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(180, 140, 220)
        val poly = Path().apply {
            moveTo((tx).toFloat(), (ty - 16).toFloat())
            lineTo((tx + 18).toFloat(), (ty - 6).toFloat())
            lineTo((tx + 12).toFloat(), (ty + 16).toFloat())
            lineTo((tx - 14).toFloat(), (ty + 14).toFloat())
            lineTo((tx - 18).toFloat(), (ty - 4).toFloat())
            close()
        }
        canvas.drawPath(poly, paint)

        // Cockpit instrument panel
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(25, 30, 45)
        canvas.drawRect(0f, 210f, 320f, 240f, paint)
        paint.color = Color.CYAN
        paint.textSize = 10f
        canvas.drawText("N64 64-BIT 3D ENGINE", 16f, 226f, paint)
        canvas.drawText("TARGET LOCK: OK", 140f, 226f, paint)
        canvas.drawText("SCORE: $score", 240f, 226f, paint)
    }

    /** Draws campaign progress in the prototype's existing low-resolution HUD style. */
    private fun drawCampaignOverlay() {
        if (!::campaign.isInitialized) return
        val view = campaign.view
        val canvas = Canvas(screenBitmap)
        val paint = Paint().apply { isAntiAlias = false }
        val h = screenBitmap.height.toFloat()
        val w = screenBitmap.width.toFloat()
        val panelHeight = if (currentSystem == RetroSystem.GBC) 22f else 20f
        paint.color = Color.argb(220, 8, 12, 20)
        canvas.drawRect(0f, h - panelHeight, w, h, paint)
        if (view.bossActive) {
            paint.style = Paint.Style.FILL
            when (currentSystem) {
                RetroSystem.GB -> {
                    paint.color = Color.rgb(15, 56, 15)
                    canvas.drawRect(w / 2f - 9f, h / 2f - 9f, w / 2f + 9f, h / 2f + 9f, paint)
                    paint.color = Color.rgb(155, 188, 15)
                    canvas.drawRect(w / 2f - 5f, h / 2f - 3f, w / 2f - 2f, h / 2f, paint)
                    canvas.drawRect(w / 2f + 2f, h / 2f - 3f, w / 2f + 5f, h / 2f, paint)
                }
                RetroSystem.GBC -> {
                    paint.color = Color.rgb(220, 80, 255)
                    val crystal = Path().apply {
                        moveTo(w / 2f, h / 2f - 18f)
                        lineTo(w / 2f + 14f, h / 2f)
                        lineTo(w / 2f, h / 2f + 18f)
                        lineTo(w / 2f - 14f, h / 2f)
                        close()
                    }
                    canvas.drawPath(crystal, paint)
                }
                RetroSystem.GBA -> {
                    paint.color = Color.rgb(245, 150, 30)
                    canvas.drawRoundRect(w / 2f - 20f, h * 0.58f, w / 2f + 20f, h * 0.68f, 5f, 5f, paint)
                    paint.color = Color.DKGRAY
                    canvas.drawRect(w / 2f - 14f, h * 0.65f, w / 2f - 7f, h * 0.70f, paint)
                    canvas.drawRect(w / 2f + 7f, h * 0.65f, w / 2f + 14f, h * 0.70f, paint)
                }
                RetroSystem.NDS -> {
                    paint.color = Color.RED
                    canvas.drawCircle(w / 2f, h / 2f, 12f, paint)
                    paint.color = Color.WHITE
                    canvas.drawCircle(w / 2f, h / 2f, 4f, paint)
                }
                RetroSystem.N64 -> {
                    paint.color = Color.rgb(180, 140, 220)
                    paint.style = Paint.Style.STROKE
                    val r = 22f
                    val bossShape = Path().apply {
                        moveTo(w / 2f, h / 2f - r)
                        lineTo(w / 2f + r, h / 2f - 5f)
                        lineTo(w / 2f + 13f, h / 2f + r)
                        lineTo(w / 2f - 17f, h / 2f + r * 0.7f)
                        lineTo(w / 2f - r, h / 2f - 7f)
                        close()
                    }
                    canvas.drawPath(bossShape, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
        if (!view.bossActive && currentSystem == RetroSystem.GB) {
            val sentries = listOf(36f to 36f, 120f to 44f, 80f to 108f, 40f to 104f, 120f to 104f)
            paint.color = Color.rgb(48, 98, 48)
            sentries.forEachIndexed { index, point ->
                if (index < view.objectiveGoal && index !in defeatedDungeonSentries) {
                    canvas.drawRect(point.first - 5f, point.second - 5f, point.first + 5f, point.second + 5f, paint)
                    paint.color = Color.rgb(15, 56, 15)
                    canvas.drawRect(point.first - 2f, point.second - 2f, point.first + 2f, point.second, paint)
                    paint.color = Color.rgb(48, 98, 48)
                }
            }
        }
        paint.color = when (currentSystem) {
            RetroSystem.GB -> Color.rgb(15, 56, 15)
            RetroSystem.GBC -> Color.rgb(100, 220, 190)
            RetroSystem.GBA -> Color.YELLOW
            RetroSystem.NDS -> Color.CYAN
            RetroSystem.N64 -> Color.rgb(255, 200, 0)
        }
        paint.textSize = if (w < 200f) 6f else 8f
        val label = if (view.completed) "CAMPAIGN CLEAR" else "LV ${view.level}/3 ${view.levelTitle}"
        canvas.drawText(label.take(34), 3f, h - 11f, paint)
        paint.textSize = if (w < 200f) 5f else 7f
        val detail = when {
            view.completed -> view.message
            view.bossActive -> "BOSS ${view.bossName} HP ${view.bossHealth}/${view.bossHealthMax}"
            view.objectiveProgress == 0 -> view.story
            else -> "${view.objective}: ${view.objectiveProgress}/${view.objectiveGoal}"
        }
        canvas.drawText(detail.take(if (w < 200f) 28 else 44), 3f, h - 3f, paint)
        if (currentSystem == RetroSystem.NDS && bottomScreenBitmap != null) {
            val bottom = Canvas(bottomScreenBitmap!!)
            paint.textSize = 8f
            paint.color = Color.WHITE
            bottom.drawText(if (view.bossActive) "BOSS: ${view.bossName}" else "LV ${view.level}: ${view.objective}", 5f, 12f, paint)
            if (view.bossActive) {
                paint.color = Color.RED
                bottom.drawRect(5f, 17f, 5f + 246f * view.bossHealth / view.bossHealthMax, 21f, paint)
            }
        }
    }

    private fun createSnapshot(): FrameSnapshot {
        val stateJson = JSONObject().apply {
            put("frameCounter", frameCounter)
            put("playTimeSeconds", playTimeSeconds)
            put("gameTick", gameTick)
            put("trackCurve", trackCurve.toDouble())
            put("shieldCharge", shieldCharge)
            put("collectedGems", JSONArray().apply { collectedGems.sorted().forEach { put(it) } })
            put("defeatedDungeonSentries", JSONArray().apply { defeatedDungeonSentries.sorted().forEach { put(it) } })
            put("touchTargets", JSONArray().apply {
                touchTargets.forEach { target ->
                    put(JSONArray().put(target[0].toDouble()).put(target[1].toDouble()).put(target[2].toDouble()).put(target[3].toDouble()))
                }
            })
            put("lasers", JSONArray().apply {
                lasers.forEach { laser ->
                    put(JSONArray().put(laser[0].toDouble()).put(laser[1].toDouble()).put(laser[2].toDouble())
                        .put(laser[3].toDouble()).put(laser[4].toDouble()).put(laser[5].toDouble()))
                }
            })
            put("playerX", playerX.toDouble())
            put("playerY", playerY.toDouble())
            put("playerVx", playerVx.toDouble())
            put("playerVy", playerVy.toDouble())
            put("playerHealth", playerHealth)
            put("score", score)
            put("speed", speed.toDouble())
            put("trackPosition", trackPosition.toDouble())
            put("lap", lap)
            put("pitch", pitch.toDouble())
            put("yaw", yaw.toDouble())
            if (::campaign.isInitialized) {
                val campaignState = campaign.snapshot()
                put("campaignStageIndex", campaignState.stageIndex)
                put("campaignProgress", campaignState.progress)
                put("campaignBossHealth", campaignState.bossHealth)
                put("campaignBossActive", campaignState.bossActive)
                put("campaignCompleted", campaignState.completed)
            }
        }.toString()

        val pixels = IntArray(screenBitmap.width * screenBitmap.height)
        screenBitmap.getPixels(pixels, 0, screenBitmap.width, 0, 0, screenBitmap.width, screenBitmap.height)

        return FrameSnapshot(
            frameIndex = frameCounter,
            timestampMs = System.currentTimeMillis(),
            stateJson = stateJson,
            displayBuffer = pixels
        )
    }

    private fun restoreFromSnapshot(snapshot: FrameSnapshot) {
        try {
            val json = JSONObject(snapshot.stateJson)
            frameCounter = json.optLong("frameCounter", snapshot.frameIndex)
            playTimeSeconds = json.optLong("playTimeSeconds", playTimeSeconds)
            gameTick = json.optInt("gameTick", gameTick)
            trackCurve = json.optDouble("trackCurve", trackCurve.toDouble()).toFloat()
            shieldCharge = json.optInt("shieldCharge", shieldCharge).coerceIn(0, 100)

            collectedGems.clear()
            json.optJSONArray("collectedGems")?.let { values ->
                for (index in 0 until values.length()) collectedGems.add(values.optInt(index))
            }
            defeatedDungeonSentries.clear()
            json.optJSONArray("defeatedDungeonSentries")?.let { values ->
                for (index in 0 until values.length()) defeatedDungeonSentries.add(values.optInt(index))
            }
            json.optJSONArray("touchTargets")?.let { values ->
                for (index in 0 until minOf(values.length(), touchTargets.size)) {
                    val target = values.optJSONArray(index) ?: continue
                    if (target.length() >= 4) {
                        touchTargets[index][0] = target.optDouble(0).toFloat()
                        touchTargets[index][1] = target.optDouble(1).toFloat()
                        touchTargets[index][2] = target.optDouble(2).toFloat()
                        touchTargets[index][3] = target.optDouble(3).toFloat()
                    }
                }
            }
            lasers.clear()
            json.optJSONArray("lasers")?.let { values ->
                for (index in 0 until values.length()) {
                    val laser = values.optJSONArray(index) ?: continue
                    if (laser.length() >= 6) {
                        lasers.add(FloatArray(6) { part -> laser.optDouble(part).toFloat() })
                    }
                }
            }
            touchWasActive = false
            playerX = json.optDouble("playerX", playerX.toDouble()).toFloat()
            playerY = json.optDouble("playerY", playerY.toDouble()).toFloat()
            playerVx = json.optDouble("playerVx", playerVx.toDouble()).toFloat()
            playerVy = json.optDouble("playerVy", playerVy.toDouble()).toFloat()
            playerHealth = json.optInt("playerHealth", playerHealth)
            score = json.optInt("score", score)
            speed = json.optDouble("speed", speed.toDouble()).toFloat()
            trackPosition = json.optDouble("trackPosition", trackPosition.toDouble()).toFloat()
            lap = json.optInt("lap", lap)
            pitch = json.optDouble("pitch", pitch.toDouble()).toFloat()
            yaw = json.optDouble("yaw", yaw.toDouble()).toFloat()
            if (::campaign.isInitialized && json.has("campaignStageIndex")) {
                campaign.restore(
                    CampaignSnapshot(
                        stageIndex = json.optInt("campaignStageIndex", 0),
                        progress = json.optInt("campaignProgress", 0),
                        bossHealth = json.optInt("campaignBossHealth", 0),
                        bossActive = json.optBoolean("campaignBossActive", false),
                        completed = json.optBoolean("campaignCompleted", false)
                    )
                )
            }

            screenBitmap.setPixels(
                snapshot.displayBuffer,
                0,
                screenBitmap.width,
                0,
                0,
                screenBitmap.width,
                screenBitmap.height
            )
        } catch (_: Exception) {}
    }

    fun exportSaveStateBase64(): String {
        val snapshot = createSnapshot()
        val data = JSONObject().apply {
            put("stateJson", snapshot.stateJson)
            put("gameId", currentGame?.id ?: "unknown")
            put("system", currentSystem.code)
            put("playTimeSeconds", playTimeSeconds)
        }.toString()
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun loadSaveStateBase64(base64Data: String): Boolean {
        return try {
            val raw = Base64.decode(base64Data, Base64.NO_WRAP)
            val json = JSONObject(String(raw, Charsets.UTF_8))
            val stateJsonStr = json.getString("stateJson")
            val dummyPixels = IntArray(screenBitmap.width * screenBitmap.height)
            restoreFromSnapshot(
                FrameSnapshot(
                    frameIndex = frameCounter,
                    timestampMs = System.currentTimeMillis(),
                    stateJson = stateJsonStr,
                    displayBuffer = dummyPixels
                )
            )
            renderFrame()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun generateThumbnailBase64(): String {
        return try {
            val scaled = Bitmap.createScaledBitmap(screenBitmap, 80, (80 / currentSystem.defaultAspect).toInt(), true)
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 85, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }
}
