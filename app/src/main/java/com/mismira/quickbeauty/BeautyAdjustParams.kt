package com.quickbeauty.app

/**
 * 뷰티 보정 파라미터 모델 (0 ~ 100)
 */
data class BeautyAdjustParams(
    var coolTone: Int = 0,     // 1. 쿨톤 피부
    var faceSize: Int = 0,     // 2. 얼굴 전체 크기 축소 (소두)
    var chinSlim: Int = 0,     // 3. 턱선 V라인 슬림
    var faceLength: Int = 0,   // 4. 얼굴 세로 길이 축소 (얼굴 짧게 / 하관 리프팅)
    var shoulder: Int = 0,     // 5. 어깨 넓히기 (직각 어깨)
    var bodySlim: Int = 0      // 6. 몸매 슬림
) {
    // 이전 버전 및 호환성을 위한 faceSlim alias
    var faceSlim: Int
        get() = faceSize
        set(value) {
            faceSize = value
        }

    init {
        coolTone = coolTone.coerceIn(0, 100)
        faceSize = faceSize.coerceIn(0, 100)
        chinSlim = chinSlim.coerceIn(0, 100)
        faceLength = faceLength.coerceIn(0, 100)
        shoulder = shoulder.coerceIn(0, 100)
        bodySlim = bodySlim.coerceIn(0, 100)
    }

    fun isDefault(): Boolean = coolTone == 0 && faceSize == 0 && chinSlim == 0 && faceLength == 0 && shoulder == 0 && bodySlim == 0

    fun hasAnyEffect(): Boolean = !isDefault()

    fun reset() {
        coolTone = 0
        faceSize = 0
        chinSlim = 0
        faceLength = 0
        shoulder = 0
        bodySlim = 0
    }

    fun set(c: Int, fs: Int, cs: Int, fl: Int, sh: Int, b: Int) {
        coolTone = c.coerceIn(0, 100)
        faceSize = fs.coerceIn(0, 100)
        chinSlim = cs.coerceIn(0, 100)
        faceLength = fl.coerceIn(0, 100)
        shoulder = sh.coerceIn(0, 100)
        bodySlim = b.coerceIn(0, 100)
    }

    // 5개 인자 호환용 오버로드
    fun set(c: Int, fs: Int, cs: Int, fl: Int, b: Int) {
        set(c, fs, cs, fl, 0, b)
    }

    // 4개 인자 호환용 오버로드
    fun set(c: Int, fs: Int, fl: Int, b: Int) {
        set(c, fs, 0, fl, 0, b)
    }
}

