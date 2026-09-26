package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiService
import com.example.ai.MissionManager
import com.example.ai.RobotCommandValidator
import com.example.data.local.SettingsDataStore
import com.example.data.network.Esp32CameraStreamer
import com.example.data.network.Esp32RestClient
import com.example.data.network.Esp32WebSocketClient
import com.example.domain.model.DriveMode
import com.example.domain.model.GeminiRobotCommand
import com.example.domain.model.MissionState
import com.example.domain.model.MotionCommandType
import com.example.domain.model.RobotConnectionState
import com.example.domain.model.RobotFaceExpression
import com.example.domain.model.SettingsConfig
import com.example.domain.model.TelemetryData
import com.example.robot.RobotCommandManager
import com.example.robot.RobotSimulator
import com.example.robot.SpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RobotViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val settingsDataStore = SettingsDataStore(context)
    val settingsConfig: StateFlow<SettingsConfig> = settingsDataStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsConfig()
    )

    private val webSocketClient = Esp32WebSocketClient(viewModelScope)
    private val restClient = Esp32RestClient()
    private val simulator = RobotSimulator(viewModelScope)
    private val cameraStreamer = Esp32CameraStreamer(viewModelScope)
    private val geminiService = GeminiService()

    private val _safetyAlert = MutableStateFlow<String?>(null)
    val safetyAlert: StateFlow<String?> = _safetyAlert.asStateFlow()

    val commandManager = RobotCommandManager(
        scope = viewModelScope,
        webSocketClient = webSocketClient,
        simulator = simulator,
        onSafetyStopTriggered = { reason ->
            _safetyAlert.value = reason
            triggerHapticFeedback(strong = true)
        }
    )

    val telemetry: StateFlow<TelemetryData> = commandManager.currentTelemetry
    val connectionState: StateFlow<RobotConnectionState> = commandManager.connectionState
    val faceExpression: StateFlow<RobotFaceExpression> = commandManager.activeFaceExpression

    private val _driveMode = MutableStateFlow(DriveMode.MANUAL)
    val driveMode: StateFlow<DriveMode> = _driveMode.asStateFlow()

    private val _speedSliderPercent = MutableStateFlow(80)
    val speedSliderPercent: StateFlow<Int> = _speedSliderPercent.asStateFlow()

    // Speech manager
    val speechManager: SpeechManager = SpeechManager(
        context = context,
        onSpeechRecognized = { spokenText ->
            executeAiMissionPrompt(spokenText)
        },
        onListeningStateChanged = { listening ->
            if (listening) {
                commandManager.setFaceExpression(RobotFaceExpression.LISTENING)
            } else {
                commandManager.setFaceExpression(RobotFaceExpression.NORMAL)
            }
        },
        onSpeakingStateChanged = { speaking ->
            if (speaking) {
                commandManager.setFaceExpression(RobotFaceExpression.SPEAKING)
            } else {
                commandManager.setFaceExpression(RobotFaceExpression.NORMAL)
            }
        }
    )

    // Mission Manager
    val missionManager = MissionManager(
        scope = viewModelScope,
        commandManager = commandManager,
        onFaceChanged = { expr ->
            commandManager.setFaceExpression(expr)
        },
        onSpeak = { speechText ->
            if (settingsConfig.value.ttsFeedbackEnabled) {
                speechManager.speak(speechText)
            }
        }
    )
    val missionState: StateFlow<MissionState> = missionManager.missionState

    // AI Screen State
    private val _aiInputText = MutableStateFlow("")
    val aiInputText: StateFlow<String> = _aiInputText.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _lastAiCommand = MutableStateFlow<GeminiRobotCommand?>(null)
    val lastAiCommand: StateFlow<GeminiRobotCommand?> = _lastAiCommand.asStateFlow()

    // Camera & Vision
    val simulatedCameraFrame: StateFlow<Bitmap?> = simulator.simulatedFrame
    val liveCameraFrame: StateFlow<Bitmap?> = cameraStreamer.liveFrame

    private val _visionAnalysisResult = MutableStateFlow<String?>(null)
    val visionAnalysisResult: StateFlow<String?> = _visionAnalysisResult.asStateFlow()

    private val _isAnalyzingVision = MutableStateFlow(false)
    val isAnalyzingVision: StateFlow<Boolean> = _isAnalyzingVision.asStateFlow()

    // Test connection result
    private val _testConnectionResult = MutableStateFlow<String?>(null)
    val testConnectionResult: StateFlow<String?> = _testConnectionResult.asStateFlow()

    init {
        viewModelScope.launch {
            settingsConfig.collect { config ->
                commandManager.applyConfig(config)
                _speedSliderPercent.value = config.speedLimitPercent
                if (!config.isDemoMode) {
                    cameraStreamer.startStream(config.camUrl)
                } else {
                    cameraStreamer.stopStream()
                }
            }
        }
    }

    fun setDriveMode(mode: DriveMode) {
        _driveMode.value = mode
        if (mode == DriveMode.MANUAL) {
            missionManager.cancelCurrentMission()
        }
        triggerHapticFeedback(strong = false)
    }

    fun setAiInputText(text: String) {
        _aiInputText.value = text
    }

    fun setSpeedSlider(value: Int) {
        _speedSliderPercent.value = value
    }

    // Manual Joystick control - completely direct, zero AI overhead
    fun onJoystickMove(x: Float, y: Float) {
        val multiplier = _speedSliderPercent.value / 100f
        commandManager.sendJoystickDrive(x, y, multiplier)
    }

    fun onDirectionButton(type: MotionCommandType) {
        triggerHapticFeedback(strong = false)
        val speed = _speedSliderPercent.value
        commandManager.sendMotionCommand(type, speed)
    }

    fun onEmergencyStop() {
        triggerHapticFeedback(strong = true)
        commandManager.emergencyStop()
        missionManager.cancelCurrentMission()
        speechManager.stopSpeaking()
        _safetyAlert.value = "EMERGENCY STOP ACTIVATED"
    }

    fun clearSafetyAlert() {
        _safetyAlert.value = null
    }

    // AI Mission trigger
    fun executeAiMissionPrompt(prompt: String = _aiInputText.value) {
        if (prompt.isBlank()) return
        _isAiLoading.value = true
        _aiError.value = null
        commandManager.setFaceExpression(RobotFaceExpression.THINKING)

        viewModelScope.launch {
            val result = geminiService.parseUserIntentToCommand(prompt)
            _isAiLoading.value = false

            result.onSuccess { parsedCmd ->
                _lastAiCommand.value = parsedCmd
                // Validate schema and boundaries
                when (val validation = RobotCommandValidator.validate(parsedCmd, settingsConfig.value.speedLimitPercent)) {
                    is RobotCommandValidator.ValidationResult.Valid -> {
                        _driveMode.value = DriveMode.AI
                        missionManager.startMission(validation.command, settingsConfig.value.isDemoMode)
                    }
                    is RobotCommandValidator.ValidationResult.Invalid -> {
                        _aiError.value = "Validation Error: ${validation.reason}"
                        commandManager.setFaceExpression(RobotFaceExpression.ERROR)
                        speechManager.speak("Sorry, command could not be executed: ${validation.reason}")
                    }
                }
            }.onFailure { ex ->
                _aiError.value = "Gemini Error: ${ex.localizedMessage ?: "Failed to interpret command"}"
                commandManager.setFaceExpression(RobotFaceExpression.SAD)
                speechManager.speak("AI service was unavailable. You can drive using manual control.")
            }
        }
    }

    fun cancelActiveMission() {
        missionManager.cancelCurrentMission()
        commandManager.sendMotionCommand(MotionCommandType.STOP, 0)
        commandManager.setFaceExpression(RobotFaceExpression.NORMAL)
    }

    fun analyzeCurrentCameraFrame() {
        val currentBitmap = if (settingsConfig.value.isDemoMode) {
            simulatedCameraFrame.value
        } else {
            liveCameraFrame.value
        }

        if (currentBitmap == null) {
            _visionAnalysisResult.value = "No camera frame available to inspect."
            return
        }

        _isAnalyzingVision.value = true
        _visionAnalysisResult.value = "Analyzing live camera frame with Gemini Vision..."
        commandManager.setFaceExpression(RobotFaceExpression.THINKING)

        viewModelScope.launch {
            val result = geminiService.analyzeVisionFrame(currentBitmap)
            _isAnalyzingVision.value = false
            result.onSuccess { analysis ->
                _visionAnalysisResult.value = analysis
                commandManager.setFaceExpression(RobotFaceExpression.NORMAL)
                if (settingsConfig.value.ttsFeedbackEnabled) {
                    speechManager.speak("Vision analysis: ${analysis.take(120)}")
                }
            }.onFailure { ex ->
                _visionAnalysisResult.value = "Vision Analysis Failed: ${ex.localizedMessage}"
                commandManager.setFaceExpression(RobotFaceExpression.ERROR)
            }
        }
    }

    fun saveSettings(newConfig: SettingsConfig) {
        viewModelScope.launch {
            settingsDataStore.updateSettings(newConfig)
        }
    }

    fun testEsp32Connection() {
        _testConnectionResult.value = "Testing connection to ${settingsConfig.value.esp32Ip}..."
        viewModelScope.launch {
            val res = restClient.testConnection(settingsConfig.value.esp32Ip, settingsConfig.value.wsPort)
            _testConnectionResult.value = res.second
        }
    }

    fun triggerHapticFeedback(strong: Boolean = false) {
        if (!settingsConfig.value.hapticEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (strong) {
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createOneShot(25, 80)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (strong) 120 else 25)
            }
        } catch (e: Exception) {
            // Ignore vibration error
        }
    }

    override fun onCleared() {
        super.onCleared()
        commandManager.emergencyStop()
        speechManager.shutdown()
        cameraStreamer.stopStream()
        webSocketClient.disconnect()
        simulator.stop()
    }
}
