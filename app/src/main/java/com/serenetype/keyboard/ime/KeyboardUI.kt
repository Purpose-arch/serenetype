package com.serenetype.keyboard.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.serenetype.keyboard.data.KeyFontStyle
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.data.SmartTapMode
import com.serenetype.keyboard.input.ActivePointer
import com.serenetype.keyboard.input.KeyDetector
import com.serenetype.keyboard.input.PointerTrackerQueue
import com.serenetype.keyboard.input.TypingTimeRecorder
import com.serenetype.keyboard.data.KeyboardLayouts
import com.serenetype.keyboard.theme.KeyboardPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

private val WHITESPACE_REGEX = Regex("\\s+")

enum class KeyboardLayer { LETTERS, SYM1, SYM2, EMOJI, CLIPBOARD }

@Immutable
private data class KeyPreviewState(
    val pointerId: PointerId,
    val label: String,
    val centerX: Float,
    val topY: Float
)

@Stable
private class KeyPreviewController {
    var active by mutableStateOf<KeyPreviewState?>(null)
        private set

    fun show(pointerId: PointerId, label: String, centerX: Float, topY: Float) {
        active = KeyPreviewState(pointerId, label, centerX, topY)
    }

    fun hide(pointerId: PointerId) {
        if (active?.pointerId == pointerId) {
            active = null
        }
    }
}

@Composable
fun KeyboardUI(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    shiftState: Int,
    clipboardHistory: List<String>,
    recentEmojis: List<String>,
    emojiFontFamily: FontFamily,
    nextCharPriorsProvider: () -> Map<Char, Float> = { emptyMap() },
    touchBiasProvider: (Char) -> Pair<Float, Float>? = { null },
    onTouchSample: (Char, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onKeyGeometryUpdate: (Map<Char, Pair<Float, Float>>, Float) -> Unit = { _, _ -> },
    onChar: (String) -> Unit,
    onCharWithPos: (String, Float?, Float?) -> Unit = { c, _, _ -> onChar(c) },
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onShiftTap: () -> Unit,
    onCycleLanguage: (Int) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onTopAction: (String) -> Unit,
    onHaptic: () -> Unit = {},
    onPasteClip: (String) -> Unit,
    onRemoveClip: (String) -> Unit,
    onClearClips: () -> Unit = {}
) {
    var layer by remember { mutableStateOf(KeyboardLayer.LETTERS) }
    var showLangMenu by remember { mutableStateOf(false) }

    val fontFamily = when (config.fontStyle) {
        KeyFontStyle.SYSTEM_SANS -> FontFamily.SansSerif
        KeyFontStyle.ROUNDED -> FontFamily.SansSerif
        KeyFontStyle.MONOSPACE -> FontFamily.Monospace
    }

    val langLayout = KeyboardLayouts.languages[config.activeLanguage]
        ?: KeyboardLayouts.languages.getValue("en")

    val density = LocalDensity.current
    val quickSymbols = remember(config.quickSymbolPopupChars) {
        config.quickSymbolPopupChars.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    }
    val previewController = remember { KeyPreviewController() }
    var showQuickSymOverlay by remember { mutableStateOf(false) }
    var quickSymSelectedIdx by remember { mutableIntStateOf(-1) }
    var quickSymOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var quickSymAnchorX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.trayBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = config.sidePaddingDp.dp,
                    end = config.sidePaddingDp.dp,
                    top = 6.dp,
                    bottom = config.bottomPaddingDp.dp
                )
        ) {
            if (config.showTopBar && config.topBarButtons.isNotEmpty()) {
                TopUtilityBar(
                    buttons = config.topBarButtons,
                    activeLayer = layer,
                    config = config,
                    palette = palette,
                    onButtonClick = { id ->
                        when (id) {
                            "EMOJI" -> { onHaptic(); layer = if (layer == KeyboardLayer.EMOJI) KeyboardLayer.LETTERS else KeyboardLayer.EMOJI }
                            "CLIPBOARD" -> { onHaptic(); layer = if (layer == KeyboardLayer.CLIPBOARD) KeyboardLayer.LETTERS else KeyboardLayer.CLIPBOARD }
                            else -> onTopAction(id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            when (layer) {
                KeyboardLayer.EMOJI -> {
                    IosEmojiPickerPanel(
                        config = config,
                        palette = palette,
                        recentEmojis = recentEmojis,
                        emojiFontFamily = emojiFontFamily,
                        onEmojiClick = onEmoji,
                        onBackspace = onBackspace,
                        onDeleteWord = onDeleteWord,
                        onBackToLetters = { onHaptic(); layer = KeyboardLayer.LETTERS }
                    )
                }

                KeyboardLayer.CLIPBOARD -> {
                    ClipboardPanel(
                        config = config,
                        palette = palette,
                        clips = clipboardHistory,
                        onPaste = {
                            onPasteClip(it)
                            layer = KeyboardLayer.LETTERS
                        },
                        onRemove = onRemoveClip,
                        onClearAll = { onHaptic(); onClearClips() },
                        onBackToLetters = { onHaptic(); layer = KeyboardLayer.LETTERS }
                    )
                }

                else -> {
                    val currentLayer = layer
                    val sym1Rows = remember(config.sym1Row1, config.sym1Row2, config.sym1Row3) {
                        fun parseRow(raw: String, fallback: List<String>): List<String> =
                            raw.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }.ifEmpty { fallback }
                        listOf(
                            parseRow(config.sym1Row1, KeyboardLayouts.symbolsPage1[0]),
                            parseRow(config.sym1Row2, KeyboardLayouts.symbolsPage1[1]),
                            listOf("ALT_SYM") + parseRow(config.sym1Row3, listOf("*", "\"", "‘", ".", ",", "!", "?")) + listOf("BACK")
                        )
                    }
                    val sym2Rows = remember(config.sym2Row1, config.sym2Row2, config.sym2Row3) {
                        fun parseRow(raw: String, fallback: List<String>): List<String> =
                            raw.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }.ifEmpty { fallback }
                        listOf(
                            parseRow(config.sym2Row1, KeyboardLayouts.symbolsPage2[0]),
                            parseRow(config.sym2Row2, KeyboardLayouts.symbolsPage2[1]),
                            listOf("ALT_123") + parseRow(config.sym2Row3, listOf("`", "√", "π", "÷", "×", "¶", "∆", "°")) + listOf("BACK")
                        )
                    }
                    val rows = when (currentLayer) {
                        KeyboardLayer.LETTERS -> buildList {
                            if (config.showNumberRow) add(KeyboardLayouts.numberRow)
                            addAll(langLayout.rows)
                        }
                        KeyboardLayer.SYM1 -> sym1Rows
                        KeyboardLayer.SYM2 -> sym2Rows
                        else -> emptyList()
                    }

                    val maxRowLen = rows.maxOfOrNull { it.size } ?: 10
                    val uniformFontSp = if (maxRowLen >= 12) {
                        (config.keyTextSizeSp - 2.5f).coerceAtLeast(16f)
                    } else {
                        config.keyTextSizeSp
                    }

                    val shiftIcon = when (shiftState) {
                        2 -> KeyboardIcons.CapsLockFilled
                        1 -> KeyboardIcons.ShiftFilled
                        else -> KeyboardIcons.ShiftOutline
                    }

                    val useUnifiedGrid = currentLayer == KeyboardLayer.LETTERS
                    val scope = rememberCoroutineScope()
                    val keyDetector = remember(currentLayer, config.activeLanguage, config.showNumberRow) {
                        KeyDetector()
                    }
                    val pointerQueue = remember(currentLayer, config.activeLanguage) {
                        PointerTrackerQueue()
                    }
                    val typingRecorder = remember { TypingTimeRecorder() }
                    val pressedKeyCounts = remember(currentLayer, config.activeLanguage) {
                        mutableStateMapOf<String, Int>()
                    }
                    var gridRootOrigin by remember { mutableStateOf(Offset.Zero) }
                    var gridAltsKey by remember { mutableStateOf<String?>(null) }
                    var gridAltsSelectedIdx by remember { mutableIntStateOf(0) }
                    val altCellStepPx = with(density) { 42.dp.toPx() }

                    val latestShiftState by rememberUpdatedState(shiftState)
                    val latestSmartAlpha by rememberUpdatedState(config.smartTapMode.alphaWeight)
                    val latestInstantCommit by rememberUpdatedState(config.instantKeyCommit)
                    val latestPriorsProvider by rememberUpdatedState(nextCharPriorsProvider)
                    val latestBiasProvider by rememberUpdatedState(touchBiasProvider)
                    val latestOnTouchSample by rememberUpdatedState(onTouchSample)
                    val latestOnGeometryUpdate by rememberUpdatedState(onKeyGeometryUpdate)
                    val latestOnCharWithPos by rememberUpdatedState(onCharWithPos)

                    val cornerPx = with(density) { config.cornerRadiusDp.dp.toPx() }
                    val shadowOffsetPx = with(density) { 1.dp.toPx() }
                    val borderWidthPx = with(density) { 0.75.dp.toPx() }

                    fun commitPointerKey(p: ActivePointer, rootPos: Offset) {
                        if (p.committed || p.altsTriggered) return
                        p.committed = true
                        latestOnCharWithPos(p.label, rootPos.x, rootPos.y)
                        if (p.keyId.length == 1 && p.keyId[0].isLetter()) {
                            latestOnTouchSample(
                                p.keyId[0],
                                rootPos.x - p.rect.center.x,
                                rootPos.y - p.rect.center.y,
                                p.rect.width,
                                p.rect.height
                            )
                        }
                    }

                    fun releaseVisualForPointer(p: ActivePointer) {
                        p.holdJob?.cancel()
                        if (!p.altsTriggered) {
                            val c = (pressedKeyCounts[p.keyId] ?: 1) - 1
                            if (c <= 0) pressedKeyCounts.remove(p.keyId) else pressedKeyCounts[p.keyId] = c
                        }
                        previewController.hide(p.pointerId)
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(config.verticalGapDp.dp)
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                gridRootOrigin = coords.positionInRoot()
                            }
                            .then(
                                if (useUnifiedGrid) {
                                    Modifier.drawBehind {
                                        val boundsMap = keyDetector.getAllBounds()
                                        val cr = CornerRadius(cornerPx, cornerPx)
                                        val showShadow = config.showKeyShadow
                                        val showBorder = config.showKeyBorder && palette.keyBorder != Color.Transparent
                                        for ((keyId, rect) in boundsMap) {
                                            val left = rect.left - gridRootOrigin.x
                                            val top = rect.top - gridRootOrigin.y
                                            val sz = Size(rect.width, rect.height)
                                            if (showShadow) {
                                                drawRoundRect(
                                                    color = palette.keyShadow,
                                                    topLeft = Offset(left, top + shadowOffsetPx),
                                                    size = sz,
                                                    cornerRadius = cr
                                                )
                                            }
                                            val isPressed = (pressedKeyCounts[keyId] ?: 0) > 0
                                            drawRoundRect(
                                                color = if (isPressed) palette.keyPressedBackground else palette.keyBackground,
                                                topLeft = Offset(left, top),
                                                size = sz,
                                                cornerRadius = cr
                                            )
                                            if (showBorder) {
                                                drawRoundRect(
                                                    color = palette.keyBorder,
                                                    topLeft = Offset(left, top),
                                                    size = sz,
                                                    cornerRadius = cr,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidthPx)
                                                )
                                            }
                                        }
                                    }
                                } else Modifier
                            )
                            .then(
                                if (useUnifiedGrid) {
                                    Modifier.pointerInput(currentLayer, config.activeLanguage, config.smartTapMode) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val nowMs = android.os.SystemClock.uptimeMillis()
                                                for (change in event.changes) {
                                                    if (change.changedToDown() && !change.isConsumed) {
                                                        val rootPos = gridRootOrigin + change.position
                                                        if (pointerQueue.isNoiseTap(rootPos, nowMs, keyDetector.avgKeyWidthPx)) {
                                                            continue
                                                        }
                                                        val best = keyDetector.detectKey(
                                                            rootPos = rootPos,
                                                            priors = latestPriorsProvider(),
                                                            alphaWeight = latestSmartAlpha,
                                                            biasProvider = latestBiasProvider
                                                        ) ?: continue

                                                        val (bestKey, rect) = best
                                                        val label = if (latestShiftState > 0) bestKey.uppercase() else bestKey
                                                        val isFastBurst = typingRecorder.onKeyInput(nowMs)

                                                        pressedKeyCounts[bestKey] = (pressedKeyCounts[bestKey] ?: 0) + 1
                                                        if (!isFastBurst) {
                                                            previewController.show(
                                                                pointerId = change.id,
                                                                label = label,
                                                                centerX = rect.center.x,
                                                                topY = rect.top
                                                            )
                                                        }

                                                        val alts = KeyboardLayouts.altChars[bestKey.lowercase()].orEmpty()
                                                        val instant = latestInstantCommit && alts.isEmpty()
                                                        val ptr = ActivePointer(
                                                            pointerId = change.id,
                                                            keyId = bestKey,
                                                            label = label,
                                                            rect = rect,
                                                            downTimeMs = nowMs,
                                                            downPos = rootPos,
                                                            lastPos = rootPos
                                                        )
                                                        pointerQueue.add(ptr)

                                                        if (instant) {
                                                            commitPointerKey(ptr, rootPos)
                                                        } else if (alts.isNotEmpty()) {
                                                            ptr.holdJob = scope.launch {
                                                                delay(360L)
                                                                ptr.altsTriggered = true
                                                                val c = (pressedKeyCounts[ptr.keyId] ?: 1) - 1
                                                                if (c <= 0) pressedKeyCounts.remove(ptr.keyId) else pressedKeyCounts[ptr.keyId] = c
                                                                previewController.hide(change.id)
                                                                gridAltsSelectedIdx = 0
                                                                gridAltsKey = ptr.keyId
                                                                onHaptic()
                                                            }
                                                        }
                                                    } else if (change.pressed) {
                                                        val ptr = pointerQueue.find(change.id) ?: continue
                                                        val rootPos = gridRootOrigin + change.position
                                                        if (ptr.altsTriggered) {
                                                            val alts = KeyboardLayouts.altChars[ptr.keyId.lowercase()].orEmpty()
                                                            if (alts.size > 1) {
                                                                val startX = ptr.rect.center.x - (alts.size * altCellStepPx) / 2f
                                                                val idx = ((rootPos.x - startX) / altCellStepPx).toInt().coerceIn(0, alts.lastIndex)
                                                                if (idx != gridAltsSelectedIdx) {
                                                                    gridAltsSelectedIdx = idx
                                                                    onHaptic()
                                                                }
                                                            }
                                                            continue
                                                        }
                                                        if (ptr.committed) continue
                                                        ptr.lastPos = rootPos
                                                        if (keyDetector.isMajorEnoughMove(ptr.rect, rootPos)) {
                                                            val moved = keyDetector.detectKey(
                                                                rootPos = rootPos,
                                                                priors = latestPriorsProvider(),
                                                                alphaWeight = latestSmartAlpha,
                                                                biasProvider = latestBiasProvider
                                                            )
                                                            if (moved != null && moved.first != ptr.keyId) {
                                                                ptr.holdJob?.cancel()
                                                                val oldC = (pressedKeyCounts[ptr.keyId] ?: 1) - 1
                                                                if (oldC <= 0) pressedKeyCounts.remove(ptr.keyId) else pressedKeyCounts[ptr.keyId] = oldC
                                                                ptr.keyId = moved.first
                                                                ptr.rect = moved.second
                                                                ptr.label = if (latestShiftState > 0) moved.first.uppercase() else moved.first
                                                                pressedKeyCounts[ptr.keyId] = (pressedKeyCounts[ptr.keyId] ?: 0) + 1
                                                                previewController.show(
                                                                    pointerId = change.id,
                                                                    label = ptr.label,
                                                                    centerX = ptr.rect.center.x,
                                                                    topY = ptr.rect.top
                                                                )
                                                                val newAlts = KeyboardLayouts.altChars[ptr.keyId.lowercase()].orEmpty()
                                                                if (newAlts.isNotEmpty()) {
                                                                    ptr.holdJob = scope.launch {
                                                                        delay(360L)
                                                                        ptr.altsTriggered = true
                                                                        val c = (pressedKeyCounts[ptr.keyId] ?: 1) - 1
                                                                        if (c <= 0) pressedKeyCounts.remove(ptr.keyId) else pressedKeyCounts[ptr.keyId] = c
                                                                        previewController.hide(change.id)
                                                                        gridAltsSelectedIdx = 0
                                                                        gridAltsKey = ptr.keyId
                                                                        onHaptic()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else if (!change.pressed) {
                                                        val rootPos = gridRootOrigin + change.position
                                                        pointerQueue.recordUp(rootPos, nowMs)
                                                        // LineageOS FIFO guarantee: commit all older active pointers before this one
                                                        pointerQueue.releaseOlderThan(change.id) { older ->
                                                            releaseVisualForPointer(older)
                                                            commitPointerKey(older, older.lastPos)
                                                        }
                                                        val ptr = pointerQueue.remove(change.id)
                                                        if (ptr != null) {
                                                            releaseVisualForPointer(ptr)
                                                            if (ptr.altsTriggered) {
                                                                val alts = KeyboardLayouts.altChars[ptr.keyId.lowercase()].orEmpty()
                                                                val rawAlt = alts.getOrNull(gridAltsSelectedIdx)
                                                                if (rawAlt != null) {
                                                                    val outAlt = if (latestShiftState > 0) rawAlt.uppercase() else rawAlt
                                                                    latestOnCharWithPos(outAlt, null, null)
                                                                }
                                                                gridAltsKey = null
                                                            } else if (!ptr.committed) {
                                                                val w = ptr.rect.width.coerceAtLeast(1f)
                                                                val h = ptr.rect.height.coerceAtLeast(1f)
                                                                val inBounds = rootPos.x in (ptr.rect.left - w * 0.45f)..(ptr.rect.right + w * 0.45f) &&
                                                                    rootPos.y in (ptr.rect.top - h * 0.6f)..(ptr.rect.bottom + h * 0.6f)
                                                                if (inBounds) {
                                                                    commitPointerKey(ptr, rootPos)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else Modifier
                            ),
                        verticalArrangement = Arrangement.spacedBy(config.verticalGapDp.dp)
                    ) {
                        rows.forEach { rowKeys ->
                            val letterKeysInRow = rowKeys.count { it != "SHIFT" && it != "BACK" && it != "ALT_SYM" && it != "ALT_123" }
                            val funcWeight = if (rowKeys.contains("SHIFT") || rowKeys.contains("ALT_SYM") || rowKeys.contains("ALT_123")) {
                                ((maxRowLen - letterKeysInRow) / 2f).coerceIn(1.35f, 1.85f)
                            } else 1.45f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(config.horizontalGapDp.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowKeys.forEach { key ->
                                    when (key) {
                                        "SHIFT" -> {
                                            FuncKeyCap(
                                                modifier = Modifier.weight(funcWeight),
                                                height = config.keyHeightDp.dp,
                                                radius = config.funcCornerRadiusDp.dp,
                                                config = config,
                                                palette = palette,
                                                isActive = shiftState > 0,
                                                onClick = onShiftTap
                                            ) {
                                                Icon(
                                                    imageVector = shiftIcon,
                                                    contentDescription = "shift",
                                                    tint = if (shiftState > 0) palette.accentText else palette.keyText,
                                                    modifier = Modifier.size(23.dp)
                                                )
                                            }
                                        }

                                        "BACK" -> {
                                            BackspaceKeyCap(
                                                modifier = Modifier.weight(funcWeight),
                                                height = config.keyHeightDp.dp,
                                                radius = config.funcCornerRadiusDp.dp,
                                                config = config,
                                                palette = palette,
                                                onBackspace = onBackspace,
                                                onDeleteWord = onDeleteWord
                                            )
                                        }

                                        "ALT_SYM" -> {
                                            FuncKeyCap(
                                                modifier = Modifier.weight(funcWeight),
                                                height = config.keyHeightDp.dp,
                                                radius = config.funcCornerRadiusDp.dp,
                                                config = config,
                                                palette = palette,
                                                onClick = { onHaptic(); layer = KeyboardLayer.SYM2 }
                                            ) {
                                                Text(
                                                    text = "#+=",
                                                    color = palette.keyText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = fontFamily,
                                                    letterSpacing = 0.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        "ALT_123" -> {
                                            FuncKeyCap(
                                                modifier = Modifier.weight(funcWeight),
                                                height = config.keyHeightDp.dp,
                                                radius = config.funcCornerRadiusDp.dp,
                                                config = config,
                                                palette = palette,
                                                onClick = { onHaptic(); layer = KeyboardLayer.SYM1 }
                                            ) {
                                                Text(
                                                    text = "123",
                                                    color = palette.keyText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = fontFamily,
                                                    letterSpacing = 0.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        else -> {
                                            val label = if (currentLayer == KeyboardLayer.LETTERS && shiftState > 0) {
                                                key.uppercase()
                                            } else key
                                            val alts = KeyboardLayouts.altChars[key.lowercase()].orEmpty()

                                            CharKeyCap(
                                                modifier = Modifier.weight(1f),
                                                label = label,
                                                alts = alts,
                                                uppercaseAlts = currentLayer == KeyboardLayer.LETTERS && shiftState > 0,
                                                height = config.keyHeightDp.dp,
                                                radius = config.cornerRadiusDp.dp,
                                                fontSizeSp = uniformFontSp,
                                                fontFamily = fontFamily,
                                                config = config,
                                                palette = palette,
                                                previewController = previewController,
                                                externalTouchEnabled = useUnifiedGrid,
                                                externalPressed = (pressedKeyCounts[key] ?: 0) > 0,
                                                externalShowAlts = gridAltsKey == key,
                                                externalAltsSelectedIdx = if (gridAltsKey == key) gridAltsSelectedIdx else -1,
                                                onDismissExternalAlts = { gridAltsKey = null },
                                                onBoundsChanged = { rect ->
                                                    if (currentLayer == KeyboardLayer.LETTERS) {
                                                        keyDetector.updateKeyBounds(key, rect)
                                                        val (centers, avgW) = keyDetector.snapshotGeometry()
                                                        if (centers.size >= 20) {
                                                            latestOnGeometryUpdate(centers, avgW)
                                                        }
                                                    }
                                                },
                                                onCommit = onChar
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // Bottom Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(config.horizontalGapDp.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            config.bottomRowOrder.forEach { btnId ->
                                when (btnId) {
                                    "123" -> {
                                        Sym123KeyCap(
                                            modifier = Modifier.weight(config.symKeyWeight),
                                            label = if (currentLayer == KeyboardLayer.LETTERS) "123" else "abc",
                                            isLettersLayer = currentLayer == KeyboardLayer.LETTERS,
                                            height = config.keyHeightDp.dp,
                                            radius = config.funcCornerRadiusDp.dp,
                                            fontFamily = fontFamily,
                                            config = config,
                                            palette = palette,
                                            quickSymbols = quickSymbols,
                                            showOverlay = showQuickSymOverlay,
                                            selectedIdx = quickSymSelectedIdx,
                                            overlayBounds = quickSymOverlayBounds,
                                            onShowOverlayChange = { showQuickSymOverlay = it },
                                            onSelectedIdxChange = { quickSymSelectedIdx = it },
                                            onAnchorXChange = { quickSymAnchorX = it },
                                            onHaptic = onHaptic,
                                            onTapSwitch = {
                                                layer = if (currentLayer == KeyboardLayer.LETTERS) KeyboardLayer.SYM1 else KeyboardLayer.LETTERS
                                            },
                                            onCommitSymbol = onChar
                                        )
                                    }

                                    "COMMA" -> {
                                        CharKeyCap(
                                            modifier = Modifier.weight(config.punctWeight),
                                            label = ",",
                                            alts = listOf(";"),
                                            height = config.keyHeightDp.dp,
                                            radius = config.funcCornerRadiusDp.dp,
                                            fontSizeSp = uniformFontSp,
                                            fontFamily = fontFamily,
                                            config = config,
                                            palette = palette,
                                            previewController = previewController,
                                            onCommit = onChar
                                        )
                                    }

                                    "PERIOD" -> {
                                        CharKeyCap(
                                            modifier = Modifier.weight(config.punctWeight),
                                            label = ".",
                                            alts = listOf("?", "!", ":", "…"),
                                            height = config.keyHeightDp.dp,
                                            radius = config.funcCornerRadiusDp.dp,
                                            fontSizeSp = uniformFontSp,
                                            fontFamily = fontFamily,
                                            config = config,
                                            palette = palette,
                                            previewController = previewController,
                                            onCommit = onChar
                                        )
                                    }

                                    "GLOBE" -> if (config.showGlobeKey) {
                                        Box(modifier = Modifier.weight(1.1f)) {
                                            FuncKeyCap(
                                                modifier = Modifier.fillMaxWidth(),
                                                height = config.keyHeightDp.dp,
                                                radius = config.funcCornerRadiusDp.dp,
                                                config = config,
                                                palette = palette,
                                                onClick = { onCycleLanguage(1) },
                                                onLongClick = { onHaptic(); showLangMenu = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Language,
                                                    contentDescription = "language",
                                                    tint = palette.keyText,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }

                                            if (showLangMenu) {
                                                val menuOffsetY = with(density) { (-64).dp.roundToPx() }
                                                Popup(
                                                    alignment = Alignment.TopStart,
                                                    offset = IntOffset(0, menuOffsetY),
                                                    onDismissRequest = { showLangMenu = false },
                                                    properties = PopupProperties(focusable = false, clippingEnabled = false)
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(palette.popupBackground)
                                                            .border(0.8.dp, palette.divider, RoundedCornerShape(14.dp))
                                                            .padding(6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        config.enabledLanguages.forEach { code ->
                                                            val info = KeyboardLayouts.languages[code] ?: return@forEach
                                                            val active = code == config.activeLanguage
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(if (active) palette.accent else Color.Transparent)
                                                                    .pointerInput(code) {
                                                                        detectTapGestures(onTap = {
                                                                            onSelectLanguage(code)
                                                                            showLangMenu = false
                                                                        })
                                                                    }
                                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                                            ) {
                                                                Text(
                                                                    text = info.displayName.lowercase(),
                                                                    color = if (active) palette.accentText else palette.popupText,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "EMOJI" -> if (config.showEmojiKey) {
                                        FuncKeyCap(
                                            modifier = Modifier.weight(1.1f),
                                            height = config.keyHeightDp.dp,
                                            radius = config.funcCornerRadiusDp.dp,
                                            config = config,
                                            palette = palette,
                                            onClick = { onHaptic(); layer = KeyboardLayer.EMOJI }
                                        ) {
                                            Icon(
                                                imageVector = KeyboardIcons.Emoji,
                                                contentDescription = "emoji",
                                                tint = palette.keyText,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    "SPACE" -> {
                                        SpacebarKeyCap(
                                            modifier = Modifier.weight(config.spaceWeight),
                                            label = if (config.showSpacebarLabel) langLayout.spaceLabel else "",
                                            height = config.keyHeightDp.dp,
                                            radius = config.cornerRadiusDp.dp,
                                            fontFamily = fontFamily,
                                            config = config,
                                            palette = palette,
                                            onSpace = onSpace,
                                            onSwipeLanguage = onCycleLanguage
                                        )
                                    }

                                    "ENTER" -> {
                                        FuncKeyCap(
                                            modifier = Modifier.weight(config.enterWeight),
                                            height = config.keyHeightDp.dp,
                                            radius = config.funcCornerRadiusDp.dp,
                                            config = config,
                                            palette = palette,
                                            isActive = true,
                                            onClick = onEnter
                                        ) {
                                            Icon(
                                                imageVector = KeyboardIcons.Return,
                                                contentDescription = "return",
                                                tint = palette.accentText,
                                                modifier = Modifier.size(23.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Isolated Key Preview Overlay (reads active state only inside this composable)
        if (config.showKeyPopup) {
            KeyPreviewOverlay(
                controller = previewController,
                palette = palette,
                fontFamily = fontFamily,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(40f)
            )
        }

        // In-IME Quick Symbol Overlay
        AnimatedVisibility(
            visible = showQuickSymOverlay && quickSymbols.isNotEmpty(),
            enter = fadeIn(tween(80)) + scaleIn(tween(110), initialScale = 0.88f),
            exit = fadeOut(tween(70)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .zIndex(50f)
                .padding(
                    start = with(density) { quickSymAnchorX.toDp().coerceAtLeast(config.sidePaddingDp.dp) },
                    bottom = (config.bottomPaddingDp + config.keyHeightDp + config.verticalGapDp + 8f).dp
                )
        ) {
            val cols = quickSymbols.size.coerceIn(1, 8)
            val rowsCount = ((quickSymbols.size + cols - 1) / cols).coerceAtLeast(1)
            val cellSizeDp = 42.dp
            val gapDp = 4.dp
            val padDp = 6.dp
            Column(
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        quickSymOverlayBounds = coords.boundsInRoot()
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.popupBackground)
                    .border(0.75.dp, palette.divider, RoundedCornerShape(14.dp))
                    .padding(padDp),
                verticalArrangement = Arrangement.spacedBy(gapDp)
            ) {
                for (r in 0 until rowsCount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gapDp)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            if (idx < quickSymbols.size) {
                                val isSel = idx == quickSymSelectedIdx
                                Box(
                                    modifier = Modifier
                                        .size(cellSizeDp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSel) palette.accent else palette.funcKeyBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = quickSymbols[idx],
                                        color = if (isSel) palette.accentText else palette.popupText,
                                        fontSize = 20.sp,
                                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                        fontFamily = fontFamily,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(cellSizeDp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyPreviewOverlay(
    controller: KeyPreviewController,
    palette: KeyboardPalette,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val boxW = 48.dp
    val boxH = 54.dp
    val boxWPx = with(density) { boxW.toPx() }
    val boxHPx = with(density) { boxH.toPx() }
    val minXPx = with(density) { 4.dp.toPx() }
    val minYPx = with(density) { 2.dp.toPx() }
    val gapYPx = with(density) { 6.dp.toPx() }
    val shape = remember { RoundedCornerShape(11.dp) }

    val preview = controller.active
    var lastLabel by remember { mutableStateOf("") }
    if (preview != null) {
        lastLabel = preview.label
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = controller.active
                if (p == null) {
                    alpha = 0f
                } else {
                    alpha = 1f
                    translationX = (p.centerX - boxWPx / 2f).coerceAtLeast(minXPx)
                    translationY = (p.topY - boxHPx - gapYPx).coerceAtLeast(minYPx)
                }
            }
            .width(boxW)
            .height(boxH)
            .background(palette.popupBackground, shape)
            .border(0.8.dp, palette.divider, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = lastLabel,
            color = palette.popupText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TopUtilityBar(
    buttons: List<String>,
    activeLayer: KeyboardLayer,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    onButtonClick: (String) -> Unit
) {
    var confirmedId by remember { mutableStateOf<String?>(null) }
    var confirmTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(confirmTick) {
        if (confirmedId != null) {
            delay(600L)
            confirmedId = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(config.horizontalGapDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { id ->
            val selected = (id == "EMOJI" && activeLayer == KeyboardLayer.EMOJI) ||
                (id == "CLIPBOARD" && activeLayer == KeyboardLayer.CLIPBOARD)
            val isConfirmed = confirmedId == id
            val supportsConfirm = id == "SELECT_ALL" || id == "COPY" || id == "PASTE" || id == "CUT" || id == "UNDO"

            val icon: ImageVector = when (id) {
                "EMOJI" -> KeyboardIcons.Emoji
                "CLIPBOARD" -> KeyboardIcons.Clipboard
                "SELECT_ALL" -> KeyboardIcons.SelectAll
                "COPY" -> KeyboardIcons.Copy
                "PASTE" -> KeyboardIcons.Paste
                "CUT" -> Icons.Rounded.ContentCut
                "UNDO" -> Icons.AutoMirrored.Rounded.Undo
                else -> Icons.Rounded.MoreHoriz
            }

            var pressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessHigh),
                label = "TopBtnScale"
            )
            val shape = remember(config.funcCornerRadiusDp) { RoundedCornerShape(config.funcCornerRadiusDp.dp) }
            val bg by animateColorAsState(
                targetValue = when {
                    selected -> palette.accent
                    pressed -> palette.funcKeyPressedBackground
                    else -> palette.funcKeyBackground
                },
                animationSpec = tween(80),
                label = "TopBtnBg"
            )

            val iconAlpha by animateFloatAsState(
                targetValue = if (isConfirmed) 0f else 1f,
                animationSpec = tween(130),
                label = "TopBtnIconAlpha"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (isConfirmed) 0.72f else 1f,
                animationSpec = tween(130),
                label = "TopBtnIconScale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                if (config.showKeyShadow) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 1.dp)
                            .background(palette.keyShadow, shape)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg, shape)
                        .then(
                            if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                                Modifier.border(0.75.dp, palette.keyBorder, shape)
                            } else Modifier
                        )
                        .pointerInput(id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                pressed = true
                                onButtonClick(id)
                                if (supportsConfirm) {
                                    confirmedId = id
                                    confirmTick++
                                }
                                waitForUpOrCancellation()
                                pressed = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = id.lowercase(),
                        tint = if (selected) palette.accentText else palette.keyText,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                alpha = iconAlpha
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    if (supportsConfirm) {
                        AnimatedCheckCircle(
                            visible = isConfirmed,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedCheckCircle(
    visible: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val circleProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    val checkProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    val overallAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val overallScale = remember { androidx.compose.animation.core.Animatable(0.85f) }

    LaunchedEffect(visible) {
        if (visible) {
            circleProgress.snapTo(0f)
            checkProgress.snapTo(0f)
            overallAlpha.snapTo(1f)
            overallScale.snapTo(0.85f)
            kotlinx.coroutines.coroutineScope {
                launch { overallScale.animateTo(1f, tween(170, easing = FastOutSlowInEasing)) }
                circleProgress.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            }
            checkProgress.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
        } else {
            kotlinx.coroutines.coroutineScope {
                launch { overallScale.animateTo(0.85f, tween(140, easing = FastOutSlowInEasing)) }
                overallAlpha.animateTo(0f, tween(140))
            }
            circleProgress.snapTo(0f)
            checkProgress.snapTo(0f)
        }
    }

    val checkSrcPath = remember { Path() }
    val checkDstPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(
        modifier = modifier.graphicsLayer {
            alpha = overallAlpha.value
            scaleX = overallScale.value
            scaleY = overallScale.value
        }
    ) {
        val strokePx = (size.minDimension / 24f) * 2.35f
        val inset = strokePx * 1.4f
        val arcSize = size.minDimension - inset * 2f

        val cp = circleProgress.value
        if (cp > 0f) {
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * cp,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        val kp = checkProgress.value
        if (kp > 0f) {
            val w = size.width
            val h = size.height
            checkSrcPath.reset()
            checkSrcPath.moveTo(w * 0.33f, h * 0.51f)
            checkSrcPath.lineTo(w * 0.45f, h * 0.63f)
            checkSrcPath.lineTo(w * 0.68f, h * 0.39f)

            checkDstPath.reset()
            pathMeasure.setPath(checkSrcPath, false)
            pathMeasure.getSegment(0f, pathMeasure.length * kp, checkDstPath, true)

            drawPath(
                path = checkDstPath,
                color = tint,
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
private fun ClipboardPanel(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    clips: List<String>,
    onPaste: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBackToLetters: () -> Unit
) {
    val rowCount = if (config.showNumberRow) 5 else 4
    val totalHeight = (config.keyHeightDp * rowCount + config.verticalGapDp * (rowCount - 1)).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "clipboard",
                color = palette.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (clips.isNotEmpty()) {
                    FuncKeyCap(
                        modifier = Modifier.width(56.dp),
                        height = 30.dp,
                        radius = config.funcCornerRadiusDp.dp,
                        config = config,
                        palette = palette,
                        onClick = onClearAll
                    ) {
                        Text("clear", color = palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                FuncKeyCap(
                    modifier = Modifier.width(56.dp),
                    height = 30.dp,
                    radius = config.funcCornerRadiusDp.dp,
                    config = config,
                    palette = palette,
                    onClick = onBackToLetters
                ) {
                    Text("abc", color = palette.keyText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("clipboard is empty", color = palette.secondaryText, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(clips, key = { it }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.keyBackground)
                            .pointerInput(item) {
                                detectTapGestures(onTap = { onPaste(item) })
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item,
                            color = palette.keyText,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "remove",
                            tint = palette.secondaryText,
                            modifier = Modifier
                                .size(18.dp)
                                .pointerInput(item) {
                                    detectTapGestures(onTap = { onRemove(item) })
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharKeyCap(
    modifier: Modifier,
    label: String,
    alts: List<String>,
    uppercaseAlts: Boolean = false,
    height: Dp,
    radius: Dp,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    previewController: KeyPreviewController,
    externalTouchEnabled: Boolean = false,
    externalPressed: Boolean = false,
    externalShowAlts: Boolean = false,
    externalAltsSelectedIdx: Int = -1,
    onDismissExternalAlts: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit = {},
    onCommit: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var showAlts by remember { mutableStateOf(false) }
    var keyRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var keySizePx by remember { mutableStateOf(IntSize.Zero) }
    val shape = remember(radius) { RoundedCornerShape(radius) }

    val latestLabel by rememberUpdatedState(label)
    val latestAlts by rememberUpdatedState(alts)
    val latestInstantCommit by rememberUpdatedState(config.instantKeyCommit && alts.isEmpty())
    val latestOnCommit by rememberUpdatedState(onCommit)

    val effectivePressed = if (externalTouchEnabled) externalPressed else pressed
    val effectiveShowAlts = if (externalTouchEnabled) externalShowAlts else showAlts
    val scale = if (effectivePressed) 0.95f else 1f
    val bg = if (effectivePressed) palette.keyPressedBackground else palette.keyBackground

    Box(
        modifier = modifier
            .height(height)
            .onGloballyPositioned { coords ->
                keyRootOrigin = coords.positionInRoot()
                keySizePx = coords.size
                onBoundsChanged(coords.boundsInRoot())
            }
            .then(
                if (!externalTouchEnabled) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier
            )
    ) {
        if (!externalTouchEnabled && config.showKeyShadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .background(palette.keyShadow, shape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!externalTouchEnabled) Modifier.background(bg, shape) else Modifier
                )
                .then(
                    if (!externalTouchEnabled && config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                        Modifier.border(0.75.dp, palette.keyBorder, shape)
                    } else Modifier
                )
                .then(
                    if (!externalTouchEnabled) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        previewController.show(
                            pointerId = down.id,
                            label = latestLabel,
                            centerX = keyRootOrigin.x + keySizePx.width / 2f,
                            topY = keyRootOrigin.y
                        )

                        if (latestInstantCommit) {
                            latestOnCommit(latestLabel)
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                pressed = false
                                previewController.hide(down.id)
                            }
                        } else {
                            var altsTriggered = false
                            val holdJob = if (latestAlts.isNotEmpty()) {
                                scope.launch {
                                    delay(360L)
                                    altsTriggered = true
                                    pressed = false
                                    previewController.hide(down.id)
                                    showAlts = true
                                }
                            } else null

                            var lastPos = down.position
                            var cancelled = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    lastPos = change.position
                                    if (altsTriggered) {
                                        cancelled = true
                                        break
                                    }
                                    if (!change.pressed) break
                                }
                            } finally {
                                holdJob?.cancel()
                                pressed = false
                                previewController.hide(down.id)
                            }

                            if (!cancelled && !altsTriggered) {
                                val w = keySizePx.width.toFloat().coerceAtLeast(1f)
                                val h = keySizePx.height.toFloat().coerceAtLeast(1f)
                                val inBounds = lastPos.x in (-w * 0.45f)..(w * 1.45f) &&
                                    lastPos.y in (-h * 0.6f)..(h * 1.6f)
                                if (inBounds) {
                                    latestOnCommit(latestLabel)
                                }
                            }
                        }
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = palette.keyText,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }

        if (effectiveShowAlts && alts.isNotEmpty()) {
            val altOffsetY = with(density) { (-52).dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, altOffsetY),
                onDismissRequest = {
                    showAlts = false
                    onDismissExternalAlts()
                },
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.popupBackground)
                        .border(0.8.dp, palette.divider, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    alts.forEachIndexed { idx, rawAlt ->
                        val alt = if (uppercaseAlts) rawAlt.uppercase() else rawAlt
                        val isSel = externalTouchEnabled && idx == externalAltsSelectedIdx
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) palette.accent else Color.Transparent)
                                .pointerInput(alt) {
                                    detectTapGestures(onTap = {
                                        latestOnCommit(alt)
                                        showAlts = false
                                        onDismissExternalAlts()
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = alt,
                                color = if (isSel) palette.accentText else palette.popupText,
                                fontSize = 20.sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sym123KeyCap(
    modifier: Modifier,
    label: String,
    isLettersLayer: Boolean,
    height: Dp,
    radius: Dp,
    fontFamily: FontFamily,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    quickSymbols: List<String>,
    showOverlay: Boolean,
    selectedIdx: Int,
    overlayBounds: Rect?,
    onShowOverlayChange: (Boolean) -> Unit,
    onSelectedIdxChange: (Int) -> Unit,
    onAnchorXChange: (Float) -> Unit,
    onHaptic: () -> Unit,
    onTapSwitch: () -> Unit,
    onCommitSymbol: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val shape = remember(radius) { RoundedCornerShape(radius) }

    val latestShowOverlay by rememberUpdatedState(showOverlay)
    val latestSelectedIdx by rememberUpdatedState(selectedIdx)
    val latestOverlayBounds by rememberUpdatedState(overlayBounds)

    var pressed by remember { mutableStateOf(false) }
    var keyRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var lastTrackedRootPos by remember { mutableStateOf(Offset.Zero) }

    val latestIsLettersLayer by rememberUpdatedState(isLettersLayer)
    val latestPopupEnabled by rememberUpdatedState(config.quickSymbolPopupEnabled)
    val latestDelayMs by rememberUpdatedState(config.quickSymbolPopupDelayMs)
    val latestQuickSymbols by rememberUpdatedState(quickSymbols)
    val latestOnTapSwitch by rememberUpdatedState(onTapSwitch)
    val latestOnCommitSymbol by rememberUpdatedState(onCommitSymbol)

    fun computeSelection(rootPos: Offset, rect: Rect?, syms: List<String>): Int {
        if (rect == null || syms.isEmpty()) return -1
        val curCols = syms.size.coerceIn(1, 8)
        val curRows = ((syms.size + curCols - 1) / curCols).coerceAtLeast(1)
        val padPx = with(density) { 6.dp.toPx() }
        val slackX = with(density) { 14.dp.toPx() }
        val slackY = with(density) { 20.dp.toPx() }
        return if (
            rootPos.x in (rect.left - slackX)..(rect.right + slackX) &&
            rootPos.y in (rect.top - slackY)..(rect.bottom + slackY)
        ) {
            val innerW = (rect.width - padPx * 2).coerceAtLeast(1f)
            val innerH = (rect.height - padPx * 2).coerceAtLeast(1f)
            val c = ((rootPos.x - rect.left - padPx) / innerW * curCols).toInt().coerceIn(0, curCols - 1)
            val r = ((rootPos.y - rect.top - padPx) / innerH * curRows).toInt().coerceIn(0, curRows - 1)
            val idx = r * curCols + c
            if (idx in syms.indices) idx else -1
        } else {
            -1
        }
    }

    LaunchedEffect(showOverlay, overlayBounds) {
        if (showOverlay && overlayBounds != null) {
            val newIdx = computeSelection(lastTrackedRootPos, overlayBounds, latestQuickSymbols)
            if (newIdx != latestSelectedIdx) {
                onSelectedIdxChange(newIdx)
                if (newIdx >= 0) onHaptic()
            }
        }
    }

    val scale = if (pressed) 0.94f else 1f
    val bg = if (pressed) palette.funcKeyPressedBackground else palette.funcKeyBackground

    Box(
        modifier = modifier
            .height(height)
            .onGloballyPositioned { coords ->
                keyRootOrigin = coords.positionInRoot()
                onAnchorXChange(keyRootOrigin.x)
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        if (config.showKeyShadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .background(palette.keyShadow, shape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg, shape)
                .then(
                    if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                        Modifier.border(0.75.dp, palette.keyBorder, shape)
                    } else Modifier
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val syms = latestQuickSymbols
                        val enablePopup = latestIsLettersLayer && latestPopupEnabled && syms.isNotEmpty()
                        if (!enablePopup) {
                            try {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    onHaptic()
                                    latestOnTapSwitch()
                                }
                            } finally {
                                pressed = false
                            }
                        } else {
                            onShowOverlayChange(false)
                            onSelectedIdxChange(-1)
                            val downRootPos = keyRootOrigin + down.position
                            lastTrackedRootPos = downRootPos

                            val holdJob = scope.launch {
                                delay(latestDelayMs.toLong())
                                onHaptic()
                                onShowOverlayChange(true)
                                val newIdx = computeSelection(lastTrackedRootPos, latestOverlayBounds, syms)
                                if (newIdx != latestSelectedIdx) {
                                    onSelectedIdxChange(newIdx)
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) break
                                    lastTrackedRootPos = keyRootOrigin + change.position
                                    if (latestShowOverlay) {
                                        val newIdx = computeSelection(lastTrackedRootPos, latestOverlayBounds, syms)
                                        if (newIdx != latestSelectedIdx) {
                                            onSelectedIdxChange(newIdx)
                                            if (newIdx >= 0) onHaptic()
                                        }
                                    }
                                }
                                val wasOverlay = latestShowOverlay
                                val chosenIdx = latestSelectedIdx
                                val chosen = if (wasOverlay && chosenIdx in syms.indices) syms[chosenIdx] else null
                                if (!wasOverlay) {
                                    onHaptic()
                                    latestOnTapSwitch()
                                } else if (chosen != null) {
                                    latestOnCommitSymbol(chosen)
                                } else if ((lastTrackedRootPos - downRootPos).getDistance() < with(density) { 18.dp.toPx() }) {
                                    latestOnTapSwitch()
                                }
                            } finally {
                                holdJob.cancel()
                                onShowOverlayChange(false)
                                onSelectedIdxChange(-1)
                                pressed = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = palette.keyText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamily,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun FuncKeyCap(
    modifier: Modifier,
    height: Dp,
    radius: Dp,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    isActive: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val scale = if (pressed) 0.94f else 1f
    val bg = when {
        isActive -> palette.accent
        pressed -> palette.funcKeyPressedBackground
        else -> palette.funcKeyBackground
    }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)

    Box(modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }) {
        if (config.showKeyShadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .background(palette.keyShadow, shape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg, shape)
                .then(
                    if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                        Modifier.border(0.75.dp, palette.keyBorder, shape)
                    } else Modifier
                )
                .pointerInput(onLongClick != null) {
                    if (onLongClick == null) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            pressed = true
                            latestOnClick()
                            waitForUpOrCancellation()
                            pressed = false
                        }
                    } else {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            var longTriggered = false
                            val holdJob = scope.launch {
                                delay(350L)
                                longTriggered = true
                                pressed = false
                                latestOnLongClick?.invoke()
                            }
                            try {
                                val up = waitForUpOrCancellation()
                                holdJob.cancel()
                                if (up != null && !longTriggered) {
                                    latestOnClick()
                                }
                            } finally {
                                holdJob.cancel()
                                pressed = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
private fun BackspaceKeyCap(
    modifier: Modifier,
    height: Dp,
    radius: Dp,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val scale = if (pressed) 0.94f else 1f
    val bg = if (pressed) palette.funcKeyPressedBackground else palette.funcKeyBackground

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(320)
            var elapsed = 320L
            while (pressed) {
                if (elapsed < 950L) {
                    onBackspace()
                    delay(35)
                    elapsed += 35L
                } else {
                    onDeleteWord()
                    delay(65)
                }
            }
        }
    }

    Box(modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }) {
        if (config.showKeyShadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .background(palette.keyShadow, shape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg, shape)
                .then(
                    if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                        Modifier.border(0.75.dp, palette.keyBorder, shape)
                    } else Modifier
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        onBackspace()
                        waitForUpOrCancellation()
                        pressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = KeyboardIcons.Backspace,
                contentDescription = "backspace",
                tint = palette.keyText,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun SpacebarKeyCap(
    modifier: Modifier,
    label: String,
    height: Dp,
    radius: Dp,
    fontFamily: FontFamily,
    config: KeyboardConfig,
    palette: KeyboardPalette,
    onSpace: () -> Unit,
    onSwipeLanguage: (Int) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val latestSwipeEnabled by rememberUpdatedState(config.spacebarSwipeLang)
    val latestOnSpace by rememberUpdatedState(onSpace)
    val latestOnSwipeLanguage by rememberUpdatedState(onSwipeLanguage)
    val swipeThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    val scale = if (pressed) 0.97f else 1f
    val bg = if (pressed) palette.keyPressedBackground else palette.keyBackground

    Box(modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }) {
        if (config.showKeyShadow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 1.dp)
                    .background(palette.keyShadow, shape)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg, shape)
                .then(
                    if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                        Modifier.border(0.75.dp, palette.keyBorder, shape)
                    } else Modifier
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        var totalDx = 0f
                        var totalDy = 0f
                        var swiped = false
                        var prevPos = down.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!swiped) {
                                        latestOnSpace()
                                    }
                                    break
                                }
                                val delta = change.position - prevPos
                                prevPos = change.position
                                totalDx += delta.x
                                totalDy += delta.y
                                if (latestSwipeEnabled && !swiped && hypot(totalDx, totalDy) >= swipeThresholdPx) {
                                    swiped = true
                                    pressed = false
                                    val dir = if (abs(totalDx) >= abs(totalDy)) {
                                        if (totalDx > 0f) 1 else -1
                                    } else {
                                        if (totalDy < 0f) 1 else -1
                                    }
                                    latestOnSwipeLanguage(dir)
                                    change.consume()
                                }
                            }
                        } finally {
                            pressed = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (label.isNotEmpty()) {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                    label = "SpaceLangLabel"
                ) { text ->
                    Text(
                        text = text,
                        color = palette.secondaryText,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosEmojiPickerPanel(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    recentEmojis: List<String>,
    emojiFontFamily: FontFamily,
    onEmojiClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onBackToLetters: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categories = KeyboardLayouts.emojiCategories
    val totalPages = 1 + categories.size
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { totalPages })

    val categoryIcons = listOf(
        Icons.Rounded.Schedule,
        Icons.Rounded.Mood,
        Icons.Rounded.Pets,
        Icons.Rounded.Restaurant,
        Icons.Rounded.SportsSoccer,
        Icons.Rounded.DirectionsCar,
        Icons.Rounded.Lightbulb,
        Icons.Rounded.FavoriteBorder
    )

    val pageTitle = if (pagerState.currentPage == 0) {
        "recent"
    } else {
        categories[pagerState.currentPage - 1].title
    }

    val rowCount = if (config.showNumberRow) 5 else 4
    val panelHeight = (config.keyHeightDp * rowCount + config.verticalGapDp * (rowCount - 1)).dp
    val emojiTextStyle = LocalTextStyle.current.copy(
        fontFamily = emojiFontFamily,
        platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = pageTitle,
            color = palette.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val emojis = if (page == 0) recentEmojis else categories[page - 1].emojis
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(emojis, key = { idx, item -> "${page}_${idx}_$item" }) { _, emoji ->
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(emoji) {
                                detectTapGestures(onTap = { onEmojiClick(emoji) })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 26.sp,
                            style = emojiTextStyle
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FuncKeyCap(
                modifier = Modifier.width(48.dp),
                height = 34.dp,
                radius = config.funcCornerRadiusDp.dp,
                config = config,
                palette = palette,
                onClick = onBackToLetters
            ) {
                Text("abc", color = palette.keyText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                categoryIcons.forEachIndexed { idx, vec ->
                    val active = idx == pagerState.currentPage
                    val bg by animateColorAsState(
                        targetValue = if (active) palette.keyBackground else Color.Transparent,
                        animationSpec = tween(120),
                        label = "EmojiTabBg"
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .pointerInput(idx) {
                                detectTapGestures(onTap = {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = vec,
                            contentDescription = if (idx == 0) "recent" else categories[idx - 1].title,
                            tint = if (active) palette.keyText else palette.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            BackspaceKeyCap(
                modifier = Modifier.width(48.dp),
                height = 34.dp,
                radius = config.funcCornerRadiusDp.dp,
                config = config,
                palette = palette,
                onBackspace = onBackspace,
                onDeleteWord = onDeleteWord
            )
        }
    }
}


