package com.quickbeauty.app

import org.junit.Assert.*
import org.junit.Test

class BodySlimProfileTest {
    @Test fun armBandTransfersWithoutHorizontalExpansion() {
        for (strength in listOf(0f,.10f,.22f,.38f)) for (side in listOf(-1f,1f)) {
            for (i in 35..80) {
                val a=side*i;val b=side*(i+1)
                val outA=a-BodySlimProfile.inwardDistance(a,100f)*strength
                val outB=b-BodySlimProfile.inwardDistance(b,100f)*strength
                assertEquals(1f,kotlin.math.abs(outB-outA),.0001f)
            }
        }
    }
    @Test fun plateauIsContinuousAndDoesNotFoldTheTransition() {
        for (u in listOf(0f,BodySlimProfile.INNER,BodySlimProfile.ARM_END,BodySlimProfile.OUTER)) {
            val a=BodySlimProfile.inwardDistance(u*100-0.001f,100f)
            val b=BodySlimProfile.inwardDistance(u*100+0.001f,100f)
            assertTrue(kotlin.math.abs(a-b)<.01f)
        }
        var previous=Float.NEGATIVE_INFINITY
        for (x in -200..200) {
            val out=x-BodySlimProfile.inwardDistance(x.toFloat(),100f)*.38f
            assertTrue(out>previous);previous=out
        }
        assertEquals(0f,BodySlimProfile.inwardDistance(200f,100f),0f)
    }
}
