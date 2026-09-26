package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SettingsConfig
import com.example.ui.RobotViewModel
import com.example.ui.components.EmergencyStopButton
import com.example.ui.theme.AiPurple
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.RobotGreen
import com.example.ui.theme.SpaceBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: RobotViewModel,
    modifier: Modifier = Modifier
) {
    val currentConfig by viewModel.settingsConfig.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()

    var ipText by remember(currentConfig) { mutableStateOf(currentConfig.esp32Ip) }
    var portText by remember(currentConfig) { mutableStateOf(currentConfig.wsPort.toString()) }
    var camUrlText by remember(currentConfig) { mutableStateOf(currentConfig.camUrl) }
    var isDemoMode by remember(currentConfig) { mutableStateOf(currentConfig.isDemoMode) }
    var speedLimit by remember(currentConfig) { mutableStateOf(currentConfig.speedLimitPercent) }
    var watchdogMsText by remember(currentConfig) { mutableStateOf(currentConfig.watchdogTimeoutMs.toString()) }
    var obstacleDist by remember(currentConfig) { mutableStateOf(currentConfig.obstacleSafetyDistanceCm) }
    var cellCount by remember(currentConfig) { mutableStateOf(currentConfig.batteryCellCount) }
    var criticalBatt by remember(currentConfig) { mutableStateOf(currentConfig.criticalBatteryThreshold) }
    var ttsEnabled by remember(currentConfig) { mutableStateOf(currentConfig.ttsFeedbackEnabled) }
    var hapticEnabled by remember(currentConfig) { mutableStateOf(currentConfig.hapticEnabled) }

    var saveConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ROBOT CONFIGURATION",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "Wi-Fi, Telemetry, Safety Watchdogs, and Battery Tuning",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            EmergencyStopButton(
                onClick = { viewModel.onEmergencyStop() },
                size = 50.dp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Demo Mode vs Physical Robot Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDemoMode) AiPurple else CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SIMULATED DEMO MODE",
                        color = if (isDemoMode) AiPurple else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isDemoMode) "Testing with virtual robot (no physical ESP32 required)" else "Direct Wi-Fi connection to physical ESP32 hardware",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = { isDemoMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AiPurple
                    ),
                    modifier = Modifier.testTag("switch_demo_mode")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ESP32 Wi-Fi & Ports Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ESP32 NETWORK ENDPOINTS",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it },
                        label = { Text("ESP32 IP Address", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.5f).testTag("input_esp32_ip"),
                        colors = outlinedColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("WS Port", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f).testTag("input_ws_port"),
                        colors = outlinedColors(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = camUrlText,
                    onValueChange = { camUrlText = it },
                    label = { Text("ESP32-CAM Stream / Snapshot URL", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("input_cam_url"),
                    colors = outlinedColors(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.testEsp32Connection() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_test_conn")
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = "Test", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PING & TEST ESP32 CONNECTION", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (testResult != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = testResult.orEmpty(),
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Safety Watchdogs & Speed Limits
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "SAFETY LIMITS & WATCHDOGS",
                    color = CyberOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Maximum Speed Limit: $speedLimit%",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = speedLimit.toFloat(),
                    onValueChange = { speedLimit = it.toInt() },
                    valueRange = 20f..100f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = CyberOrange, activeTrackColor = CyberOrange)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Obstacle Safety Stop Distance: $obstacleDist cm",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = obstacleDist.toFloat(),
                    onValueChange = { obstacleDist = it.toInt() },
                    valueRange = 10f..50f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = EmergencyRed, activeTrackColor = EmergencyRed)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = watchdogMsText,
                    onValueChange = { watchdogMsText = it },
                    label = { Text("Communication Watchdog Auto-Stop Timeout (ms)", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedColors(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Battery Tuning & Pack Config
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "BATTERY MONITORING (INA219)",
                    color = RobotGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Cell Count (2S = 7.4V, 3S = 11.1V):", color = TextPrimary, fontSize = 12.sp)
                    Text(text = "${cellCount}S Pack", color = RobotGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = cellCount.toFloat(),
                    onValueChange = { cellCount = it.toInt() },
                    valueRange = 1f..4f,
                    steps = 2,
                    colors = SliderDefaults.colors(thumbColor = RobotGreen, activeTrackColor = RobotGreen)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Critical Battery Threshold (%):", color = TextPrimary, fontSize = 12.sp)
                    Text(text = "$criticalBatt%", color = EmergencyRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = criticalBatt.toFloat(),
                    onValueChange = { criticalBatt = it.toInt() },
                    valueRange = 5f..30f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = EmergencyRed, activeTrackColor = EmergencyRed)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Feedback options (TTS and Haptics)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Voice TTS Feedback", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = ttsEnabled, onCheckedChange = { ttsEnabled = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Tactile Haptic Vibration", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                val newConfig = SettingsConfig(
                    esp32Ip = ipText.trim(),
                    wsPort = portText.toIntOrNull() ?: 81,
                    camUrl = camUrlText.trim(),
                    isDemoMode = isDemoMode,
                    speedLimitPercent = speedLimit,
                    watchdogTimeoutMs = watchdogMsText.toLongOrNull() ?: 500L,
                    obstacleSafetyDistanceCm = obstacleDist,
                    batteryCellCount = cellCount,
                    criticalBatteryThreshold = criticalBatt,
                    ttsFeedbackEnabled = ttsEnabled,
                    hapticEnabled = hapticEnabled
                )
                viewModel.saveSettings(newConfig)
                saveConfirmation = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_save_settings")
        ) {
            Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (saveConfirmation) "SETTINGS SAVED!" else "SAVE CONFIGURATION",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberCyan,
    unfocusedBorderColor = CardBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = CardSurface,
    unfocusedContainerColor = CardSurface
)
