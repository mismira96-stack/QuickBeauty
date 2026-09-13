package com.mismira.quickbeauty

/**
 * 뷰티 보정 파라미터 모델 (0 ~ 100)
 */
data class BeautyAdjustParams(
    var coolTone: Int = 0,
    var faceSlim: Int = 0,
    var chinSlim: Int = 0,
    var bodySlim: Int = 0
) {
    init {
        coolTone = coolTone.coerceIn(0, 100)
        faceSlim = faceSlim.coerceIn(0, 100)
        chinSlim = chinSlim.coerceIn(0, 100)
        bodySlim = bodySlim.coerceIn(0, 100)
    }

    fun isDefault(): Boolean = coolTone == 0 && faceSlim == 0 && chinSlim == 0 && bodySlim == 0

    fun reset() {
        coolTone = 0
        faceSlim = 0
        chinSlim = 0
        bodySlim = 0
    }
}
