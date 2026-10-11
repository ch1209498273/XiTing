package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ±30s 的目标位置换算（clampSeekTarget）。
 *
 * 连点累加逻辑住在 BlackOverlay（UI 状态，JVM 测不了），这里钉死边界行为：
 * 越过片尾夹到片尾、退过片头夹到 0、时长读不到（=0）时只保下限不乱裁——
 * 「listen until sleep」场景下用户点 +30 的目标永远不该变成负数或 NaN。
 */
class SeekMathTest {

    @Test
    fun insideDurationPassesThrough() {
        // clamp 只管边界：范围内的目标原样通过（+30 的加法在调用方）
        assertEquals(130_000L, clampSeekTarget(100_000L + 30_000L, 600_000L))
        assertEquals(70_000L, clampSeekTarget(100_000L - 30_000L, 600_000L))
    }

    @Test
    fun beyondEndClampsToDuration() {
        assertEquals(600_000L, clampSeekTarget(590_000L + 30_000L, 600_000L))
        assertEquals(600_000L, clampSeekTarget(Long.MAX_VALUE / 2, 600_000L))
    }

    @Test
    fun beforeStartClampsToZero() {
        assertEquals(0L, clampSeekTarget(20_000L - 30_000L, 600_000L))
        assertEquals(0L, clampSeekTarget(-5_000L, 600_000L))
    }

    @Test
    fun missingDurationOnlyKeepsLowerBound() {
        // duration 读不到（=0）：不臆造上限，只保证不为负
        assertEquals(130_000L, clampSeekTarget(130_000L, 0L))
        assertEquals(0L, clampSeekTarget(-30_000L, 0L))
        assertEquals(0L, clampSeekTarget(0L, -1L))
    }
}
