package com.serenetype.keyboard.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupImportResult(
    val appliedPreferences: Int,
    val skippedPreferences: Int,
    val restoredUserFiles: Int
)

/**
 * Forward- and backward-compatible JSON backup/restore engine:
 * - Dynamically exports ALL DataStore preferences (with explicit type tags) + user dictionaries & touch bias.
 * - Imports per-key with strict type/enum/range validation so newer/older app versions
 *   restore every compatible setting even if some keys were added, removed, or changed.
 */
object SettingsBackupManager {
    private const val SCHEMA_VERSION = 2
    private val USER_DATA_FILES = listOf(
        "user_dict.json",
        "user_bigrams.json",
        "user_trigrams.json",
        "touch_bias.json"
    )

    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefsMap = appCtx.keyboardDataStore.data.first().asMap()

        val prefsJson = JSONObject()
        prefsMap.entries
            .sortedBy { it.key.name }
            .forEach { (key, value) ->
                val entry = JSONObject()
                when (value) {
                    is String -> {
                        entry.put("type", "string")
                        entry.put("value", value)
                    }
                    is Boolean -> {
                        entry.put("type", "boolean")
                        entry.put("value", value)
                    }
                    is Int -> {
                        entry.put("type", "int")
                        entry.put("value", value)
                    }
                    is Float -> {
                        entry.put("type", "float")
                        entry.put("value", value.toDouble())
                    }
                    is Long -> {
                        entry.put("type", "long")
                        entry.put("value", value)
                    }
                    is Set<*> -> {
                        entry.put("type", "string_set")
                        val arr = JSONArray()
                        value.filterIsInstance<String>().forEach { arr.put(it) }
                        entry.put("value", arr)
                    }
                    else -> return@forEach
                }
                prefsJson.put(key.name, entry)
            }

        val userDataJson = JSONObject()
        for (fileName in USER_DATA_FILES) {
            val f = File(appCtx.filesDir, fileName)
            if (f.exists() && f.length() > 0L) {
                runCatching {
                    userDataJson.put(fileName, JSONObject(f.readText()))
                }
            }
        }

        JSONObject().apply {
            put("app", "serenetype")
            put("schema_version", SCHEMA_VERSION)
            put("exported_at", System.currentTimeMillis())
            put("preferences", prefsJson)
            put("user_data", userDataJson)
        }.toString(2)
    }

    suspend fun importBackupJson(context: Context, rawJson: String): BackupImportResult = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val root = JSONObject(rawJson)
        val appTag = root.optString("app", "")
        require(appTag == "serenetype" || root.has("preferences")) { "Invalid SereneType backup file" }

        val prefsJson = root.optJSONObject("preferences") ?: JSONObject()
        var applied = 0
        var skipped = 0

        appCtx.keyboardDataStore.edit { mutablePrefs ->
            val keys = prefsJson.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val entry = prefsJson.optJSONObject(keyName)
                if (entry == null) {
                    skipped++
                    continue
                }
                val ok = applySinglePreference(mutablePrefs, keyName, entry)
                if (ok) applied++ else skipped++
            }
        }

        var restoredFiles = 0
        val userDataJson = root.optJSONObject("user_data")
        if (userDataJson != null) {
            for (fileName in USER_DATA_FILES) {
                val obj = userDataJson.optJSONObject(fileName) ?: continue
                runCatching {
                    File(appCtx.filesDir, fileName).writeText(obj.toString())
                    restoredFiles++
                }
            }
        }

        BackupImportResult(
            appliedPreferences = applied,
            skippedPreferences = skipped,
            restoredUserFiles = restoredFiles
        )
    }

    private fun applySinglePreference(
        prefs: MutablePreferences,
        keyName: String,
        entry: JSONObject
    ): Boolean {
        return runCatching {
            val type = entry.optString("type", "")
            when (type) {
                "string" -> {
                    val v = entry.getString("value")
                    if (!validateKnownString(keyName, v)) return@runCatching false
                    prefs[stringPreferencesKey(keyName)] = v
                    true
                }
                "boolean" -> {
                    prefs[booleanPreferencesKey(keyName)] = entry.getBoolean("value")
                    true
                }
                "int" -> {
                    val v = entry.getInt("value")
                    val clamped = when (keyName) {
                        "haptic_strength" -> v.coerceIn(1, 3)
                        "quick_sym_delay" -> v.coerceIn(100, 450)
                        else -> v
                    }
                    prefs[intPreferencesKey(keyName)] = clamped
                    true
                }
                "float" -> {
                    val v = entry.getDouble("value").toFloat()
                    if (v.isNaN() || v.isInfinite()) return@runCatching false
                    prefs[floatPreferencesKey(keyName)] = v
                    true
                }
                "long" -> {
                    prefs[longPreferencesKey(keyName)] = entry.getLong("value")
                    true
                }
                "string_set" -> {
                    val arr = entry.getJSONArray("value")
                    val set = HashSet<String>(arr.length())
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    prefs[stringSetPreferencesKey(keyName)] = set
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun validateKnownString(keyName: String, value: String): Boolean {
        return when (keyName) {
            "theme_mode" -> runCatching { ThemeMode.valueOf(value) }.isSuccess
            "font_style" -> runCatching { KeyFontStyle.valueOf(value) }.isSuccess
            "smart_tap_mode" -> runCatching { SmartTapMode.valueOf(value) }.isSuccess
            "candidate_bar_mode" -> runCatching { CandidateBarMode.valueOf(value) }.isSuccess
            else -> true
        }
    }
}
