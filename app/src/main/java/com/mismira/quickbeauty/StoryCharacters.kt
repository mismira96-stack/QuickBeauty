package com.quickbeauty.app

import android.icu.text.BreakIterator
import java.util.Locale

object StoryCharacters {
    fun split(value: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.KOREAN)
        iterator.setText(value)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result.add(value.substring(start, end))
            start = end
            end = iterator.next()
        }
        return result
    }
}
