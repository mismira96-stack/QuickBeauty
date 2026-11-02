package com.quickbeauty.app

import kotlin.math.abs

object StoryTextWrapping {
    /** Prefer word boundaries so a title does not split 행복 into 행 / 복. */
    fun twoLines(value: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(value) <= maxWidth) return value
        val candidates = Regex("\\s+").findAll(value).map { match ->
            value.substring(0, match.range.first).trimEnd() to
                value.substring(match.range.last + 1).trimStart()
        }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toList()
        if (candidates.isEmpty()) return value
        val fitting = candidates.filter { measure(it.first) <= maxWidth && measure(it.second) <= maxWidth }
        val selected = (fitting.ifEmpty { candidates }).minByOrNull {
            abs(measure(it.first) - measure(it.second))
        } ?: return value
        return selected.first + "\n" + selected.second
    }
}
