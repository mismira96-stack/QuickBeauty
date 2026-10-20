package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class EditSnapshotTest {
    @Test fun switchingFacesDoesNotMakeSavedEditsDirty() {
        val a = BeautyAdjustParams(vitality = 75)
        val b = BeautyAdjustParams(faceSize = 30)
        val saved = EditSnapshot.capture(a.copy(coolTone = 12), mapOf(0 to a, 1 to b), 0, true)
        assertEquals(saved, EditSnapshot.capture(b.copy(coolTone = 12), mapOf(0 to a, 1 to b), 1, true))
        assertNotEquals(saved, EditSnapshot.capture(b.copy(coolTone = 12, faceSize = 31),
            mapOf(0 to a, 1 to b), 1, true))
    }

    @Test fun savingCapturesValuesWithoutFollowingFurtherEdits() {
        val params = BeautyAdjustParams(vitality = 75)
        val saved = EditSnapshot.capture(params, emptyMap(), 0, false)
        params.vitality = 80
        assertEquals(75, saved.global.vitality)
        assertNotEquals(saved, EditSnapshot.capture(params, emptyMap(), 0, false))
        params.vitality = 75
        assertEquals(saved, EditSnapshot.capture(params, emptyMap(), 0, false))
    }

    @Test fun zeroValuedFaceEntriesDoNotTriggerUnsavedPrompt() {
        val empty = EditSnapshot(BeautyAdjustParams(), emptyMap())
        assertEquals(empty, EditSnapshot.capture(BeautyAdjustParams(),
            mapOf(0 to BeautyAdjustParams(), 1 to BeautyAdjustParams()), 1, true))
    }
}
