package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.RobotFaceExpression
import com.example.ui.theme.AiPurple
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.RobotGreen

@Composable
fun RobotFaceView(
    expression: RobotFaceExpression,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    height: Dp = 150.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "face_anim")

    // Blink / Pulse animation
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    // Speaking mouth motion
    val mouthAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouth"
    )

    // Thinking scanner
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan"
    )

    // Listening pulse
    val listenRing by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listen"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF070B12))
            .border(2.dp, CardBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle scanlines CRT background
            for (y in 0..h.toInt() step 6) {
                drawLine(
                    color = Color(0x0A00F0FF),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(w, y.toFloat()),
                    strokeWidth = 1f
                )
            }

            val eyeY = h * 0.45f
            val leftEyeX = w * 0.32f
            val rightEyeX = w * 0.68f

            val primaryColor = when (expression) {
                RobotFaceExpression.HAPPY -> RobotGreen
                RobotFaceExpression.ANGRY, RobotFaceExpression.ERROR -> EmergencyRed
                RobotFaceExpression.THINKING -> CyberOrange
                RobotFaceExpression.LISTENING -> AiPurple
                RobotFaceExpression.SPEAKING -> CyberCyan
                else -> CyberCyan
            }

            fun drawEye(cx: Float, cy: Float, isLeft: Boolean) {
                when (expression) {
                    RobotFaceExpression.NORMAL -> {
                        // Oval rounded eyes with subtle blink
                        val currentH = 34f * (if (blinkScale < 0.25f) blinkScale else 1f)
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(cx - 24f, cy - currentH / 2f),
                            size = Size(48f, currentH),
                            cornerRadius = CornerRadius(24f, 24f)
                        )
                        // Inner pupil glint
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(cx + 6f, cy - 6f)
                        )
                    }

                    RobotFaceExpression.HAPPY -> {
                        // Arched crescent eye (^_^)
                        val path = Path().apply {
                            moveTo(cx - 24f, cy + 8f)
                            quadraticTo(cx, cy - 24f, cx + 24f, cy + 8f)
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }

                    RobotFaceExpression.SAD -> {
                        // Downward droop
                        val path = Path().apply {
                            moveTo(cx - 24f, cy - 8f)
                            quadraticTo(cx, cy + 18f, cx + 24f, cy - 8f)
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(width = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }

                    RobotFaceExpression.ANGRY -> {
                        // Slanted aggressive eyes (>_<)
                        val path = Path().apply {
                            if (isLeft) {
                                moveTo(cx - 24f, cy - 14f)
                                lineTo(cx + 24f, cy + 8f)
                            } else {
                                moveTo(cx + 24f, cy - 14f)
                                lineTo(cx - 24f, cy + 8f)
                            }
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }

                    RobotFaceExpression.SURPRISED -> {
                        // Wide circular eye (◉_◉)
                        drawCircle(
                            color = primaryColor,
                            radius = 24f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 6f)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 10f,
                            center = Offset(cx, cy)
                        )
                    }

                    RobotFaceExpression.THINKING -> {
                        // Squinted eyes (-_-)
                        drawLine(
                            color = primaryColor,
                            start = Offset(cx - 22f, cy),
                            end = Offset(cx + 22f, cy),
                            strokeWidth = 7f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    RobotFaceExpression.LISTENING -> {
                        // Eye with pulsing audio radar wave
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.4f),
                            radius = 26f * listenRing,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 16f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = Offset(cx + 3f, cy - 3f)
                        )
                    }

                    RobotFaceExpression.SPEAKING -> {
                        // Round alert eyes
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(cx - 22f, cy - 18f),
                            size = Size(44f, 36f),
                            cornerRadius = CornerRadius(20f, 20f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(cx + 5f, cy - 5f)
                        )
                    }

                    RobotFaceExpression.SLEEPING -> {
                        // Closed curved eye lines
                        val path = Path().apply {
                            moveTo(cx - 20f, cy)
                            quadraticTo(cx, cy + 12f, cx + 20f, cy)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF64748B),
                            style = Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }

                    RobotFaceExpression.ERROR -> {
                        // Red warning cross / exclamation
                        drawLine(
                            color = primaryColor,
                            start = Offset(cx - 16f, cy - 16f),
                            end = Offset(cx + 16f, cy + 16f),
                            strokeWidth = 8f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        drawLine(
                            color = primaryColor,
                            start = Offset(cx + 16f, cy - 16f),
                            end = Offset(cx - 16f, cy + 16f),
                            strokeWidth = 8f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }

            drawEye(leftEyeX, eyeY, isLeft = true)
            drawEye(rightEyeX, eyeY, isLeft = false)

            // Scanning line for THINKING expression
            if (expression == RobotFaceExpression.THINKING) {
                val scanX = (w / 2f) + (scanOffset * (w * 0.35f))
                drawLine(
                    color = CyberOrange.copy(alpha = 0.8f),
                    start = Offset(scanX, eyeY - 25f),
                    end = Offset(scanX, eyeY + 25f),
                    strokeWidth = 3f
                )
            }

            // Mouth rendering
            val mouthY = h * 0.78f
            val mouthCenter = Offset(w / 2f, mouthY)

            when (expression) {
                RobotFaceExpression.SPEAKING -> {
                    // Animated talking mouth opening
                    val mouthH = 8f + (22f * mouthAnim)
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(mouthCenter.x - 30f, mouthCenter.y - mouthH / 2f),
                        size = Size(60f, mouthH),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }

                RobotFaceExpression.HAPPY -> {
                    // Wide cheerful smile
                    val mouthPath = Path().apply {
                        moveTo(mouthCenter.x - 32f, mouthCenter.y - 6f)
                        quadraticTo(mouthCenter.x, mouthCenter.y + 16f, mouthCenter.x + 32f, mouthCenter.y - 6f)
                    }
                    drawPath(
                        path = mouthPath,
                        color = primaryColor,
                        style = Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }

                RobotFaceExpression.SAD -> {
                    val mouthPath = Path().apply {
                        moveTo(mouthCenter.x - 24f, mouthCenter.y + 10f)
                        quadraticTo(mouthCenter.x, mouthCenter.y - 6f, mouthCenter.x + 24f, mouthCenter.y + 10f)
                    }
                    drawPath(
                        path = mouthPath,
                        color = primaryColor,
                        style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }

                RobotFaceExpression.ERROR -> {
                    // Jagged line
                    val mouthPath = Path().apply {
                        moveTo(mouthCenter.x - 28f, mouthCenter.y)
                        lineTo(mouthCenter.x - 14f, mouthCenter.y + 6f)
                        lineTo(mouthCenter.x, mouthCenter.y - 6f)
                        lineTo(mouthCenter.x + 14f, mouthCenter.y + 6f)
                        lineTo(mouthCenter.x + 28f, mouthCenter.y)
                    }
                    drawPath(
                        path = mouthPath,
                        color = primaryColor,
                        style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }

                RobotFaceExpression.SLEEPING -> {
                    // Small relaxed line
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(mouthCenter.x - 12f, mouthCenter.y),
                        end = Offset(mouthCenter.x + 12f, mouthCenter.y),
                        strokeWidth = 4f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                else -> {
                    // Normal subtle friendly smirk
                    val mouthPath = Path().apply {
                        moveTo(mouthCenter.x - 18f, mouthCenter.y)
                        quadraticTo(mouthCenter.x, mouthCenter.y + 7f, mouthCenter.x + 18f, mouthCenter.y)
                    }
                    drawPath(
                        path = mouthPath,
                        color = primaryColor.copy(alpha = 0.8f),
                        style = Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
        }
    }
}
