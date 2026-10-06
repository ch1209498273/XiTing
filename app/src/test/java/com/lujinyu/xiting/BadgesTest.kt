package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 徽章列纵向布局回归。
 *
 * [Badges.columnCenters] 把「第 1 格在最下、难度递进自下而上」这条设计约定
 * 写成了纯函数，这里把该约定钉死：调错了只是歪一列，不会崩，但用户一眼看出来。
 */
class BadgesTest {

    private val r = 20f
    private val gap = 8f
    private val centerY = 300f

    @Test
    fun `格数为零时返回空数组`() {
        assertEquals(0, Badges.columnCenters(0, centerY, r, gap).size)
        assertEquals(0, Badges.columnCenters(-1, centerY, r, gap).size)
    }

    @Test
    fun `只有一枚时正好居中`() {
        val ys = Badges.columnCenters(1, centerY, r, gap)
        assertEquals(1, ys.size)
        assertEquals(centerY, ys[0], 0.001f)
    }

    @Test
    fun `第一格在最下、向上递增递进`() {
        val ys = Badges.columnCenters(4, centerY, r, gap)
        assertEquals(4, ys.size)
        assertTrue("第 1 格必须在最下", ys[0] > ys[1] && ys[1] > ys[2] && ys[2] > ys[3])
    }

    @Test
    fun `相邻圆心距恒为两倍半径加间隙`() {
        val ys = Badges.columnCenters(4, centerY, r, gap)
        for (i in 0 until ys.size - 1) {
            assertEquals(r * 2f + gap, ys[i] - ys[i + 1], 0.001f)
        }
    }

    @Test
    fun `整列关于 centerY 对称`() {
        val ys = Badges.columnCenters(4, centerY, r, gap)
        assertEquals(centerY - ys.first(), ys.last() - centerY, 0.001f)
    }

    @Test
    fun `整列不会超出给定半径加间隙的包络`() {
        val ys = Badges.columnCenters(4, centerY, r, gap)
        // 列的总跨度 = 圆心距×数 - 间隙（首尾各扣一个半径）
        val span = ys.first() - ys.last() + r * 2f
        assertEquals(r * 2f * 4 + gap * 3, span, 0.001f)
    }
}
