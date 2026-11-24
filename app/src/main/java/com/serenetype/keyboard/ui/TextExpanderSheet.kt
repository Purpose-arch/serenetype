package com.serenetype.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serenetype.keyboard.data.TextSnippet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextExpanderSheet(
    snippets: List<TextSnippet>,
    onDismiss: () -> Unit,
    onSaveSnippet: (TextSnippet) -> Unit,
    onDeleteSnippet: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<TextSnippet?>(null) }
    var showForm by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("text expander", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("expand triggers into phrases", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(
                    onClick = {
                        editing = null
                        showForm = true
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "add")
                }
            }

            if (snippets.isEmpty()) {
                Surface(
                    shape = SereneTokens.InnerShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("no snippets yet", fontWeight = FontWeight.Medium)
                        Text(
                            "add a trigger like \"omw\" → \"on my way\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalIconButton(
                            onClick = {
                                editing = null
                                showForm = true
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "add snippet")
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snippets.forEach { item ->
                        Surface(
                            shape = SereneTokens.InnerShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editing = item
                                        showForm = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(item.trigger, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (item.requireSpace) "on space" else "instant",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.expansion,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        editing = item
                                        showForm = true
                                    }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "edit", modifier = Modifier.size(19.dp))
                                    }
                                    IconButton(onClick = { onDeleteSnippet(item.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "delete", modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        var trigger by remember(editing) { mutableStateOf(editing?.trigger ?: "") }
        var expansion by remember(editing) { mutableStateOf(editing?.expansion ?: "") }
        var requireSpace by remember(editing) { mutableStateOf(editing?.requireSpace ?: false) }

        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (editing == null) "new snippet" else "edit snippet", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it.replace(" ", "") },
                        label = { Text("trigger") },
                        singleLine = true,
                        shape = SereneTokens.SmallShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expansion,
                        onValueChange = { expansion = it },
                        label = { Text("expansion") },
                        minLines = 2,
                        maxLines = 6,
                        shape = SereneTokens.SmallShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { requireSpace = !requireSpace }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("require space", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (requireSpace) "expands after space" else "expands immediately",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LiquidGlassSwitch(checked = requireSpace, onCheckedChange = { requireSpace = it })
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForm = false }) { Text("cancel") }
            },
            confirmButton = {
                Button(
                    enabled = trigger.isNotBlank() && expansion.isNotBlank(),
                    onClick = {
                        onSaveSnippet(
                            TextSnippet(
                                id = editing?.id ?: "snip_${System.currentTimeMillis()}",
                                trigger = trigger.trim(),
                                expansion = expansion,
                                requireSpace = requireSpace
                            )
                        )
                        showForm = false
                    },
                    shape = SereneTokens.SmallShape
                ) {
                    Text("save")
                }
            }
        )
    }
}
