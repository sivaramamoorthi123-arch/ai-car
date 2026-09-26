package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberOrange
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thumbRadius: Dp = 38.dp,
    onValueChange: (x: Float, y: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val thumbOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    Box(
        modifier = modifier
            .size(size)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val baseRadius = (size.toPx() / 2f) - thumbRadius.toPx()

                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                        val dragVector = offset - center
                        val dist = sqrt(dragVector.x * dragVector.x + dragVector.y * dragVector.y)
                        val clampedDist = dist.coerceAtMost(baseRadius)
                        val angle = atan2(dragVector.y, dragVector.x)
                        val clampedOffset = Offset(
                            cos(angle) * clampedDist,
                            sin(angle) * clampedDist
                        )
                        coroutineScope.launch {
                            thumbOffset.snapTo(clampedOffset)
                        }
                        val normalizedX = (clampedOffset.x / baseRadius).coerceIn(-1f, 1f)
                        val normalizedY = (clampedOffset.y / baseRadius).coerceIn(-1f, 1f)
                        onValueChange(normalizedX, normalizedY)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val current = thumbOffset.value + dragAmount
                        val dist = sqrt(current.x * current.x + current.y * current.y)
                        val clampedOffset = if (dist > baseRadius) {
                            val angle = atan2(current.y, current.x)
                            Offset(cos(angle) * baseRadius, sin(angle) * baseRadius)
                        } else {
                            current
                        }
                        coroutineScope.launch {
                            thumbOffset.snapTo(clampedOffset)
                        }
                        val normalizedX = (clampedOffset.x / baseRadius).coerceIn(-1f, 1f)
                        val normalizedY = (clampedOffset.y / baseRadius).coerceIn(-1f, 1f)
                        onValueChange(normalizedX, normalizedY)
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            thumbOffset.animateTo(Offset.Zero)
                        }
                        onValueChange(0f, 0f)
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            thumbOffset.animateTo(Offset.Zero)
                        }
                        onValueChange(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.width / 2f
            val thumbPx = thumbRadius.toPx()

            // Outer Base Ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CardSurface, Color(0xFF0B101D)),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // Neon Border
            drawCircle(
                color = CardBorder,
                radius = baseRadius - 2f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )

            // Concentric guide rings
            drawCircle(
                color = CyberCyan.copy(alpha = 0.15f),
                radius = baseRadius * 0.65f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
            drawCircle(
                color = CyberCyan.copy(alpha = 0.25f),
                radius = baseRadius * 0.35f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )

            // Crosshair lines
            drawLine(
                color = CyberCyan.copy(alpha = 0.2f),
                start = Offset(center.x - baseRadius * 0.8f, center.y),
                end = Offset(center.x + baseRadius * 0.8f, center.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = CyberCyan.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - baseRadius * 0.8f),
                end = Offset(center.x, center.y + baseRadius * 0.8f),
                strokeWidth = 1.5f
            )

            // Thumb Stick
            val thumbCenter = center + thumbOffset.value

            // Glow around thumb stick
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyberCyan.copy(alpha = 0.45f), Color.Transparent),
                    center = thumbCenter,
                    radius = thumbPx * 1.5f
                ),
                radius = thumbPx * 1.5f,
                center = thumbCenter
            )

            // Thumb Body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF26354D), Color(0xFF131A26)),
                    center = thumbCenter,
                    radius = thumbPx
                ),
                radius = thumbPx,
                center = thumbCenter
            )

            // Thumb Ring
            drawCircle(
                color = CyberCyan,
                radius = thumbPx,
                center = thumbCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )

            // Inner Accent Dot
            drawCircle(
                color = CyberOrange,
                radius = 6.dp.toPx(),
                center = thumbCenter
            )
        }
    }
}
