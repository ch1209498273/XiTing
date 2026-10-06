package com.lujinyu.xiting

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

    @Test
    fun `渐变半径必须覆盖所有形态的光晕圆半径`() {
        assertTrue(
            "渐变半径 ${PetView.GLOW_GRADIENT_RATIO} 小于光晕圆最大半径 ${PetView.GLOW_CIRCLE_MAX_RATIO}，" +
                "光晕会被截成可见的圆盘边界",
            PetView.GLOW_GRADIENT_RATIO >= PetView.GLOW_CIRCLE_MAX_RATIO
        )
    }

    @Test
    fun `渐变半径不能大得离谱`() {
        // 渐变半径远大于光晕圆的话，淡出会拖到光晕圈外，光晕看起来发虚不集中
        val ratio = PetView.GLOW_GRADIENT_RATIO / PetView.GLOW_CIRCLE_MAX_RATIO
        assertTrue("渐变半径是光晕圆的 ${"%.2f".format(ratio)} 倍，淡出拖得太远",
            ratio <= 1.15f)
    }

    @Test
    fun `最大形态半径就是常量的定义来源`() {
        // GLOW_CIRCLE_MAX_RATIO = 最大形态半径(0.25) × 脉动系数(1.85)
        val biggest = 0.25f
        val pulse = 1.85f
        assertTrue(
            "常量与实际用到的最大圆半径对不上：${PetView.GLOW_CIRCLE_MAX_RATIO} vs ${biggest * pulse}",
            kotlin.math.abs(PetView.GLOW_CIRCLE_MAX_RATIO - biggest * pulse) < 1e-6f
        )
    }
}