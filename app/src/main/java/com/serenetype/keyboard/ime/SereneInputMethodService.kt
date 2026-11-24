package com.serenetype.keyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.serenetype.keyboard.data.EmojiFontManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.serenetype.keyboard.data.DictionaryEngine
import com.serenetype.keyboard.data.KeyboardConfig
import com.serenetype.keyboard.data.SettingsRepository
import com.serenetype.keyboard.data.SmartTapMode
import com.serenetype.keyboard.input.ComposingSession
import com.serenetype.keyboard.input.LastCorrection
import com.serenetype.keyboard.theme.KeyboardThemeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class SereneInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hapticDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val hapticScope = CoroutineScope(SupervisorJob() + hapticDispatcher)

    private lateinit var repository: SettingsRepository
    private lateinit var dictionary: DictionaryEngine
    private lateinit var clipboardManager: ClipboardManager
    private val clipHistory = mutableStateListOf<String>()

    private var lastSpaceTime = 0L
    private var shiftState by mutableIntStateOf(0)
    private var lastShiftTap = 0L
    private var latestConfig = KeyboardConfig()
    private var instantActiveLanguage by mutableStateOf<String?>(null)
    private var instantEndChars: Set<Char> = emptySet()

    private var isPasswordField = false
    private var isNoSuggestions = false

    private val session = ComposingSession()
    private var cachedPriorsWord: String = ""
    private var cachedPriorsLang: String = ""
    private var cachedPriorsMap: Map<Char, Float> = emptyMap()
    private var keyCentersMap: Map<Char, Pair<Float, Float>> = emptyMap()
    private var avgKeyWidthPx: Float = 0f

    private val touchBiasMap = HashMap<Char, Pair<Float, Float>>()
    private var lastTouchChar: Char? = null
    private var lastTouchDelta: Pair<Float, Float>? = null
    private lateinit var touchBiasFile: File

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboard()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        repository = SettingsRepository(applicationContext)
        dictionary = DictionaryEngine.getInstance(applicationContext)
        touchBiasFile = File(applicationContext.filesDir, "touch_bias.json")
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        captureClipboard()
        serviceScope.launch {
            latestConfig = repository.configFlow.first()
            dictionary.ensureLoaded()
            loadTouchBiasAsync()
            EmojiFontManager.ensureLoaded(applicationContext)
        }
    }

    private suspend fun loadTouchBiasAsync() = withContext(Dispatchers.IO) {
        if (!touchBiasFile.exists()) return@withContext
        val loaded = runCatching {
            val obj = JSONObject(touchBiasFile.readText())
            val out = HashMap<Char, Pair<Float, Float>>()
            obj.keys().forEach { k ->
                if (k.length == 1) {
                    val arr = obj.optJSONArray(k)
                    if (arr != null && arr.length() == 2) {
                        out[k[0]] = arr.optDouble(0, 0.0).toFloat() to arr.optDouble(1, 0.0).toFloat()
                    }
                }
            }
            out
        }.getOrNull() ?: return@withContext
        withContext(Dispatchers.Main.immediate) {
            touchBiasMap.putAll(loaded)
        }
    }

    private fun saveTouchBiasAsync() {
        if (touchBiasMap.isEmpty()) return
        val snapshot = HashMap(touchBiasMap)
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val obj = JSONObject()
                snapshot.forEach { (ch, pair) ->
                    val arr = org.json.JSONArray()
                    arr.put(pair.first.toDouble())
                    arr.put(pair.second.toDouble())
                    obj.put(ch.toString(), arr)
                }
                touchBiasFile.writeText(obj.toString())
            }
        }
    }

    private fun captureClipboard() {
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isSensitive = clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (isSensitive) return
        }
        val text = runCatching {
            clip.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()?.trim() ?: return
        if (text.isEmpty()) return

        val updateHistory = {
            clipHistory.remove(text)
            clipHistory.add(0, text)
            if (clipHistory.size > 20) {
                clipHistory.removeAt(clipHistory.lastIndex)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateHistory()
        } else {
            serviceScope.launch { updateHistory() }
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, true)
            w.decorView.let { decor ->
                decor.setViewTreeLifecycleOwner(this@SereneInputMethodService)
                decor.setViewTreeViewModelStoreOwner(this@SereneInputMethodService)
                decor.setViewTreeSavedStateRegistryOwner(this@SereneInputMethodService)
            }
        }

        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@SereneInputMethodService)
            setViewTreeViewModelStoreOwner(this@SereneInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@SereneInputMethodService)

            setContent {
                val storedConfig by repository.configFlow.collectAsState(initial = latestConfig)
                val recentEmojis by repository.recentEmojisFlow.collectAsState(initial = KeyboardConfig().recentEmojis)
                val emojiFontFamily by EmojiFontManager.fontFamilyFlow.collectAsState()
                val activeLang = instantActiveLanguage?.takeIf { it in storedConfig.enabledLanguages } ?: storedConfig.activeLanguage
                val config = remember(storedConfig, activeLang) {
                    if (storedConfig.activeLanguage == activeLang) storedConfig else storedConfig.copy(activeLanguage = activeLang)
                }
                SideEffect {
                    latestConfig = config
                }
                LaunchedEffect(config.textExpanderEnabled, config.textSnippets) {
                    instantEndChars = if (config.textExpanderEnabled) {
                        config.textSnippets.mapNotNull { s ->
                            if (!s.requireSpace && s.trigger.isNotEmpty()) s.trigger.last() else null
                        }.toSet()
                    } else emptySet()
                }

                val isDark = isSystemInDarkTheme()
                val palette = remember(
                    config.themeMode,
                    config.activeCustomThemeId,
                    config.customThemes,
                    isDark
                ) {
                    KeyboardThemeEngine.resolve(applicationContext, config, isDark)
                }

                LaunchedEffect(palette.trayBackground, isDark) {
                    window?.window?.let { w ->
                        w.navigationBarColor = palette.trayBackground.toArgb()
                        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !isDark
                    }
                }

                val onCharWithPosCb = remember {
                    { ch: String, tx: Float?, ty: Float? ->
                        handleCharInput(ch, tx, ty)
                    }
                }
                val onCharCb = remember { { ch: String -> handleCharInput(ch, null, null) } }
                val onTouchSampleCb = remember {
                    { ch: Char, dx: Float, dy: Float, keyW: Float, keyH: Float ->
                        recordTouchBias(ch, dx, dy, keyW, keyH)
                    }
                }
                val onGeometryUpdateCb = remember {
                    { centers: Map<Char, Pair<Float, Float>>, avgW: Float ->
                        keyCentersMap = centers
                        avgKeyWidthPx = avgW
                    }
                }
                val nextCharPriorsProvider = remember {
                    {
                        val word = session.currentWord
                        val lang = latestConfig.activeLanguage
                        if (latestConfig.smartTapMode == SmartTapMode.OFF || isPasswordField || word.isEmpty()) {
                            emptyMap()
                        } else if (word == cachedPriorsWord && lang == cachedPriorsLang) {
                            cachedPriorsMap
                        } else {
                            dictionary.getNextCharPriors(word, lang).also {
                                cachedPriorsWord = word
                                cachedPriorsLang = lang
                                cachedPriorsMap = it
                            }
                        }
                    }
                }
                val touchBiasProvider = remember { { ch: Char -> touchBiasMap[ch] } }
                val onEmojiCb = remember {
                    { emoji: String ->
                        performHaptic(latestConfig)
                        session.finishWord(null)
                        session.lastCorrection = null
                        currentInputConnection?.commitText(emoji, 1)
                        serviceScope.launch { repository.recordRecentEmoji(emoji) }
                        Unit
                    }
                }
                val onBackspaceCb = remember { { handleBackspace() } }
                val onDeleteWordCb = remember { { handleDeleteWord() } }
                val onSpaceCb = remember { { handleSpaceInput() } }
                val onEnterCb = remember {
                    {
                        performHaptic(latestConfig)
                        session.finishWord(null)
                        session.lastCorrection = null
                        handleEnterAction()
                    }
                }
                val onShiftTapCb = remember {
                    {
                        performHaptic(latestConfig)
                        val now = System.currentTimeMillis()
                        shiftState = when {
                            shiftState == 1 && now - lastShiftTap < 350 -> 2
                            shiftState == 2 -> 0
                            shiftState == 1 -> 0
                            else -> 1
                        }
                        lastShiftTap = now
                    }
                }
                val onCycleLangCb = remember {
                    { delta: Int ->
                        performHaptic(latestConfig)
                        session.resetAll()
                        val list = latestConfig.enabledLanguages
                        if (list.size > 1) {
                            val current = instantActiveLanguage?.takeIf { it in list } ?: latestConfig.activeLanguage
                            val idx = list.indexOf(current).coerceAtLeast(0)
                            val next = list[(idx + delta + list.size) % list.size]
                            instantActiveLanguage = next
                            serviceScope.launch(Dispatchers.IO) { repository.setActiveLanguage(next) }
                        }
                    }
                }
                val onSelectLangCb = remember {
                    { lang: String ->
                        performHaptic(latestConfig)
                        session.resetAll()
                        instantActiveLanguage = lang
                        serviceScope.launch(Dispatchers.IO) { repository.setActiveLanguage(lang) }
                        Unit
                    }
                }
                val onTopActionCb = remember {
                    { actionId: String ->
                        performHaptic(latestConfig)
                        session.lastCorrection = null
                        handleTopAction(actionId)
                    }
                }
                val onHapticCb = remember { { performHaptic(latestConfig) } }
                val onPasteClipCb = remember {
                    { text: String ->
                        performHaptic(latestConfig)
                        session.resetAll()
                        currentInputConnection?.commitText(text, 1)
                        Unit
                    }
                }
                val onRemoveClipCb = remember { { text: String -> clipHistory.remove(text); Unit } }
                val onClearClipsCb = remember { { clipHistory.clear() } }

                KeyboardUI(
                    config = config,
                    palette = palette,
                    shiftState = shiftState,
                    clipboardHistory = clipHistory,
                    recentEmojis = recentEmojis,
                    emojiFontFamily = emojiFontFamily,
                    nextCharPriorsProvider = nextCharPriorsProvider,
                    touchBiasProvider = touchBiasProvider,
                    onTouchSample = onTouchSampleCb,
                    onKeyGeometryUpdate = onGeometryUpdateCb,
                    onCharWithPos = onCharWithPosCb,
                    onChar = onCharCb,
                    onEmoji = onEmojiCb,
                    onBackspace = onBackspaceCb,
                    onDeleteWord = onDeleteWordCb,
                    onSpace = onSpaceCb,
                    onEnter = onEnterCb,
                    onShiftTap = onShiftTapCb,
                    onCycleLanguage = onCycleLangCb,
                    onSelectLanguage = onSelectLangCb,
                    onTopAction = onTopActionCb,
                    onHaptic = onHapticCb,
                    onPasteClip = onPasteClipCb,
                    onRemoveClip = onRemoveClipCb,
                    onClearClips = onClearClipsCb
                )
            }
        }
    }

    private fun handleCharInput(ch: String, touchX: Float?, touchY: Float?) {
        val config = latestConfig
        performHaptic(config)
        val isSeparator = ch == "," || ch == "." || ch == "!" || ch == "?" || ch == ";" || ch == ":"
        if (isSeparator) {
            if (!isPasswordField && config.textExpanderEnabled && ch.isNotEmpty() && ch.last() in instantEndChars) {
                val ic = currentInputConnection
                val before = if (ic != null) runCatching { ic.getTextBeforeCursor(64, 0)?.toString() }.getOrNull() ?: "" else ""
                val combined = before + ch
                val matched = config.textSnippets.firstOrNull { s ->
                    !s.requireSpace && s.trigger.isNotEmpty() && combined.endsWith(s.trigger) &&
                        (combined.length == s.trigger.length || !combined[combined.length - s.trigger.length - 1].isLetterOrDigit())
                }
                if (matched != null && ic != null) {
                    val deleteBefore = (matched.trigger.length - ch.length).coerceAtLeast(0)
                    ic.beginBatchEdit()
                    if (deleteBefore > 0) ic.deleteSurroundingText(deleteBefore, 0)
                    ic.commitText(matched.expansion, 1)
                    ic.endBatchEdit()
                    session.finishWord(null)
                    session.lastCorrection = null
                    if (config.autoCap && (ch == "." || ch == "!" || ch == "?") && shiftState == 0 && shouldAutoCap()) {
                        shiftState = 1
                    }
                    return
                }
            }
            commitWithAutocorrect(ch, config)
            if (!isPasswordField && config.autoCap && (ch == "." || ch == "!" || ch == "?") && shiftState == 0 && shouldAutoCap()) {
                shiftState = 1
            }
            return
        }

        session.lastCorrection = null
        val out = if (shiftState > 0) ch.uppercase() else ch
        currentInputConnection?.commitText(out, 1)
        if (out.length == 1 && out[0].isLetter()) {
            session.appendChar(out[0], touchX, touchY)
        } else {
            session.finishWord(null)
        }

        if (!isPasswordField && config.textExpanderEnabled && out.isNotEmpty() && out.last() in instantEndChars) {
            checkInstantTextExpansion(config)
        }
        if (shiftState == 1) {
            shiftState = 0
        }
    }

    private fun handleBackspace() {
        val config = latestConfig
        performHaptic(config)
        val ic = currentInputConnection ?: return

        val lc = session.lastCorrection
        if (lc != null) {
            val expectedSuffix = lc.correctedWord + lc.separator
            val before = runCatching { ic.getTextBeforeCursor(expectedSuffix.length, 0)?.toString() }.getOrNull()
            if (before == expectedSuffix) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expectedSuffix.length, 0)
                ic.commitText(lc.originalWord, 1)
                ic.endBatchEdit()
                dictionary.suppressCorrection(lc.originalWord, config.activeLanguage)
                session.restoreWord(lc.originalWord)
                session.lastCorrection = null
                return
            }
            session.lastCorrection = null
        }

        revertLastTouchBias()
        session.dropLastChar()

        val selected = runCatching { ic.getSelectedText(0) }.getOrNull()
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        if (!isPasswordField && config.autoCap && shouldAutoCap() && shiftState == 0) {
            shiftState = 1
        } else if (!config.autoCap && shiftState == 1) {
            shiftState = 0
        }
    }

    private fun handleDeleteWord() {
        val config = latestConfig
        performHaptic(config)
        session.resetAll()
        val ic = currentInputConnection ?: return
        val before = runCatching { ic.getTextBeforeCursor(64, 0)?.toString() }.getOrNull()
        if (before.isNullOrEmpty()) return
        var idx = before.length
        while (idx > 0 && before[idx - 1].isWhitespace()) idx--
        while (idx > 0 && !before[idx - 1].isWhitespace()) idx--
        val deleteLen = (before.length - idx).coerceAtLeast(1)
        ic.deleteSurroundingText(deleteLen, 0)
        if (!isPasswordField && config.autoCap && shouldAutoCap() && shiftState == 0) {
            shiftState = 1
        }
    }

    private fun handleSpaceInput() {
        val config = latestConfig
        performHaptic(config)
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()

        val needBefore = !isPasswordField && (
            config.doubleSpacePeriod ||
            (config.textExpanderEnabled && config.textSnippets.isNotEmpty()) ||
            (!isNoSuggestions && config.autocorrectEnabled)
        )
        val before = if (needBefore) {
            runCatching { ic.getTextBeforeCursor(64, 0)?.toString() }.getOrNull() ?: ""
        } else ""

        if (!isPasswordField &&
            config.doubleSpacePeriod &&
            now - lastSpaceTime < 350 &&
            before.length >= 2 &&
            before.endsWith(" ") &&
            before[before.length - 2].isLetterOrDigit()
        ) {
            session.lastCorrection = null
            session.finishWord(null)
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            if (config.autoCap) shiftState = 1
        } else {
            commitWithAutocorrect(" ", config, prefetchedBefore = before)
            if (!isPasswordField && config.autoCap && shiftState != 2 && shouldAutoCap()) {
                shiftState = 1
            }
        }
        lastSpaceTime = now
    }

    private fun checkInstantTextExpansion(config: KeyboardConfig): Boolean {
        if (isPasswordField || !config.textExpanderEnabled || config.textSnippets.isEmpty()) return false
        val ic = currentInputConnection ?: return false
        val before = runCatching { ic.getTextBeforeCursor(64, 0)?.toString() }.getOrNull() ?: return false
        for (snippet in config.textSnippets) {
            val trig = snippet.trigger
            if (!snippet.requireSpace && trig.isNotEmpty() && before.endsWith(trig)) {
                val boundaryOk = before.length == trig.length || !before[before.length - trig.length - 1].isLetterOrDigit()
                if (boundaryOk) {
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(trig.length, 0)
                    ic.commitText(snippet.expansion, 1)
                    ic.endBatchEdit()
                    return true
                }
            }
        }
        return false
    }

    private fun commitWithAutocorrect(
        separator: String,
        config: KeyboardConfig,
        prefetchedBefore: String? = null
    ) {
        val ic = currentInputConnection ?: return
        if (isPasswordField) {
            session.resetAll()
            ic.commitText(separator, 1)
            return
        }

        val needBefore = (config.textExpanderEnabled && config.textSnippets.isNotEmpty()) ||
            (!isNoSuggestions && config.autocorrectEnabled)
        val before = prefetchedBefore ?: if (needBefore) {
            runCatching { ic.getTextBeforeCursor(64, 0)?.toString() }.getOrNull() ?: ""
        } else ""

        if (config.textExpanderEnabled && config.textSnippets.isNotEmpty()) {
            for (snippet in config.textSnippets) {
                val trig = snippet.trigger
                if (snippet.requireSpace && trig.isNotEmpty() && before.endsWith(trig)) {
                    val boundaryOk = before.length == trig.length || !before[before.length - trig.length - 1].isLetterOrDigit()
                    if (boundaryOk) {
                        ic.beginBatchEdit()
                        ic.deleteSurroundingText(trig.length, 0)
                        ic.commitText(snippet.expansion + separator, 1)
                        ic.endBatchEdit()
                        session.finishWord(null)
                        session.lastCorrection = null
                        return
                    }
                }
            }
        }

        val word = if (!isNoSuggestions && before.isNotEmpty()) extractTrailingWord(before) else ""
        val touches = if (word.length == session.touchPoints.size) session.touchPoints else emptyList()
        val p1 = session.prevWord1
        val p2 = session.prevWord2

        if (!isNoSuggestions && word.isNotEmpty() && config.autocorrectEnabled) {
            val suggestion = dictionary.suggestCorrection(
                word = word,
                lang = config.activeLanguage,
                prevWord1 = p1,
                prevWord2 = p2,
                touchPoints = touches,
                keyCenters = keyCentersMap,
                avgKeyWidthPx = avgKeyWidthPx
            )
            if (suggestion != null && suggestion != word) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(suggestion + separator, 1)
                ic.endBatchEdit()
                session.finishWord(suggestion, separator)
                session.lastCorrection = LastCorrection(word, suggestion, separator)
                return
            }
        }

        if (!isNoSuggestions && word.isNotEmpty()) {
            dictionary.learnWord(word, config.activeLanguage, prevWord1 = p1, prevWord2 = p2)
        }
        session.finishWord(word.ifEmpty { null }, separator)
        session.lastCorrection = null
        ic.commitText(separator, 1)
    }

    private fun extractTrailingWord(before: String): String {
        if (before.isEmpty()) return ""
        var idx = before.length
        while (idx > 0 && before[idx - 1].isLetter()) {
            idx--
        }
        if (idx > 0 && !before[idx - 1].isWhitespace() && before[idx - 1] !in listOf('(', '"', '«', '\n')) {
            return ""
        }
        return before.substring(idx)
    }

    private fun handleTopAction(actionId: String) {
        val ic = currentInputConnection ?: return
        when (actionId) {
            "SELECT_ALL" -> {
                val selected = runCatching { ic.getSelectedText(0) }.getOrNull()
                if (!selected.isNullOrEmpty()) {
                    val extracted = runCatching {
                        ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    }.getOrNull()
                    if (extracted != null && extracted.selectionEnd >= 0) {
                        ic.setSelection(extracted.selectionEnd, extracted.selectionEnd)
                    } else {
                        val now = android.os.SystemClock.uptimeMillis()
                        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
                        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
                    }
                } else {
                    ic.performContextMenuAction(android.R.id.selectAll)
                }
            }
            "COPY" -> ic.performContextMenuAction(android.R.id.copy)
            "PASTE" -> {
                if (!ic.performContextMenuAction(android.R.id.paste)) {
                    val clip = runCatching {
                        clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                    }.getOrNull()
                    if (!clip.isNullOrEmpty()) ic.commitText(clip, 1)
                }
            }
            "CUT" -> ic.performContextMenuAction(android.R.id.cut)
            "UNDO" -> {
                if (!ic.performContextMenuAction(android.R.id.undo)) {
                    val now = android.os.SystemClock.uptimeMillis()
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val inputType = info?.inputType ?: InputType.TYPE_NULL
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        isPasswordField = (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )) || (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        isNoSuggestions = isPasswordField || (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        captureClipboard()
        session.resetAll()
        shiftState = if (!isPasswordField && latestConfig.autoCap && shouldAutoCap()) 1 else 0
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        session.resetAll()
        dictionary.flushUserDictAsync()
        saveTouchBiasAsync()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
        hapticScope.cancel()
        hapticDispatcher.close()
        super.onDestroy()
    }

    private fun shouldAutoCap(): Boolean {
        val ic = currentInputConnection ?: return false
        val info = currentInputEditorInfo ?: return false
        if (info.inputType == InputType.TYPE_NULL) return false
        return runCatching { ic.getCursorCapsMode(info.inputType) != 0 }.getOrDefault(false)
    }

    private fun handleEnterAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val isMultiLine = info != null && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (!isMultiLine && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun recordTouchBias(ch: Char, dx: Float, dy: Float, keyW: Float, keyH: Float) {
        if (isPasswordField || keyW <= 1f || keyH <= 1f) return
        val lower = ch.lowercaseChar()
        val prev = touchBiasMap[lower]
        val alpha = 0.045f
        val maxDx = keyW * 0.22f
        val maxDy = keyH * 0.22f
        val nx = ((prev?.first ?: 0f) * (1f - alpha) + dx * alpha).coerceIn(-maxDx, maxDx)
        val ny = ((prev?.second ?: 0f) * (1f - alpha) + dy * alpha).coerceIn(-maxDy, maxDy)
        lastTouchChar = lower
        lastTouchDelta = prev
        touchBiasMap[lower] = nx to ny
    }

    private fun revertLastTouchBias() {
        val ch = lastTouchChar ?: return
        val prev = lastTouchDelta
        if (prev == null) {
            touchBiasMap.remove(ch)
        } else {
            touchBiasMap[ch] = prev
        }
        lastTouchChar = null
        lastTouchDelta = null
    }

    private val cachedVibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }
    private val hasVibrator: Boolean by lazy { cachedVibrator?.hasVibrator() == true }
    private val hasAmplitudeControl: Boolean by lazy { cachedVibrator?.hasAmplitudeControl() == true }
    private var lastHapticTimeMs = 0L

    private fun performHaptic(config: KeyboardConfig, isLightTick: Boolean = false) {
        if (!config.hapticEnabled || !hasVibrator) return
        val now = android.os.SystemClock.uptimeMillis()
        val minCooldown = if (isLightTick) 45L else 24L
        if (now - lastHapticTimeMs < minCooldown) return
        lastHapticTimeMs = now

        val vibrator = cachedVibrator ?: return
        val (durationMs, amplitude) = if (isLightTick) {
            6L to 95
        } else {
            when (config.hapticStrength) {
                1 -> 8L to 110
                3 -> 22L to 235
                else -> 14L to 175
            }
        }
        val finalAmp = if (hasAmplitudeControl) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(durationMs, finalAmp)
        hapticScope.launch {
            runCatching { vibrator.vibrate(effect) }
        }
    }
}
