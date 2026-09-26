package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BatteryState
import com.example.domain.model.DriveMode
import com.example.domain.model.RobotConnectionState
import com.example.domain.model.TelemetryData
import com.example.ui.theme.AiPurple
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.RobotAmber
import com.example.ui.theme.RobotGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StatusHeader(
    connectionState: RobotConnectionState,
    telemetry: TelemetryData,
    driveMode: DriveMode,
    onDriveModeSelected: (DriveMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("status_header")
    ) {
        // Top Row: Connection status pill + Battery + Wi-Fi + Ping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection Pill
            val (statusText, statusColor) = when (connectionState) {
                RobotConnectionState.CONNECTED -> "CONNECTED" to RobotGreen
                RobotConnectionState.CONNECTING, RobotConnectionState.RECONNECTING -> "CONNECTING" to RobotAmber
                RobotConnectionState.DEMO_MODE -> "DEMO MODE" to AiPurple
                RobotConnectionState.DISCONNECTED -> "OFFLINE" to EmergencyRed
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // Quick Telemetry Badges (Battery, WiFi, Ping)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Battery
                val battColor = when (telemetry.batteryState) {
                    BatteryState.GOOD -> RobotGreen
                    BatteryState.MEDIUM -> RobotAmber
                    BatteryState.LOW, BatteryState.CRITICAL -> EmergencyRed
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (telemetry.batteryPercent <= 15) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                        contentDescription = "Battery",
                        tint = battColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${telemetry.batteryPercent}% (${telemetry.batteryVoltage}V)",
                        color = battColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Latency Ping
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Latency",
                        tint = CyberCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${telemetry.latencyMs}ms",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Wi-Fi RSSI
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wi-Fi",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${telemetry.wifiRssi}dBm",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mode Switch Selector: MANUAL vs AI MODE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF090D15))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(3.dp)
        ) {
            // Manual Mode Option
            val isManual = driveMode == DriveMode.MANUAL
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isManual) CyberCyan else Color.Transparent)
                    .clickable { onDriveModeSelected(DriveMode.MANUAL) }
                    .padding(vertical = 6.dp)
                    .testTag("mode_manual_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MANUAL CONTROL",
                    color = if (isManual) Color.Black else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // AI Mode Option
            val isAi = driveMode == DriveMode.AI
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAi) AiPurple else Color.Transparent)
                    .clickable { onDriveModeSelected(DriveMode.AI) }
                    .padding(vertical = 6.dp)
                    .testTag("mode_ai_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GEMINI AI MODE",
                    color = if (isAi) Color.White else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
