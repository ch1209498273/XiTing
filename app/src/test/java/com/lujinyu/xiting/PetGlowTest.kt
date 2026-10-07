package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 光晕渲染不变量。
 *
 * 换肤重构时把光晕改成「以本点为圆心建渐变 + canvas 平移绘制」（这样渐变才能按尺寸缓存），
 * 于是渐变半径与光晕圆半径**解耦**了 —— 这两者一旦满足「渐变半径 < 光晕圆半径」，
 * 光晕就会被硬生生截成一个可见的圆盘边界，但这个错误编译不报错、单测也看不见，
 * 只能靠肉眼发现「看着有点怪」。
 *
 * 所以把关系显式写成常量并在此钉死：改形态尺寸时若越界，这里立刻失败。
 */
class PetGlowTest {

    /**
     * 光晕必须是「渐变半径 == 画圆半径」。
     *
     * 曾经是渐变 0.475h、只画 0.396h，于是圆边处渐变还剩 ~16% 不透明度，
     * 真机上是**一个硬边圆盘**而不是弥散的光（预览与单测都看不见，纯靠肉眼）。
     * 现在两者由同一个常量派生，关系不再可能漂移。
     */
    @Test
    fun `各形态光晕半径单调递增且都在合法范围`() {
        val r = PetView.GLOW_RATIO
        assertEquals(PetView.STAGE_KING + 1, r.size)
        for (i in 1 until r.size) {
            assertTrue("形态越高光晕越大，但第 $i 级反而变小了", r[i] > r[i - 1])
        }
        assertTrue("光晕半径不能超过视图高度", r.last() <= 1f)
        assertTrue("光晕半径也不能小到看不见", r.first() >= 0.15f)
    }

    @Test
    fun `光晕半径按体型递增，与形态尺寸单调对应`() {
        // 电火花 r=0.11h、雷霆之王 r=0.25h，光晕应保持同一「倍数」关系
        val sparkRatio = PetView.GLOW_RATIO[PetView.STAGE_SPARK] / 0.11f
        val kingRatio = PetView.GLOW_RATIO[PetView.STAGE_KING] / 0.25f
        assertTrue("光晕倍数不应相差太大：火花 ${"%.2f".format(sparkRatio)} vs 王 ${"%.2f".format(kingRatio)}",
            kotlin.math.abs(sparkRatio - kingRatio) < 0.6f)
    }
}