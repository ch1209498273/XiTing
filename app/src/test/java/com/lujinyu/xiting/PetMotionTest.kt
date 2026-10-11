package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class PetMotionTest {
    @Test fun holdingBallChargesButShortTapStillResponds() {
        assertEquals(PetAction.BOUNCE,PetMotion.action(1,90,0f,0f))
        assertEquals(PetAction.CHARGE,PetMotion.action(1,700,0f,0f))
    }
    @Test fun stormRespondsToHorizontalSwipeButNotVerticalScroll() {
        assertEquals(PetAction.SPIN,PetMotion.action(3,150,80f,6f))
        assertEquals(PetAction.NONE,PetMotion.action(3,150,6f,80f))
        assertEquals(PetAction.NONE,PetMotion.action(2,150,80f,6f))
    }
    @Test fun tapsAndLongPressesHaveStageSpecificResponses() {
        assertEquals(PetAction.IGNITE,PetMotion.action(0,100,0f,0f))
        assertEquals(PetAction.LIGHTNING,PetMotion.action(2,100,0f,0f))
        assertEquals(PetAction.SPIN,PetMotion.action(3,100,0f,0f))
        assertEquals(PetAction.WAVE,PetMotion.action(4,100,0f,0f))
        assertEquals(PetAction.WINGS,PetMotion.action(4,650,0f,0f))
    }
    @Test fun motionSettlesAndStaysWithinBounds() {
        for(a in PetAction.entries.filter{it!=PetAction.NONE}) {
            val f=PetMotion.frame(a,10_000,1f)
            assertEquals(1f,f.scaleX,.0001f);assertEquals(1f,f.scaleY,.0001f)
            assertEquals(0f,f.lift,.0001f);assertEquals(0f,f.effect,.0001f)
            for(t in 0..2000 step 17) {
                val v=PetMotion.frame(a,t.toLong(),1f)
                assertTrue(v.scaleX in .7f..1.3f && v.scaleY in .7f..1.3f)
                assertTrue(v.effect in 0f..1f && v.lift.isFinite())
            }
        }
    }
    @Test fun helpAndCollectionTakePriorityOverPet() {
        assertEquals(PetTarget.HELP,PetMotion.target(true,true,true))
        assertEquals(PetTarget.ENERGY,PetMotion.target(false,true,true))
        assertEquals(PetTarget.PET,PetMotion.target(false,false,true))
        assertEquals(PetTarget.NONE,PetMotion.target(false,false,false))
    }
}
