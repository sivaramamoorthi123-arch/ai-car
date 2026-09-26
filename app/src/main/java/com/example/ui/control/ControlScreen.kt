package com.example.ui.control

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MotionCommandType
import com.example.ui.RobotViewModel
import com.example.ui.components.EmergencyStopButton
import com.example.ui.components.ObstacleRadar
import com.example.ui.components.VirtualJoystick
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.RobotGreen
import com.example.ui.theme.SpaceBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ControlScreen(
    viewModel: RobotViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsState()
    val speedPercent by viewModel.speedSliderPercent.collectAsState()
    val settingsConfig by viewModel.settingsConfig.collectAsState()
    val simulatedFrame by viewModel.simulatedCameraFrame.collectAsState()
    val liveFrame by viewModel.liveCameraFrame.collectAsState()

    val currentBitmap = if (settingsConfig.isDemoMode) simulatedFrame else liveFrame

    var showCameraPip by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBackground)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape layout: Split Camera/Telemetry on Left, Joystick/Controls on Right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Panel: Camera Stream & Telemetry
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    CameraPipView(
                        bitmap = currentBitmap,
                        isStreaming = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ObstacleRadar(
                        distanceCm = telemetry.obstacleDistanceCm,
                        safetyThresholdCm = settingsConfig.obstacleSafetyDistanceCm
                    )
                }

                // Right Panel: Joystick, Speed Slider, Stop
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    SpeedControlRow(
                        speed = speedPercent,
                        onSpeedChanged = { viewModel.setSpeedSlider(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VirtualJoystick(
                            size = 180.dp,
                            onValueChange = { x, y -> viewModel.onJoystickMove(x, y) }
                        )

                        EmergencyStopButton(
                            onClick = { viewModel.onEmergencyStop() },
                            size = 80.dp
                        )
                    }

                    // D-Pad Quick Actions
                    DirectionalPad(
                        onCommand = { viewModel.onDirectionButton(it) },
                        onStop = { viewModel.onDirectionButton(MotionCommandType.STOP) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // Portrait Layout: Responsive scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Mini Camera Feed Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANUAL DRIVING CONSOLE",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .clickable { showCameraPip = !showCameraPip }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Toggle Cam",
                            tint = if (showCameraPip) CyberCyan else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCameraPip) "HIDE CAM" else "SHOW CAM",
                            color = if (showCameraPip) CyberCyan else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showCameraPip) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CameraPipView(
                        bitmap = currentBitmap,
                        isStreaming = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Obstacle Distance Sensor Radar
                ObstacleRadar(
                    distanceCm = telemetry.obstacleDistanceCm,
                    safetyThresholdCm = settingsConfig.obstacleSafetyDistanceCm
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Speed Limit Slider
                SpeedControlRow(
                    speed = speedPercent,
                    onSpeedChanged = { viewModel.setSpeedSlider(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Virtual Joystick & Emergency Stop Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualJoystick(
                            size = 190.dp,
                            onValueChange = { x, y -> viewModel.onJoystickMove(x, y) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SMOOTH JOYSTICK",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmergencyStopButton(
                            onClick = { viewModel.onEmergencyStop() },
                            size = 86.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "EMERGENCY HALT",
                            color = EmergencyRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Precision Step D-Pad (Forward, Backward, Left, Right, Spin)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PRECISION DIRECTIONAL PAD",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DirectionalPad(
                            onCommand = { viewModel.onDirectionButton(it) },
                            onStop = { viewModel.onDirectionButton(MotionCommandType.STOP) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CameraPipView(
    bitmap: android.graphics.Bitmap?,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Robot Live View",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "No Camera",
                    tint = TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Awaiting ESP32-CAM Feed...",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Live badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(RobotGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "LIVE",
                    color = RobotGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SpeedControlRow(
    speed: Int,
    onSpeedChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Speed",
                tint = CyberOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SPEED",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeedChanged(it.toInt()) },
                valueRange = 10f..100f,
                steps = 9,
                modifier = Modifier.weight(1f).testTag("speed_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = CyberOrange,
                    activeTrackColor = CyberOrange,
                    inactiveTrackColor = CardBorder
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$speed%",
                color = CyberOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun DirectionalPad(
    onCommand: (MotionCommandType) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Forward
        PadButton(
            icon = Icons.Default.ArrowUpward,
            label = "FWD",
            onClick = { onCommand(MotionCommandType.FORWARD) },
            testTag = "btn_fwd"
        )

        // Middle Row: Spin Left, Left, Stop, Right, Spin Right
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadButton(
                icon = Icons.AutoMirrored.Filled.RotateLeft,
                label = "SPIN L",
                onClick = { onCommand(MotionCommandType.SPIN_LEFT) },
                testTag = "btn_spin_l",
                size = 48.dp
            )
            PadButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "LEFT",
                onClick = { onCommand(MotionCommandType.LEFT) },
                testTag = "btn_left",
                size = 52.dp
            )
            // Center Hold Stop
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(EmergencyRed.copy(alpha = 0.2f))
                    .border(1.dp, EmergencyRed, RoundedCornerShape(12.dp))
                    .clickable(onClick = onStop)
                    .testTag("btn_pad_stop"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HALT",
                    color = EmergencyRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            PadButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = "RIGHT",
                onClick = { onCommand(MotionCommandType.RIGHT) },
                testTag = "btn_right",
                size = 52.dp
            )
            PadButton(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                label = "SPIN R",
                onClick = { onCommand(MotionCommandType.SPIN_RIGHT) },
                testTag = "btn_spin_r",
                size = 48.dp
            )
        }

        // Backward
        PadButton(
            icon = Icons.Default.ArrowDownward,
            label = "REV",
            onClick = { onCommand(MotionCommandType.BACKWARD) },
            testTag = "btn_rev"
        )
    }
}

@Composable
private fun PadButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurfaceElevated)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = CyberCyan,
            modifier = Modifier.size(24.dp)
        )
    }
}
