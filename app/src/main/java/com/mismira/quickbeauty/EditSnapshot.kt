package com.quickbeauty.app

/** Independent of which face is currently selected; values are copied at capture time. */
internal data class EditSnapshot(
    val global: BeautyAdjustParams,
    val faces: Map<Int, BeautyAdjustParams>
) {
    companion object {
        fun capture(params: BeautyAdjustParams, faces: Map<Int, BeautyAdjustParams>,
                    selected: Int, multiple: Boolean): EditSnapshot {
            if (!multiple) return EditSnapshot(params.copy(), emptyMap())
            val edits = faces.mapValues { it.value.copy(coolTone = 0, legStretch = 0) }.toMutableMap()
            edits[selected] = params.copy(coolTone = 0, legStretch = 0)
            return EditSnapshot(BeautyAdjustParams(coolTone = params.coolTone, legStretch = params.legStretch),
                edits.filterValues { it.hasAnyEffect() })
        }
    }
}
