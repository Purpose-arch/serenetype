package com.serenetype.keyboard.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.MoreHoriz
import com.serenetype.keyboard.ime.KeyboardIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.data.SettingsRepository
import com.serenetype.keyboard.data.TopBarButton
import com.serenetype.keyboard.theme.KeyboardPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InteractiveLayoutEditor(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    repository: SettingsRepository
) {
    val scope = rememberCoroutineScope()

    var keyHeight by remember(config.keyHeightDp) { mutableFloatStateOf(config.keyHeightDp) }
    var sidePad by remember(config.sidePaddingDp) { mutableFloatStateOf(config.sidePaddingDp) }
    var bottomPad by remember(config.bottomPaddingDp) { mutableFloatStateOf(config.bottomPaddingDp) }
    var symWeight by remember(config.symKeyWeight) { mutableFloatStateOf(config.symKeyWeight) }
    var spaceWeight by remember(config.spaceWeight) { mutableFloatStateOf(config.spaceWeight) }
    var enterWeight by remember(config.enterWeight) { mutableFloatStateOf(config.enterWeight) }
    var punctWeight by remember(config.punctWeight) { mutableFloatStateOf(config.punctWeight) }
    var order by remember(config.bottomRowOrder) { mutableStateOf(config.bottomRowOrder) }
    var topButtons by remember(config.topBarButtons) { mutableStateOf(config.topBarButtons) }

    val currentOrder by rememberUpdatedState(order)
    val currentTop by rememberUpdatedState(topButtons)

    // Debounced persistence for pinch-zoom gesture
    LaunchedEffect(keyHeight) {
        if (keyHeight != config.keyHeightDp) {
            delay(200)
            repository.setFloat("keyHeight", keyHeight)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "interactive layout",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { scope.launch { repository.resetIosDefaults() } }) {
                    Text("reset")
                }
            }
            Text(
                "drag bottom keys to reorder · drag edges to resize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.trayBackground)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            keyHeight = (keyHeight * zoom).coerceIn(36f, 62f)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePad.dp)
                        .padding(top = 10.dp, bottom = bottomPad.coerceAtMost(48f).dp),
                    verticalArrangement = Arrangement.spacedBy(config.verticalGapDp.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(config.horizontalGapDp.dp)
                    ) {
                        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { k ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(keyHeight.dp)
                                    .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
                                    .background(palette.keyBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(k, color = palette.keyText, fontSize = 16.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(config.horizontalGapDp.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        order.forEach { btnId ->
                            val isHidden = (btnId == "GLOBE" && !config.showGlobeKey) || (btnId == "EMOJI" && !config.showEmojiKey)
                            if (isHidden) return@forEach

                            val weight = when (btnId) {
                                "123" -> symWeight
                                "SPACE" -> spaceWeight
                                "ENTER" -> enterWeight
                                "COMMA", "PERIOD" -> punctWeight
                                else -> 1.1f
                            }
                            val label = when (btnId) {
                                "123" -> "123"
                                "COMMA" -> ","
                                "PERIOD" -> "."
                                "GLOBE" -> "lang"
                                "EMOJI" -> "emj"
                                "SPACE" -> if (config.showSpacebarLabel) "space" else "—"
                                "ENTER" -> "↵"
                                else -> btnId
                            }
                            var dragAcc by remember(btnId) { mutableFloatStateOf(0f) }

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .height(keyHeight.dp)
                                    .clip(RoundedCornerShape(config.funcCornerRadiusDp.dp))
                                    .background(if (btnId == "ENTER") palette.accent else palette.funcKeyBackground)
                                    .border(
                                        0.7.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        RoundedCornerShape(config.funcCornerRadiusDp.dp)
                                    )
                                    .pointerInput(btnId) {
                                        detectDragGestures(
                                            onDragStart = { dragAcc = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAcc += dragAmount.x
                                                val list = currentOrder
                                                val curIdx = list.indexOf(btnId)
                                                if (curIdx >= 0) {
                                                    if (dragAcc > 46f && curIdx < list.lastIndex) {
                                                        val mutable = list.toMutableList()
                                                        mutable[curIdx] = mutable[curIdx + 1].also { mutable[curIdx + 1] = mutable[curIdx] }
                                                        order = mutable
                                                        dragAcc = 0f
                                                        scope.launch { repository.setBottomRowOrder(mutable) }
                                                    } else if (dragAcc < -46f && curIdx > 0) {
                                                        val mutable = list.toMutableList()
                                                        mutable[curIdx] = mutable[curIdx - 1].also { mutable[curIdx - 1] = mutable[curIdx] }
                                                        order = mutable
                                                        dragAcc = 0f
                                                        scope.launch { repository.setBottomRowOrder(mutable) }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(
                                        Icons.Outlined.DragIndicator,
                                        contentDescription = null,
                                        tint = if (btnId == "ENTER") palette.accentText.copy(alpha = 0.7f) else palette.secondaryText,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = if (btnId == "ENTER") palette.accentText else palette.keyText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(14.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch { repository.setFloat("keyHeight", keyHeight) }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                keyHeight = (keyHeight + dragAmount.y * 0.25f).coerceIn(36f, 62f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(14.dp)
                        .height(64.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch { repository.setFloat("sidePad", sidePad) }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                sidePad = (sidePad - dragAmount.x * 0.3f).coerceIn(0f, 24f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.animateContentSize(spring())
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("top bar", fontWeight = FontWeight.Medium)
                    LiquidGlassSwitch(
                        checked = config.showTopBar,
                        onCheckedChange = { scope.launch { repository.setBool("showTopBar", it) } }
                    )
                }

                if (config.showTopBar) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "drag to reorder · tap to toggle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    fun topBarIcon(id: String) = when (id) {
                        "EMOJI" -> KeyboardIcons.Emoji
                        "CLIPBOARD" -> KeyboardIcons.Clipboard
                        "SELECT_ALL" -> KeyboardIcons.SelectAll
                        "COPY" -> KeyboardIcons.Copy
                        "PASTE" -> KeyboardIcons.Paste
                        "CUT" -> Icons.Rounded.ContentCut
                        "UNDO" -> Icons.AutoMirrored.Rounded.Undo
                        else -> Icons.Rounded.MoreHoriz
                    }

                    if (topButtons.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            topButtons.forEach { btnId ->
                                val info = TopBarButton.entries.find { it.id == btnId }
                                var dragX by remember(btnId) { mutableFloatStateOf(0f) }
                                var isDraggingBtn by remember(btnId) { mutableStateOf(false) }
                                val dragScale by animateFloatAsState(
                                    targetValue = if (isDraggingBtn) 1.08f else 1f,
                                    animationSpec = spring(dampingRatio = 0.7f),
                                    label = "TopDragScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .graphicsLayer { scaleX = dragScale; scaleY = dragScale }
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .pointerInput(btnId) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragX = 0f
                                                    isDraggingBtn = true
                                                },
                                                onDragEnd = { isDraggingBtn = false },
                                                onDragCancel = { isDraggingBtn = false },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragX += dragAmount.x
                                                    val list = currentTop
                                                    val curIdx = list.indexOf(btnId)
                                                    if (curIdx >= 0) {
                                                        if (dragX > 36f && curIdx < list.lastIndex) {
                                                            val mutable = list.toMutableList()
                                                            mutable[curIdx] = mutable[curIdx + 1].also { mutable[curIdx + 1] = mutable[curIdx] }
                                                            topButtons = mutable
                                                            dragX = 0f
                                                            scope.launch { repository.setTopBarButtons(mutable) }
                                                        } else if (dragX < -36f && curIdx > 0) {
                                                            val mutable = list.toMutableList()
                                                            mutable[curIdx] = mutable[curIdx - 1].also { mutable[curIdx - 1] = mutable[curIdx] }
                                                            topButtons = mutable
                                                            dragX = 0f
                                                            scope.launch { repository.setTopBarButtons(mutable) }
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = topBarIcon(btnId),
                                        contentDescription = info?.label ?: btnId,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TopBarButton.entries.forEach { btn ->
                            val enabled = btn.id in topButtons
                            FilterChip(
                                selected = enabled,
                                onClick = { scope.launch { repository.toggleTopBarButton(btn.id) } },
                                leadingIcon = {
                                    Icon(
                                        imageVector = topBarIcon(btn.id),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(btn.label) }
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("dimensions", fontWeight = FontWeight.SemiBold)

                LabeledSlider(
                    title = "key height",
                    valueText = "${keyHeight.toInt()} dp",
                    value = keyHeight,
                    range = 36f..62f,
                    onChange = { keyHeight = it },
                    onChangeFinished = { scope.launch { repository.setFloat("keyHeight", keyHeight) } }
                )
                LabeledSlider(
                    title = "side padding",
                    valueText = "${sidePad.toInt()} dp",
                    value = sidePad,
                    range = 0f..24f,
                    onChange = { sidePad = it },
                    onChangeFinished = { scope.launch { repository.setFloat("sidePad", sidePad) } }
                )
                LabeledSlider(
                    title = "bottom padding",
                    valueText = "${bottomPad.toInt()} dp",
                    value = bottomPad,
                    range = 2f..102f,
                    onChange = { bottomPad = it },
                    onChangeFinished = { scope.launch { repository.setFloat("bottomPad", bottomPad) } }
                )
                LabeledSlider(
                    title = "space width",
                    valueText = "${String.format("%.1f", spaceWeight)}x",
                    value = spaceWeight,
                    range = 2.4f..6.5f,
                    onChange = { spaceWeight = it },
                    onChangeFinished = { scope.launch { repository.setFloat("spaceWeight", spaceWeight) } }
                )
                LabeledSlider(
                    title = "comma & period",
                    valueText = "${String.format("%.1f", punctWeight)}x",
                    value = punctWeight,
                    range = 0.6f..1.5f,
                    onChange = { punctWeight = it },
                    onChangeFinished = { scope.launch { repository.setFloat("punctWeight", punctWeight) } }
                )
                LabeledSlider(
                    title = "enter width",
                    valueText = "${String.format("%.1f", enterWeight)}x",
                    value = enterWeight,
                    range = 1.1f..2.6f,
                    onChange = { enterWeight = it },
                    onChangeFinished = { scope.launch { repository.setFloat("enterWeight", enterWeight) } }
                )
                LabeledSlider(
                    title = "123 width",
                    valueText = "${String.format("%.1f", symWeight)}x",
                    value = symWeight,
                    range = 0.9f..2.2f,
                    onChange = { symWeight = it },
                    onChangeFinished = { scope.launch { repository.setFloat("symWeight", symWeight) } }
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        LiquidGlassSlider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range
        )
    }
}
