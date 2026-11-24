package com.serenetype.keyboard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.data.SettingsRepository
import com.serenetype.keyboard.ime.KeyboardIcons
import com.serenetype.keyboard.theme.KeyboardPalette
import kotlinx.coroutines.launch

private val singleCharPresets = listOf(
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
    "-", "/", ":", ";", "(", ")", "$", "&", "@", "\"",
    ".", ",", "?", "!", "'", "[", "]", "{", "}", "#",
    "%", "^", "*", "+", "=", "_", "\\", "|", "~", "<",
    ">", "€", "£", "¥", "₽", "₴", "•", "`", "√", "π",
    "÷", "×", "¶", "∆", "°", "№", "§", "±", "≠", "≈", "—"
)

private enum class SymTab(val label: String) {
    PAGE_123("123"),
    PAGE_SYM("#+="),
    HOLD_123("hold 123")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveSymbolEditor(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    repository: SettingsRepository
) {
    val scope = rememberCoroutineScope()

    fun parse(s: String): MutableList<String> =
        s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()

    var tab by remember { mutableStateOf(SymTab.PAGE_123) }

    var s1r1 by remember(config.sym1Row1) { mutableStateOf(parse(config.sym1Row1)) }
    var s1r2 by remember(config.sym1Row2) { mutableStateOf(parse(config.sym1Row2)) }
    var s1r3 by remember(config.sym1Row3) { mutableStateOf(parse(config.sym1Row3)) }

    var s2r1 by remember(config.sym2Row1) { mutableStateOf(parse(config.sym2Row1)) }
    var s2r2 by remember(config.sym2Row2) { mutableStateOf(parse(config.sym2Row2)) }
    var s2r3 by remember(config.sym2Row3) { mutableStateOf(parse(config.sym2Row3)) }

    var quickEnabled by remember(config.quickSymbolPopupEnabled) { mutableStateOf(config.quickSymbolPopupEnabled) }
    var quickList by remember(config.quickSymbolPopupChars) { mutableStateOf(parse(config.quickSymbolPopupChars)) }
    var quickDelay by remember(config.quickSymbolPopupDelayMs) { mutableFloatStateOf(config.quickSymbolPopupDelayMs.toFloat()) }
    var delayExpanded by remember { mutableStateOf(false) }

    // Dialog state: pair(rowIndex 0..5 for pages, 6 for quickList; itemIndex or -1 to add to quickList)
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dialogInput by remember { mutableStateOf("") }

    fun persistPages(
        r11: List<String> = s1r1,
        r12: List<String> = s1r2,
        r13: List<String> = s1r3,
        r21: List<String> = s2r1,
        r22: List<String> = s2r2,
        r23: List<String> = s2r3
    ) {
        scope.launch {
            repository.setSymbolRows(
                r11.joinToString(" "),
                r12.joinToString(" "),
                r13.joinToString(" "),
                r21.joinToString(" "),
                r22.joinToString(" "),
                r23.joinToString(" ")
            )
        }
    }

    fun persistQuick(
        enabled: Boolean = quickEnabled,
        list: List<String> = quickList,
        delayMs: Int = quickDelay.toInt()
    ) {
        scope.launch {
            repository.setBool("quickSymEnabled", enabled)
            repository.setQuickSymbolPopup(list.joinToString(" "), delayMs)
        }
    }

    Surface(
        shape = SereneTokens.OuterShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring())
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("symbols", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (tab == SymTab.HOLD_123) "tap to edit · + to add" else "tap any key to replace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab == SymTab.HOLD_123 && quickEnabled) {
                        FilledTonalIconButton(
                            onClick = {
                                dialogInput = ""
                                editTarget = 6 to -1
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "add quick symbol", modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(
                        onClick = {
                            val d = KeyboardConfig()
                            s1r1 = parse(d.sym1Row1)
                            s1r2 = parse(d.sym1Row2)
                            s1r3 = parse(d.sym1Row3)
                            s2r1 = parse(d.sym2Row1)
                            s2r2 = parse(d.sym2Row2)
                            s2r3 = parse(d.sym2Row3)
                            quickEnabled = d.quickSymbolPopupEnabled
                            quickList = parse(d.quickSymbolPopupChars)
                            quickDelay = d.quickSymbolPopupDelayMs.toFloat()
                            scope.launch {
                                repository.resetSymbolRows()
                                repository.setBool("quickSymEnabled", d.quickSymbolPopupEnabled)
                                repository.setQuickSymbolPopup(d.quickSymbolPopupChars, d.quickSymbolPopupDelayMs)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "reset symbols", modifier = Modifier.size(19.dp))
                    }
                }
            }

            LiquidGlassSegmentedRow(
                items = SymTab.entries,
                selectedItem = tab,
                labelProvider = { it.label },
                onSelect = { tab = it }
            )

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "InteractiveSymTab"
            ) { activeTab ->
                when (activeTab) {
                    SymTab.PAGE_123, SymTab.PAGE_SYM -> {
                        val isPage1 = activeTab == SymTab.PAGE_123
                        val rows = if (isPage1) listOf(s1r1, s1r2, s1r3) else listOf(s2r1, s2r2, s2r3)
                        val rowOffset = if (isPage1) 0 else 3

                        StaticSymbolGrid(
                            rows = rows,
                            leftFuncLabel = if (isPage1) "#+=" else "123",
                            palette = palette,
                            onTapKey = { rIdx, kIdx ->
                                dialogInput = rows[rIdx][kIdx]
                                editTarget = (rowOffset + rIdx) to kIdx
                            }
                        )
                    }

                    SymTab.HOLD_123 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SereneTokens.InnerShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    quickEnabled = !quickEnabled
                                    persistQuick(enabled = quickEnabled)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("quick 123 popup", fontWeight = FontWeight.Medium)
                                Text(
                                    "hold 123 and slide to insert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LiquidGlassSwitch(
                                checked = quickEnabled,
                                onCheckedChange = {
                                    quickEnabled = it
                                    persistQuick(enabled = it)
                                }
                            )
                        }

                        if (quickEnabled) {
                            ExpandableGlassSection(
                                title = "hold delay",
                                summary = "${quickDelay.toInt()} ms",
                                expanded = delayExpanded,
                                onToggle = { delayExpanded = !delayExpanded }
                            ) {
                                LiquidGlassSlider(
                                    value = quickDelay,
                                    onValueChange = { quickDelay = it },
                                    valueRange = 100f..450f,
                                    onValueChangeFinished = { persistQuick(delayMs = quickDelay.toInt()) }
                                )
                            }

                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SereneTokens.InnerShape)
                                    .background(palette.trayBackground)
                                    .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), SereneTokens.InnerShape)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                quickList.forEachIndexed { idx, sym ->
                                    val quickFontSp = when {
                                        sym.length <= 1 -> 16f
                                        sym.length == 2 -> 13f
                                        else -> 11f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
                                            .clip(SereneTokens.SmallShape)
                                            .background(palette.keyBackground)
                                            .clickable {
                                                dialogInput = sym
                                                editTarget = 6 to idx
                                            }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sym,
                                            color = palette.keyText,
                                            fontSize = quickFontSp.sp,
                                            maxLines = 1
                                        )
                                        if (quickList.size > 1) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                    .clickable {
                                                        val updated = quickList.toMutableList().apply { removeAt(idx) }
                                                        quickList = updated
                                                        persistQuick(list = updated)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "remove",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(8.dp)
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
        }
    }

    editTarget?.let { (targetRow, targetIdx) ->
        val isAddingQuick = targetRow == 6 && targetIdx < 0
        val currentKeyLabel = when (targetRow) {
            0 -> s1r1.getOrNull(targetIdx)
            1 -> s1r2.getOrNull(targetIdx)
            2 -> s1r3.getOrNull(targetIdx)
            3 -> s2r1.getOrNull(targetIdx)
            4 -> s2r2.getOrNull(targetIdx)
            5 -> s2r3.getOrNull(targetIdx)
            6 -> quickList.getOrNull(targetIdx)
            else -> null
        }

        val allowMultiChar = targetRow == 6
        fun commitSingleChar(raw: String) {
            val trimmed = raw.trim()
            val ch = if (allowMultiChar) trimmed else trimmed.take(1)
            if (ch.isEmpty()) return
            fun applyTo(list: MutableList<String>): MutableList<String> {
                return list.toMutableList().apply {
                    if (isAddingQuick) add(ch) else if (targetIdx in indices) set(targetIdx, ch)
                }
            }
            when (targetRow) {
                0 -> { val u = applyTo(s1r1); s1r1 = u; persistPages(r11 = u) }
                1 -> { val u = applyTo(s1r2); s1r2 = u; persistPages(r12 = u) }
                2 -> { val u = applyTo(s1r3); s1r3 = u; persistPages(r13 = u) }
                3 -> { val u = applyTo(s2r1); s2r1 = u; persistPages(r21 = u) }
                4 -> { val u = applyTo(s2r2); s2r2 = u; persistPages(r22 = u) }
                5 -> { val u = applyTo(s2r3); s2r3 = u; persistPages(r23 = u) }
                6 -> { val u = applyTo(quickList); quickList = u; persistQuick(list = u) }
            }
            editTarget = null
        }

        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isAddingQuick && currentKeyLabel != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SereneTokens.SmallShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentKeyLabel,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = if (isAddingQuick) "add symbol" else "replace key",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dialogInput,
                        onValueChange = { input ->
                            val clean = input.replace(" ", "")
                            dialogInput = if (allowMultiChar) clean else if (clean.isEmpty()) "" else clean.takeLast(1)
                        },
                        label = { Text(if (allowMultiChar) "symbol or short text" else "single symbol") },
                        singleLine = true,
                        shape = SereneTokens.SmallShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("presets:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        singleCharPresets.forEach { sym ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(SereneTokens.SmallShape)
                                    .background(
                                        if (sym == dialogInput) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .clickable { commitSingleChar(sym) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sym, fontSize = 17.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("cancel") }
            },
            confirmButton = {
                Button(
                    enabled = dialogInput.isNotBlank(),
                    onClick = { commitSingleChar(dialogInput) },
                    shape = SereneTokens.SmallShape
                ) {
                    Text(if (isAddingQuick) "add" else "save")
                }
            }
        )
    }
}

@Composable
private fun StaticSymbolGrid(
    rows: List<List<String>>,
    leftFuncLabel: String,
    palette: KeyboardPalette,
    onTapKey: (rowIdx: Int, keyIdx: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SereneTokens.InnerShape)
            .background(palette.trayBackground)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), SereneTokens.InnerShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        rows.forEachIndexed { rIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rIdx == 2) {
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .height(38.dp)
                            .clip(SereneTokens.SmallShape)
                            .background(palette.funcKeyBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(leftFuncLabel, color = palette.keyText, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }

                rowItems.forEachIndexed { kIdx, sym ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(SereneTokens.SmallShape)
                            .background(palette.keyBackground)
                            .clickable { onTapKey(rIdx, kIdx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sym,
                            color = palette.keyText,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (rIdx == 2) {
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .height(38.dp)
                            .clip(SereneTokens.SmallShape)
                            .background(palette.funcKeyBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = KeyboardIcons.Backspace,
                            contentDescription = "backspace",
                            tint = palette.keyText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
