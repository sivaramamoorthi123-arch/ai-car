package com.example.ai

import com.example.domain.model.GeminiRobotCommand

object RobotCommandValidator {

    sealed class ValidationResult {
        data class Valid(val command: GeminiRobotCommand) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    private val validActions = setOf(
        "MOVE",
        "TURN",
        "STOP",
        "SEARCH_OBJECT",
        "APPROACH_OBJECT",
        "FOLLOW_OBJECT",
        "RETURN_HOME",
        "EXPLORE",
        "SPEAK",
        "FACE",
        "STATUS"
    )

    private val validDirections = setOf(
        "FORWARD",
        "BACKWARD",
        "LEFT",
        "RIGHT",
        "STOP"
    )

    fun validate(command: GeminiRobotCommand, maxSpeedLimit: Int): ValidationResult {
        val action = command.action.uppercase().trim()
        if (action !in validActions) {
            return ValidationResult.Invalid("Unknown robot action: ${command.action}")
        }

        if (action == "STOP") {
            return ValidationResult.Valid(
                command.copy(
                    action = "STOP",
                    direction = "STOP",
                    speed_percent = 0,
                    speech = command.speech ?: "Stopping robot immediately."
                )
            )
        }

        var direction = command.direction?.uppercase()?.trim()
        if (direction != null && direction !in validDirections) {
            return ValidationResult.Invalid("Invalid direction: $direction")
        }

        // Clamp speed
        val rawSpeed = command.speed_percent ?: 50
        val clampedSpeed = rawSpeed.coerceIn(10, maxSpeedLimit)

        // Validate distance (1 to 200 cm safe discrete threshold)
        val distance = command.distance_cm?.let {
            if (it <= 0) return ValidationResult.Invalid("Distance must be positive")
            it.coerceIn(1, 200)
        }

        // Validate turn angle (0 to 360 degrees)
        val angle = command.turn_angle?.let {
            it.coerceIn(0, 360)
        }

        // Validate target for search missions
        if ((action == "SEARCH_OBJECT" || action == "APPROACH_OBJECT") && command.target.isNullOrBlank()) {
            return ValidationResult.Invalid("Target object must be specified for search mission")
        }

        val sanitized = command.copy(
            action = action,
            direction = direction,
            speed_percent = clampedSpeed,
            distance_cm = distance,
            turn_angle = angle,
            speech = command.speech ?: "Executing $action"
        )

        return ValidationResult.Valid(sanitized)
    }
}
