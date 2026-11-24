package com.serenetype.keyboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unified concentric design system tokens for SereneType Settings:
 * - Level 1 (Outer section cards): 24.dp
 * - Level 2 (Nested rows, previews, inputs): 16.dp (24.dp - 8.dp padding)
 * - Level 3 (Small badges, key tiles): 10.dp
 * - Interactive selectors (Switch, Radio, Checkbox, Segmented Pill): CircleShape (full capsule/circle)
 */
object SereneTokens {
    val RadiusOuter = 24.dp
    val RadiusInner = 16.dp
    val RadiusSmall = 10.dp
    val OuterShape = RoundedCornerShape(RadiusOuter)
    val InnerShape = RoundedCornerShape(RadiusInner)
    val SmallShape = RoundedCornerShape(RadiusSmall)
}

/**
 * Lightweight iOS-inspired Liquid Glass slider:
 * - Crisp frosted track with subtle specular rim.
 * - Glass capsule thumb that gently expands and turns translucent frosted glass while dragging.
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
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "GlassThumbW"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 24.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "GlassThumbH"
    )
    val thumbFillAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.40f else 0.96f,
        animationSpec = tween(140),
        label = "GlassThumbAlpha"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
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
                    ambientColor = Color.Black.copy(alpha = 0.32f),
                    spotColor = Color.Black.copy(alpha = 0.32f)
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
            .clip(CircleShape)
            .background(trackColor)
            .border(0.7.dp, Color.White.copy(alpha = 0.18f), CircleShape)
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

/**
 * iOS-consistent circular multi-select checkmark with PathMeasure stroke-draw animation.
 */
@Composable
fun LiquidGlassCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "GlassCheckBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.White.copy(alpha = 0.42f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(180),
        label = "GlassCheckBorder"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "GlassCheckPress"
    )
    val popScale = remember { Animatable(1f) }
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = if (checked) 210 else 130, easing = FastOutSlowInEasing),
        label = "GlassCheckPath"
    )

    var firstRender by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (firstRender) {
            firstRender = false
            return@LaunchedEffect
        }
        if (checked) {
            popScale.snapTo(0.88f)
            popScale.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 480f))
        }
    }

    val checkTint = MaterialTheme.colorScheme.onPrimary
    val srcPath = remember { Path() }
    val dstPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                val s = pressScale * popScale.value
                scaleX = s
                scaleY = s
            }
            .then(
                if (checked) Modifier.shadow(2.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                else Modifier
            )
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, borderColor, CircleShape)
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
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = checkProgress
            if (p > 0.01f) {
                val w = size.width
                val h = size.height
                srcPath.reset()
                srcPath.moveTo(w * 0.28f, h * 0.52f)
                srcPath.lineTo(w * 0.44f, h * 0.68f)
                srcPath.lineTo(w * 0.73f, h * 0.35f)

                dstPath.reset()
                pathMeasure.setPath(srcPath, false)
                pathMeasure.getSegment(0f, pathMeasure.length * p, dstPath, true)

                drawPath(
                    path = dstPath,
                    color = checkTint,
                    style = Stroke(
                        width = (w / 24f) * 2.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * iOS-consistent circular radio button with smooth spring dot & subtle orb pulse.
 */
@Composable
fun LiquidGlassRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "GlassRadioBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.42f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(180),
        label = "GlassRadioBorder"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "GlassRadioPress"
    )
    val popScale = remember { Animatable(1f) }
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 440f),
        label = "GlassRadioDot"
    )

    var firstRender by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstRender) {
            firstRender = false
            return@LaunchedEffect
        }
        if (selected) {
            popScale.snapTo(0.88f)
            popScale.animateTo(1f, spring(dampingRatio = 0.54f, stiffness = 460f))
        }
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                val s = pressScale * popScale.value
                scaleX = s
                scaleY = s
            }
            .then(
                if (selected) Modifier.shadow(2.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                else Modifier
            )
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, borderColor, CircleShape)
            .pointerInput(selected) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                    alpha = dotScale.coerceIn(0f, 1f)
                }
                .shadow(1.5.dp, CircleShape)
                .background(Color.White.copy(alpha = 0.96f), CircleShape)
        )
    }
}

/**
 * iOS-inspired capsule segmented control with a sliding frosted-glass pill:
 * - Full capsule geometry (CircleShape) matching iOS segmented controls.
 * - Supports tap AND smooth finger drag across segments with haptic detents.
 * - Uses graphicsLayer translation + single-pass gradient (zero RenderEffect blur stalls).
 */
@Composable
fun <T> LiquidGlassSegmentedRow(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val count = items.size
    val selectedIdx = items.indexOf(selectedItem).coerceIn(0, count - 1)
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(selectedIdx.toFloat()) }

    val latestItems by rememberUpdatedState(items)
    val latestOnSelect by rememberUpdatedState(onSelect)

    val padPx = with(density) { 3.dp.toPx() }
    val innerWidthPx = (trackWidthPx - padPx * 2f).coerceAtLeast(1f)
    val segWidthPx = innerWidthPx / count.toFloat()

    val animatedIndex by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else selectedIdx.toFloat(),
        animationSpec = if (isDragging) {
            spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.88f, stiffness = 500f)
        },
        label = "FrostedSegPos"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "FrostedSegScale"
    )

    val trackBg = if (isDark) Color.Black.copy(alpha = 0.28f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val pillBrush = remember(isDark, isDragging) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF434754).copy(alpha = if (isDragging) 0.86f else 0.96f),
                    Color(0xFF2B2E37).copy(alpha = if (isDragging) 0.82f else 0.94f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDragging) 0.88f else 0.97f),
                    Color(0xFFF1F2F6).copy(alpha = if (isDragging) 0.84f else 0.95f)
                )
            )
        }
    }
    val pillRim = remember(isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.36f else 0.92f),
                Color.White.copy(alpha = if (isDark) 0.08f else 0.25f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(trackBg)
            .border(0.7.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.24f), CircleShape)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val curInner = (trackWidthPx - padPx * 2f).coerceAtLeast(1f)
                    val curSegW = curInner / count.toFloat()
                    val downX = down.position.x
                    val touchSlop = viewConfiguration.touchSlop
                    var startedDrag = false
                    var lastHapticIdx = ((downX - padPx) / curSegW).toInt().coerceIn(0, count - 1)

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val chosenIdx = ((change.position.x - padPx) / curSegW)
                                    .toInt()
                                    .coerceIn(0, count - 1)
                                latestOnSelect(latestItems[chosenIdx])
                                break
                            }
                            val dx = change.position.x - downX
                            if (!startedDrag && abs(dx) > touchSlop) {
                                startedDrag = true
                                isDragging = true
                            }
                            if (startedDrag) {
                                change.consume()
                                dragFraction = ((change.position.x - padPx - curSegW * 0.5f) / curSegW)
                                    .coerceIn(0f, (count - 1).toFloat())
                                val hoveredIdx = ((change.position.x - padPx) / curSegW).toInt().coerceIn(0, count - 1)
                                if (hoveredIdx != lastHapticIdx) {
                                    lastHapticIdx = hoveredIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    } finally {
                        isDragging = false
                    }
                }
            }
            .padding(3.dp)
    ) {
        if (trackWidthPx > 0) {
            val pillWidthDp = with(density) { segWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .width(pillWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = animatedIndex * segWidthPx
                        scaleX = pillScale
                        scaleY = pillScale
                    }
                    .shadow(
                        elevation = if (isDragging) 6.dp else 2.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.24f),
                        spotColor = Color.Black.copy(alpha = 0.24f)
                    )
                    .clip(CircleShape)
                    .background(pillBrush)
                    .border(0.9.dp, pillRim, CircleShape)
            )
        }

        val activeIdxFloat = if (isDragging) dragFraction.roundToInt().coerceIn(0, count - 1) else selectedIdx
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                val isCurrent = idx == activeIdxFloat
                val textColor by animateColorAsState(
                    targetValue = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(140),
                    label = "SegTextColor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Collapsible card for slider-heavy sections so accidental swipes during scrolling don't alter values.
 */
@Composable
fun ExpandableGlassSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "ExpandChevron"
    )

    Surface(
        shape = SereneTokens.OuterShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring())
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "collapse" else "expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring()) + fadeIn(tween(150)),
                exit = shrinkVertically(spring()) + fadeOut(tween(120))
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                }
            }
        }
    }
}
