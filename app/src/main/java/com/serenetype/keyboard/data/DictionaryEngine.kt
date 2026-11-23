package com.serenetype.keyboard.data

import android.content.Context
import com.serenetype.keyboard.nlp.BinaryDict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class DictionaryEngine private constructor(private val context: Context) {

    companion object {
        private val SUPPORTED_LANGS = listOf("en", "ru", "fi", "de", "uk")

        private val SEEDED_BIGRAMS: Map<String, Map<String, Set<String>>> = mapOf(
            "ru" to mapOf(
                "доброе" to setOf("утро"),
                "добрый" to setOf("день", "вечер"),
                "доброй" to setOf("ночи"),
                "спокойной" to setOf("ночи"),
                "как" to setOf("дела", "ты", "вы", "жизнь", "можно", "так"),
                "привет" to setOf("как", "что", "ты"),
                "спасибо" to setOf("большое", "за", "тебе", "вам"),
                "в" to setOf("общем", "целом", "итоге", "принципе", "самом"),
                "на" to setOf("самом", "деле", "работу", "работе", "связи"),
                "по" to setOf("поводу", "этому", "поэтому", "сути"),
                "всё" to setOf("хорошо", "будет", "равно", "норм", "окей"),
                "все" to setOf("хорошо", "будет", "равно", "нормально"),
                "до" to setOf("встречи", "завтра", "связи"),
                "у" to setOf("меня", "тебя", "нас", "вас", "него", "неё")
            ),
            "en" to mapOf(
                "good" to setOf("morning", "afternoon", "evening", "night", "job", "luck"),
                "thank" to setOf("you"),
                "thanks" to setOf("for", "a", "so"),
                "how" to setOf("are", "is", "was", "about", "much", "many"),
                "see" to setOf("you", "ya"),
                "on" to setOf("the", "my", "your", "time", "way"),
                "in" to setOf("the", "a", "my", "this", "fact"),
                "of" to setOf("the", "course", "this", "all"),
                "for" to setOf("you", "me", "the", "sure"),
                "i" to setOf("am", "have", "will", "think", "know", "want", "need", "love")
            )
        )

        @Volatile
        private var instance: DictionaryEngine? = null

        fun getInstance(context: Context): DictionaryEngine {
            return instance ?: synchronized(this) {
                instance ?: DictionaryEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val binaryDicts = ConcurrentHashMap<String, BinaryDict>()
    private val userWordsByLang = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val wordBigramsByLang = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>>()
    private val wordTrigramsByLang = ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>>()
    private val suppressedWords = ConcurrentHashMap.newKeySet<String>()

    private val userDictFile = File(context.filesDir, "user_dict.json")
    private val userBigramsFile = File(context.filesDir, "user_bigrams.json")
    private val userTrigramsFile = File(context.filesDir, "user_trigrams.json")
    private val downloadedDir = File(context.filesDir, "dicts").apply { mkdirs() }

    private val loadMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loadMutex.withLock {
            if (loaded) return@withLock
            loadUserDict()
            loadUserBigrams()
            loadUserTrigrams()
            for (lang in SUPPORTED_LANGS) {
                ensureBinaryDict(lang, forceRebuild = false)
            }
            loaded = true
        }
    }

    private fun ensureBinaryDict(lang: String, forceRebuild: Boolean) {
        val sdictFile = File(downloadedDir, "${lang}_v2.sdict")
        val extFile = File(downloadedDir, "${lang}_ext.txt")
        val needsBuild = forceRebuild ||
            !sdictFile.exists() ||
            (extFile.exists() && extFile.lastModified() > sdictFile.lastModified())

        if (needsBuild) {
            val map = HashMap<String, Int>(4096)
            runCatching {
                context.assets.open("dict/${lang}_base.txt").bufferedReader().useLines { lines ->
                    var rank = 6000
                    lines.forEach { line ->
                        var start = -1
                        for (i in 0..line.length) {
                            val isSpace = i == line.length || line[i].isWhitespace()
                            if (!isSpace && start == -1) {
                                start = i
                            } else if (isSpace && start != -1) {
                                val w = line.substring(start, i).lowercase()
                                start = -1
                                if (w.length >= 2 && !map.containsKey(w)) {
                                    map[w] = rank.coerceAtLeast(15)
                                    if (rank > 120) rank -= 2
                                }
                            }
                        }
                    }
                }
            }
            if (extFile.exists()) {
                runCatching {
                    extFile.bufferedReader().useLines { lines ->
                        var freq = 1200
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                val sepIdx = trimmed.indexOfFirst { it == '\t' || it == ' ' }
                                val w = (if (sepIdx > 0) trimmed.substring(0, sepIdx) else trimmed).lowercase()
                                if (w.length >= 2 && !map.containsKey(w)) {
                                    map[w] = freq.coerceAtLeast(5)
                                    if (freq > 5) freq--
                                }
                            }
                        }
                    }
                }
            }
            if (map.isNotEmpty()) {
                BinaryDict.buildFromWordFreqs(map, sdictFile)
            }
        }

        if (sdictFile.exists()) {
            BinaryDict.open(sdictFile)?.let { binaryDicts[lang] = it }
        }
    }

    private fun loadUserDict() {
        if (!userDictFile.exists()) return
        runCatching {
            val obj = JSONObject(userDictFile.readText())
            obj.keys().forEach { k ->
                val v = obj.opt(k)
                if (v is JSONObject) {
                    val langMap = userWordsByLang.getOrPut(k) { ConcurrentHashMap() }
                    v.keys().forEach { w -> langMap[w] = v.optInt(w, 1) }
                } else if (v is Number) {
                    val lang = if (k.any { it in '\u0400'..'\u04FF' }) "ru" else "en"
                    userWordsByLang.getOrPut(lang) { ConcurrentHashMap() }[k] = v.toInt()
                }
            }
        }
    }

    private fun loadUserBigrams() {
        loadNestedCounts(userBigramsFile, wordBigramsByLang)
    }

    private fun loadUserTrigrams() {
        loadNestedCounts(userTrigramsFile, wordTrigramsByLang)
    }

    private fun loadNestedCounts(
        file: File,
        target: ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>>
    ) {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            root.keys().forEach { lang ->
                val langObj = root.optJSONObject(lang) ?: return@forEach
                val langMap = target.getOrPut(lang) { ConcurrentHashMap() }
                langObj.keys().forEach { prev ->
                    val nextObj = langObj.optJSONObject(prev) ?: return@forEach
                    val nextMap = langMap.getOrPut(prev) { ConcurrentHashMap() }
                    nextObj.keys().forEach { nx -> nextMap[nx] = nextObj.optInt(nx, 1) }
                }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(2500L)
            saveUserDataBlocking()
        }
    }

    fun flushUserDictAsync() {
        saveJob?.cancel()
        saveJob = ioScope.launch {
            saveUserDataBlocking()
        }
    }

    private fun saveUserDataBlocking() {
        runCatching {
            val root = JSONObject()
            userWordsByLang.forEach { (lang, words) ->
                val langObj = JSONObject()
                words.forEach { (k, v) -> langObj.put(k, v) }
                root.put(lang, langObj)
            }
            userDictFile.writeText(root.toString())
        }
        saveNestedCounts(userBigramsFile, wordBigramsByLang)
        saveNestedCounts(userTrigramsFile, wordTrigramsByLang)
    }

    private fun saveNestedCounts(
        file: File,
        src: ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>>
    ) {
        runCatching {
            val bRoot = JSONObject()
            src.forEach { (lang, prevs) ->
                val langObj = JSONObject()
                prevs.forEach { (prev, nexts) ->
                    val nObj = JSONObject()
                    nexts.forEach { (nx, cnt) -> nObj.put(nx, cnt) }
                    langObj.put(prev, nObj)
                }
                bRoot.put(lang, langObj)
            }
            file.writeText(bRoot.toString())
        }
    }

    fun learnWord(rawWord: String, lang: String = "en", prevWord1: String? = null, prevWord2: String? = null) {
        val w = rawWord.trim().lowercase()
        if (w.length < 2 || w.length > 32 || w.any { !it.isLetter() }) return
        for (i in 0 until w.length - 2) {
            if (w[i] == w[i + 1] && w[i + 1] == w[i + 2]) return
        }
        val langMap = userWordsByLang.getOrPut(lang) { ConcurrentHashMap() }
        langMap[w] = (langMap[w] ?: 0) + 1

        val p1 = prevWord1?.trim()?.lowercase()
        if (!p1.isNullOrEmpty() && p1.length >= 2) {
            val langBigrams = wordBigramsByLang.getOrPut(lang) { ConcurrentHashMap() }
            val nextMap = langBigrams.getOrPut(p1) { ConcurrentHashMap() }
            nextMap[w] = (nextMap[w] ?: 0) + 1
            if (langBigrams.size > 600) {
                langBigrams.keys.firstOrNull()?.let { langBigrams.remove(it) }
            }

            val p2 = prevWord2?.trim()?.lowercase()
            if (!p2.isNullOrEmpty() && p2.length >= 2) {
                val triKey = "$p2 $p1"
                val langTrigrams = wordTrigramsByLang.getOrPut(lang) { ConcurrentHashMap() }
                val triNext = langTrigrams.getOrPut(triKey) { ConcurrentHashMap() }
                triNext[w] = (triNext[w] ?: 0) + 1
                if (langTrigrams.size > 600) {
                    langTrigrams.keys.firstOrNull()?.let { langTrigrams.remove(it) }
                }
            }
        }
        scheduleSave()
    }

    fun suppressCorrection(rawWord: String, lang: String = "en") {
        val w = rawWord.trim().lowercase()
        if (w.isEmpty()) return
        suppressedWords.add(w)
        val langMap = userWordsByLang.getOrPut(lang) { ConcurrentHashMap() }
        langMap[w] = (langMap[w] ?: 0) + 3
        scheduleSave()
    }

    fun isKnown(word: String, lang: String): Boolean {
        val lower = word.lowercase()
        if (userWordsByLang[lang]?.containsKey(lower) == true) return true
        return binaryDicts[lang]?.isKnown(lower) == true
    }

    fun getNextCharPriors(prefix: String, lang: String): Map<Char, Float> {
        if (!loaded || prefix.isEmpty()) return emptyMap()
        val lower = prefix.lowercase()
        val dict = binaryDicts[lang]
        val basePriors = dict?.getNextCharPriors(lower)?.toMutableMap() ?: HashMap()

        val userMap = userWordsByLang[lang]
        if (userMap != null && userMap.isNotEmpty()) {
            for ((w, cnt) in userMap) {
                if (w.length > lower.length && w.startsWith(lower)) {
                    val nx = w[lower.length]
                    val boost = (cnt * 0.08f).coerceAtMost(0.45f)
                    basePriors[nx] = ((basePriors[nx] ?: 0f) + boost).coerceAtMost(1f)
                }
            }
        }
        return basePriors
    }

    private fun contextBonus(cand: String, lang: String, p1: String?, p2: String?): Int {
        var bonus = 0
        if (!p1.isNullOrEmpty()) {
            if (SEEDED_BIGRAMS[lang]?.get(p1)?.contains(cand) == true) {
                bonus += 2200
            }
            val bgHits = wordBigramsByLang[lang]?.get(p1)?.get(cand) ?: 0
            if (bgHits > 0) {
                bonus += (bgHits * 900).coerceAtMost(3600)
            }
            if (!p2.isNullOrEmpty()) {
                val triHits = wordTrigramsByLang[lang]?.get("$p2 $p1")?.get(cand) ?: 0
                if (triHits > 0) {
                    bonus += (triHits * 1600).coerceAtMost(4800)
                }
            }
        }
        return bonus
    }

    fun suggestCorrection(
        word: String,
        lang: String,
        prevWord1: String? = null,
        prevWord2: String? = null,
        touchPoints: List<Pair<Float, Float>> = emptyList(),
        keyCenters: Map<Char, Pair<Float, Float>> = emptyMap(),
        avgKeyWidthPx: Float = 0f
    ): String? {
        if (!loaded) return null
        val len = word.length
        if (len < 3 || len > 18) return null
        if (word.any { !it.isLetter() }) return null
        if (word.all { it.isUpperCase() }) return null

        val lower = word.lowercase()
        if (lower in suppressedWords) return null
        if (isKnown(lower, lang)) return null

        var bestWord: String? = null
        var bestScore = Int.MIN_VALUE

        val p1 = prevWord1?.trim()?.lowercase()?.ifEmpty { null }
        val p2 = prevWord2?.trim()?.lowercase()?.ifEmpty { null }

        // 1. Check user personal dictionary
        val langUserWords = userWordsByLang[lang]
        if (langUserWords != null) {
            for ((cand, count) in langUserWords) {
                if (abs(cand.length - len) > 1) continue
                if (isEditDistanceOne(lower, cand)) {
                    val score = 10000 + count * 50 + contextBonus(cand, lang, p1, p2)
                    if (score > bestScore) {
                        bestScore = score
                        bestWord = cand
                    }
                }
            }
        }

        // 2. Rescore top-K spatial candidates from binary Trie using phrase & sentence context
        val topCandidates = binaryDicts[lang]?.findTopCandidates(
            lowerWord = lower,
            touchPoints = touchPoints,
            keyCenters = keyCenters,
            avgKeyWidthPx = avgKeyWidthPx,
            maxResults = 6
        ).orEmpty()

        for ((cand, baseScore) in topCandidates) {
            val total = baseScore + contextBonus(cand, lang, p1, p2)
            if (total > bestScore) {
                bestScore = total
                bestWord = cand
            }
        }

        val result = bestWord ?: return null
        return preserveCase(word, result)
    }

    private fun preserveCase(original: String, corrected: String): String {
        val hasMixedCase = original.any { it.isUpperCase() } && original.any { it.isLowerCase() }
        if (hasMixedCase && original.length == corrected.length) {
            val sb = StringBuilder(corrected.length)
            for (i in corrected.indices) {
                val c = corrected[i]
                sb.append(if (original[i].isUpperCase()) c.uppercaseChar() else c)
            }
            return sb.toString()
        }
        return if (original.firstOrNull()?.isUpperCase() == true) {
            corrected.replaceFirstChar { it.uppercase() }
        } else {
            corrected
        }
    }

    private fun isEditDistanceOne(a: String, b: String): Boolean {
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > 1) return false

        if (la == lb) {
            var diffs = 0
            var firstDiff = -1
            for (i in 0 until la) {
                if (a[i] != b[i]) {
                    diffs++
                    if (diffs == 1) {
                        firstDiff = i
                    } else if (diffs == 2) {
                        return i == firstDiff + 1 &&
                            a[firstDiff] == b[i] &&
                            a[i] == b[firstDiff] &&
                            a.regionMatches(i + 1, b, i + 1, la - i - 1)
                    } else {
                        return false
                    }
                }
            }
            return diffs == 1
        }

        val shorter = if (la < lb) a else b
        val longer = if (la < lb) b else a
        val sLen = shorter.length
        val lLen = longer.length
        var i = 0
        var j = 0
        var skipped = false
        while (i < sLen && j < lLen) {
            if (shorter[i] != longer[j]) {
                if (skipped) return false
                skipped = true
                j++
            } else {
                i++
                j++
            }
        }
        return true
    }

    fun getUserWordCount(): Int = userWordsByLang.values.sumOf { it.size }

    fun hasExtendedDict(lang: String): Boolean = File(downloadedDir, "${lang}_ext.txt").exists()

    suspend fun downloadExtendedDictionary(lang: String): Boolean = withContext(Dispatchers.IO) {
        val url = when (lang) {
            "en" -> "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"
            "ru" -> "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_50k.txt"
            "fi" -> "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/fi/fi_50k.txt"
            "de" -> "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_50k.txt"
            "uk" -> "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/uk/uk_50k.txt"
            else -> return@withContext false
        }
        return@withContext runCatching {
            val text = URL(url).readText()
            if (text.length < 1000) return@runCatching false
            File(downloadedDir, "${lang}_ext.txt").writeText(text)
            loadMutex.withLock {
                ensureBinaryDict(lang, forceRebuild = true)
            }
            true
        }.getOrDefault(false)
    }
}
