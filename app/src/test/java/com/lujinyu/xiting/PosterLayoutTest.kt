package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 海报版式（PosterLayout）。
 *
 * 钉死三类东西：
 * 1. **回归锚**：3:4 × 3 行的帧必须与 2026-10-11 版式重构前的手写坐标逐项一致
 *    —— 重构不改变既有海报的观感（像素级对齐 196/561/929/1029/1309/1355/1407 那条链）；
 * 2. **不变量**：两种比例 × 1..4 行，纵向链无重叠、页脚收在画布内、精灵不低于下限；
 * 3. **弹性方向**：比例越方（高度越小）、行数越多，精灵越小 —— 收缩方向不许反。
 */
class PosterLayoutTest {

    /** 精灵边长下限 = 0.28 × 1080（PosterLayout.PET_MIN_SCALE 的字面镜像，钉死公开行为） */
    private val petMin = 302

    @Test
    fun portrait34ThreeLinesMatchesLegacyCoordinates() {
        val f = PosterLayout.compute(PosterLayout.Ratio.PORTRAIT_3_4, 3)
        assertEquals(116f, f.headerY, 0.01f)
        assertEquals(196f, f.petTop, 0.01f)
        assertEquals(561, f.petSize)
        assertEquals(929f, f.headY, 0.01f)
        assertEquals(987f, f.hintY, 0.01f)
        assertEquals(1029f, f.cardTop, 0.01f)
        assertEquals(280f, f.cardH, 0.01f)
        assertEquals(68f, f.cardTextTop, 0.01f)
        assertEquals(1309f, f.cardBottom, 0.01f)
        assertEquals(1355f, f.footer1Y, 0.01f)
        assertEquals(1407f, f.footer2Y, 0.01f)
    }

    @Test
    fun bothRatiosOneToFourLinesHaveNoVerticalOverlap() {
        for (ratio in PosterLayout.Ratio.values()) {
            for (n in 1..4) {
                val f = PosterLayout.compute(ratio, n)
                assertTrue("$ratio/$n 顶栏在精灵上方", f.headerY < f.petTop)
                assertTrue("$ratio/$n 精灵在主数字上方", f.petTop + f.petSize < f.headY)
                assertTrue("$ratio/$n 说明在主数字下方", f.headY < f.hintY)
                assertTrue("$ratio/$n 卡片在说明下方", f.hintY < f.cardTop)
                assertEquals("$ratio/$n 卡片底自洽", f.cardTop + f.cardH, f.cardBottom, 0.01f)
                assertTrue("$ratio/$n 页脚在卡片下方", f.cardBottom < f.footer1Y)
                assertTrue("$ratio/$n 仓库行在脚注下方", f.footer1Y < f.footer2Y)
                // 基线以下至少留 20px（字形下伸 + 视觉收边），页脚不许贴边/出画布
                assertTrue("$ratio/$n 页脚收在画布内", f.footer2Y <= ratio.h - 20f)
                assertTrue("$ratio/$n 精灵不低于下限", f.petSize >= petMin)
            }
        }
    }

    @Test
    fun squareRatioShrinksPetAgainstPortrait() {
        val p34 = PosterLayout.compute(PosterLayout.Ratio.PORTRAIT_3_4, 3).petSize
        val p11 = PosterLayout.compute(PosterLayout.Ratio.SQUARE_1_1, 3).petSize
        assertTrue("1:1 的精灵（$p11）应小于 3:4（$p34）", p11 < p34)
    }

    @Test
    fun moreLinesNeverGrowThePet() {
        for (ratio in PosterLayout.Ratio.values()) {
            var prev = Int.MAX_VALUE
            for (n in 1..4) {
                val s = PosterLayout.compute(ratio, n).petSize
                assertTrue("$ratio 行数 $n：精灵 $s 应 ≤ 前一行数的 $prev", s <= prev)
                prev = s
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fiveLinesIsRejected() {
        PosterLayout.compute(PosterLayout.Ratio.SQUARE_1_1, 5)
    }
}
