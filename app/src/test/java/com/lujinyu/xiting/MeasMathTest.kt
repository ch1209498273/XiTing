package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 省电实测（被动差值版）的纯计算。
 *
 * 算式在主代码的 [MeasMath] 里（PowerCalib 要调用它才能保证单测测的是线上那套），
 * 这里验证行为。
 */
class MeasMathTest {

    private val MAX = 1500.0
    private val MIN_MS = 2 * 60_000L
    private val ONE_HOUR = 3_600_000L

    // ── 单段速率 ──

    @Test
    fun `正常段给出速率`() {
        // 1 小时消耗 1200 mWh = 1_200_000 µWh
        val r = MeasMath.rate(10_000_000, 8_800_000, ONE_HOUR, MAX)
        assertTrue(r != null && abs(r!! - 0.33333) < 0.001)
    }

    @Test
    fun `短于两分钟的段被丢弃`() {
        assertNull(MeasMath.rate(1_000_000, 999_000, MIN_MS - 1, MAX))
    }

    @Test
    fun `恰好两分钟的段保留`() {
        assertTrue(MeasMath.rate(1_000_000, 999_000, MIN_MS, MAX) != null)
    }

    @Test
    fun `计数器上升时丢弃（充电中）`() {
        assertNull(MeasMath.rate(1_000_000, 1_000_500, ONE_HOUR, MAX))
    }

    @Test
    fun `计数器不变时丢弃`() {
        assertNull(MeasMath.rate(5_000_000, 5_000_000, ONE_HOUR, MAX))
    }

    @Test
    fun `异常高速率被丢弃`() {
        // 10^11 µWh 在 2 分钟内消耗完 = 833_333 µWh/ms，远超 1500 上限
        assertNull(MeasMath.rate(1_000_000_000_000L, 990_000_000_000L, MIN_MS, MAX))
    }

    // ── 折算 ──

    @Test
    fun `亮屏比黑屏耗电多时算出正数`() {
        // 亮屏 1h 耗 2000 mWh，黑屏 1h 耗 800 mWh → 省 1200 mWh/h
        val mah = MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        )
        assertEquals(1200, mah)
    }

    @Test
    fun `黑屏反而更耗电时不给结论`() {
        val mah = MeasMath.savingMah(
            onUwh = 800_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 2_000_000, offMs = ONE_HOUR, offN = 5,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        )
        assertNull("黑屏更耗电时必须返回 null 而不是负数", mah)
    }

    @Test
    fun `亮屏样本不足时不给结论`() {
        assertNull(MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 2,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        ))
    }

    @Test
    fun `黑屏样本不足时不给结论`() {
        assertNull(MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 1,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        ))
    }

    @Test
    fun `累计时长越长省电越多`() {
        val h1 = MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        )
        val h2 = MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = 2 * ONE_HOUR, minOn = 3, minOff = 3
        )
        assertEquals(1200, h1)
        assertEquals(2400, h2)
    }

    @Test
    fun `极大累计时长不溢出`() {
        val mah = MeasMath.savingMah(
            onUwh = 2_000_000, onMs = ONE_HOUR, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = Long.MAX_VALUE / 4, minOn = 3, minOff = 3
        )
        assertEquals(Int.MAX_VALUE, mah)
    }

    @Test
    fun `零时长段被丢弃`() {
        assertNull(MeasMath.savingMah(
            onUwh = 2_000_000, onMs = 0, onN = 5,
            offUwh = 800_000, offMs = ONE_HOUR, offN = 5,
            totalMs = ONE_HOUR, minOn = 3, minOff = 3
        ))
    }
}
