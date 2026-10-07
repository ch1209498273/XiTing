// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.graphics.Color

/**
 * 精灵调色板：把「色相」从**绘制后的后处理滤镜**变成**绘制参数**。
 *
 * ## 为什么不再用 ColorMatrix 做换肤
 *
 * 旧实现是「画完再整体做色相旋转」。这个思路有两个无法回避的问题：
 *
 * 1. **撞色被完整保留**。青翼、紫影、金冠各自 `+hue`，转到三个不同的地方，
 *    于是每换一次肤，附属元素就在重新掷一次骰子 —— 用户看到的「配色」其实是随机结果，
 *    不存在任何设计意图。放大真机截图能直接看到紫身体配青绿翼再配绿冠。
 * 2. **状态语义被染色**。灰白色的未解锁徽章经过旋转会变成橙红/绿色实心感，
 *    看起来像「已解锁」（已单独移出滤镜，但同类问题还会再犯）。
 *
 * 改成「按色相现算颜色」之后：一只精灵身上的**所有**颜色都来自同一个 hue，
 * 只在明度与饱和度上有差别 —— 天然的同色调色板。
 *
 * ## 低饱和度的元素自动保住原色
 *
 * 五官用的墨色 `#2E3B47`、打瞌睡时的灰色 `#CFD8DC`、闭眼线条，饱和度都很低。
 * HSV 取色时它们本来就接近中性，改色相几乎看不出变化 —— 不需要写任何保护逻辑。
 *
 * ## 两种取色路径
 *
 * - **经典配色**（skinHue == 0）：各形态用自己的基准色相，靠它 + 剪影区分身份。
 * - **其余五款皮肤**：统一用皮肤的色相，形态身份完全交给剪影承载。
 */
object PetPalette {

    /**
     * 各形态在「经典配色」下的基准色相（度）。
     *
     * 刻意选成两两差异明显：暖黄(45) / 紫(270) / 天蓝(205) / 青(190) / 蓝紫(285)。
     * 电火花与电球的对比靠暖冷拉开，风暴之灵与雷云精灵的对比靠 190/205 之外的明度差。
     */
    private val BASE_HUE = floatArrayOf(45f, 270f, 205f, 190f, 285f)

    private const val FALLBACK_HUE = 265f

    /**
     * 生效色相（纯函数，可单测，不碰任何 Android API）。
     *
     * skinHue == 0 表示「经典配色」，此时用形态自己的基准色相；
     * 否则皮肤的色相直接**取代**基准色相（不是叠加偏移），
     * 这样六款皮肤必然是六个干净的单一色相，互不串味。
     */
    fun hueFor(stage: Int, skinHue: Float): Float {
        if (skinHue != 0f) return skinHue
        return if (stage in BASE_HUE.indices) BASE_HUE[stage] else FALLBACK_HUE
    }

    /**
     * 一整套派生色。
     *
     * 全部来自同一个 hue，靠 s(饱和度) / v(明度) 拉开层次。
     *
     * ⚠️ **明度不能太高、饱和度不能太低** —— 第一版把 body 定在 v=0.86 / s=0.52，
     * 结果用户反馈「这几个精灵像鬼一样」。原因很直白：幽灵就是「又亮又淡的白」，
     * 而整套配色收敛成单色之后，只剩下一个高明度低饱和的主体，没有任何东西压住它。
     * 现在主体明显压暗、显著加饱和，只有受光面才留高光 —— 整体读作「有体积的实体」
     * 而不是「一团发光的雾」。
     */
    class Palette(private val hue: Float, private val satMul: Float) {
        private fun c(s: Float, v: Float): Int =
            Color.HSVToColor(floatArrayOf(hue, (s * satMul).coerceIn(0f, 1f), v))

        /** 受光高光面 —— 唯一允许接近纯白的一层 */
        val highlight = c(0.45f, 1.00f)
        /** 主体亮面 */
        val bodyLight = c(0.62f, 0.86f)
        /** 主体本色 */
        val body = c(0.70f, 0.72f)
        /** 暗面 */
        val shade = c(0.75f, 0.54f)
        /** 最暗处（描边、内阴影）—— 必须真的暗，否则整体还是发灰 */
        val deep = c(0.80f, 0.36f)
        /** 强调色：闪电、光环、螺旋臂 */
        val accent = c(0.95f, 1.00f)
        /** 次强调色：远端闪电、尾部 */
        val accentSoft = c(0.85f, 0.88f)
        /** 光晕 */
        val glow = c(0.62f, 1.00f)
    }

    fun of(stage: Int, skinHue: Float, satMul: Float) = Palette(hueFor(stage, skinHue), satMul)
}