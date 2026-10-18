package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class SleepTimerPolicyTest {
    @Test fun onlyExplicitPausedStateCanReportAlreadyPaused() {
        assertEquals(PauseDecision.ALREADY_PAUSED,SleepTimerPolicy.pauseDecision(true,2,true))
        for(state in listOf(0,1,4,5,7,9,10,11,99))
            assertEquals("state=$state",PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(true,state,true))
    }
    @Test fun advancingStatesRequestPauseOnlyWhenSupported() {
        for(state in listOf(3,6,8)) {
            assertEquals(PauseDecision.REQUEST,SleepTimerPolicy.pauseDecision(true,state,true))
            assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(true,state,false))
        }
        assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(false,2,true))
    }
    @Test fun cancellationAndPastEpisodeDoNotArmATimer() {
        assertEquals(0L,SleepTimerPolicy.deadline(0,0,1000,100))
        assertEquals(0L,SleepTimerPolicy.deadline(0,900,1000,100))
    }
    @Test fun relativeAndEpisodeDeadlinesUseMonotonicClockAndBoundDuration() {
        assertEquals(90100L,SleepTimerPolicy.deadline(0,91000,1000,100))
        assertEquals(900100L,SleepTimerPolicy.deadline(15,0,1000,100))
        assertEquals(36000100L,SleepTimerPolicy.deadline(Long.MAX_VALUE,0,1000,100))
    }
    @Test fun pauseNeverTogglesPausedOrTargetsUnsupportedSession() {
        assertEquals(PauseDecision.REQUEST,SleepTimerPolicy.pauseDecision(true,3,true))
        assertEquals(PauseDecision.ALREADY_PAUSED,SleepTimerPolicy.pauseDecision(true,2,true))
        assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(true,3,false))
        assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(false,2,true))
    }
}
