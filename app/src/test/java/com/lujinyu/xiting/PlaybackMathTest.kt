package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class PlaybackMathTest {
    @Test fun extrapolatesOnlyWhileAdvancing() {
        assertEquals(120_000L, PlaybackMath.position(100_000, 10_000, 20_000, 2f, true, 600_000))
        assertEquals(100_000L, PlaybackMath.position(100_000, 10_000, 20_000, 2f, false, 600_000))
    }
    @Test fun unavailableOrFutureTimestampDoesNotInventProgress() {
        assertEquals(100_000L, PlaybackMath.position(100_000, 0, 20_000, 1f, true, 600_000))
        assertEquals(100_000L, PlaybackMath.position(100_000, 30_000, 20_000, 1f, true, 600_000))
    }
    @Test fun invalidSpeedsDoNotCreatePositionsOrDeadlines() {
        for (speed in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(100_000L, PlaybackMath.position(100_000, 10_000, 20_000, speed, true, 600_000))
            assertNull(PlaybackMath.remainingMs(600_000, 100_000, speed))
        }
    }
    @Test fun clipsToStartAndEndAndSaturates() {
        assertEquals(0L, PlaybackMath.position(-1, 10_000, 20_000, 1f, false, 600_000))
        assertEquals(600_000L, PlaybackMath.position(590_000, 10_000, 20_000, 2f, true, 600_000))
        assertEquals(Long.MAX_VALUE, PlaybackMath.position(Long.MAX_VALUE - 1, 1, Long.MAX_VALUE, 2f, true, 0))
    }
    @Test fun episodeTimeUsesSpeedAndRoundsUp() {
        assertEquals(250_000L, PlaybackMath.remainingMs(600_000, 100_000, 2f))
        assertEquals(1_000_000L, PlaybackMath.remainingMs(600_000, 100_000, .5f))
        assertEquals(1L, PlaybackMath.remainingMs(3, 2, 2f))
        assertEquals(0L, PlaybackMath.remainingMs(600_000, 600_000, 1f))
        assertNull(PlaybackMath.remainingMs(0, 100_000, 1f))
    }
    @Test fun seekOffsetsSaturateRatherThanWrap() {
        assertEquals(Long.MAX_VALUE,PlaybackMath.offset(Long.MAX_VALUE-5,30_000,0))
        assertEquals(0L,PlaybackMath.offset(100,Long.MIN_VALUE,0))
        assertEquals(600_000L,PlaybackMath.offset(590_000,30_000,600_000))
        assertEquals(70_000L,PlaybackMath.offset(100_000,-30_000,600_000))
    }
}
