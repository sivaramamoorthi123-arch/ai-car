package com.example.domain.model

enum class RobotConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEMO_MODE,
    RECONNECTING
}

enum class BatteryState {
    GOOD,      // > 50%
    MEDIUM,    // 25% - 50%
    LOW,       // 15% - 25%
    CRITICAL   // < 15%
}

enum class RobotFaceExpression {
    NORMAL,
    HAPPY,
    SAD,
    ANGRY,
    SURPRISED,
    THINKING,
    LISTENING,
    SPEAKING,
    SLEEPING,
    ERROR
}

enum class DriveMode {
    MANUAL,
    AI
}

enum class MotionCommandType {
    FORWARD,
    BACKWARD,
    LEFT,
    RIGHT,
    SPIN_LEFT,
    SPIN_RIGHT,
    STOP
}

data class TelemetryData(
    val batteryVoltage: Double = 7.8,
    val batteryPercent: Int = 82,
    val currentAmperes: Double = 0.45,
    val obstacleDistanceCm: Int = 55,
    val leftMotorSpeed: Int = 0,
    val rightMotorSpeed: Int = 0,
    val wifiRssi: Int = -52,
    val latencyMs: Long = 18L,
    val lastReceivedTimestamp: Long = System.currentTimeMillis(),
    val stateName: String = "STANDBY"
) {
    val batteryState: BatteryState
        get() = when {
            batteryPercent <= 15 -> BatteryState.CRITICAL
            batteryPercent <= 25 -> BatteryState.LOW
            batteryPercent <= 50 -> BatteryState.MEDIUM
            else -> BatteryState.GOOD
        }

    val isObstacleWarning: Boolean
        get() = obstacleDistanceCm in 1..25

    val powerWatts: Double
        get() = (batteryVoltage * currentAmperes * 100).toLong() / 100.0
}

enum class MissionStatus {
    IDLE,
    SEARCHING,
    OBJECT_FOUND,
    ALIGNING,
    APPROACHING,
    TARGET_DISTANCE,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MissionState(
    val status: MissionStatus = MissionStatus.IDLE,
    val missionTitle: String = "No Active Mission",
    val targetObject: String? = null,
    val targetDistanceCm: Int = 25,
    val currentStepDescription: String = "Standby - waiting for instruction",
    val progress: Float = 0f,
    val missionLogs: List<String> = emptyList()
)

data class SettingsConfig(
    val esp32Ip: String = "192.168.4.1",
    val wsPort: Int = 81,
    val camUrl: String = "http://192.168.4.1:81/stream",
    val isDemoMode: Boolean = true,
    val speedLimitPercent: Int = 80,
    val watchdogTimeoutMs: Long = 500L,
    val obstacleSafetyDistanceCm: Int = 20,
    val batteryCellCount: Int = 2,
    val criticalBatteryThreshold: Int = 15,
    val ttsFeedbackEnabled: Boolean = true,
    val hapticEnabled: Boolean = true
)

data class GeminiRobotCommand(
    val type: String = "ROBOT_COMMAND",
    val action: String = "MOVE",
    val direction: String? = "FORWARD",
    val distance_cm: Int? = null,
    val turn_angle: Int? = null,
    val speed_percent: Int? = 50,
    val target: String? = null,
    val face_expression: String? = "NORMAL",
    val speech: String? = null,
    val reasoning: String? = null
)
