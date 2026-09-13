package com.mismira.quickbeauty

/**
 * 뷰티 보정 파라미터 모델 (0 ~ 100)
 */
data class BeautyAdjustParams(
    var coolTone: Int = 0,
    var faceSlim: Int = 0,     // 1. 얼굴 가로 폭 축소 (갸름한 윤곽)
    var faceLength: Int = 0,   // 2. 얼굴 세로 길이 축소 (얼굴 짧게 / 하관 리프팅)
    var bodySlim: Int = 0      // 3. 몸매 슬림
) {
    // 이전 버전 및 호환성을 위한 chinSlim alias
    var chinSlim: Int
        get() = faceLength
        set(value) {
            faceLength = value
        }

    init {
        coolTone = coolTone.coerceIn(0, 100)
        faceSlim = faceSlim.coerceIn(0, 100)
        faceLength = faceLength.coerceIn(0, 100)
        bodySlim = bodySlim.coerceIn(0, 100)
    }

    fun isDefault(): Boolean = coolTone == 0 && faceSlim == 0 && faceLength == 0 && bodySlim == 0

    fun hasAnyEffect(): Boolean = !isDefault()

    fun reset() {
        coolTone = 0
        faceSlim = 0
        faceLength = 0
        bodySlim = 0
    }

    fun set(c: Int, f: Int, fl: Int, b: Int) {
        coolTone = c.coerceIn(0, 100)
        faceSlim = f.coerceIn(0, 100)
        faceLength = fl.coerceIn(0, 100)
        bodySlim = b.coerceIn(0, 100)
    }
}
