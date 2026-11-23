package com.serenetype.keyboard.input

data class LastCorrection(
    val originalWord: String,
    val correctedWord: String,
    val separator: String
)

/**
 * Tracks the active word prefix, its exact (x, y) touch trajectory,
 * and a 2-word sentence context window (prevWord2, prevWord1) for phrase-aware T9.
 */
class ComposingSession {
    private val wordBuf = StringBuilder(24)
    private val touches = ArrayList<Pair<Float, Float>>(24)

    var prevWord2: String = ""
        private set
    var prevWord1: String = ""
        private set
    var atSentenceStart: Boolean = true
        private set
    var lastCorrection: LastCorrection? = null

    val currentWord: String
        get() = wordBuf.toString()

    val touchPoints: List<Pair<Float, Float>>
        get() = touches

    fun appendChar(ch: Char, touchX: Float?, touchY: Float?) {
        if (wordBuf.length >= 32) return
        wordBuf.append(ch.lowercaseChar())
        if (touchX != null && touchY != null) {
            touches.add(touchX to touchY)
        }
    }

    fun dropLastChar() {
        if (wordBuf.isNotEmpty()) {
            wordBuf.deleteCharAt(wordBuf.length - 1)
        }
        if (touches.isNotEmpty()) {
            touches.removeAt(touches.lastIndex)
        }
    }

    fun restoreWord(word: String) {
        wordBuf.clear()
        wordBuf.append(word.lowercase())
        touches.clear()
    }

    fun finishWord(committedWord: String?, separator: String = " ") {
        if (!committedWord.isNullOrEmpty()) {
            prevWord2 = prevWord1
            prevWord1 = committedWord.lowercase()
            atSentenceStart = false
        }
        if (separator == "." || separator == "!" || separator == "?" || separator == "\n") {
            prevWord2 = ""
            prevWord1 = ""
            atSentenceStart = true
        }
        wordBuf.clear()
        touches.clear()
    }

    fun resetAll() {
        wordBuf.clear()
        touches.clear()
        prevWord2 = ""
        prevWord1 = ""
        atSentenceStart = true
        lastCorrection = null
    }
}
