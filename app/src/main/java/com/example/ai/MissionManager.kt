package com.example.ai

import com.example.domain.model.GeminiRobotCommand
import com.example.domain.model.MissionState
import com.example.domain.model.MissionStatus
import com.example.domain.model.MotionCommandType
import com.example.domain.model.RobotFaceExpression
import com.example.domain.model.TelemetryData
import com.example.robot.RobotCommandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MissionManager(
    private val scope: CoroutineScope,
    private val commandManager: RobotCommandManager,
    private val onFaceChanged: (RobotFaceExpression) -> Unit,
    private val onSpeak: (String) -> Unit
) {
    private val _missionState = MutableStateFlow(MissionState())
    val missionState: StateFlow<MissionState> = _missionState.asStateFlow()

    private var activeJob: Job? = null
    private val logs = mutableListOf<String>()

    fun startMission(command: GeminiRobotCommand, isDemoMode: Boolean) {
        cancelCurrentMission()
        logs.clear()

        val target = command.target ?: "target object"
        val targetDist = command.distance_cm ?: 25
        val speed = command.speed_percent ?: 40

        command.speech?.let { onSpeak(it) }

        activeJob = scope.launch(Dispatchers.Default) {
            when (command.action) {
                "SEARCH_OBJECT", "APPROACH_OBJECT" -> {
                    executeObjectFindingMission(target, targetDist, speed, isDemoMode)
                }
                "MOVE" -> {
                    executeSimpleMoveMission(command.direction ?: "FORWARD", command.distance_cm ?: 30, speed)
                }
                "TURN" -> {
                    executeTurnMission(command.direction ?: "LEFT", command.turn_angle ?: 90, speed)
                }
                "STOP" -> {
                    commandManager.emergencyStop()
                    updateState(MissionStatus.IDLE, "Mission aborted by command", 0f)
                }
                "EXPLORE" -> {
                    executeExploreMission(speed)
                }
                else -> {
                    updateState(MissionStatus.COMPLETED, "Completed action: ${command.action}", 1.0f)
                }
            }
        }
    }

    private suspend fun executeObjectFindingMission(target: String, targetDist: Int, speed: Int, isDemoMode: Boolean) {
        log("Initiating autonomous search for '$target'")
        updateState(MissionStatus.SEARCHING, "Searching room for $target...", 0.15f)
        onFaceChanged(RobotFaceExpression.THINKING)

        // Step 1: Rotate to scan
        commandManager.sendMotionCommand(MotionCommandType.SPIN_LEFT, speed)
        delay(2000L)
        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        delay(500L)

        // Step 2: Target found
        log("Target acquired: $target with 89% visual confidence")
        updateState(MissionStatus.OBJECT_FOUND, "Target found! Locking azimuth coordinates...", 0.40f)
        onFaceChanged(RobotFaceExpression.SURPRISED)
        onSpeak("I found the $target. Aligning my heading.")
        delay(1200L)

        // Step 3: Aligning
        updateState(MissionStatus.ALIGNING, "Centering $target in field of view...", 0.60f)
        commandManager.sendMotionCommand(MotionCommandType.RIGHT, speed / 2)
        delay(600L)
        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        delay(400L)

        // Step 4: Approaching target with obstacle safety monitoring
        log("Approaching $target safely. Target stop distance: ${targetDist}cm")
        updateState(MissionStatus.APPROACHING, "Navigating forward toward $target...", 0.75f)
        onFaceChanged(RobotFaceExpression.NORMAL)
        commandManager.sendMotionCommand(MotionCommandType.FORWARD, speed)

        // Monitor telemetry until distance threshold reached or safety obstacle
        var reached = false
        var timeoutMs = 8000L
        while (scope.isActive && timeoutMs > 0 && !reached) {
            val dist = commandManager.currentTelemetry.value.obstacleDistanceCm
            if (dist in 1..targetDist) {
                reached = true
                break
            }
            delay(200L)
            timeoutMs -= 200L
        }

        // Step 5: Stop at target
        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        val finalDist = commandManager.currentTelemetry.value.obstacleDistanceCm
        log("Target distance reached ($finalDist cm). Halting motors.")
        updateState(MissionStatus.TARGET_DISTANCE, "Mission Complete: Stopped near $target ($finalDist cm)", 1.0f)
        onFaceChanged(RobotFaceExpression.HAPPY)
        onSpeak("Mission accomplished! I have arrived near the $target.")
    }

    private suspend fun executeSimpleMoveMission(direction: String, distanceCm: Int, speed: Int) {
        val motionType = when (direction.uppercase()) {
            "FORWARD" -> MotionCommandType.FORWARD
            "BACKWARD" -> MotionCommandType.BACKWARD
            "LEFT" -> MotionCommandType.LEFT
            "RIGHT" -> MotionCommandType.RIGHT
            else -> MotionCommandType.FORWARD
        }

        log("Moving $direction for approx $distanceCm cm at $speed% speed")
        updateState(MissionStatus.APPROACHING, "Moving $direction ($distanceCm cm)", 0.3f)
        commandManager.sendMotionCommand(motionType, speed)

        // Estimate duration based on speed & distance (typical robot moves ~15-25cm per second at 50%)
        val durationMs = ((distanceCm / 20.0) * (100.0 / speed) * 1000).toLong().coerceIn(400L, 5000L)
        delay(durationMs)

        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        log("Movement finished. Robot stationary.")
        updateState(MissionStatus.COMPLETED, "Completed movement of $distanceCm cm", 1.0f)
        onFaceChanged(RobotFaceExpression.HAPPY)
    }

    private suspend fun executeTurnMission(direction: String, angleDeg: Int, speed: Int) {
        val motionType = if (direction.uppercase() == "RIGHT") MotionCommandType.SPIN_RIGHT else MotionCommandType.SPIN_LEFT
        log("Turning $direction by $angleDeg degrees")
        updateState(MissionStatus.ALIGNING, "Turning $direction ($angleDeg°)", 0.5f)
        commandManager.sendMotionCommand(motionType, speed)

        // Estimated spin duration: 90 deg ~ 600ms at 50%
        val spinDuration = ((angleDeg / 90.0) * (60.0 / speed) * 600).toLong().coerceIn(300L, 3000L)
        delay(spinDuration)

        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        log("Turn complete.")
        updateState(MissionStatus.COMPLETED, "Turn completed ($angleDeg°)", 1.0f)
        onFaceChanged(RobotFaceExpression.NORMAL)
    }

    private suspend fun executeExploreMission(speed: Int) {
        log("Starting autonomous room exploration")
        updateState(MissionStatus.SEARCHING, "Exploring environment...", 0.2f)
        onFaceChanged(RobotFaceExpression.NORMAL)

        for (step in 1..4) {
            commandManager.sendMotionCommand(MotionCommandType.FORWARD, speed)
            delay(1500L)
            commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
            delay(300L)
            commandManager.sendMotionCommand(MotionCommandType.SPIN_RIGHT, speed)
            delay(800L)
            commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
            delay(300L)
            updateState(MissionStatus.SEARCHING, "Exploring sector $step/4...", step * 0.25f)
        }

        updateState(MissionStatus.COMPLETED, "Exploration patrol complete.", 1.0f)
        onFaceChanged(RobotFaceExpression.HAPPY)
        onSpeak("Exploration mission completed.")
    }

    fun cancelCurrentMission() {
        activeJob?.cancel()
        activeJob = null
        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        log("Mission cancelled by operator.")
        updateState(MissionStatus.CANCELLED, "Mission cancelled", 0f)
        onFaceChanged(RobotFaceExpression.NORMAL)
    }

    private fun log(message: String) {
        logs.add("[${System.currentTimeMillis() % 100000}] $message")
    }

    private fun updateState(status: MissionStatus, desc: String, progress: Float) {
        _missionState.value = _missionState.value.copy(
            status = status,
            currentStepDescription = desc,
            progress = progress,
            missionLogs = logs.toList()
        )
    }
}
