package com.serenetype.keyboard.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.ln

/**
 * Spatial key detector with:
 * 1. 60% Sacred Core: inner 60% of any key is 100% immune to dictionary override.
 * 2. Bounded Border Arbitration: outer 40% / gap blends spatial Gaussian distance with
 *    bounded language priors (never overpowering actual touch position).
 * 3. Slide Hysteresis: prevents key preview flicker when finger moves near a border.
 */
class KeyDetector {
    private val keyBounds = HashMap<String, Rect>(40)
    private val keyCenters = HashMap<Char, Pair<Float, Float>>(40)
    var avgKeyWidthPx: Float = 0f
        private set
    var avgKeyHeightPx: Float = 0f
        private set

    fun updateKeyBounds(key: String, rect: Rect) {
        keyBounds[key] = rect
    }

    fun clear() {
        keyBounds.clear()
        keyCenters.clear()
        avgKeyWidthPx = 0f
        avgKeyHeightPx = 0f
    }

    fun snapshotGeometry(): Pair<Map<Char, Pair<Float, Float>>, Float> {
        if (keyBounds.isEmpty()) return emptyMap<Char, Pair<Float, Float>>() to 0f
        keyCenters.clear()
        var sumW = 0f
        var sumH = 0f
        var cnt = 0
        keyBounds.forEach { (k, r) ->
            if (k.length == 1 && k[0].isLetter()) {
                keyCenters[k[0].lowercaseChar()] = r.center.x to r.center.y
                sumW += r.width
                sumH += r.height
                cnt++
            }
        }
        if (cnt > 0) {
            avgKeyWidthPx = sumW / cnt
            avgKeyHeightPx = sumH / cnt
        }
        return HashMap(keyCenters) to avgKeyWidthPx
    }

    fun detectKey(
        rootPos: Offset,
        priors: Map<Char, Float>,
        alphaWeight: Float,
        biasProvider: (Char) -> Pair<Float, Float>?
    ): Pair<String, Rect>? {
        if (keyBounds.isEmpty()) return null

        // Pass 1: 60% Sacred Core check (inner 60% of key rect is unconditionally honored)
        for ((key, rect) in keyBounds) {
            val w = rect.width.coerceAtLeast(1f)
            val h = rect.height.coerceAtLeast(1f)
            val ch = if (key.length == 1) key[0].lowercaseChar() else null
            val bias = if (ch != null) biasProvider(ch) else null
            val cx = rect.center.x + (bias?.first ?: 0f)
            val cy = rect.center.y + (bias?.second ?: 0f)
            if (abs(rootPos.x - cx) <= w * 0.30f && abs(rootPos.y - cy) <= h * 0.30f) {
                return key to rect
            }
        }

        // Pass 2: Border / gap arbitration with bounded language prior
        var bestKey: String? = null
        var bestRect: Rect? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for ((key, rect) in keyBounds) {
            val w = rect.width.coerceAtLeast(1f)
            val h = rect.height.coerceAtLeast(1f)
            val ch = if (key.length == 1) key[0].lowercaseChar() else null
            val bias = if (ch != null) biasProvider(ch) else null
            val cx = rect.center.x + (bias?.first ?: 0f)
            val cy = rect.center.y + (bias?.second ?: 0f)

            val rawDx = abs(rootPos.x - cx)
            val rawDy = abs(rootPos.y - cy)
            if (rawDx > w * 1.40f || rawDy > h * 0.85f) continue

            val nx = rawDx / (w * 0.50f)
            val ny = rawDy / (h * 0.52f)
            val spatialScore = -0.5f * (nx * nx + ny * ny)

            val priorBonus = if (alphaWeight > 0f && priors.isNotEmpty() && ch != null && ch.isLetter()) {
                val p = (priors[ch] ?: 0.01f).coerceAtLeast(0.01f)
                (alphaWeight * 0.32f * ln(p)).coerceIn(-1.35f, 1.35f)
            } else 0f

            val score = spatialScore + priorBonus
            if (score > bestScore) {
                bestScore = score
                bestKey = key
                bestRect = rect
            }
        }
        return if (bestKey != null && bestRect != null) bestKey to bestRect else null
    }

    fun isMajorEnoughMove(currentRect: Rect, rootPos: Offset): Boolean {
        val hysteresis = (currentRect.width * 0.15f).coerceAtLeast(6f)
        return rootPos.x < currentRect.left - hysteresis ||
            rootPos.x > currentRect.right + hysteresis ||
            rootPos.y < currentRect.top - hysteresis ||
            rootPos.y > currentRect.bottom + hysteresis
    }

    fun getAllBounds(): Map<String, Rect> = keyBounds
}
