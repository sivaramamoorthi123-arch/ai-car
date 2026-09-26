package com.example.robot

import android.os.SystemClock
import android.util.Log
import com.example.data.network.Esp32WebSocketClient
import com.example.domain.model.MotionCommandType
import com.example.domain.model.RobotConnectionState
import com.example.domain.model.RobotFaceExpression
import com.example.domain.model.SettingsConfig
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
import org.json.JSONObject
import kotlin.math.abs

class RobotCommandManager(
    private val scope: CoroutineScope,
    private val webSocketClient: Esp32WebSocketClient,
    private val simulator: RobotSimulator,
    private val onSafetyStopTriggered: (String) -> Unit
) {
    private val tag = "RobotCommandManager"

    private var currentConfig = SettingsConfig()
    private var lastSentCommandTime = 0L
    private var lastCommandType = MotionCommandType.STOP
    private var lastSpeed = 0

    private val _connectionState = MutableStateFlow(RobotConnectionState.DEMO_MODE)
    val connectionState: StateFlow<RobotConnectionState> = _connectionState.asStateFlow()

    private val _currentTelemetry = MutableStateFlow(TelemetryData())
    val currentTelemetry: StateFlow<TelemetryData> = _currentTelemetry.asStateFlow()

    private val _activeFaceExpression = MutableStateFlow(RobotFaceExpression.NORMAL)
    val activeFaceExpression: StateFlow<RobotFaceExpression> = _activeFaceExpression.asStateFlow()

    private var safetyWatchdogJob: Job? = null

    init {
        // Collect telemetry from WebSocket
        scope.launch(Dispatchers.IO) {
            webSocketClient.telemetryFlow.collect { telem ->
                if (!currentConfig.isDemoMode) {
                    _currentTelemetry.value = telem
                    checkSafetyLimits(telem)
                }
            }
        }

        // Collect connection state from WebSocket
        scope.launch(Dispatchers.IO) {
            webSocketClient.connectionState.collect { state ->
                if (!currentConfig.isDemoMode) {
                    _connectionState.value = state
                }
            }
        }

        // Collect simulator telemetry in demo mode
        scope.launch(Dispatchers.IO) {
            simulator.telemetry.collect { simTelem ->
                if (currentConfig.isDemoMode) {
                    _currentTelemetry.value = simTelem
                    checkSafetyLimits(simTelem)
                }
            }
        }

        // Start safety watchdog
        startSafetyWatchdog()
    }

    fun applyConfig(config: SettingsConfig) {
        val modeChanged = currentConfig.isDemoMode != config.isDemoMode
        val ipChanged = currentConfig.esp32Ip != config.esp32Ip || currentConfig.wsPort != config.wsPort
        currentConfig = config

        if (config.isDemoMode) {
            webSocketClient.disconnect()
            simulator.start()
            _connectionState.value = RobotConnectionState.DEMO_MODE
        } else {
            simulator.stop()
            if (modeChanged || ipChanged) {
                webSocketClient.connect(config.esp32Ip, config.wsPort)
            }
        }
    }

    /**
     * Direct, ultra-low latency manual joystick driving.
     * Uses differential throttle calculation and background WebSocket dispatch.
     * ZERO calls to Gemini.
     */
    fun sendJoystickDrive(x: Float, y: Float, speedMultiplier: Float) {
        // Apply dead-zone of 0.12
        val magnitude = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
        if (magnitude < 0.12f) {
            if (lastCommandType != MotionCommandType.STOP) {
                sendMotionCommand(MotionCommandType.STOP, 0)
            }
            return
        }

        // Determine dominant direction and differential speed
        val motionType: MotionCommandType
        val speedPercent = ((magnitude.coerceAtMost(1f) * speedMultiplier * (currentConfig.speedLimitPercent / 100f)) * 100).toInt()

        if (abs(y) >= abs(x)) {
            motionType = if (y < 0) MotionCommandType.FORWARD else MotionCommandType.BACKWARD
        } else {
            motionType = if (x > 0) MotionCommandType.RIGHT else MotionCommandType.LEFT
        }

        // Rate limit: minimum 50ms interval unless stopping or direction shifted
        val now = SystemClock.elapsedRealtime()
        val timeDelta = now - lastSentCommandTime
        if (timeDelta > 50L || motionType != lastCommandType || abs(speedPercent - lastSpeed) > 10) {
            sendMotionCommand(motionType, speedPercent)
        }
    }

    /**
     * Direct command transmission
     */
    fun sendMotionCommand(type: MotionCommandType, speedPercent: Int) {
        // Obstacle Safety Check: if moving forward and obstacle is too close, abort forward!
        if (type == MotionCommandType.FORWARD && _currentTelemetry.value.obstacleDistanceCm in 1..currentConfig.obstacleSafetyDistanceCm) {
            Log.w(tag, "Obstacle safety override! Distance ${_currentTelemetry.value.obstacleDistanceCm} cm")
            sendDirectStop()
            onSafetyStopTriggered("Obstacle hazard! Motors stopped (${_currentTelemetry.value.obstacleDistanceCm} cm)")
            return
        }

        lastSentCommandTime = SystemClock.elapsedRealtime()
        lastCommandType = type
        val safeSpeed = speedPercent.coerceIn(0, currentConfig.speedLimitPercent)
        lastSpeed = safeSpeed

        if (currentConfig.isDemoMode) {
            simulator.handleMotionCommand(type, safeSpeed)
            return
        }

        // Send over persistent WebSocket
        scope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("type", "MOTION")
                put("command", type.name)
                put("speed", safeSpeed)
            }.toString()
            webSocketClient.sendCommand(json)
        }
    }

    /**
     * Emergency STOP: Halts motors immediately.
     * Fires multiple stop frames as failsafe.
     */
    fun emergencyStop() {
        Log.i(tag, "EMERGENCY STOP TRIGGERED!")
        lastCommandType = MotionCommandType.STOP
        lastSpeed = 0

        if (currentConfig.isDemoMode) {
            simulator.handleMotionCommand(MotionCommandType.STOP, 0)
            simulator.setFaceExpression(RobotFaceExpression.ERROR)
            _activeFaceExpression.value = RobotFaceExpression.ERROR
            return
        }

        sendDirectStop()
        // Send a burst of 2 stop commands for reliable transmission
        scope.launch(Dispatchers.IO) {
            delay(40L)
            sendDirectStop()
        }
    }

    private fun sendDirectStop() {
        val stopPayload = JSONObject().apply {
            put("type", "MOTION")
            put("command", "STOP")
            put("speed", 0)
        }.toString()
        webSocketClient.sendCommand(stopPayload)
        _activeFaceExpression.value = RobotFaceExpression.NORMAL
    }

    fun setFaceExpression(expression: RobotFaceExpression) {
        _activeFaceExpression.value = expression
        if (currentConfig.isDemoMode) {
            simulator.setFaceExpression(expression)
        } else {
            scope.launch(Dispatchers.IO) {
                val json = JSONObject().apply {
                    put("type", "FACE")
                    put("expression", expression.name)
                }.toString()
                webSocketClient.sendCommand(json)
            }
        }
    }

    private fun checkSafetyLimits(telem: TelemetryData) {
        // Battery critical safety check
        if (telem.batteryPercent <= currentConfig.criticalBatteryThreshold && telem.batteryPercent > 0) {
            if (lastCommandType != MotionCommandType.STOP) {
                emergencyStop()
                onSafetyStopTriggered("Critical Battery (${telem.batteryPercent}% / ${telem.batteryVoltage}V)! Safe mode active.")
            }
        }

        // Active obstacle check while in motion
        if (lastCommandType == MotionCommandType.FORWARD && telem.obstacleDistanceCm in 1..currentConfig.obstacleSafetyDistanceCm) {
            emergencyStop()
            onSafetyStopTriggered("Obstacle detected (${telem.obstacleDistanceCm}cm)! Auto-stopped.")
        }
    }

    private fun startSafetyWatchdog() {
        safetyWatchdogJob?.cancel()
        safetyWatchdogJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(200L)
                val isMoving = lastCommandType != MotionCommandType.STOP && lastSpeed > 0
                if (isMoving && !currentConfig.isDemoMode) {
                    val timeSinceLast = SystemClock.elapsedRealtime() - lastSentCommandTime
                    if (timeSinceLast > currentConfig.watchdogTimeoutMs) {
                        Log.w(tag, "Safety Watchdog: No command sent for $timeSinceLast ms. Auto-stopping.")
                        sendDirectStop()
                    }
                }
            }
        }
    }
}
