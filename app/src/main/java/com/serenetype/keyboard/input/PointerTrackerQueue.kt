package com.serenetype.keyboard.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import kotlinx.coroutines.Job
import kotlin.math.hypot

class ActivePointer(
    val pointerId: PointerId,
    var keyId: String,
    var label: String,
    var rect: Rect,
    val downTimeMs: Long,
    val downPos: Offset,
    var lastPos: Offset,
    var holdJob: Job? = null,
    var altsTriggered: Boolean = false,
    var committed: Boolean = false
)

/**
 * LineageOS / AOSP LatinIME style FIFO pointer queue:
 * Guarantees keys are committed strictly in press order during fast two-thumb typing
 * (prevents letter transpositions like "пр" -> "рп" when the second thumb lifts first).
 */
class PointerTrackerQueue {
    private val queue = ArrayList<ActivePointer>(4)
    private var lastUpTimeMs = 0L
    private var lastUpPos = Offset.Zero

    fun isNoiseTap(rootPos: Offset, nowMs: Long, avgKeyWidthPx: Float): Boolean {
        val dt = nowMs - lastUpTimeMs
        if (dt in 0L..42L && avgKeyWidthPx > 1f) {
            val d = hypot(rootPos.x - lastUpPos.x, rootPos.y - lastUpPos.y)
            if (d < avgKeyWidthPx * 0.28f) return true
        }
        return false
    }

    fun recordUp(rootPos: Offset, nowMs: Long) {
        lastUpTimeMs = nowMs
        lastUpPos = rootPos
    }

    fun add(pointer: ActivePointer) {
        queue.removeAll { it.pointerId == pointer.pointerId }
        queue.add(pointer)
    }

    fun find(pointerId: PointerId): ActivePointer? {
        for (i in queue.indices) {
            if (queue[i].pointerId == pointerId) return queue[i]
        }
        return null
    }

    fun remove(pointerId: PointerId): ActivePointer? {
        for (i in queue.indices) {
            if (queue[i].pointerId == pointerId) {
                return queue.removeAt(i)
            }
        }
        return null
    }

    fun releaseOlderThan(pointerId: PointerId, onPhantomUp: (ActivePointer) -> Unit) {
        val targetIdx = queue.indexOfFirst { it.pointerId == pointerId }
        if (targetIdx <= 0) return
        val older = ArrayList<ActivePointer>(targetIdx)
        for (i in 0 until targetIdx) {
            older.add(queue[i])
        }
        for (p in older) {
            queue.remove(p)
            onPhantomUp(p)
        }
    }

    fun releaseAll(onPhantomUp: (ActivePointer) -> Unit) {
        if (queue.isEmpty()) return
        val copy = ArrayList(queue)
        queue.clear()
        for (p in copy) {
            onPhantomUp(p)
        }
    }

    fun clear() {
        for (p in queue) {
            p.holdJob?.cancel()
        }
        queue.clear()
    }
}

class TypingTimeRecorder {
    private var lastKeyInputMs = 0L

    fun onKeyInput(nowMs: Long): Boolean {
        val isFastBurst = lastKeyInputMs > 0L && (nowMs - lastKeyInputMs) in 0L..95L
        lastKeyInputMs = nowMs
        return isFastBurst
    }

    fun isInFastBurst(nowMs: Long): Boolean = lastKeyInputMs > 0L && (nowMs - lastKeyInputMs) in 0L..95L
}
