package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class PetGrowthTest {
    @Test fun thresholdsAndRemainingAreAboutGrowthNotWornForm() {
        val points=longArrayOf(0,29,30,299,300,1499,1500,5999,6000)
        val stages=intArrayOf(0,0,1,1,2,2,3,3,4)
        val remaining=longArrayOf(30,1,270,1,1200,1,4500,1,0)
        points.forEachIndexed { i,gp ->
            val p=PetGrowth.progress(gp)
            assertEquals(stages[i],p.stage)
            assertEquals(remaining[i],p.remaining)
            assertEquals(gp>=6000,p.complete)
        }
    }
    @Test fun corruptNegativeAndVeryLargeProgressStayBounded() {
        assertEquals(30L,PetGrowth.progress(-5).remaining)
        assertTrue(PetGrowth.progress(Long.MAX_VALUE).complete)
    }
}
