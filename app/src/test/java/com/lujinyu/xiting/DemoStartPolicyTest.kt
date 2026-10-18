package com.lujinyu.xiting
import org.junit.Assert.*
import org.junit.Test
class DemoStartPolicyTest {
 @Test fun missingOverlayDoesNotStartSilentBlackScreen(){assertEquals(DemoStartDecision.PERMISSION,demoStartDecision(false,false,5,false))}
 @Test fun existingCurtainOrTimerIsPreserved(){assertEquals(DemoStartDecision.BUSY,demoStartDecision(true,true,5,false))}
 @Test fun mutedMediaGivesVolumeHelpBeforeCurtain(){assertEquals(DemoStartDecision.MUTED,demoStartDecision(true,false,0,false))}
 @Test fun otherPlaybackMustNotBeInterruptedBySample(){assertEquals(DemoStartDecision.PLAYBACK,demoStartDecision(true,false,5,true))}
 @Test fun idleAudibleDemoCanStartWithoutExternalPlayer(){assertEquals(DemoStartDecision.START,demoStartDecision(true,false,5,false))}
}
