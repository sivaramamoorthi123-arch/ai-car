package com.example.data.network

import android.util.Log
import com.example.domain.model.RobotConnectionState
import com.example.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Esp32WebSocketClient(
    private val scope: CoroutineScope
) {
    private val tag = "Esp32WebSocket"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentUrl: String = ""
    private var shouldReconnect = true

    private val _connectionState = MutableStateFlow(RobotConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RobotConnectionState> = _connectionState.asStateFlow()

    private val _telemetryFlow = MutableStateFlow(TelemetryData())
    val telemetryFlow: StateFlow<TelemetryData> = _telemetryFlow.asStateFlow()

    private val _incomingRawMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingRawMessages: SharedFlow<String> = _incomingRawMessages.asSharedFlow()

    fun connect(ip: String, port: Int) {
        val sanitizedIp = ip.trim()
        currentUrl = "ws://$sanitizedIp:$port/ws"
        shouldReconnect = true
        initiateConnection()
    }

    private fun initiateConnection() {
        if (currentUrl.isEmpty()) return
        _connectionState.value = RobotConnectionState.CONNECTING
        Log.d(tag, "Connecting to $currentUrl...")

        val request = Request.Builder()
            .url(currentUrl)
            .build()

        webSocket?.close(1000, "Reconnecting")
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Connected to ESP32 WebSocket")
                _connectionState.value = RobotConnectionState.CONNECTED
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.IO) {
                    processIncomingMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "Closed: $code / $reason")
                _connectionState.value = RobotConnectionState.DISCONNECTED
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "Connection failed: ${t.message}")
                _connectionState.value = RobotConnectionState.DISCONNECTED
                stopHeartbeat()
                scheduleReconnect()
            }
        })
    }

    private fun processIncomingMessage(text: String) {
        try {
            _incomingRawMessages.tryEmit(text)
            val json = JSONObject(text)
            val type = json.optString("type")

            if (type.equals("TELEMETRY", ignoreCase = true) || json.has("battery_voltage")) {
                val voltage = json.optDouble("battery_voltage", 7.8)
                val percent = json.optInt("battery_percent", 80)
                val current = json.optDouble("current", 0.4)
                val dist = json.optInt("distance_cm", 50)
                val rssi = json.optInt("wifi_rssi", -50)
                val left = json.optInt("left_speed", 0)
                val right = json.optInt("right_speed", 0)
                val state = json.optString("state", "RUNNING")

                val updated = _telemetryFlow.value.copy(
                    batteryVoltage = voltage,
                    batteryPercent = percent,
                    currentAmperes = current,
                    obstacleDistanceCm = dist,
                    leftMotorSpeed = left,
                    rightMotorSpeed = right,
                    wifiRssi = rssi,
                    lastReceivedTimestamp = System.currentTimeMillis(),
                    stateName = state
                )
                _telemetryFlow.value = updated
            } else if (type.equals("PONG", ignoreCase = true)) {
                val sentTs = json.optLong("timestamp", 0L)
                if (sentTs > 0) {
                    val rtt = (System.currentTimeMillis() - sentTs).coerceAtLeast(1L)
                    _telemetryFlow.value = _telemetryFlow.value.copy(latencyMs = rtt)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing message: ${e.message}")
        }
    }

    fun sendCommand(jsonString: String): Boolean {
        val ws = webSocket
        return if (ws != null && _connectionState.value == RobotConnectionState.CONNECTED) {
            ws.send(jsonString)
        } else {
            false
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && _connectionState.value == RobotConnectionState.CONNECTED) {
                val pingJson = JSONObject().apply {
                    put("type", "PING")
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                webSocket?.send(pingJson)
                delay(1500L) // 1.5 second ping interval for responsive latency display without network saturation
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(2500L)
            if (shouldReconnect && _connectionState.value == RobotConnectionState.DISCONNECTED) {
                _connectionState.value = RobotConnectionState.RECONNECTING
                initiateConnection()
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = RobotConnectionState.DISCONNECTED
    }
}
