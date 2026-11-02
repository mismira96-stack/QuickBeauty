package com.quickbeauty.app

enum class StoryTextStyle { TEMPLATE, TITLE, LABEL, JOURNAL, MEMO;
    companion object { val availableFonts=listOf(TEMPLATE,MEMO,JOURNAL) }
}

enum class StoryBackgroundMode { AUTO, WHITE, NONE }
