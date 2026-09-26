package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.RobotCommandValidator
import com.example.domain.model.GeminiRobotCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Robot Car", appName)
    }

    @Test
    fun `validate valid move command within safety limits`() {
        val cmd = GeminiRobotCommand(
            action = "MOVE",
            direction = "FORWARD",
            distance_cm = 50,
            speed_percent = 70
        )
        val result = RobotCommandValidator.validate(cmd, maxSpeedLimit = 80)
        assertTrue(result is RobotCommandValidator.ValidationResult.Valid)
        val valid = (result as RobotCommandValidator.ValidationResult.Valid).command
        assertEquals("MOVE", valid.action)
        assertEquals("FORWARD", valid.direction)
        assertEquals(50, valid.distance_cm)
        assertEquals(70, valid.speed_percent)
    }

    @Test
    fun `clamp excessive speed to configured maximum limit`() {
        val cmd = GeminiRobotCommand(
            action = "MOVE",
            direction = "FORWARD",
            speed_percent = 120
        )
        val result = RobotCommandValidator.validate(cmd, maxSpeedLimit = 60)
        assertTrue(result is RobotCommandValidator.ValidationResult.Valid)
        val valid = (result as RobotCommandValidator.ValidationResult.Valid).command
        assertEquals(60, valid.speed_percent)
    }

    @Test
    fun `reject command with invalid action`() {
        val cmd = GeminiRobotCommand(
            action = "DESTROY_WALL"
        )
        val result = RobotCommandValidator.validate(cmd, maxSpeedLimit = 80)
        assertTrue(result is RobotCommandValidator.ValidationResult.Invalid)
    }

    @Test
    fun `emergency stop command always valid and sets speed to zero`() {
        val cmd = GeminiRobotCommand(
            action = "STOP",
            speed_percent = 100
        )
        val result = RobotCommandValidator.validate(cmd, maxSpeedLimit = 80)
        assertTrue(result is RobotCommandValidator.ValidationResult.Valid)
        val valid = (result as RobotCommandValidator.ValidationResult.Valid).command
        assertEquals(0, valid.speed_percent)
        assertEquals("STOP", valid.direction)
    }
}
