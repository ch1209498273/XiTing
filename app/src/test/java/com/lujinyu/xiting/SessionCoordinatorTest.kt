package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class SessionCoordinatorTest {
    @Test fun rollingClockBackDoesNotInventAnEndTime() {
        val c=SessionCoordinator();c.begin("clock",1000,"sleep")
        assertNull(c.finish(SessionEndReason.TIMER,100)!!.endedAt)
    }
    @Test fun runningServiceDoesNotInventPlaybackOrSession() {
        val c=SessionCoordinator()
        c.update(AssistantPhase.RUNNING,CurtainPhase.OFF,PlaybackReport.UNKNOWN,TimerPhase.NONE,0,false)
        assertEquals("",c.snapshot.id);assertEquals(PlaybackReport.UNKNOWN,c.snapshot.playback)
    }
    @Test fun aSessionEndsOnlyOnceEvenWhenServiceThenDies() {
        val c=SessionCoordinator();c.begin("one",100,"sleep")
        val result=c.finish(SessionEndReason.TIMER,200)!!
        assertEquals("timer",result.reason);assertEquals(200L,result.endedAt)
        assertNull(c.finish(SessionEndReason.USER,210))
    }
    @Test fun pauseRequestIsNotConfirmation() {
        val c=SessionCoordinator();c.begin("one",100,"sleep")
        c.pause(PauseReport.REQUESTED);assertEquals(PauseReport.REQUESTED,c.snapshot.pause)
        c.pause(PauseReport.UNCONFIRMED);assertEquals(PauseReport.UNCONFIRMED,c.snapshot.pause)
    }
    @Test fun newSessionResetsPreviousResult() {
        val c=SessionCoordinator();c.begin("one",100,"sleep");c.pause(PauseReport.FAILED)
        c.finish(SessionEndReason.USER,200);c.begin("two",300,"continuous")
        assertEquals(PauseReport.NONE,c.snapshot.pause);assertEquals("two",c.snapshot.id)
    }
    @Test fun unknownInterruptionDoesNotInventEndTime() {
        val c=SessionCoordinator();c.begin("one",100,"episode")
        assertNull(c.finish(SessionEndReason.INTERRUPTED,null)!!.endedAt)
    }
    @Test fun cancelKeepsSessionButClearsTimerAndRemaining() {
        val c=SessionCoordinator();c.begin("one",100,"sleep")
        c.update(AssistantPhase.RUNNING,CurtainPhase.LOCKED,PlaybackReport.PAUSED,TimerPhase.NONE,9000,true)
        assertEquals("one",c.snapshot.id);assertEquals(0L,c.snapshot.remainingMs)
    }
}
