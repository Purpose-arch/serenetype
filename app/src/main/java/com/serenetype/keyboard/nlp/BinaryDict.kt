package com.serenetype.keyboard.nlp

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Compact memory-mapped binary Trie dictionary (.sdict v2):
 * - Zero JVM object allocations per word on load (backed by MappedByteBuffer).
 * - O(word.length * log K) exact lookup.
 * - O(prefix.length * log K) next-character probability priors + embedded char bigrams.
 * - Bayesian-inspired DFS Damerau-Levenshtein automaton (up to 2 edits for len >= 5)
 *   with continuous quadratic/Gaussian spatial touch scoring + log-frequency language model.
 */
class BinaryDict private constructor(
    private val buf: ByteBuffer,
    private val nodeCount: Int,
    private val bigramOffset: Int
) {
    companion object {
        private const val MAGIC = 0x53444943 // "SDIC"
        private const val VERSION = 2
        private const val HEADER_SIZE = 16
        private const val ROOT_OFFSET = HEADER_SIZE

        fun open(file: File): BinaryDict? {
            if (!file.exists() || file.length() < HEADER_SIZE) return null
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val channel = raf.channel
                    val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
                    mapped.order(ByteOrder.BIG_ENDIAN)
                    val magic = mapped.getInt(0)
                    val ver = mapped.getShort(4).toInt()
                    if (magic != MAGIC || ver != VERSION) return null
                    val nodeCount = mapped.getInt(6)
                    val bigramOff = mapped.getInt(10)
                    BinaryDict(mapped, nodeCount, bigramOff)
                }
            }.getOrNull()
        }

        fun buildFromWordFreqs(
            words: Map<String, Int>,
            outFile: File
        ): Boolean {
            return runCatching {
                val root = BuildNode()
                val charBigrams = HashMap<Int, Long>()

                words.forEach { (raw, freq) ->
                    val w = raw.trim().lowercase()
                    if (w.length < 2 || w.length > 28) return@forEach
                    val f = freq.coerceIn(1, 65000)
                    var cur = root
                    for (i in w.indices) {
                        cur.subtreeSum += f.toLong()
                        val ch = w[i]
                        if (i > 0) {
                            val pairKey = (w[i - 1].code shl 16) or ch.code
                            charBigrams[pairKey] = (charBigrams[pairKey] ?: 0L) + f.toLong()
                        }
                        cur = cur.getOrCreateChild(ch)
                    }
                    cur.subtreeSum += f.toLong()
                    if (f > cur.wordFreq) cur.wordFreq = f
                }

                // Assign byte offsets (DFS pre-order)
                val ordered = ArrayList<BuildNode>(words.size.coerceAtLeast(64))
                fun assign(n: BuildNode, currentOff: Int): Int {
                    n.offset = currentOff
                    ordered.add(n)
                    var nextOff = currentOff + 8 + n.children.size * 6
                    for (c in n.children) {
                        nextOff = assign(c.node, nextOff)
                    }
                    return nextOff
                }
                val bigramOff = assign(root, HEADER_SIZE)

                val tmpFile = File(outFile.parentFile, "${outFile.name}.tmp")
                DataOutputStream(BufferedOutputStream(tmpFile.outputStream(), 65536)).use { out ->
                    out.writeInt(MAGIC)
                    out.writeShort(VERSION)
                    out.writeInt(ordered.size)
                    out.writeInt(bigramOff)
                    out.writeShort(0) // reserved

                    for (n in ordered) {
                        out.writeShort(n.wordFreq.coerceIn(0, 65535))
                        out.writeInt(n.subtreeSum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        out.writeShort(n.children.size)
                        for (c in n.children) {
                            out.writeChar(c.ch.code)
                            out.writeInt(c.node.offset)
                        }
                    }

                    val byPrev = HashMap<Char, MutableList<Pair<Char, Long>>>()
                    charBigrams.forEach { (k, v) ->
                        val p = (k ushr 16).toChar()
                        val nx = (k and 0xFFFF).toChar()
                        byPrev.getOrPut(p) { ArrayList() }.add(nx to v)
                    }
                    out.writeShort(byPrev.size)
                    byPrev.forEach { (prevCh, list) ->
                        val total = list.sumOf { it.second }.coerceAtLeast(1L)
                        val sorted = list.sortedByDescending { it.second }.take(24)
                        out.writeChar(prevCh.code)
                        out.writeByte(sorted.size)
                        for ((nx, cnt) in sorted) {
                            out.writeChar(nx.code)
                            val probByte = ((cnt * 255L) / total).toInt().coerceIn(1, 255)
                            out.writeByte(probByte)
                        }
                    }
                }
                if (outFile.exists()) outFile.delete()
                tmpFile.renameTo(outFile)
                true
            }.getOrDefault(false)
        }
    }

    private class BuildChild(val ch: Char, val node: BuildNode)
    private class BuildNode {
        var wordFreq: Int = 0
        var subtreeSum: Long = 0L
        var offset: Int = 0
        val children = ArrayList<BuildChild>(2)

        fun getOrCreateChild(ch: Char): BuildNode {
            var lo = 0
            var hi = children.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val mc = children[mid].ch
                if (mc == ch) return children[mid].node
                if (mc < ch) lo = mid + 1 else hi = mid - 1
            }
            val n = BuildNode()
            children.add(lo, BuildChild(ch, n))
            return n
        }
    }

    private val charBigrams: Map<Char, Map<Char, Float>> by lazy { parseBigrams() }

    private fun parseBigrams(): Map<Char, Map<Char, Float>> {
        if (bigramOffset <= 0 || bigramOffset + 2 > buf.capacity()) return emptyMap()
        val res = HashMap<Char, Map<Char, Float>>()
        var pos = bigramOffset
        val prevCount = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2
        for (i in 0 until prevCount) {
            if (pos + 3 > buf.capacity()) break
            val prevCh = buf.getChar(pos)
            pos += 2
            val n = buf.get(pos).toInt() and 0xFF
            pos += 1
            val inner = HashMap<Char, Float>(n * 2)
            for (j in 0 until n) {
                if (pos + 3 > buf.capacity()) break
                val nx = buf.getChar(pos)
                pos += 2
                val pByte = buf.get(pos).toInt() and 0xFF
                pos += 1
                inner[nx] = pByte / 255f
            }
            res[prevCh] = inner
        }
        return res
    }

    private fun findChildOffset(nodeOffset: Int, ch: Char): Int {
        val childCount = buf.getShort(nodeOffset + 6).toInt() and 0xFFFF
        if (childCount == 0) return -1
        var lo = 0
        var hi = childCount - 1
        val base = nodeOffset + 8
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val entryOff = base + mid * 6
            val mc = buf.getChar(entryOff)
            if (mc == ch) return buf.getInt(entryOff + 2)
            if (mc < ch) lo = mid + 1 else hi = mid - 1
        }
        return -1
    }

    fun isKnown(lowerWord: String): Boolean {
        if (lowerWord.length < 2) return false
        var off = ROOT_OFFSET
        for (i in lowerWord.indices) {
            off = findChildOffset(off, lowerWord[i])
            if (off < 0) return false
        }
        val freq = buf.getShort(off).toInt() and 0xFFFF
        return freq > 0
    }

    fun getWordFrequency(lowerWord: String): Int {
        if (lowerWord.length < 2) return 0
        var off = ROOT_OFFSET
        for (i in lowerWord.indices) {
            off = findChildOffset(off, lowerWord[i])
            if (off < 0) return 0
        }
        return buf.getShort(off).toInt() and 0xFFFF
    }

    fun getNextCharPriors(lowerPrefix: String): Map<Char, Float> {
        if (lowerPrefix.isEmpty()) return emptyMap()
        var off = ROOT_OFFSET
        for (i in lowerPrefix.indices) {
            off = findChildOffset(off, lowerPrefix[i])
            if (off < 0) {
                val last = lowerPrefix.last()
                return charBigrams[last] ?: emptyMap()
            }
        }
        val childCount = buf.getShort(off + 6).toInt() and 0xFFFF
        if (childCount == 0) return emptyMap()
        var sum = 0L
        val base = off + 8
        for (i in 0 until childCount) {
            val childOff = buf.getInt(base + i * 6 + 2)
            sum += (buf.getInt(childOff + 2).toLong() and 0xFFFFFFFFL)
        }
        if (sum <= 0L) return emptyMap()
        val denom = sum.toFloat()
        val out = HashMap<Char, Float>(childCount * 2)
        val bigrams = charBigrams[lowerPrefix.last()]
        for (i in 0 until childCount) {
            val entryOff = base + i * 6
            val ch = buf.getChar(entryOff)
            val childOff = buf.getInt(entryOff + 2)
            val cSum = (buf.getInt(childOff + 2).toLong() and 0xFFFFFFFFL).toFloat()
            val trieProb = cSum / denom
            val bgProb = bigrams?.get(ch) ?: 0f
            out[ch] = trieProb * 0.78f + bgProb * 0.22f
        }
        return out
    }

    fun getBigramPrior(prevChar: Char, nextChar: Char): Float {
        return charBigrams[prevChar]?.get(nextChar) ?: 0f
    }

    fun findBestCorrection(
        lowerWord: String,
        touchPoints: List<Pair<Float, Float>>,
        keyCenters: Map<Char, Pair<Float, Float>>,
        avgKeyWidthPx: Float
    ): Pair<String, Int>? {
        return findTopCandidates(lowerWord, touchPoints, keyCenters, avgKeyWidthPx, maxResults = 1).firstOrNull()
    }

    /**
     * Bayesian-inspired spatial + Damerau-Levenshtein search:
     * - Supports up to 2 edits for words >= 5 chars (1 edit for shorter words).
     * - Continuous Gaussian/quadratic spatial score using actual (x, y) touch trajectory.
     * - Log-frequency language prior so high-frequency inflections & case forms rank accurately.
     */
    fun findTopCandidates(
        lowerWord: String,
        touchPoints: List<Pair<Float, Float>>,
        keyCenters: Map<Char, Pair<Float, Float>>,
        avgKeyWidthPx: Float,
        maxResults: Int = 6,
        maxEditsOverride: Int? = null,
        maxVisitsOverride: Int? = null
    ): List<Pair<String, Int>> {
        val len = lowerWord.length
        if (len < 2 || len > 18) return emptyList()

        val maxEdits = maxEditsOverride ?: if (len >= 5) 2 else 1
        val candidates = HashMap<String, Int>(16)
        val hasGeom = avgKeyWidthPx > 1f && keyCenters.isNotEmpty()
        val pathBuf = CharArray(len + 3)
        var dfsVisits = 0
        val maxDfsVisits = maxVisitsOverride ?: 3500

        fun continuousSpatialScore(idx: Int, candCh: Char, typedCh: Char): Int {
            if (!hasGeom) return 0
            val candCenter = keyCenters[candCh] ?: return -1500
            val touch = if (idx < touchPoints.size) touchPoints[idx] else keyCenters[typedCh]
            if (touch == null) return -1500
            val normDist = hypot(touch.first - candCenter.first, touch.second - candCenter.second) / avgKeyWidthPx
            if (normDist > 1.85f) return Int.MIN_VALUE // hard spatial prune
            return (3200f - normDist * normDist * 1650f).toInt()
        }

        fun neighborProximityBonus(chA: Char, chB: Char, hitBonus: Int): Int {
            if (!hasGeom) return 0
            val pA = keyCenters[chA] ?: return 0
            val pB = keyCenters[chB] ?: return 0
            val normDist = hypot(pA.first - pB.first, pA.second - pB.second) / avgKeyWidthPx
            return if (normDist <= 1.45f) hitBonus else -600
        }

        fun dfs(nodeOff: Int, wordIdx: Int, depth: Int, editsLeft: Int, editsUsed: Int, scoreAcc: Int) {
            if (depth >= pathBuf.size || dfsVisits >= maxDfsVisits) return
            dfsVisits++

            if (wordIdx == len) {
                val freq = buf.getShort(nodeOff).toInt() and 0xFFFF
                if (freq > 0) {
                    val langScore = (ln((freq + 1).toDouble()) * 420.0).toInt()
                    val editPenalty = editsUsed * 1950
                    val total = langScore + scoreAcc - editPenalty
                    val candStr = String(pathBuf, 0, depth)
                    val prev = candidates[candStr]
                    if (prev == null || total > prev) {
                        candidates[candStr] = total
                    }
                }
                if (editsLeft > 0) {
                    val childCount = buf.getShort(nodeOff + 6).toInt() and 0xFFFF
                    val base = nodeOff + 8
                    for (i in 0 until childCount) {
                        val entryOff = base + i * 6
                        val ch = buf.getChar(entryOff)
                        val childOff = buf.getInt(entryOff + 2)
                        pathBuf[depth] = ch
                        dfs(childOff, wordIdx, depth + 1, editsLeft - 1, editsUsed + 1, scoreAcc + 650)
                    }
                }
                return
            }

            val targetCh = lowerWord[wordIdx]
            val childCount = buf.getShort(nodeOff + 6).toInt() and 0xFFFF
            val base = nodeOff + 8

            // 1. Exact match branch (0 edits)
            val exactChildOff = findChildOffset(nodeOff, targetCh)
            if (exactChildOff >= 0) {
                pathBuf[depth] = targetCh
                dfs(exactChildOff, wordIdx + 1, depth + 1, editsLeft, editsUsed, scoreAcc + 900)
            }

            if (editsLeft <= 0) return

            // 2. Transposition (wordIdx and wordIdx + 1 swapped)
            if (wordIdx + 1 < len && lowerWord[wordIdx + 1] != targetCh) {
                val nextCh = lowerWord[wordIdx + 1]
                val c1 = findChildOffset(nodeOff, nextCh)
                if (c1 >= 0) {
                    val c2 = findChildOffset(c1, targetCh)
                    if (c2 >= 0) {
                        pathBuf[depth] = nextCh
                        pathBuf[depth + 1] = targetCh
                        dfs(c2, wordIdx + 2, depth + 2, editsLeft - 1, editsUsed + 1, scoreAcc + 2500)
                    }
                }
            }

            // 3. Deletion (skip accidental extra tap at wordIdx)
            val delBonus = if (wordIdx > 0) neighborProximityBonus(targetCh, lowerWord[wordIdx - 1], 1800) else 0
            dfs(nodeOff, wordIdx + 1, depth, editsLeft - 1, editsUsed + 1, scoreAcc + delBonus)

            // 4. Substitution & Insertion across children
            for (i in 0 until childCount) {
                val entryOff = base + i * 6
                val ch = buf.getChar(entryOff)
                if (ch == targetCh) continue
                val childOff = buf.getInt(entryOff + 2)

                // Substitution with continuous spatial score
                val subSpatial = continuousSpatialScore(wordIdx, ch, targetCh)
                if (subSpatial != Int.MIN_VALUE) {
                    pathBuf[depth] = ch
                    dfs(childOff, wordIdx + 1, depth + 1, editsLeft - 1, editsUsed + 1, scoreAcc + subSpatial)
                }

                // Insertion (missed letter before targetCh)
                if (editsUsed == 0 || len >= 6) {
                    val insBonus = neighborProximityBonus(ch, targetCh, 1400)
                    if (insBonus >= 0) {
                        pathBuf[depth] = ch
                        dfs(childOff, wordIdx, depth + 1, editsLeft - 1, editsUsed + 1, scoreAcc + insBonus)
                    }
                }
            }
        }

        dfs(ROOT_OFFSET, 0, 0, maxEdits, 0, 0)
        if (candidates.isEmpty()) return emptyList()
        return candidates.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key to it.value }
    }
}
