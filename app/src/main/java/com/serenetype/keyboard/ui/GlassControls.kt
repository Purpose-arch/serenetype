package com.serenetype.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Lightweight iOS-inspired Liquid Glass slider:
 * - Crisp frosted track with subtle specular rim.
 * - Glass capsule thumb that gently expands and turns translucent frosted glass while dragging,
 *   letting the active track color refract through without heavy GPU blur stalls.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 34.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "GlassThumbW"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "GlassThumbH"
    )
    val thumbFillAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.36f else 0.96f,
        animationSpec = tween(140),
        label = "GlassThumbAlpha"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "GlassThumbScale"
    )

    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val usablePx = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
    val thumbOffsetX = (fraction * usablePx).roundToInt()

    val activeColor = MaterialTheme.colorScheme.primary
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val rimBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.22f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true

                    fun updateFromX(x: Float) {
                        val halfThumb = thumbWidthPx / 2f
                        val newFrac = ((x - halfThumb) / usablePx).coerceIn(0f, 1f)
                        val newVal = valueRange.start + newFrac * span
                        if ((newFrac == 0f || newFrac == 1f) && newVal != latestValue) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnChange(newVal)
                    }

                    updateFromX(down.position.x)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    } finally {
                        isDragging = false
                        latestOnFinished()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Frosted track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(trackBg)
                .border(0.6.dp, Color.White.copy(alpha = 0.16f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(activeColor.copy(alpha = 0.82f), activeColor)
                        ),
                        CircleShape
                    )
            )
        }

        // Liquid glass capsule thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetX, 0) }
                .size(thumbWidth, thumbHeight)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .shadow(
                    elevation = if (isDragging) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
                .background(Color.White.copy(alpha = thumbFillAlpha), CircleShape)
                .border(1.1.dp, rimBrush, CircleShape)
        )
    }
}

/**
 * Lightweight iOS-inspired Liquid Glass switch with spring thumb stretch.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        animationSpec = tween(180),
        label = "GlassSwitchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) (if (pressed) 19.dp else 24.dp) else 3.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "GlassSwitchOffset"
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed) 29.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "GlassSwitchThumbW"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.45f else 0.96f,
        animationSpec = tween(120),
        label = "GlassSwitchThumbAlpha"
    )

    val rimBrush = remember {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.25f)
            )
        )
    }

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 30.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor)
            .border(0.7.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
            .pointerInput(checked) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onCheckedChange(!checked) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = thumbWidth, height = 24.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White.copy(alpha = thumbAlpha), CircleShape)
                .border(1.dp, rimBrush, CircleShape)
        )
    }
}
