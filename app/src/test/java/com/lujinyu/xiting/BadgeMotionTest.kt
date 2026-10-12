package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class BadgeMotionTest {
    @Test fun `celebration settles and never loops`() {
        for(t in listOf(1800L,10000L,Long.MAX_VALUE)) {
            val f=BadgeMotion.frame(t,true)
            assertFalse(f.active);assertEquals(1f,f.scale,0f);assertEquals(1f,f.alpha,0f)
            assertEquals(0f,f.burst,0f);assertEquals(0f,f.shine,0f)
        }
    }
    @Test fun `system disabled animations show complete medal immediately`() {
        val f=BadgeMotion.frame(0,false)
        assertFalse(f.active);assertEquals(1f,f.scale,0f);assertEquals(1f,f.alpha,0f)
    }
    @Test fun `all frames stay finite and within drawing bounds`() {
        for(t in -100L..1800L step 13) {
            val f=BadgeMotion.frame(t,true)
            assertTrue(f.scale in 0.5f..1.15f)
            assertTrue(f.alpha in 0f..1f);assertTrue(f.burst in 0f..1f);assertTrue(f.shine in 0f..1f)
        }
    }
}
