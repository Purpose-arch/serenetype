package com.serenetype.keyboard.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.serenetype.keyboard.data.*
import com.serenetype.keyboard.theme.KeyboardPalette
import com.serenetype.keyboard.theme.KeyboardThemeEngine
import kotlinx.coroutines.launch

private enum class SettingsRoute(val title: String) {
    HOME("serenetype"),
    THEMES("themes"),
    APPEARANCE("appearance"),
    LAYOUT("layout"),
    TYPING("typing")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(applicationContext)
        val dictionary = DictionaryEngine.getInstance(applicationContext)

        setContent {
            val dark = isSystemInDarkTheme()
            val baseScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                }
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            val colorScheme = if (dark) {
                baseScheme.copy(
                    background = Color(0xFF0D0E12),
                    surface = Color(0xFF0D0E12),
                    surfaceContainerLow = Color(0xFF15171E),
                    secondaryContainer = Color(0xFF353A4D),
                    onSecondaryContainer = Color(0xFFE2E6F3)
                )
            } else {
                baseScheme
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SereneSettingsApp(repository = repository, dictionary = dictionary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SereneSettingsApp(
    repository: SettingsRepository,
    dictionary: DictionaryEngine
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val config by repository.configFlow.collectAsState(initial = KeyboardConfig())
    val isDark = isSystemInDarkTheme()
    val previewPalette = remember(
        config.themeMode,
        config.activeCustomThemeId,
        config.customThemes,
        isDark
    ) {
        KeyboardThemeEngine.resolve(context, config, isDark)
    }

    var route by remember { mutableStateOf(SettingsRoute.HOME) }
    var showTestBox by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf("") }
    var editingTheme by remember { mutableStateOf<CustomThemeSpec?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showSymbolEditor by remember { mutableStateOf(false) }
    var showTextExpander by remember { mutableStateOf(false) }

    var hasEnExt by remember { mutableStateOf(dictionary.hasExtendedDict("en")) }
    var hasRuExt by remember { mutableStateOf(dictionary.hasExtendedDict("ru")) }
    var hasFiExt by remember { mutableStateOf(dictionary.hasExtendedDict("fi")) }
    var hasDeExt by remember { mutableStateOf(dictionary.hasExtendedDict("de")) }
    var hasUkExt by remember { mutableStateOf(dictionary.hasExtendedDict("uk")) }
    var downloadingLang by remember { mutableStateOf<String?>(null) }
    var hasEmojiFont by remember { mutableStateOf(EmojiFontManager.isDownloaded(context)) }
    var downloadingEmojiFont by remember { mutableStateOf(false) }
    var userWordCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        dictionary.ensureLoaded()
        userWordCount = dictionary.getUserWordCount()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val json = SettingsBackupManager.exportBackupJson(context)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }.isSuccess
                snackbarHostState.showSnackbar(if (ok) "backup exported" else "export failed")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Cannot open input stream")
                    SettingsBackupManager.importBackupJson(context, raw)
                }.getOrNull()
                if (res != null) {
                    val msg = if (res.skippedPreferences > 0) {
                        "restored ${res.appliedPreferences} settings · ${res.skippedPreferences} skipped"
                    } else {
                        "restored ${res.appliedPreferences} settings"
                    }
                    snackbarHostState.showSnackbar(msg)
                } else {
                    snackbarHostState.showSnackbar("invalid backup file")
                }
            }
        }
    }

    val imm = remember { context.getSystemService(InputMethodManager::class.java) }
    var isEnabled by remember { mutableStateOf(false) }
    var isSelected by remember { mutableStateOf(false) }

    fun refreshImeStatus() {
        val pkg = context.packageName
        isEnabled = imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        isSelected = defaultIme.startsWith(pkg)
    }

    DisposableEffect(lifecycleOwner) {
        refreshImeStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
                userWordCount = dictionary.getUserWordCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = route != SettingsRoute.HOME) {
        route = SettingsRoute.HOME
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = route.title,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (route != SettingsRoute.HOME) {
                        IconButton(onClick = { route = SettingsRoute.HOME }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
                        }
                    }
                },
                actions = {
                    if (route == SettingsRoute.HOME) {
                        IconButton(onClick = { showTestBox = !showTestBox }) {
                            Icon(Icons.Rounded.Keyboard, contentDescription = "test")
                        }
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                if (targetState != SettingsRoute.HOME) {
                    (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(180)))
                }
            },
            label = "SettingsNavigation"
        ) { currentRoute ->
            when (currentRoute) {
                SettingsRoute.HOME -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (!isEnabled || !isSelected) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.animateContentSize(spring())
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("enable serenetype", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(if (isEnabled) "1. enabled" else "1. enable")
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    imm?.showInputMethodPicker()
                                                    refreshImeStatus()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(if (isSelected) "2. selected" else "2. select")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(visible = showTestBox) {
                                OutlinedTextField(
                                    value = testText,
                                    onValueChange = { testText = it },
                                    placeholder = { Text("type here...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                )
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column {
                                    CleanNavRow(
                                        icon = Icons.Rounded.Palette,
                                        title = "themes",
                                        subtitle = config.themeMode.label,
                                        onClick = { route = SettingsRoute.THEMES }
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    CleanNavRow(
                                        icon = Icons.Rounded.Tune,
                                        title = "appearance",
                                        subtitle = "${config.keyTextSizeSp.toInt()} sp · ${config.horizontalGapDp.toInt()} dp gap",
                                        onClick = { route = SettingsRoute.APPEARANCE }
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    CleanNavRow(
                                        icon = Icons.Rounded.OpenWith,
                                        title = "layout",
                                        subtitle = "bottom ${config.bottomPaddingDp.toInt()} dp · top bar",
                                        onClick = { route = SettingsRoute.LAYOUT }
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    CleanNavRow(
                                        icon = Icons.Rounded.Language,
                                        title = "typing",
                                        subtitle = config.enabledLanguages.joinToString(", ") { KeyboardLayouts.languages[it]?.displayName ?: it },
                                        onClick = { route = SettingsRoute.TYPING }
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("backup & restore", fontWeight = FontWeight.Medium)
                                    Text(
                                        "export or import all settings, themes, snippets & dictionary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = { exportLauncher.launch("serenetype-backup.json") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("export")
                                        }
                                        Button(
                                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("import")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsRoute.THEMES -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            KeyboardMiniPreview(
                                config = config,
                                palette = previewPalette
                            )
                        }

                        item {
                            val modes = ThemeMode.entries.filter { it != ThemeMode.CUSTOM }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                modes.forEachIndexed { idx, mode ->
                                    val active = config.themeMode == mode
                                    Surface(
                                        onClick = { scope.launch { repository.setThemeMode(mode) } },
                                        shape = expressiveShape(idx, modes.size),
                                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(mode.label, fontWeight = FontWeight.Medium)
                                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            RadioButton(selected = active, onClick = { scope.launch { repository.setThemeMode(mode) } })
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("custom themes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                FilledTonalButton(
                                    onClick = {
                                        editingTheme = null
                                        showEditor = true
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("new")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                config.customThemes.forEachIndexed { idx, custom ->
                                    val active = config.themeMode == ThemeMode.CUSTOM && config.activeCustomThemeId == custom.id
                                    Surface(
                                        onClick = { scope.launch { repository.selectCustomTheme(custom.id) } },
                                        shape = expressiveShape(idx, config.customThemes.size),
                                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(KeyboardThemeEngine.parseHexColor(custom.accentColorHex, Color.Blue))
                                                )
                                                Column {
                                                    Text(custom.name, fontWeight = FontWeight.Medium)
                                                    Text(custom.trayColorHex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row {
                                                IconButton(onClick = {
                                                    editingTheme = custom
                                                    showEditor = true
                                                }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "edit")
                                                }
                                                if (config.customThemes.size > 1) {
                                                    IconButton(onClick = { scope.launch { repository.deleteCustomTheme(custom.id) } }) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "delete")
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

                SettingsRoute.APPEARANCE -> {
                    var cornerRadiusDraft by remember(config.cornerRadiusDp) { mutableFloatStateOf(config.cornerRadiusDp) }
                    var funcCornerRadiusDraft by remember(config.funcCornerRadiusDp) { mutableFloatStateOf(config.funcCornerRadiusDp) }
                    var hGapDraft by remember(config.horizontalGapDp) { mutableFloatStateOf(config.horizontalGapDp) }
                    var vGapDraft by remember(config.verticalGapDp) { mutableFloatStateOf(config.verticalGapDp) }
                    var textSizeDraft by remember(config.keyTextSizeSp) { mutableFloatStateOf(config.keyTextSizeSp) }

                    val previewConfig = remember(
                        config,
                        cornerRadiusDraft,
                        funcCornerRadiusDraft,
                        hGapDraft,
                        vGapDraft,
                        textSizeDraft
                    ) {
                        config.copy(
                            cornerRadiusDp = cornerRadiusDraft,
                            funcCornerRadiusDp = funcCornerRadiusDraft,
                            horizontalGapDp = hGapDraft,
                            verticalGapDp = vGapDraft,
                            keyTextSizeSp = textSizeDraft
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            KeyboardMiniPreview(
                                config = previewConfig,
                                palette = previewPalette
                            )
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SliderItem(
                                        title = "key radius",
                                        valueText = "${cornerRadiusDraft.toInt()} dp",
                                        value = cornerRadiusDraft,
                                        range = 0f..20f,
                                        onChange = { cornerRadiusDraft = it },
                                        onChangeFinished = { scope.launch { repository.setFloat("cornerRadius", cornerRadiusDraft) } }
                                    )
                                    SliderItem(
                                        title = "func radius",
                                        valueText = "${funcCornerRadiusDraft.toInt()} dp",
                                        value = funcCornerRadiusDraft,
                                        range = 0f..20f,
                                        onChange = { funcCornerRadiusDraft = it },
                                        onChangeFinished = { scope.launch { repository.setFloat("funcCornerRadius", funcCornerRadiusDraft) } }
                                    )
                                    SliderItem(
                                        title = "horizontal gap",
                                        valueText = "${hGapDraft.toInt()} dp",
                                        value = hGapDraft,
                                        range = 1f..8f,
                                        onChange = { hGapDraft = it },
                                        onChangeFinished = { scope.launch { repository.setFloat("hGap", hGapDraft) } }
                                    )
                                    SliderItem(
                                        title = "vertical gap",
                                        valueText = "${vGapDraft.toInt()} dp",
                                        value = vGapDraft,
                                        range = 2f..14f,
                                        onChange = { vGapDraft = it },
                                        onChangeFinished = { scope.launch { repository.setFloat("vGap", vGapDraft) } }
                                    )
                                    SliderItem(
                                        title = "font size",
                                        valueText = "${textSizeDraft.toInt()} sp",
                                        value = textSizeDraft,
                                        range = 16f..30f,
                                        onChange = { textSizeDraft = it },
                                        onChangeFinished = { scope.launch { repository.setFloat("textSize", textSizeDraft) } }
                                    )
                                }
                            }
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ExpressiveSwitchRow("key shadow", config.showKeyShadow, expressiveShape(0, 3)) {
                                    scope.launch { repository.setBool("showShadow", it) }
                                }
                                ExpressiveSwitchRow("key border", config.showKeyBorder, expressiveShape(1, 3)) {
                                    scope.launch { repository.setBool("showBorder", it) }
                                }
                                ExpressiveSwitchRow("key popup", config.showKeyPopup, expressiveShape(2, 3)) {
                                    scope.launch { repository.setBool("showPopup", it) }
                                }
                            }
                        }

                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("font", fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        KeyFontStyle.entries.forEach { st ->
                                            FilterChip(
                                                selected = config.fontStyle == st,
                                                onClick = { scope.launch { repository.setFontStyle(st) } },
                                                label = { Text(st.label, fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = {
                                    if (!downloadingEmojiFont && !hasEmojiFont) {
                                        downloadingEmojiFont = true
                                        scope.launch {
                                            hasEmojiFont = EmojiFontManager.downloadAppleEmojiFont(context)
                                            downloadingEmojiFont = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("apple color emoji", fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (hasEmojiFont) "ios emoji font active" else "optional download · uses system font otherwise",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    when {
                                        downloadingEmojiFont -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        hasEmojiFont -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("ready", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                            IconButton(onClick = {
                                                scope.launch {
                                                    EmojiFontManager.deleteAppleEmojiFont(context)
                                                    hasEmojiFont = false
                                                }
                                            }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "remove emoji font", modifier = Modifier.size(19.dp))
                                            }
                                        }
                                        else -> Text("get", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                    }
                }

                SettingsRoute.LAYOUT -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            InteractiveLayoutEditor(
                                config = config,
                                palette = previewPalette,
                                repository = repository
                            )
                        }

                        item {
                            Text("symbols", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = { showSymbolEditor = true },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("symbols (123, #+= & hold 123)", fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (config.quickSymbolPopupEnabled) {
                                                "pages + quick 123 popup (${config.quickSymbolPopupDelayMs} ms)"
                                            } else {
                                                "customize symbol pages"
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "open symbol editor", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                SettingsRoute.TYPING -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Text("languages", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            val langs = KeyboardLayouts.languages.values.toList()
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                langs.forEachIndexed { idx, lang ->
                                    val checked = lang.code in config.enabledLanguages
                                    Surface(
                                        onClick = { scope.launch { repository.toggleLanguage(lang.code) } },
                                        shape = expressiveShape(idx, langs.size),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(lang.displayName, fontWeight = FontWeight.Medium)
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { scope.launch { repository.toggleLanguage(lang.code) } }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("typing & t9", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { scope.launch { repository.disableAllHelpers() } }) {
                                    Text("disable all")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ExpressiveSwitchRow("t9 autocorrect", config.autocorrectEnabled, expressiveShape(0, 3)) {
                                    scope.launch { repository.setBool("autocorrect", it) }
                                }
                                ExpressiveSwitchRow("auto-caps", config.autoCap, expressiveShape(1, 3)) {
                                    scope.launch { repository.setBool("autoCap", it) }
                                }
                                ExpressiveSwitchRow("double-space period", config.doubleSpacePeriod, expressiveShape(2, 3)) {
                                    scope.launch { repository.setBool("doubleSpaceDot", it) }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("smart tap zones", fontWeight = FontWeight.Medium)
                                    Text(
                                        "expands hitboxes of likely next letters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SmartTapMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = config.smartTapMode == mode,
                                                onClick = { scope.launch { repository.setSmartTapMode(mode) } },
                                                label = { Text(mode.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("text expander", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ExpressiveSwitchRow("enable snippets", config.textExpanderEnabled, expressiveShape(0, 2)) {
                                    scope.launch { repository.setBool("textExpander", it) }
                                }
                                Surface(
                                    onClick = { showTextExpander = true },
                                    shape = expressiveShape(1, 2),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("snippets (${config.textSnippets.size})", fontWeight = FontWeight.Medium)
                                            Text("custom triggers & phrases", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = "open snippets", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        item {
                            Text("dictionaries ($userWordCount local)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val dictEntries = listOf(
                                    Triple("en", "english 50k", hasEnExt),
                                    Triple("ru", "russian 50k", hasRuExt),
                                    Triple("fi", "finnish 50k", hasFiExt),
                                    Triple("de", "german 50k", hasDeExt),
                                    Triple("uk", "ukrainian 50k", hasUkExt)
                                )
                                dictEntries.forEachIndexed { idx, (code, title, downloaded) ->
                                    DictDownloadRow(
                                        title = title,
                                        isDownloaded = downloaded,
                                        isLoading = downloadingLang == code,
                                        shape = expressiveShape(idx, dictEntries.size)
                                    ) {
                                        downloadingLang = code
                                        scope.launch {
                                            val ok = dictionary.downloadExtendedDictionary(code)
                                            when (code) {
                                                "en" -> hasEnExt = ok
                                                "ru" -> hasRuExt = ok
                                                "fi" -> hasFiExt = ok
                                                "de" -> hasDeExt = ok
                                                "uk" -> hasUkExt = ok
                                            }
                                            downloadingLang = null
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("keys & haptics", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ExpressiveSwitchRow("instant key commit", config.instantKeyCommit, expressiveShape(0, 5)) {
                                    scope.launch { repository.setBool("instantKeyCommit", it) }
                                }
                                ExpressiveSwitchRow("spacebar label", config.showSpacebarLabel, expressiveShape(1, 5)) {
                                    scope.launch { repository.setBool("showSpaceLabel", it) }
                                }
                                ExpressiveSwitchRow("swipe space for language", config.spacebarSwipeLang, expressiveShape(2, 5)) {
                                    scope.launch { repository.setBool("spaceSwipe", it) }
                                }
                                ExpressiveSwitchRow("number row", config.showNumberRow, expressiveShape(3, 5)) {
                                    scope.launch { repository.setBool("showNumRow", it) }
                                }
                                ExpressiveSwitchRow("haptics", config.hapticEnabled, expressiveShape(4, 5)) {
                                    scope.launch { repository.setBool("hapticEnabled", it) }
                                }
                            }
                        }

                        if (config.hapticEnabled) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().animateContentSize(spring())
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("haptic strength", fontWeight = FontWeight.Medium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(1 to "light", 2 to "medium", 3 to "crisp").forEach { (lvl, title) ->
                                                FilterChip(
                                                    selected = config.hapticStrength == lvl,
                                                    onClick = { scope.launch { repository.setHapticStrength(lvl) } },
                                                    label = { Text(title) }
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

    if (showTextExpander) {
        TextExpanderSheet(
            snippets = config.textSnippets,
            onDismiss = { showTextExpander = false },
            onSaveSnippet = { snippet -> scope.launch { repository.saveTextSnippet(snippet) } },
            onDeleteSnippet = { id -> scope.launch { repository.deleteTextSnippet(id) } }
        )
    }

    if (showSymbolEditor) {
        SymbolEditorSheet(
            config = config,
            palette = previewPalette,
            onDismiss = { showSymbolEditor = false },
            onReset = {
                scope.launch {
                    repository.resetSymbolRows()
                    val def = KeyboardConfig()
                    repository.setBool("quickSymEnabled", def.quickSymbolPopupEnabled)
                    repository.setQuickSymbolPopup(def.quickSymbolPopupChars, def.quickSymbolPopupDelayMs)
                }
            },
            onSavePages = { s1r1, s1r2, s1r3, s2r1, s2r2, s2r3 ->
                scope.launch { repository.setSymbolRows(s1r1, s1r2, s1r3, s2r1, s2r2, s2r3) }
            },
            onSaveQuickPopup = { enabled, chars, delayMs ->
                scope.launch {
                    repository.setBool("quickSymEnabled", enabled)
                    repository.setQuickSymbolPopup(chars, delayMs)
                }
            }
        )
    }

    if (showEditor) {
        ThemeEditorDialog(
            initial = editingTheme,
            onDismiss = { showEditor = false },
            onSave = { spec ->
                scope.launch { repository.saveCustomTheme(spec) }
                showEditor = false
            }
        )
    }
}

@Composable
private fun KeyboardMiniPreview(
    config: KeyboardConfig,
    palette: KeyboardPalette
) {
    val fontFamily = when (config.fontStyle) {
        KeyFontStyle.SYSTEM_SANS -> FontFamily.SansSerif
        KeyFontStyle.ROUNDED -> FontFamily.SansSerif
        KeyFontStyle.MONOSPACE -> FontFamily.Monospace
    }
    val keyRadius = (config.cornerRadiusDp * 0.85f).dp
    val funcRadius = (config.funcCornerRadiusDp * 0.85f).dp
    val hGap = (config.horizontalGapDp * 0.85f).dp
    val vGap = (config.verticalGapDp * 0.85f).dp
    val fontSize = (config.keyTextSizeSp * 0.62f).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.trayBackground)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(vGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(hGap)
        ) {
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { k ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .then(
                            if (config.showKeyShadow) Modifier.padding(bottom = 1.dp) else Modifier
                        )
                        .clip(RoundedCornerShape(keyRadius))
                        .background(palette.keyBackground)
                        .then(
                            if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                                Modifier.border(0.75.dp, palette.keyBorder, RoundedCornerShape(keyRadius))
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(k, color = palette.keyText, fontSize = fontSize, fontFamily = fontFamily, maxLines = 1)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(hGap)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(funcRadius))
                    .background(palette.funcKeyBackground),
                contentAlignment = Alignment.Center
            ) {
                Text("123", color = palette.keyText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(4.3f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(keyRadius))
                    .background(palette.keyBackground)
                    .then(
                        if (config.showKeyBorder && palette.keyBorder != Color.Transparent) {
                            Modifier.border(0.75.dp, palette.keyBorder, RoundedCornerShape(keyRadius))
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (config.showSpacebarLabel) {
                    Text("space", color = palette.secondaryText, fontSize = 11.sp, fontFamily = fontFamily)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1.65f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(funcRadius))
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Text("↵", color = palette.accentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun expressiveShape(index: Int, count: Int): Shape {
    if (count <= 1) return RoundedCornerShape(22.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun DictDownloadRow(
    title: String,
    isDownloaded: Boolean,
    isLoading: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (!isLoading) onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (isDownloaded) "ready" else "get",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CleanNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpressiveSwitchRow(
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            LiquidGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderItem(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        LiquidGlassSlider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range
        )
    }
}
