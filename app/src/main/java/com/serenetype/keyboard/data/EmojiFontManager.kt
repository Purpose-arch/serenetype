package com.serenetype.keyboard.data

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object EmojiFontManager {
    private const val FONT_URL =
        "https://github.com/samuelngs/apple-emoji-ttf/releases/latest/download/AppleColorEmoji-Linux.ttf"
    private const val FONT_FILENAME = "AppleColorEmoji.ttf"
    private const val MIN_VALID_BYTES = 1_000_000L

    private val mutex = Mutex()
    private val _fontFamilyFlow = MutableStateFlow<FontFamily>(FontFamily.Default)
    val fontFamilyFlow: StateFlow<FontFamily> = _fontFamilyFlow.asStateFlow()

    @Volatile
    private var initialized = false

    private fun fontFile(context: Context): File {
        val dir = File(context.applicationContext.filesDir, "fonts").apply { mkdirs() }
        return File(dir, FONT_FILENAME)
    }

    fun isDownloaded(context: Context): Boolean {
        val f = fontFile(context)
        return f.exists() && f.length() >= MIN_VALID_BYTES
    }

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        mutex.withLock {
            if (initialized) return@withLock
            val appCtx = context.applicationContext
            val localFile = fontFile(appCtx)
            val loaded = runCatching {
                when {
                    localFile.exists() && localFile.length() >= MIN_VALID_BYTES -> {
                        FontFamily(Font(localFile))
                    }
                    appCtx.assets.list("fonts")?.contains(FONT_FILENAME) == true -> {
                        FontFamily(Font(path = "fonts/$FONT_FILENAME", assetManager = appCtx.assets))
                    }
                    else -> FontFamily.Default
                }
            }.getOrDefault(FontFamily.Default)
            _fontFamilyFlow.value = loaded
            initialized = true
        }
    }

    suspend fun downloadAppleEmojiFont(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val target = fontFile(appCtx)
        val tmp = File(target.parentFile, "$FONT_FILENAME.tmp")
        return@withContext mutex.withLock {
            runCatching {
                val conn = (URL(FONT_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    return@runCatching false
                }
                BufferedInputStream(conn.inputStream, 32 * 1024).use { input ->
                    BufferedOutputStream(tmp.outputStream(), 32 * 1024).use { output ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            output.write(buf, 0, read)
                        }
                    }
                }
                conn.disconnect()

                if (!tmp.exists() || tmp.length() < MIN_VALID_BYTES) {
                    tmp.delete()
                    return@runCatching false
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@runCatching false
                }
                _fontFamilyFlow.value = FontFamily(Font(target))
                initialized = true
                true
            }.getOrElse {
                tmp.delete()
                false
            }
        }
    }

    suspend fun deleteAppleEmojiFont(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = fontFile(context.applicationContext)
            if (target.exists()) target.delete()
            _fontFamilyFlow.value = FontFamily.Default
            initialized = true
        }
    }
}
