package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class WakeLockMetricsTest {
    @Test fun systemTimeoutStopsReportingHeldWithoutAReleaseCallback() {
        val meter=WakeLockMeter(50);meter.acquired(100)
        assertEquals(WakeLockSnapshot(false,0,50),meter.snapshot(200))
        meter.released(220);assertEquals(50L,meter.snapshot(230).totalMs)
    }
    @Test fun activeHoldIsIncludedInProcessTotal() {
        val meter = WakeLockMeter()
        meter.acquired(100)
        assertEquals(WakeLockSnapshot(true, 75, 75), meter.snapshot(175))
    }
    @Test fun duplicateAcquireAndReleaseDoNotDoubleCount() {
        val meter = WakeLockMeter()
        meter.acquired(100); meter.acquired(130); meter.released(160); meter.released(180)
        assertEquals(WakeLockSnapshot(false, 0, 60), meter.snapshot(200))
        meter.acquired(220)
        assertEquals(WakeLockSnapshot(true, 20, 80), meter.snapshot(240))
    }
    @Test fun earlierTimestampsCannotCreateNegativeDurations() {
        val meter = WakeLockMeter()
        meter.acquired(100)
        assertEquals(WakeLockSnapshot(true, 0, 0), meter.snapshot(90))
        meter.released(90)
        assertEquals(WakeLockSnapshot(false, 0, 0), meter.snapshot(200))
    }
    @Test fun simultaneousCallsKeepTotalConsistent() {
        val meter = WakeLockMeter()
        val threads = List(20) { Thread { repeat(100) { meter.acquired(100) } } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        meter.released(200)
        assertEquals(WakeLockSnapshot(false, 0, 100), meter.snapshot(300))
    }
}
