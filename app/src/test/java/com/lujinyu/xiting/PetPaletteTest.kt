package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 换肤取色回归。
 *
 * 旧实现是「画完整体做色相旋转」——青翼、紫影、金冠各自转到不同位置，
 * 撞色被完整保留，等于每次换肤重新掷一次骰子。现在色相是绘制参数，
 * 所以这里的取色规则就是「整套配色会不会串味」的唯一保证。
 *
 * [PetPalette.hueFor] 是纯函数，不碰任何 Android API，可直接单测。
 */
class PetPaletteTest {

    @Test
    fun `经典配色下各形态用各自的基准色相`() {
        val hues = (0..PetView.STAGE_KING).map { PetPalette.hueFor(it, 0f) }
        assertEquals("五个形态必须有各自的基准色相，否则形态之间没有身份差异",
            PetView.STAGE_KING + 1, hues.size)
        // 允许相邻两色接近（雷云 205 / 风暴 190），但不能全部一样
        assertTrue("形态基准色相不该退化成同一个值", hues.toSet().size >= 3)
    }

    @Test
    fun `皮肤色相直接取代基准色相而不是叠加偏移`() {
        // 叠加偏移会让「樱雨 180」在不同形态上落到不同颜色，
        // 一只精灵身上出现两个色相 —— 那正是要消灭的撞色。
        val skinHues = listOf(60f, 120f, 180f, 240f, 300f)
        for (h in skinHues) {
            for (stage in 0..PetView.STAGE_KING) {
                assertEquals("皮肤 $h 之下，形态 $stage 必须落到同一个色相",
                    h, PetPalette.hueFor(stage, h), 0.001f)
            }
        }
    }

    @Test
    fun `六款皮肤的色相两两不同`() {
        val skinHues = listOf(60f, 120f, 180f, 240f, 300f)
        assertEquals(skinHues.size, skinHues.toSet().size)
        val resolved = skinHues.map { PetPalette.hueFor(PetView.STAGE_KING, it) }
        assertEquals("换肤后不能有皮肤落到同一个色相", resolved.size, resolved.toSet().size)
    }

    @Test
    fun `越界的 stage 不会崩，退回默认色相`() {
        val out = PetPalette.hueFor(99, 0f)
        assertEquals(out, PetPalette.hueFor(-5, 0f))
        assertNotEquals(0f, out)
    }

    @Test
    fun `色相在 0-360 内`() {
        for (stage in 0..PetView.STAGE_KING) {
            for (h in listOf(0f, 60f, 120f, 180f, 240f, 300f)) {
                val v = PetPalette.hueFor(stage, h)
                assertTrue("色相 $v 越界", v >= 0f && v <= 360f)
            }
        }
    }
}