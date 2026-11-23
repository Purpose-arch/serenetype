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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.theme.KeyboardPalette

private val suggestedSymbols = listOf(
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
    "-", "/", ":", ";", "(", ")", "$", "&", "@", "\"",
    ".", ",", "?", "!", "'", "[", "]", "{", "}", "#",
    "%", "^", "*", "+", "=", "_", "\\", "|", "~", "<",
    ">", "€", "£", "¥", "₽", "•", "`", "√", "π", "÷",
    "×", "¶", "∆", "°", "№", "§", "±", "≠", "≈", "—"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SymbolEditorSheet(
    config: KeyboardConfig,
    palette: KeyboardPalette,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSavePages: (String, String, String, String, String, String) -> Unit,
    onSaveQuickPopup: (Boolean, String, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun parse(s: String): MutableList<String> =
        s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()

    var tab by remember { mutableIntStateOf(0) } // 0 = 123, 1 = #+=, 2 = Hold 123 Popup

    var s1r1 by remember(config.sym1Row1) { mutableStateOf(parse(config.sym1Row1)) }
    var s1r2 by remember(config.sym1Row2) { mutableStateOf(parse(config.sym1Row2)) }
    var s1r3 by remember(config.sym1Row3) { mutableStateOf(parse(config.sym1Row3)) }

    var s2r1 by remember(config.sym2Row1) { mutableStateOf(parse(config.sym2Row1)) }
    var s2r2 by remember(config.sym2Row2) { mutableStateOf(parse(config.sym2Row2)) }
    var s2r3 by remember(config.sym2Row3) { mutableStateOf(parse(config.sym2Row3)) }

    var quickEnabled by remember(config.quickSymbolPopupEnabled) { mutableStateOf(config.quickSymbolPopupEnabled) }
    var quickList by remember(config.quickSymbolPopupChars) { mutableStateOf(parse(config.quickSymbolPopupChars)) }
    var quickDelay by remember(config.quickSymbolPopupDelayMs) { mutableFloatStateOf(config.quickSymbolPopupDelayMs.toFloat()) }

    var addTargetRow by remember { mutableStateOf<Int?>(null) } // 1..6 for page rows, 7 for quick popup
    var customCharInput by remember { mutableStateOf("") }

    fun saveAll() {
        onSavePages(
            s1r1.joinToString(" "),
            s1r2.joinToString(" "),
            s1r3.joinToString(" "),
            s2r1.joinToString(" "),
            s2r2.joinToString(" "),
            s2r3.joinToString(" ")
        )
        onSaveQuickPopup(quickEnabled, quickList.joinToString(" "), quickDelay.toInt())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("symbols", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
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
                    onReset()
                }) {
                    Text("reset")
                }
            }

            // Segmented tabs
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("123", "#+=", "hold 123").forEachIndexed { idx, title ->
                    SegmentedButton(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                        label = { Text(title, fontSize = 12.sp, maxLines = 1) }
                    )
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "SymbolEditorTab"
            ) { activeTab ->
                when (activeTab) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SymbolPreviewCard(
                            palette = palette,
                            rows = listOf(s1r1, s1r2, listOf("#+=") + s1r3 + listOf("⌫"))
                        )
                        EditableSymbolRow(
                            title = "row 1",
                            items = s1r1,
                            swapLabel = "to #+= ",
                            onRemove = { idx -> if (s1r1.size > 1) s1r1 = s1r1.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s1r1[idx]
                                if (s1r1.size > 1) {
                                    s1r1 = s1r1.toMutableList().apply { removeAt(idx) }
                                    s2r1 = s2r1.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 1 }
                        )
                        EditableSymbolRow(
                            title = "row 2",
                            items = s1r2,
                            swapLabel = "to #+= ",
                            onRemove = { idx -> if (s1r2.size > 1) s1r2 = s1r2.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s1r2[idx]
                                if (s1r2.size > 1) {
                                    s1r2 = s1r2.toMutableList().apply { removeAt(idx) }
                                    s2r2 = s2r2.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 2 }
                        )
                        EditableSymbolRow(
                            title = "row 3",
                            items = s1r3,
                            swapLabel = "to #+= ",
                            onRemove = { idx -> if (s1r3.size > 1) s1r3 = s1r3.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s1r3[idx]
                                if (s1r3.size > 1) {
                                    s1r3 = s1r3.toMutableList().apply { removeAt(idx) }
                                    s2r3 = s2r3.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 3 }
                        )
                    }

                    1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SymbolPreviewCard(
                            palette = palette,
                            rows = listOf(s2r1, s2r2, listOf("123") + s2r3 + listOf("⌫"))
                        )
                        EditableSymbolRow(
                            title = "row 1",
                            items = s2r1,
                            swapLabel = "to 123",
                            onRemove = { idx -> if (s2r1.size > 1) s2r1 = s2r1.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s2r1[idx]
                                if (s2r1.size > 1) {
                                    s2r1 = s2r1.toMutableList().apply { removeAt(idx) }
                                    s1r1 = s1r1.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 4 }
                        )
                        EditableSymbolRow(
                            title = "row 2",
                            items = s2r2,
                            swapLabel = "to 123",
                            onRemove = { idx -> if (s2r2.size > 1) s2r2 = s2r2.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s2r2[idx]
                                if (s2r2.size > 1) {
                                    s2r2 = s2r2.toMutableList().apply { removeAt(idx) }
                                    s1r2 = s1r2.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 5 }
                        )
                        EditableSymbolRow(
                            title = "row 3",
                            items = s2r3,
                            swapLabel = "to 123",
                            onRemove = { idx -> if (s2r3.size > 1) s2r3 = s2r3.toMutableList().apply { removeAt(idx) } },
                            onSwapScreen = { idx ->
                                val sym = s2r3[idx]
                                if (s2r3.size > 1) {
                                    s2r3 = s2r3.toMutableList().apply { removeAt(idx) }
                                    s1r3 = s1r3.toMutableList().apply { add(sym) }
                                }
                            },
                            onAddClick = { addTargetRow = 6 }
                        )
                    }

                    else -> Column(
                        modifier = Modifier.animateContentSize(spring()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { quickEnabled = !quickEnabled }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("quick 123 popup", fontWeight = FontWeight.Medium)
                                    Text("hold 123 and slide to insert", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LiquidGlassSwitch(checked = quickEnabled, onCheckedChange = { quickEnabled = it })
                            }
                        }

                        if (quickEnabled) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("hold delay", fontWeight = FontWeight.Medium)
                                        Text("${quickDelay.toInt()} ms", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                    LiquidGlassSlider(
                                        value = quickDelay,
                                        onValueChange = { quickDelay = it },
                                        valueRange = 100f..450f
                                    )
                                }
                            }

                            EditableSymbolRow(
                                title = "quick symbols (${quickList.size})",
                                items = quickList,
                                swapLabel = null,
                                onRemove = { idx -> if (quickList.size > 1) quickList = quickList.toMutableList().apply { removeAt(idx) } },
                                onSwapScreen = {},
                                onAddClick = { addTargetRow = 7 }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    saveAll()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("save", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (addTargetRow != null) {
        val target = addTargetRow!!
        AlertDialog(
            onDismissRequest = { addTargetRow = null },
            title = { Text("add symbol", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customCharInput,
                        onValueChange = { customCharInput = it },
                        label = { Text("custom") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("presets:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedSymbols.forEach { sym ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        when (target) {
                                            1 -> s1r1 = s1r1.toMutableList().apply { add(sym) }
                                            2 -> s1r2 = s1r2.toMutableList().apply { add(sym) }
                                            3 -> s1r3 = s1r3.toMutableList().apply { add(sym) }
                                            4 -> s2r1 = s2r1.toMutableList().apply { add(sym) }
                                            5 -> s2r2 = s2r2.toMutableList().apply { add(sym) }
                                            6 -> s2r3 = s2r3.toMutableList().apply { add(sym) }
                                            7 -> quickList = quickList.toMutableList().apply { add(sym) }
                                        }
                                        addTargetRow = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sym, fontSize = 18.sp)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { addTargetRow = null }) { Text("close") }
            },
            confirmButton = {
                Button(
                    enabled = customCharInput.isNotBlank(),
                    onClick = {
                        val sym = customCharInput.trim()
                        when (target) {
                            1 -> s1r1 = s1r1.toMutableList().apply { add(sym) }
                            2 -> s1r2 = s1r2.toMutableList().apply { add(sym) }
                            3 -> s1r3 = s1r3.toMutableList().apply { add(sym) }
                            4 -> s2r1 = s2r1.toMutableList().apply { add(sym) }
                            5 -> s2r2 = s2r2.toMutableList().apply { add(sym) }
                            6 -> s2r3 = s2r3.toMutableList().apply { add(sym) }
                            7 -> quickList = quickList.toMutableList().apply { add(sym) }
                        }
                        customCharInput = ""
                        addTargetRow = null
                    }
                ) {
                    Text("add")
                }
            }
        )
    }
}

@Composable
private fun SymbolPreviewCard(
    palette: KeyboardPalette,
    rows: List<List<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.trayBackground)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { key ->
                    val isFunc = key == "#+=" || key == "123" || key == "⌫"
                    Box(
                        modifier = Modifier
                            .weight(if (isFunc) 1.4f else 1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isFunc) palette.funcKeyBackground else palette.keyBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = palette.keyText,
                            fontSize = if (isFunc) 11.sp else 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableSymbolRow(
    title: String,
    items: List<String>,
    swapLabel: String?,
    onRemove: (Int) -> Unit,
    onSwapScreen: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = onAddClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("add", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEachIndexed { idx, sym ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(sym, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (swapLabel != null) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = swapLabel,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onSwapScreen(idx) },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (items.size > 1) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "remove",
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onRemove(idx) },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
