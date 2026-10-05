package com.aeswox.arcmusic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ArcProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    val indicatorColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    
    val infiniteTransition = rememberInfiniteTransition(label = "ArcProgress")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ArcRotation"
    )

    Canvas(modifier = modifier.size(48.dp)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = strokeCap)
        val diameterOffset = stroke.width / 2
        val arcSize = size.width - stroke.width

        // Draw track
        if (trackColor != Color.Transparent) {
            drawCircle(
                color = trackColor,
                radius = arcSize / 2,
                style = stroke
            )
        }

        // Draw sweep gradient
        val brush = Brush.sweepGradient(
            0.0f to indicatorColor.copy(alpha = 0f),
            0.6f to indicatorColor,
            1.0f to indicatorColor.copy(alpha = 0f)
        )

        withTransform({
            rotate(rotation)
        }) {
            // Glow layer
            drawArc(
                brush = brush,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(diameterOffset, diameterOffset),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth.toPx() * 3, cap = strokeCap),
                alpha = 0.3f
            )
            // Main layer
            drawArc(
                brush = brush,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(diameterOffset, diameterOffset),
                size = Size(arcSize, arcSize),
                style = stroke
            )
        }
    }
}

@Composable
fun ArcProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    val indicatorColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color

    Canvas(modifier = modifier.size(48.dp)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = strokeCap)
        val diameterOffset = stroke.width / 2
        val arcSize = size.width - stroke.width
        val currentProgress = progress().coerceIn(0f, 1f)

        // Draw track
        if (trackColor != Color.Transparent) {
            drawCircle(
                color = trackColor,
                radius = arcSize / 2,
                style = stroke
            )
        }

        val sweep = 360f * currentProgress

        // Glow layer for determinate
        drawArc(
            color = indicatorColor.copy(alpha = 0.3f),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(diameterOffset, diameterOffset),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth.toPx() * 3, cap = strokeCap)
        )
        // Main layer
        drawArc(
            color = indicatorColor,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(diameterOffset, diameterOffset),
            size = Size(arcSize, arcSize),
            style = stroke
        )
    }
}

@Composable
fun ArcProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp,
    trackColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    ArcProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
        trackColor = trackColor,
        strokeCap = strokeCap
    )
}
