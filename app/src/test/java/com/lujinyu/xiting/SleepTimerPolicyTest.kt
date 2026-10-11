package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class SleepTimerPolicyTest {
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
        assertEquals(PauseDecision.REQUEST,SleepTimerPolicy.pauseDecision(true,true,true))
        assertEquals(PauseDecision.ALREADY_PAUSED,SleepTimerPolicy.pauseDecision(true,false,true))
        assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(true,true,false))
        assertEquals(PauseDecision.UNAVAILABLE,SleepTimerPolicy.pauseDecision(false,false,true))
    }
}
