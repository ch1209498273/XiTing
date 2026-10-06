// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * 单枚成就徽章（供成就弹窗 / 卡片行复用）。
 *
 * 绘制全部委托给 [Badges]，所以它和精灵身上点亮的那一列是**同一套画法** ——
 * 弹窗里看到的皇冠与精灵身上的皇冠逐像素一致，不会出现「一个 Emoji 一个手绘」的割裂。
 */
class BadgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var kind: Badges.Kind = Badges.Kind.HOURGLASS
        set(value) { if (field != value) { field = value; invalidate() } }

    var unlocked: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    private var glowFrom = 0L

    fun bind(k: Badges.Kind, u: Boolean, glow: Boolean = false) {
        kind = k
        unlocked = u
        if (glow && u) {
            glowFrom = System.currentTimeMillis()
            postInvalidateDelayed(33)
        } else {
            glowFrom = 0L
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val r = (if (width < height) width else height) * 0.46f
        if (r <= 1f) return
        var glow = 0f
        if (glowFrom > 0L) {
            val dt = System.currentTimeMillis() - glowFrom
            if (dt < GLOW_MS) {
                glow = 1f - dt / GLOW_MS.toFloat()
                postInvalidateDelayed(33)     // 脉冲期间自续挂，播完自然停
            } else {
                glowFrom = 0L
            }
        }
        Badges.draw(canvas, width / 2f, height / 2f, r, kind, unlocked, glow)
    }

    private companion object {
        const val GLOW_MS = 1400L
    }
}
