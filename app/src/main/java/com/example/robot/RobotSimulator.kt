package com.example.robot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.domain.model.MissionState
import com.example.domain.model.MissionStatus
import com.example.domain.model.MotionCommandType
import com.example.domain.model.RobotFaceExpression
import com.example.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class RobotSimulator(private val scope: CoroutineScope) {

    private val _telemetry = MutableStateFlow(
        TelemetryData(
            batteryVoltage = 7.85,
            batteryPercent = 84,
            currentAmperes = 0.35,
            obstacleDistanceCm = 65,
            leftMotorSpeed = 0,
            rightMotorSpeed = 0,
            wifiRssi = -48,
            latencyMs = 18L,
            stateName = "IDLE"
        )
    )
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _simulatedFrame = MutableStateFlow<Bitmap?>(null)
    val simulatedFrame: StateFlow<Bitmap?> = _simulatedFrame.asStateFlow()

    private val _simulatedFace = MutableStateFlow(RobotFaceExpression.NORMAL)
    val simulatedFace: StateFlow<RobotFaceExpression> = _simulatedFace.asStateFlow()

    private var loopJob: Job? = null
    private var missionJob: Job? = null

    private var currentDriveSpeed = 0
    private var currentDriveDirection = MotionCommandType.STOP
    private var simulatedDistance = 65

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(Dispatchers.Default) {
            var batteryV = 7.85
            var batteryPct = 84
            var scanAngle = 0f

            while (isActive) {
                // Adjust distance based on driving
                when (currentDriveDirection) {
                    MotionCommandType.FORWARD -> {
                        simulatedDistance = (simulatedDistance - (currentDriveSpeed / 20).coerceAtLeast(1))
                            .coerceAtLeast(8)
                    }
                    MotionCommandType.BACKWARD -> {
                        simulatedDistance = (simulatedDistance + 2).coerceAtMost(180)
                    }
                    else -> {}
                }

                // Battery load calculation
                val isMoving = currentDriveDirection != MotionCommandType.STOP && currentDriveSpeed > 0
                val current = if (isMoving) 0.95 + (currentDriveSpeed / 100.0) * 0.45 else 0.32 + Random.nextDouble(-0.02, 0.02)
                
                // Micro discharge
                batteryV = (batteryV - 0.00005).coerceAtLeast(6.2)
                val calculatedPct = (((batteryV - 6.0) / (8.4 - 6.0)) * 100).toInt().coerceIn(0, 100)
                batteryPct = calculatedPct

                val jitterLatency = (16L + Random.nextLong(0, 8))
                val rssi = -45 + Random.nextInt(-4, 3)

                val leftSpeed = when (currentDriveDirection) {
                    MotionCommandType.FORWARD -> currentDriveSpeed
                    MotionCommandType.BACKWARD -> -currentDriveSpeed
                    MotionCommandType.LEFT -> -currentDriveSpeed / 2
                    MotionCommandType.RIGHT -> currentDriveSpeed
                    MotionCommandType.SPIN_LEFT -> -currentDriveSpeed
                    MotionCommandType.SPIN_RIGHT -> currentDriveSpeed
                    MotionCommandType.STOP -> 0
                }
                val rightSpeed = when (currentDriveDirection) {
                    MotionCommandType.FORWARD -> currentDriveSpeed
                    MotionCommandType.BACKWARD -> -currentDriveSpeed
                    MotionCommandType.LEFT -> currentDriveSpeed
                    MotionCommandType.RIGHT -> -currentDriveSpeed / 2
                    MotionCommandType.SPIN_LEFT -> currentDriveSpeed
                    MotionCommandType.SPIN_RIGHT -> -currentDriveSpeed
                    MotionCommandType.STOP -> 0
                }

                _telemetry.value = _telemetry.value.copy(
                    batteryVoltage = (batteryV * 100).toInt() / 100.0,
                    batteryPercent = batteryPct,
                    currentAmperes = (current * 100).toInt() / 100.0,
                    obstacleDistanceCm = simulatedDistance,
                    leftMotorSpeed = leftSpeed,
                    rightMotorSpeed = rightSpeed,
                    wifiRssi = rssi,
                    latencyMs = jitterLatency,
                    lastReceivedTimestamp = System.currentTimeMillis(),
                    stateName = if (isMoving) "DRIVING" else "IDLE"
                )

                // Generate simulated camera frame
                scanAngle = (scanAngle + 3f) % 360f
                _simulatedFrame.value = generateSimulatedVisionBitmap(scanAngle, simulatedDistance, isMoving)

                delay(100L) // 10 Hz telemetry loop
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        missionJob?.cancel()
        missionJob = null
    }

    fun handleMotionCommand(type: MotionCommandType, speed: Int) {
        currentDriveDirection = type
        currentDriveSpeed = speed
        if (type == MotionCommandType.STOP) {
            currentDriveSpeed = 0
        }
    }

    fun setFaceExpression(expression: RobotFaceExpression) {
        _simulatedFace.value = expression
    }

    fun runSimulatedMission(
        target: String,
        targetDist: Int,
        onProgress: (MissionState) -> Unit
    ) {
        missionJob?.cancel()
        missionJob = scope.launch(Dispatchers.Default) {
            val logs = mutableListOf<String>()

            fun log(msg: String, status: MissionStatus, prog: Float) {
                logs.add("[${System.currentTimeMillis() % 100000}] $msg")
                onProgress(
                    MissionState(
                        status = status,
                        missionTitle = "Mission: Find $target",
                        targetObject = target,
                        targetDistanceCm = targetDist,
                        currentStepDescription = msg,
                        progress = prog,
                        missionLogs = logs.toList()
                    )
                )
            }

            _simulatedFace.value = RobotFaceExpression.THINKING
            log("Gemini parsed mission: Search for $target", MissionStatus.SEARCHING, 0.1f)
            delay(1200L)

            _simulatedFace.value = RobotFaceExpression.SURPRISED
            handleMotionCommand(MotionCommandType.SPIN_LEFT, 40)
            log("Sweeping room with ESP32-CAM and ultrasonic radar...", MissionStatus.SEARCHING, 0.25f)
            delay(2000L)

            handleMotionCommand(MotionCommandType.STOP, 0)
            _simulatedFace.value = RobotFaceExpression.HAPPY
            log("Target identified: $target (Confidence: 91%)", MissionStatus.OBJECT_FOUND, 0.45f)
            delay(1000L)

            _simulatedFace.value = RobotFaceExpression.NORMAL
            handleMotionCommand(MotionCommandType.RIGHT, 30)
            log("Centering target in camera frame (Aligning azimuth)", MissionStatus.ALIGNING, 0.6f)
            delay(1200L)

            handleMotionCommand(MotionCommandType.FORWARD, 50)
            log("Approaching target object safely with obstacle sensor active...", MissionStatus.APPROACHING, 0.75f)

            // Approach loop until distance reached
            while (simulatedDistance > targetDist) {
                simulatedDistance = (simulatedDistance - 5).coerceAtLeast(targetDist)
                delay(400L)
            }

            handleMotionCommand(MotionCommandType.STOP, 0)
            _simulatedFace.value = RobotFaceExpression.HAPPY
            log("Target reached at distance: ${simulatedDistance}cm. Mission accomplished!", MissionStatus.COMPLETED, 1.0f)
            delay(2000L)
            _simulatedFace.value = RobotFaceExpression.NORMAL
        }
    }

    fun cancelSimulatedMission(onCancel: () -> Unit) {
        missionJob?.cancel()
        handleMotionCommand(MotionCommandType.STOP, 0)
        _simulatedFace.value = RobotFaceExpression.NORMAL
        onCancel()
    }

    private fun generateSimulatedVisionBitmap(scanAngle: Float, distance: Int, moving: Boolean): Bitmap {
        val width = 480
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.rgb(13, 20, 32))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Grid lines
        paint.color = Color.argb(40, 0, 240, 255)
        paint.strokeWidth = 1f
        for (x in 0..width step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        for (y in 0..height step 40) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }

        // Perspective Horizon
        paint.color = Color.argb(100, 0, 240, 255)
        paint.strokeWidth = 2f
        canvas.drawLine(0f, height * 0.45f, width.toFloat(), height * 0.45f, paint)

        // Simulated target object (table/cube in front of camera)
        val targetWidth = 140f
        val targetHeight = 80f
        val targetLeft = (width / 2f) - (targetWidth / 2f) + (Math.sin(Math.toRadians(scanAngle.toDouble())).toFloat() * 15f)
        val targetTop = (height * 0.45f) + 20f

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(255, 107, 0) // Cyber orange
        paint.strokeWidth = 3f
        canvas.drawRoundRect(RectF(targetLeft, targetTop, targetLeft + targetWidth, targetTop + targetHeight), 12f, 12f, paint)

        // Target Label
        paint.style = Paint.Style.FILL
        paint.textSize = 18f
        canvas.drawText("TARGET: TABLE (91%)", targetLeft, targetTop - 8f, paint)
        canvas.drawText("${distance}cm", targetLeft + targetWidth + 10f, targetTop + 30f, paint)

        // Center HUD Crosshair
        val cx = width / 2f
        val cy = height / 2f
        paint.color = Color.rgb(0, 240, 255) // Cyan
        paint.strokeWidth = 2f
        canvas.drawLine(cx - 20f, cy, cx + 20f, cy, paint)
        canvas.drawLine(cx, cy - 20f, cx, cy + 20f, paint)
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, 35f, paint)

        // Top Status Overlay
        paint.style = Paint.Style.FILL
        paint.textSize = 16f
        paint.color = Color.rgb(0, 230, 118) // Green
        canvas.drawText("ESP32-CAM [DEMO STREAM] - 30 FPS", 20f, 30f, paint)

        paint.color = Color.rgb(255, 255, 255)
        canvas.drawText("DRIVE: ${if (moving) "ACTIVE" else "HOLD"} | RADAR: ${distance}cm", 20f, 54f, paint)

        return bitmap
    }
}
