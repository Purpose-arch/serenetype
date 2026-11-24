package com.serenetype.keyboard.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "serenetype_settings")

enum class ThemeMode(val label: String, val subtitle: String) {
    IOS_CLASSIC("ios classic", "iphone light/dark"),
    GRAPHITE_SCREENSHOT("graphite", "dark #1c1c1e · #0a84ff"),
    MATERIAL_YOU("material you", "dynamic wallpaper colors"),
    OLED_PURE("oled black", "pure #000000"),
    CUSTOM("custom", "custom palette")
}

enum class SmartTapMode(val label: String, val alphaWeight: Float) {
    OFF("off", 0f),
    BALANCED("balanced", 1.35f),
    STRONG("strong", 2.35f)
}

enum class CandidateBarMode(val label: String) {
    OFF("off"),
    SEPARATE("separate"),
    INLINE("inline"),
    DYNAMIC("dynamic")
}

enum class KeyFontStyle(val label: String) {
    SYSTEM_SANS("system"),
    ROUNDED("serif"),
    MONOSPACE("mono")
}

enum class TopBarButton(val id: String, val label: String) {
    EMOJI("EMOJI", "emoji"),
    CLIPBOARD("CLIPBOARD", "clipboard"),
    SELECT_ALL("SELECT_ALL", "select all"),
    COPY("COPY", "copy"),
    PASTE("PASTE", "paste"),
    CUT("CUT", "cut"),
    UNDO("UNDO", "undo")
}

@Immutable
data class TextSnippet(
    val id: String,
    val trigger: String,
    val expansion: String,
    val requireSpace: Boolean = false
)

@Immutable
data class CustomThemeSpec(
    val id: String,
    val name: String,
    val trayColorHex: String,
    val keyColorHex: String,
    val funcKeyColorHex: String,
    val textColorHex: String,
    val accentColorHex: String
)

@Immutable
data class KeyboardConfig(
    val themeMode: ThemeMode = ThemeMode.IOS_CLASSIC,
    val activeCustomThemeId: String = "custom_default",
    val customThemes: List<CustomThemeSpec> = listOf(
        CustomThemeSpec(
            id = "custom_default",
            name = "graphite",
            trayColorHex = "#1C1C1E",
            keyColorHex = "#3A3A3A",
            funcKeyColorHex = "#2C2C2E",
            textColorHex = "#FFFFFF",
            accentColorHex = "#0A84FF"
        )
    ),

    val cornerRadiusDp: Float = 6.0f,
    val funcCornerRadiusDp: Float = 6.0f,
    val horizontalGapDp: Float = 3.5f,
    val verticalGapDp: Float = 6.0f,
    val keyHeightDp: Float = 47.0f,
    val sidePaddingDp: Float = 2.5f,
    val bottomPaddingDp: Float = 36.0f,
    val showKeyShadow: Boolean = true,
    val showKeyBorder: Boolean = false,

    val showTopBar: Boolean = true,
    val topBarButtons: List<String> = listOf("EMOJI", "CLIPBOARD", "SELECT_ALL", "COPY", "PASTE"),

    val bottomRowOrder: List<String> = listOf("123", "COMMA", "SPACE", "PERIOD", "ENTER"),
    val symKeyWeight: Float = 1.4f,
    val spaceWeight: Float = 4.3f,
    val enterWeight: Float = 1.65f,
    val punctWeight: Float = 0.9f,

    val fontStyle: KeyFontStyle = KeyFontStyle.SYSTEM_SANS,
    val keyTextSizeSp: Float = 24.5f,
    val showKeyPopup: Boolean = true,

    val enabledLanguages: List<String> = listOf("en", "ru"),
    val activeLanguage: String = "en",
    val spacebarSwipeLang: Boolean = true,
    val showSpacebarLabel: Boolean = true,
    val showGlobeKey: Boolean = false,
    val showEmojiKey: Boolean = false,
    val showNumberRow: Boolean = false,
    val autoCap: Boolean = false,
    val doubleSpacePeriod: Boolean = false,
    val autocorrectEnabled: Boolean = true,
    val smartTapMode: SmartTapMode = SmartTapMode.BALANCED,
    val candidateBarMode: CandidateBarMode = CandidateBarMode.OFF,

    val recentEmojis: List<String> = listOf("😂", "❤️", "🔥", "👍", "😭", "🙏", "✨", "🥰"),

    val instantKeyCommit: Boolean = true,
    val hapticEnabled: Boolean = true,
    val hapticStrength: Int = 2,

    val sym1Row1: String = "1 2 3 4 5 6 7 8 9 0",
    val sym1Row2: String = "- / : ; ( ) $ & @",
    val sym1Row3: String = "* \" ‘ . , ! ?",
    val sym2Row1: String = "[ ] { } # % ^ * + =",
    val sym2Row2: String = "_ \\ | ~ < > € £ ¥ •",
    val sym2Row3: String = "` √ π ÷ × ¶ ∆ °",

    val quickSymbolPopupEnabled: Boolean = true,
    val quickSymbolPopupChars: String = "1 2 3 4 5 6 7 8 9 0 ! ? @ # $ % & * - + / ( )",
    val quickSymbolPopupDelayMs: Int = 180,

    val textExpanderEnabled: Boolean = true,
    val textSnippets: List<TextSnippet> = emptyList()
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_CUSTOM_ID = stringPreferencesKey("active_custom_id")
        val CUSTOM_THEMES_JSON = stringPreferencesKey("custom_themes_json")

        val CORNER_RADIUS = floatPreferencesKey("corner_radius")
        val FUNC_CORNER_RADIUS = floatPreferencesKey("func_corner_radius")
        val H_GAP = floatPreferencesKey("h_gap")
        val V_GAP = floatPreferencesKey("v_gap")
        val KEY_HEIGHT = floatPreferencesKey("key_height")
        val SIDE_PAD = floatPreferencesKey("side_pad")
        val BOTTOM_PAD = floatPreferencesKey("bottom_pad")
        val SHOW_SHADOW = booleanPreferencesKey("show_shadow")
        val SHOW_BORDER = booleanPreferencesKey("show_border")

        val SHOW_TOP_BAR = booleanPreferencesKey("show_top_bar")
        val TOP_BAR_BUTTONS = stringPreferencesKey("top_bar_buttons")

        val BOTTOM_ORDER = stringPreferencesKey("bottom_order")
        val SYM_WEIGHT = floatPreferencesKey("sym_weight")
        val SPACE_WEIGHT = floatPreferencesKey("space_weight")
        val ENTER_WEIGHT = floatPreferencesKey("enter_weight")
        val PUNCT_WEIGHT = floatPreferencesKey("punct_weight")

        val FONT_STYLE = stringPreferencesKey("font_style")
        val TEXT_SIZE = floatPreferencesKey("text_size")
        val SHOW_POPUP = booleanPreferencesKey("show_popup")

        val ENABLED_LANGS = stringPreferencesKey("enabled_langs")
        val ACTIVE_LANG = stringPreferencesKey("active_lang")
        val SPACE_SWIPE = booleanPreferencesKey("space_swipe")
        val SHOW_SPACE_LABEL = booleanPreferencesKey("show_space_label")
        val SHOW_GLOBE = booleanPreferencesKey("show_globe")
        val SHOW_EMOJI = booleanPreferencesKey("show_emoji")
        val SHOW_NUM_ROW = booleanPreferencesKey("show_num_row")
        val AUTO_CAP = booleanPreferencesKey("auto_cap")
        val DOUBLE_SPACE_DOT = booleanPreferencesKey("double_space_dot")
        val AUTOCORRECT = booleanPreferencesKey("autocorrect")
        val SMART_TAP_MODE = stringPreferencesKey("smart_tap_mode")
        val CANDIDATE_BAR_MODE = stringPreferencesKey("candidate_bar_mode")
        val PINNED_CLIPS_JSON = stringPreferencesKey("pinned_clips_json")

        val RECENT_EMOJIS = stringPreferencesKey("recent_emojis")

        val INSTANT_KEY_COMMIT = booleanPreferencesKey("instant_key_commit")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength")

        val SYM1_ROW1 = stringPreferencesKey("sym1_row1")
        val SYM1_ROW2 = stringPreferencesKey("sym1_row2")
        val SYM1_ROW3 = stringPreferencesKey("sym1_row3")
        val SYM2_ROW1 = stringPreferencesKey("sym2_row1")
        val SYM2_ROW2 = stringPreferencesKey("sym2_row2")
        val SYM2_ROW3 = stringPreferencesKey("sym2_row3")

        val QUICK_SYM_ENABLED = booleanPreferencesKey("quick_sym_enabled")
        val QUICK_SYM_CHARS = stringPreferencesKey("quick_sym_chars")
        val QUICK_SYM_DELAY = intPreferencesKey("quick_sym_delay")

        val TEXT_EXPANDER_ENABLED = booleanPreferencesKey("text_expander_enabled")
        val TEXT_SNIPPETS_JSON = stringPreferencesKey("text_snippets_json")
    }

    val recentEmojisFlow: Flow<List<String>> = context.keyboardDataStore.data
        .map { prefs ->
            prefs[Keys.RECENT_EMOJIS]
                ?.split("|")
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: KeyboardConfig().recentEmojis
        }
        .distinctUntilChanged()

    val configFlow: Flow<KeyboardConfig> = context.keyboardDataStore.data
        .map { prefs ->
            val default = KeyboardConfig()
            val themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: default.themeMode.name)
            }.getOrDefault(default.themeMode)

            val fontStyle = runCatching {
                KeyFontStyle.valueOf(prefs[Keys.FONT_STYLE] ?: default.fontStyle.name)
            }.getOrDefault(default.fontStyle)

            val langs = prefs[Keys.ENABLED_LANGS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it in KeyboardLayouts.languages }
                ?.takeIf { it.isNotEmpty() }
                ?: default.enabledLanguages

            val activeLang = prefs[Keys.ACTIVE_LANG]?.takeIf { it in langs } ?: langs.first()

            val validTopIds = TopBarButton.entries.map { it.id }.toSet()
            val topButtons = prefs[Keys.TOP_BAR_BUTTONS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it in validTopIds }
                ?.distinct()
                ?: default.topBarButtons

            val validBottomIds = setOf("123", "COMMA", "SPACE", "PERIOD", "ENTER", "GLOBE", "EMOJI")
            val requiredBottom = setOf("123", "SPACE", "ENTER")
            val bottomOrder = prefs[Keys.BOTTOM_ORDER]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it in validBottomIds }
                ?.distinct()
                ?.takeIf { it.containsAll(requiredBottom) }
                ?: default.bottomRowOrder

            val customThemes = prefs[Keys.CUSTOM_THEMES_JSON]
                ?.let(::parseCustomThemes)
                ?.takeIf { it.isNotEmpty() }
                ?: default.customThemes

            KeyboardConfig(
                themeMode = themeMode,
                activeCustomThemeId = prefs[Keys.ACTIVE_CUSTOM_ID] ?: default.activeCustomThemeId,
                customThemes = customThemes,
                cornerRadiusDp = (prefs[Keys.CORNER_RADIUS] ?: default.cornerRadiusDp).coerceIn(0f, 20f),
                funcCornerRadiusDp = (prefs[Keys.FUNC_CORNER_RADIUS] ?: default.funcCornerRadiusDp).coerceIn(0f, 20f),
                horizontalGapDp = (prefs[Keys.H_GAP] ?: default.horizontalGapDp).coerceIn(1f, 8f),
                verticalGapDp = (prefs[Keys.V_GAP] ?: default.verticalGapDp).coerceIn(2f, 14f),
                keyHeightDp = (prefs[Keys.KEY_HEIGHT] ?: default.keyHeightDp).coerceIn(36f, 62f),
                sidePaddingDp = (prefs[Keys.SIDE_PAD] ?: default.sidePaddingDp).coerceIn(0f, 24f),
                bottomPaddingDp = (prefs[Keys.BOTTOM_PAD] ?: default.bottomPaddingDp).coerceIn(2f, 102f),
                showKeyShadow = prefs[Keys.SHOW_SHADOW] ?: default.showKeyShadow,
                showKeyBorder = prefs[Keys.SHOW_BORDER] ?: default.showKeyBorder,
                showTopBar = prefs[Keys.SHOW_TOP_BAR] ?: default.showTopBar,
                topBarButtons = topButtons,
                bottomRowOrder = bottomOrder,
                symKeyWeight = (prefs[Keys.SYM_WEIGHT] ?: default.symKeyWeight).coerceIn(0.9f, 2.2f),
                spaceWeight = (prefs[Keys.SPACE_WEIGHT] ?: default.spaceWeight).coerceIn(2.4f, 6.5f),
                enterWeight = (prefs[Keys.ENTER_WEIGHT] ?: default.enterWeight).coerceIn(1.1f, 2.6f),
                punctWeight = (prefs[Keys.PUNCT_WEIGHT] ?: default.punctWeight).coerceIn(0.6f, 1.5f),
                fontStyle = fontStyle,
                keyTextSizeSp = (prefs[Keys.TEXT_SIZE] ?: default.keyTextSizeSp).coerceIn(16f, 30f),
                showKeyPopup = prefs[Keys.SHOW_POPUP] ?: default.showKeyPopup,
                enabledLanguages = langs,
                activeLanguage = activeLang,
                spacebarSwipeLang = prefs[Keys.SPACE_SWIPE] ?: default.spacebarSwipeLang,
                showSpacebarLabel = prefs[Keys.SHOW_SPACE_LABEL] ?: default.showSpacebarLabel,
                showGlobeKey = prefs[Keys.SHOW_GLOBE] ?: default.showGlobeKey,
                showEmojiKey = prefs[Keys.SHOW_EMOJI] ?: default.showEmojiKey,
                showNumberRow = prefs[Keys.SHOW_NUM_ROW] ?: default.showNumberRow,
                autoCap = prefs[Keys.AUTO_CAP] ?: default.autoCap,
                doubleSpacePeriod = prefs[Keys.DOUBLE_SPACE_DOT] ?: default.doubleSpacePeriod,
                autocorrectEnabled = prefs[Keys.AUTOCORRECT] ?: default.autocorrectEnabled,
                smartTapMode = runCatching {
                    SmartTapMode.valueOf(prefs[Keys.SMART_TAP_MODE] ?: default.smartTapMode.name)
                }.getOrDefault(default.smartTapMode),
                candidateBarMode = runCatching {
                    CandidateBarMode.valueOf(prefs[Keys.CANDIDATE_BAR_MODE] ?: default.candidateBarMode.name)
                }.getOrDefault(default.candidateBarMode),
                recentEmojis = default.recentEmojis,
                instantKeyCommit = prefs[Keys.INSTANT_KEY_COMMIT] ?: default.instantKeyCommit,
                hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: default.hapticEnabled,
                hapticStrength = (prefs[Keys.HAPTIC_STRENGTH] ?: default.hapticStrength).coerceIn(1, 3),
                sym1Row1 = prefs[Keys.SYM1_ROW1]?.takeIf { it.isNotBlank() } ?: default.sym1Row1,
                sym1Row2 = prefs[Keys.SYM1_ROW2]?.takeIf { it.isNotBlank() } ?: default.sym1Row2,
                sym1Row3 = prefs[Keys.SYM1_ROW3]?.takeIf { it.isNotBlank() } ?: default.sym1Row3,
                sym2Row1 = prefs[Keys.SYM2_ROW1]?.takeIf { it.isNotBlank() } ?: default.sym2Row1,
                sym2Row2 = prefs[Keys.SYM2_ROW2]?.takeIf { it.isNotBlank() } ?: default.sym2Row2,
                sym2Row3 = prefs[Keys.SYM2_ROW3]?.takeIf { it.isNotBlank() } ?: default.sym2Row3,
                quickSymbolPopupEnabled = prefs[Keys.QUICK_SYM_ENABLED] ?: default.quickSymbolPopupEnabled,
                quickSymbolPopupChars = prefs[Keys.QUICK_SYM_CHARS]?.takeIf { it.isNotBlank() } ?: default.quickSymbolPopupChars,
                quickSymbolPopupDelayMs = (prefs[Keys.QUICK_SYM_DELAY] ?: default.quickSymbolPopupDelayMs).coerceIn(100, 450),
                textExpanderEnabled = prefs[Keys.TEXT_EXPANDER_ENABLED] ?: default.textExpanderEnabled,
                textSnippets = prefs[Keys.TEXT_SNIPPETS_JSON]?.let(::parseSnippets) ?: default.textSnippets
            )
        }
        .distinctUntilChanged()

    suspend fun recordRecentEmoji(emoji: String) = context.keyboardDataStore.edit { prefs ->
        val current = (prefs[Keys.RECENT_EMOJIS] ?: KeyboardConfig().recentEmojis.joinToString("|"))
            .split("|").filter { it.isNotEmpty() }.toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        if (current.size > 32) current.removeAt(current.lastIndex)
        prefs[Keys.RECENT_EMOJIS] = current.joinToString("|")
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.keyboardDataStore.edit {
        it[Keys.THEME_MODE] = mode.name
    }

    suspend fun saveCustomTheme(spec: CustomThemeSpec) = context.keyboardDataStore.edit { prefs ->
        val existing = prefs[Keys.CUSTOM_THEMES_JSON]?.let(::parseCustomThemes)?.toMutableList()
            ?: KeyboardConfig().customThemes.toMutableList()
        val idx = existing.indexOfFirst { it.id == spec.id }
        if (idx >= 0) existing[idx] = spec else existing.add(spec)
        prefs[Keys.CUSTOM_THEMES_JSON] = serializeCustomThemes(existing)
        prefs[Keys.ACTIVE_CUSTOM_ID] = spec.id
        prefs[Keys.THEME_MODE] = ThemeMode.CUSTOM.name
    }

    suspend fun selectCustomTheme(id: String) = context.keyboardDataStore.edit {
        it[Keys.ACTIVE_CUSTOM_ID] = id
        it[Keys.THEME_MODE] = ThemeMode.CUSTOM.name
    }

    suspend fun deleteCustomTheme(id: String) = context.keyboardDataStore.edit { prefs ->
        val existing = prefs[Keys.CUSTOM_THEMES_JSON]?.let(::parseCustomThemes)?.toMutableList() ?: return@edit
        if (existing.size <= 1) return@edit
        existing.removeAll { it.id == id }
        prefs[Keys.CUSTOM_THEMES_JSON] = serializeCustomThemes(existing)
        if (prefs[Keys.ACTIVE_CUSTOM_ID] == id) {
            prefs[Keys.ACTIVE_CUSTOM_ID] = existing.first().id
        }
    }

    suspend fun setBottomRowOrder(order: List<String>) = context.keyboardDataStore.edit {
        it[Keys.BOTTOM_ORDER] = order.joinToString(",")
    }

    suspend fun toggleBottomKey(id: String) = context.keyboardDataStore.edit { prefs ->
        val valid = setOf("123", "COMMA", "SPACE", "PERIOD", "ENTER", "GLOBE", "EMOJI")
        if (id !in valid || id in setOf("123", "SPACE", "ENTER")) return@edit
        val current = (prefs[Keys.BOTTOM_ORDER] ?: KeyboardConfig().bottomRowOrder.joinToString(","))
            .split(",").map { it.trim() }.filter { it in valid }.distinct().toMutableList()
        if (id in current) {
            current.remove(id)
        } else {
            val spaceIdx = current.indexOf("SPACE").coerceAtLeast(0)
            val enterIdx = current.indexOf("ENTER").let { if (it >= 0) it else current.size }
            when (id) {
                "PERIOD" -> current.add(enterIdx, id)
                else -> current.add((spaceIdx + 1).coerceAtMost(current.size), id)
            }
        }
        prefs[Keys.BOTTOM_ORDER] = current.joinToString(",")
        prefs[Keys.SHOW_GLOBE] = "GLOBE" in current
        prefs[Keys.SHOW_EMOJI] = "EMOJI" in current
    }

    suspend fun setTopBarButtons(buttons: List<String>) = context.keyboardDataStore.edit {
        it[Keys.TOP_BAR_BUTTONS] = buttons.joinToString(",")
    }

    suspend fun toggleTopBarButton(id: String) = context.keyboardDataStore.edit { prefs ->
        val validIds = TopBarButton.entries.map { it.id }.toSet()
        val current = (prefs[Keys.TOP_BAR_BUTTONS] ?: KeyboardConfig().topBarButtons.joinToString(","))
            .split(",").map { it.trim() }.filter { it in validIds }.toMutableList()
        if (id in current) current.remove(id) else current.add(id)
        prefs[Keys.TOP_BAR_BUTTONS] = current.joinToString(",")
    }

    suspend fun disableAllHelpers() = context.keyboardDataStore.edit { prefs ->
        prefs[Keys.AUTO_CAP] = false
        prefs[Keys.DOUBLE_SPACE_DOT] = false
        prefs[Keys.AUTOCORRECT] = false
    }

    suspend fun setFloat(keyName: String, value: Float) = context.keyboardDataStore.edit { prefs ->
        when (keyName) {
            "cornerRadius" -> prefs[Keys.CORNER_RADIUS] = value.coerceIn(0f, 20f)
            "funcCornerRadius" -> prefs[Keys.FUNC_CORNER_RADIUS] = value.coerceIn(0f, 20f)
            "hGap" -> prefs[Keys.H_GAP] = value.coerceIn(1f, 8f)
            "vGap" -> prefs[Keys.V_GAP] = value.coerceIn(2f, 14f)
            "keyHeight" -> prefs[Keys.KEY_HEIGHT] = value.coerceIn(36f, 62f)
            "sidePad" -> prefs[Keys.SIDE_PAD] = value.coerceIn(0f, 24f)
            "bottomPad" -> prefs[Keys.BOTTOM_PAD] = value.coerceIn(2f, 102f)
            "symWeight" -> prefs[Keys.SYM_WEIGHT] = value.coerceIn(0.9f, 2.2f)
            "spaceWeight" -> prefs[Keys.SPACE_WEIGHT] = value.coerceIn(2.4f, 6.5f)
            "enterWeight" -> prefs[Keys.ENTER_WEIGHT] = value.coerceIn(1.1f, 2.6f)
            "punctWeight" -> prefs[Keys.PUNCT_WEIGHT] = value.coerceIn(0.6f, 1.5f)
            "textSize" -> prefs[Keys.TEXT_SIZE] = value.coerceIn(16f, 30f)
        }
    }

    suspend fun setBool(keyName: String, value: Boolean) = context.keyboardDataStore.edit { prefs ->
        when (keyName) {
            "showShadow" -> prefs[Keys.SHOW_SHADOW] = value
            "showBorder" -> prefs[Keys.SHOW_BORDER] = value
            "showPopup" -> prefs[Keys.SHOW_POPUP] = value
            "showTopBar" -> prefs[Keys.SHOW_TOP_BAR] = value
            "spaceSwipe" -> prefs[Keys.SPACE_SWIPE] = value
            "showSpaceLabel" -> prefs[Keys.SHOW_SPACE_LABEL] = value
            "showGlobe" -> prefs[Keys.SHOW_GLOBE] = value
            "showEmoji" -> prefs[Keys.SHOW_EMOJI] = value
            "showNumRow" -> prefs[Keys.SHOW_NUM_ROW] = value
            "autoCap" -> prefs[Keys.AUTO_CAP] = value
            "doubleSpaceDot" -> prefs[Keys.DOUBLE_SPACE_DOT] = value
            "autocorrect" -> prefs[Keys.AUTOCORRECT] = value
            "instantKeyCommit" -> prefs[Keys.INSTANT_KEY_COMMIT] = value
            "hapticEnabled" -> prefs[Keys.HAPTIC_ENABLED] = value
            "quickSymEnabled" -> prefs[Keys.QUICK_SYM_ENABLED] = value
            "textExpander" -> prefs[Keys.TEXT_EXPANDER_ENABLED] = value
        }
    }

    suspend fun setFontStyle(style: KeyFontStyle) = context.keyboardDataStore.edit {
        it[Keys.FONT_STYLE] = style.name
    }

    suspend fun setSmartTapMode(mode: SmartTapMode) = context.keyboardDataStore.edit {
        it[Keys.SMART_TAP_MODE] = mode.name
    }

    suspend fun setCandidateBarMode(mode: CandidateBarMode) = context.keyboardDataStore.edit {
        it[Keys.CANDIDATE_BAR_MODE] = mode.name
    }

    val pinnedClipsFlow: Flow<List<String>> = context.keyboardDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.PINNED_CLIPS_JSON] ?: return@map emptyList()
            runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { i -> arr.getString(i) }.filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }
        .distinctUntilChanged()

    suspend fun togglePinnedClip(text: String) = context.keyboardDataStore.edit { prefs ->
        val list = prefs[Keys.PINNED_CLIPS_JSON]?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                MutableList(arr.length()) { i -> arr.getString(i) }
            }.getOrNull()
        } ?: mutableListOf()
        if (text in list) list.remove(text) else list.add(0, text)
        if (list.size > 30) list.removeAt(list.lastIndex)
        val out = JSONArray()
        list.forEach { out.put(it) }
        prefs[Keys.PINNED_CLIPS_JSON] = out.toString()
    }

    suspend fun clearPinnedClips() = context.keyboardDataStore.edit { prefs ->
        prefs.remove(Keys.PINNED_CLIPS_JSON)
    }

    suspend fun setHapticStrength(strength: Int) = context.keyboardDataStore.edit {
        it[Keys.HAPTIC_STRENGTH] = strength.coerceIn(1, 3)
    }

    suspend fun setSymbolRows(
        sym1Row1: String,
        sym1Row2: String,
        sym1Row3: String,
        sym2Row1: String,
        sym2Row2: String,
        sym2Row3: String
    ) = context.keyboardDataStore.edit { prefs ->
        val d = KeyboardConfig()
        prefs[Keys.SYM1_ROW1] = sym1Row1.trim().ifEmpty { d.sym1Row1 }
        prefs[Keys.SYM1_ROW2] = sym1Row2.trim().ifEmpty { d.sym1Row2 }
        prefs[Keys.SYM1_ROW3] = sym1Row3.trim().ifEmpty { d.sym1Row3 }
        prefs[Keys.SYM2_ROW1] = sym2Row1.trim().ifEmpty { d.sym2Row1 }
        prefs[Keys.SYM2_ROW2] = sym2Row2.trim().ifEmpty { d.sym2Row2 }
        prefs[Keys.SYM2_ROW3] = sym2Row3.trim().ifEmpty { d.sym2Row3 }
    }

    suspend fun resetSymbolRows() = context.keyboardDataStore.edit { prefs ->
        prefs.remove(Keys.SYM1_ROW1)
        prefs.remove(Keys.SYM1_ROW2)
        prefs.remove(Keys.SYM1_ROW3)
        prefs.remove(Keys.SYM2_ROW1)
        prefs.remove(Keys.SYM2_ROW2)
        prefs.remove(Keys.SYM2_ROW3)
    }

    suspend fun setQuickSymbolPopup(chars: String, delayMs: Int) = context.keyboardDataStore.edit { prefs ->
        val d = KeyboardConfig()
        prefs[Keys.QUICK_SYM_CHARS] = chars.trim().ifEmpty { d.quickSymbolPopupChars }
        prefs[Keys.QUICK_SYM_DELAY] = delayMs.coerceIn(100, 450)
    }

    suspend fun setActiveLanguage(lang: String) = context.keyboardDataStore.edit {
        it[Keys.ACTIVE_LANG] = lang
    }

    suspend fun toggleLanguage(lang: String) = context.keyboardDataStore.edit { prefs ->
        val current = (prefs[Keys.ENABLED_LANGS] ?: "en,ru")
            .split(",").map { it.trim() }.filter { it in KeyboardLayouts.languages }.toMutableList()
        if (lang in current) {
            if (current.size > 1) {
                current.remove(lang)
                if (prefs[Keys.ACTIVE_LANG] == lang) {
                    prefs[Keys.ACTIVE_LANG] = current.first()
                }
            }
        } else {
            current.add(lang)
        }
        prefs[Keys.ENABLED_LANGS] = current.joinToString(",")
    }

    suspend fun resetIosDefaults() = context.keyboardDataStore.edit { prefs ->
        prefs[Keys.THEME_MODE] = ThemeMode.IOS_CLASSIC.name
        prefs[Keys.CORNER_RADIUS] = 6.0f
        prefs[Keys.FUNC_CORNER_RADIUS] = 6.0f
        prefs[Keys.H_GAP] = 3.5f
        prefs[Keys.V_GAP] = 6.0f
        prefs[Keys.KEY_HEIGHT] = 47.0f
        prefs[Keys.SIDE_PAD] = 2.5f
        prefs[Keys.BOTTOM_PAD] = 36.0f
        prefs[Keys.BOTTOM_ORDER] = "123,COMMA,SPACE,PERIOD,ENTER"
        prefs[Keys.SYM_WEIGHT] = 1.4f
        prefs[Keys.SPACE_WEIGHT] = 4.3f
        prefs[Keys.ENTER_WEIGHT] = 1.65f
        prefs[Keys.PUNCT_WEIGHT] = 0.9f
        prefs[Keys.SHOW_SHADOW] = true
        prefs[Keys.SHOW_BORDER] = false
        prefs[Keys.FONT_STYLE] = KeyFontStyle.SYSTEM_SANS.name
        prefs[Keys.TEXT_SIZE] = 24.5f
        prefs[Keys.SHOW_POPUP] = true
        prefs[Keys.SHOW_NUM_ROW] = false
        prefs[Keys.SHOW_SPACE_LABEL] = true
        prefs[Keys.SHOW_GLOBE] = false
        prefs[Keys.SHOW_EMOJI] = false
        prefs[Keys.SHOW_TOP_BAR] = true
        prefs[Keys.TOP_BAR_BUTTONS] = "EMOJI,CLIPBOARD,SELECT_ALL,COPY,PASTE"
    }

    suspend fun saveTextSnippet(snippet: TextSnippet) = context.keyboardDataStore.edit { prefs ->
        val list = prefs[Keys.TEXT_SNIPPETS_JSON]?.let(::parseSnippets)?.toMutableList() ?: mutableListOf()
        val idx = list.indexOfFirst { it.id == snippet.id }
        if (idx >= 0) list[idx] = snippet else list.add(snippet)
        prefs[Keys.TEXT_SNIPPETS_JSON] = serializeSnippets(list)
    }

    suspend fun deleteTextSnippet(id: String) = context.keyboardDataStore.edit { prefs ->
        val list = prefs[Keys.TEXT_SNIPPETS_JSON]?.let(::parseSnippets)?.toMutableList() ?: return@edit
        list.removeAll { it.id == id }
        prefs[Keys.TEXT_SNIPPETS_JSON] = serializeSnippets(list)
    }

    private fun parseSnippets(raw: String): List<TextSnippet> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            TextSnippet(
                id = o.getString("id"),
                trigger = o.getString("trigger"),
                expansion = o.getString("expansion"),
                requireSpace = o.optBoolean("requireSpace", false)
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeSnippets(list: List<TextSnippet>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("trigger", s.trigger)
                put("expansion", s.expansion)
                put("requireSpace", s.requireSpace)
            })
        }
        return arr.toString()
    }

    private fun parseCustomThemes(raw: String): List<CustomThemeSpec> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CustomThemeSpec(
                id = o.getString("id"),
                name = o.getString("name"),
                trayColorHex = o.getString("tray"),
                keyColorHex = o.getString("key"),
                funcKeyColorHex = o.getString("func"),
                textColorHex = o.getString("text"),
                accentColorHex = o.getString("accent")
            )
        }
    }.getOrDefault(emptyList())

    private fun serializeCustomThemes(list: List<CustomThemeSpec>): String {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("tray", t.trayColorHex)
                put("key", t.keyColorHex)
                put("func", t.funcKeyColorHex)
                put("text", t.textColorHex)
                put("accent", t.accentColorHex)
            })
        }
        return arr.toString()
    }
}
