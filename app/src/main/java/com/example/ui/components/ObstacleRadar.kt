package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.RobotAmber
import com.example.ui.theme.RobotGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ObstacleRadar(
    distanceCm: Int,
    safetyThresholdCm: Int = 20,
    modifier: Modifier = Modifier
) {
    val isHazard = distanceCm in 1..safetyThresholdCm
    val isCaution = distanceCm in (safetyThresholdCm + 1)..50
    val radarColor = when {
        isHazard -> EmergencyRed
        isCaution -> RobotAmber
        else -> RobotGreen
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, if (isHazard) EmergencyRed else CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini Radar Arc Canvas
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(76.dp)) {
                val cx = size.width / 2f
                val cy = size.height * 0.85f
                val maxRadius = size.width * 0.75f

                // Sector arcs (Forward 120-degree cone)
                val startAngle = 210f
                val sweepAngle = 120f

                drawArc(
                    color = CardBorder,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - maxRadius, cy - maxRadius),
                    size = Size(maxRadius * 2, maxRadius * 2),
                    style = Stroke(width = 1.5f)
                )

                drawArc(
                    color = CardBorder,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(cx - maxRadius * 0.5f, cy - maxRadius * 0.5f),
                    size = Size(maxRadius, maxRadius),
                    style = Stroke(width = 1.5f)
                )

                // Obstacle Distance Arc
                val distanceFraction = (distanceCm.coerceIn(5, 150) / 150f).coerceIn(0.1f, 1f)
                val obstacleRadius = maxRadius * distanceFraction

                drawArc(
                    color = radarColor,
                    startAngle = 230f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(cx - obstacleRadius, cy - obstacleRadius),
                    size = Size(obstacleRadius * 2, obstacleRadius * 2),
                    style = Stroke(width = 5f)
                )

                // Robot point
                drawCircle(
                    color = CyberCyan,
                    radius = 4.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FRONT ULTRASONIC SENSOR",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$distanceCm",
                    color = radarColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "cm",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = when {
                    isHazard -> "WARNING: OBSTACLE TOO CLOSE (SAFETY STOP)"
                    isCaution -> "Caution: Object nearby ($distanceCm cm)"
                    else -> "Clear trajectory - Forward path safe"
                },
                color = if (isHazard) EmergencyRed else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isHazard) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
