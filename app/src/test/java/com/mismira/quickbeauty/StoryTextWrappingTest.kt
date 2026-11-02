package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class StoryTextWrappingTest {
    @Test fun longCaptionBreaksAtWordBoundaryAndBalancesBothLines() {
        val result = StoryTextWrapping.twoLines("좋은 사람들과 행복한 시간 ♥", 12f) { it.length.toFloat() }
        assertEquals("좋은 사람들과\n행복한 시간 ♥", result)
        assertFalse(result.contains("행\n복"))
    }
    @Test fun shortCaptionKeepsSingleLine() {
        assertEquals("기분 좋은 날", StoryTextWrapping.twoLines("기분 좋은 날", 20f) { it.length.toFloat() })
    }
    @Test fun noSpacesDelegatesToNativeLineBreakerWithoutSplittingSurrogatePair() {
        val value = "행복한하루🎉"
        assertEquals(value, StoryTextWrapping.twoLines(value, 3f) { it.length.toFloat() })
    }
}
