package com.serenetype.keyboard.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RestartAlt
import com.serenetype.keyboard.ime.KeyboardIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
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
    var dimensionsExpanded by remember { mutableStateOf(false) }

    val currentOrder by rememberUpdatedState(order)
    val currentTop by rememberUpdatedState(topButtons)
    val curSymW by rememberUpdatedState(symWeight)
    val curSpaceW by rememberUpdatedState(spaceWeight)
    val curEnterW by rememberUpdatedState(enterWeight)
    val curPunctW by rememberUpdatedState(punctWeight)

    fun bottomWeight(id: String): Float = when (id) {
        "123" -> curSymW
        "SPACE" -> curSpaceW
        "ENTER" -> curEnterW
        "COMMA", "PERIOD", "GLOBE", "EMOJI" -> curPunctW.coerceAtLeast(0.95f)
        else -> 1.0f
    }

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
                IconButton(onClick = { scope.launch { repository.resetIosDefaults() } }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "reset")
                }
            }
            Text(
                "hold & drag bottom keys to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SereneTokens.OuterShape)
                    .background(palette.trayBackground)
                    .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), SereneTokens.OuterShape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePad.coerceAtLeast(6f).dp, vertical = 12.dp),
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

                    HorizontalReorderRow(
                        items = order,
                        itemWeight = { bottomWeight(it) },
                        itemHeight = keyHeight.dp,
                        spacing = config.horizontalGapDp.dp,
                        onCommitOrder = { newOrder ->
                            order = newOrder
                            scope.launch { repository.setBottomRowOrder(newOrder) }
                        }
                    ) { btnId, isDraggingThis ->
                        val contentTint = if (btnId == "ENTER" || isDraggingThis) palette.accentText else palette.keyText
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(config.funcCornerRadiusDp.dp))
                                .background(if (btnId == "ENTER" || isDraggingThis) palette.accent else palette.funcKeyBackground)
                                .border(
                                    0.7.dp,
                                    if (isDraggingThis) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    RoundedCornerShape(config.funcCornerRadiusDp.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (btnId) {
                                "GLOBE" -> Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "language",
                                    tint = contentTint,
                                    modifier = Modifier.size(17.dp)
                                )
                                "EMOJI" -> Icon(
                                    imageVector = KeyboardIcons.Emoji,
                                    contentDescription = "emoji",
                                    tint = contentTint,
                                    modifier = Modifier.size(17.dp)
                                )
                                "ENTER" -> Icon(
                                    imageVector = KeyboardIcons.Return,
                                    contentDescription = "enter",
                                    tint = contentTint,
                                    modifier = Modifier.size(17.dp)
                                )
                                else -> {
                                    val label = when (btnId) {
                                        "123" -> "123"
                                        "COMMA" -> ","
                                        "PERIOD" -> "."
                                        "SPACE" -> if (config.showSpacebarLabel) "space" else "—"
                                        else -> btnId
                                    }
                                    Text(
                                        text = label,
                                        color = contentTint,
                                        fontSize = if (btnId == "COMMA" || btnId == "PERIOD") 16.sp else 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

            }

            // Bottom keys toggles (comma, period, emoji, language)
            Surface(
                shape = SereneTokens.OuterShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("bottom keys", fontWeight = FontWeight.Medium)
                    val optionalKeys = listOf(
                        "COMMA" to "comma (,)",
                        "PERIOD" to "period (.)",
                        "EMOJI" to "emoji",
                        "GLOBE" to "language"
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SereneTokens.InnerShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        optionalKeys.forEachIndexed { idx, (id, title) ->
                            val enabled = id in order
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { repository.toggleBottomKey(id) } }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val badgeTint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        when (id) {
                                            "EMOJI" -> Icon(KeyboardIcons.Emoji, contentDescription = null, tint = badgeTint, modifier = Modifier.size(17.dp))
                                            "GLOBE" -> Icon(Icons.Rounded.Language, contentDescription = null, tint = badgeTint, modifier = Modifier.size(17.dp))
                                            "COMMA" -> Text(",", color = badgeTint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                            else -> Text(".", color = badgeTint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text(
                                        text = title,
                                        fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LiquidGlassCheckbox(
                                    checked = enabled,
                                    onCheckedChange = { scope.launch { repository.toggleBottomKey(id) } }
                                )
                            }
                            if (idx < optionalKeys.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = SereneTokens.OuterShape,
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
                    val haptic = LocalHapticFeedback.current
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                        Text(
                            "hold & drag to reorder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SereneTokens.InnerShape)
                                .background(palette.trayBackground)
                                .border(
                                    0.8.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    SereneTokens.InnerShape
                                )
                                .padding(6.dp)
                        ) {
                            HorizontalReorderRow(
                                items = topButtons,
                                itemWeight = { 1f },
                                itemHeight = 38.dp,
                                spacing = 6.dp,
                                onCommitOrder = { newOrder ->
                                    topButtons = newOrder
                                    scope.launch { repository.setTopBarButtons(newOrder) }
                                }
                            ) { btnId, isDraggingThis ->
                                val info = TopBarButton.entries.find { it.id == btnId }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(SereneTokens.SmallShape)
                                        .background(if (isDraggingThis) palette.accent else palette.funcKeyBackground)
                                        .then(
                                            if (isDraggingThis) {
                                                Modifier.border(
                                                    1.dp,
                                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f),
                                                    SereneTokens.SmallShape
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = topBarIcon(btnId),
                                        contentDescription = info?.label ?: btnId,
                                        tint = if (isDraggingThis) palette.accentText else palette.keyText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SereneTokens.InnerShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        TopBarButton.entries.forEachIndexed { idx, btn ->
                            val enabled = btn.id in topButtons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { scope.launch { repository.toggleTopBarButton(btn.id) } }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = topBarIcon(btn.id),
                                            contentDescription = null,
                                            tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Text(
                                        text = btn.label,
                                        fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LiquidGlassCheckbox(
                                    checked = enabled,
                                    onCheckedChange = { scope.launch { repository.toggleTopBarButton(btn.id) } }
                                )
                            }
                            if (idx < TopBarButton.entries.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        ExpandableGlassSection(
            title = "dimensions",
            summary = "${keyHeight.toInt()} dp height · space ${String.format("%.1f", spaceWeight)}x · pad ${bottomPad.toInt()} dp",
            expanded = dimensionsExpanded,
            onToggle = { dimensionsExpanded = !dimensionsExpanded }
        ) {
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


/**
 * Shared two-phase horizontal hold-and-drag reorder bar:
 * - Uses actual root-relative Rects (no approximate weight division).
 * - Smoothly shifts sibling items aside while dragging; commits new order once on release.
 * - Works symmetrically left-to-right and right-to-left for any item widths (including SPACE).
 */
@Composable
private fun HorizontalReorderRow(
    items: List<String>,
    itemWeight: (String) -> Float,
    itemHeight: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    onCommitOrder: (List<String>) -> Unit,
    itemContent: @Composable (id: String, isDragging: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { spacing.toPx() }
    val latestItems by rememberUpdatedState(items)
    val latestOnCommit by rememberUpdatedState(onCommitOrder)
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    val itemRects = remember { mutableStateMapOf<String, Rect>() }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(items) {
        itemRects.keys.removeAll { it !in items }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .systemGestureExclusion()
            .onGloballyPositioned { rowOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startPos ->
                        val hit = itemRects.entries.firstOrNull { (_, r) -> r.contains(startPos) }?.key
                            ?: latestItems.minByOrNull { id ->
                                val r = itemRects[id]
                                if (r == null) Float.MAX_VALUE else kotlin.math.abs(startPos.x - r.center.x)
                            }
                        if (hit != null) {
                            draggingId = hit
                            dragOffsetX = 0f
                            targetIndex = latestItems.indexOf(hit)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = {
                        val activeId = draggingId
                        val dst = targetIndex
                        val list = latestItems
                        draggingId = null
                        dragOffsetX = 0f
                        targetIndex = -1
                        if (activeId != null) {
                            val src = list.indexOf(activeId)
                            if (src >= 0 && dst in list.indices && src != dst) {
                                val mutable = list.toMutableList()
                                mutable.removeAt(src)
                                mutable.add(dst, activeId)
                                latestOnCommit(mutable)
                            }
                        }
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetX = 0f
                        targetIndex = -1
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val activeId = draggingId ?: return@detectDragGesturesAfterLongPress
                        val startRect = itemRects[activeId] ?: return@detectDragGesturesAfterLongPress
                        val list = latestItems
                        val minDx = (itemRects[list.firstOrNull()]?.left ?: 0f) - startRect.left
                        val maxDx = (itemRects[list.lastOrNull()]?.right ?: startRect.right) - startRect.right
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(minDx, maxDx)

                        val fingerX = change.position.x
                        var newIdx = 0
                        for (id in list) {
                            if (id == activeId) continue
                            val r = itemRects[id] ?: continue
                            if (fingerX > r.center.x) {
                                newIdx++
                            }
                        }
                        val clampedIdx = newIdx.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                        if (clampedIdx != targetIndex) {
                            targetIndex = clampedIdx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val activeId = draggingId
        val srcIdx = if (activeId != null) items.indexOf(activeId) else -1
        val activeWidthPx = if (activeId != null) (itemRects[activeId]?.width ?: 0f) else 0f

        items.forEachIndexed { idx, btnId ->
            key(btnId) {
                val isDraggingThis = activeId == btnId
                val shiftTargetPx = when {
                    activeId == null || isDraggingThis || srcIdx < 0 || targetIndex < 0 -> 0f
                    srcIdx < targetIndex && idx in (srcIdx + 1)..targetIndex -> -(activeWidthPx + spacingPx)
                    srcIdx > targetIndex && idx in targetIndex until srcIdx -> (activeWidthPx + spacingPx)
                    else -> 0f
                }
                val siblingShiftX by animateFloatAsState(
                    targetValue = shiftTargetPx,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                    label = "ReorderShift"
                )
                val dragScale by animateFloatAsState(
                    targetValue = if (isDraggingThis) 1.10f else 1f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
                    label = "ReorderScale"
                )

                Box(
                    modifier = Modifier
                        .weight(itemWeight(btnId))
                        .height(itemHeight)
                        .zIndex(if (isDraggingThis) 12f else 0f)
                        .onGloballyPositioned { coords ->
                            if (draggingId == null) {
                                val b = coords.boundsInRoot()
                                itemRects[btnId] = Rect(
                                    b.left - rowOrigin.x,
                                    b.top - rowOrigin.y,
                                    b.right - rowOrigin.x,
                                    b.bottom - rowOrigin.y
                                )
                            }
                        }
                        .graphicsLayer {
                            translationX = when {
                                isDraggingThis -> dragOffsetX
                                activeId != null -> siblingShiftX
                                else -> 0f
                            }
                            scaleX = dragScale
                            scaleY = dragScale
                        }
                ) {
                    itemContent(btnId, isDraggingThis)
                }
            }
        }
    }
}
