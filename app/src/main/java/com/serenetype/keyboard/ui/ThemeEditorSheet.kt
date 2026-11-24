package com.serenetype.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serenetype.keyboard.data.CustomThemeSpec
import com.serenetype.keyboard.theme.KeyboardThemeEngine
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorDialog(
    initial: CustomThemeSpec?,
    onDismiss: () -> Unit,
    onSave: (CustomThemeSpec) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "new theme") }
    var trayHex by remember { mutableStateOf(initial?.trayColorHex ?: "#18181B") }
    var keyHex by remember { mutableStateOf(initial?.keyColorHex ?: "#2C2C2E") }
    var funcHex by remember { mutableStateOf(initial?.funcKeyColorHex ?: "#3A3A3C") }
    var textHex by remember { mutableStateOf(initial?.textColorHex ?: "#FFFFFF") }
    var accentHex by remember { mutableStateOf(initial?.accentColorHex ?: "#0A84FF") }

    val allHexValid = KeyboardThemeEngine.isValidHexColor(trayHex) &&
        KeyboardThemeEngine.isValidHexColor(keyHex) &&
        KeyboardThemeEngine.isValidHexColor(funcHex) &&
        KeyboardThemeEngine.isValidHexColor(textHex) &&
        KeyboardThemeEngine.isValidHexColor(accentHex)

    val quickPalette = listOf(
        "#000000", "#18181B", "#2C2C2E", "#FFFFFF", "#E5E7EB",
        "#0A84FF", "#30D158", "#FF453A", "#BF5AF2", "#FF9F0A", "#64D2FF"
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (initial == null) "new theme" else "edit theme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("name") },
                singleLine = true,
                shape = SereneTokens.InnerShape,
                modifier = Modifier.fillMaxWidth()
            )

            ColorFieldRow("tray", trayHex, quickPalette) { trayHex = it }
            ColorFieldRow("keys", keyHex, quickPalette) { keyHex = it }
            ColorFieldRow("func keys", funcHex, quickPalette) { funcHex = it }
            ColorFieldRow("text", textHex, quickPalette) { textHex = it }
            ColorFieldRow("accent", accentHex, quickPalette) { accentHex = it }

            Button(
                enabled = allHexValid,
                onClick = {
                    onSave(
                        CustomThemeSpec(
                            id = initial?.id ?: "custom_${UUID.randomUUID()}",
                            name = name.ifBlank { "custom" },
                            trayColorHex = trayHex.trim(),
                            keyColorHex = keyHex.trim(),
                            funcKeyColorHex = funcHex.trim(),
                            textColorHex = textHex.trim(),
                            accentColorHex = accentHex.trim()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = SereneTokens.InnerShape
            ) {
                Text("save")
            }
        }
    }
}

@Composable
private fun ColorFieldRow(
    label: String,
    hex: String,
    swatches: List<String>,
    onChange: (String) -> Unit
) {
    val isValid = KeyboardThemeEngine.isValidHexColor(hex)
    val parsed = KeyboardThemeEngine.parseHexColor(hex, Color.Gray)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(parsed)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = onChange,
                    isError = !isValid,
                    singleLine = true,
                    shape = SereneTokens.SmallShape,
                    modifier = Modifier.width(120.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.forEach { s ->
                val c = KeyboardThemeEngine.parseHexColor(s, Color.Gray)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (s.equals(hex, ignoreCase = true)) 2.dp else 1.dp,
                            color = if (s.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { onChange(s) }
                )
            }
        }
    }
}
