// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * 成就徽章：四枚矢量手绘图标（⏱ 沙漏 / 🔥 火焰 / 🎯 靶心 / 👑 皇冠）。
 *
 * 为什么不用 Emoji：Emoji 走系统表情字体，ColorOS / 小米 / Pixel / 厂商魔改各画各的，
 * 同一个 🏆 在不同机器上是不同的脸，而且缩到十几 dp 后细节糊成一团。这里与精灵本体
 * 一样坚持「全 Canvas 手绘、零图片资源」，徽章在每一台设备上都一模一样。
 *
 * **两种状态**：
 * - 已解锁：金色底盘 + 实心彩色图标 + 高光
 * - 未解锁：灰白底盘 + 只描边不填充的暗色轮廓（看得出是什么，但明显没点亮）
 *
 * 绘制复用 [Badges.scratch]：与 PetView 里复用 RectF 是同一个理由——统计页精灵以
 * 30fps 自续挂重绘，每帧新建 Path 会持续给 GC 添压力。假设绘制发生在同一线程。
 */
object Badges {

    enum class Kind { HOURGLASS, FLAME, TARGET, CROWN }

    /** 一格徽章：种类 + 是否已点亮。列表顺序即展示顺序（第 1 项在最下）。 */
    data class Badge(val kind: Kind, val unlocked: Boolean)

    // ───────────────────────── 调色板 ─────────────────────────
    private const val GOLD = 0xFFFFC107.toInt()
    private const val GOLD_DEEP = 0xFFFFA000.toInt()
    private const val GOLD_PALE = 0xFFFFF3C4.toInt()
    private const val DISC_LOCKED = 0xFFF2F3F5.toInt()
    private const val RING_LOCKED = 0xFFD5D9DF.toInt()
    private const val INK_LOCKED = 0xFFBFC4CC.toInt()

    /** 徽章底盘半径（view 高度的比例）—— 180dp 的 PetView 上约 22px ≈ 7.5dp 半径 */
    const val RADIUS_RATIO = 0.042f
    /** 相邻徽章圆心距 = 2r + gap */
    const val GAP_RATIO = 0.018f

    // 复用的绘制对象（见类注释：同线程假设）
    private val scratch = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * 徽章列的纵向布局（纯函数，可单测）。
     *
     * 返回第 i 格（i 从 0 开始，0 = 第 1 格）的圆心 y，**第 1 格在最下**——
     * 难度递进自下而上，与精灵左侧能量条同向，视线不用来回跳。
     */
    fun columnCenters(count: Int, centerY: Float, r: Float, gap: Float): FloatArray {
        if (count <= 0) return FloatArray(0)
        val step = r * 2f + gap
        val span = step * count - gap          // 整列占高（相邻圆心距 × 数 - 间隙）
        val first = centerY + span / 2f - r    // 第 1 格圆心
        return FloatArray(count) { first - it * step }
    }

    /**
     * 画一枚徽章。
     *
     * @param r 徽章底盘半径
     * @param unlocked 是否已解锁
     * @param glow 0..1 高亮强度，用于「刚解锁」的脉冲衰减
     */
    fun draw(
        canvas: Canvas, cx: Float, cy: Float, r: Float,
        kind: Kind, unlocked: Boolean, glow: Float = 0f
    ) {
        drawDisc(canvas, cx, cy, r, unlocked, glow)
        val s = r * 0.62f                       // 图标外接半径
        if (unlocked) {
            when (kind) {
                Kind.HOURGLASS -> hourglass(canvas, cx, cy, s, true)
                Kind.FLAME -> flame(canvas, cx, cy, s, true)
                Kind.TARGET -> target(canvas, cx, cy, s, true)
                Kind.CROWN -> crown(canvas, cx, cy, s, true)
            }
        } else {
            // 未解锁：同一个轮廓，只描边不填充 —— 形状认得出，但明显是暗的
            stroke.color = INK_LOCKED
            stroke.strokeWidth = r * 0.12f
            when (kind) {
                Kind.HOURGLASS -> hourglass(canvas, cx, cy, s, false)
                Kind.FLAME -> flame(canvas, cx, cy, s, false)
                Kind.TARGET -> target(canvas, cx, cy, s, false)
                Kind.CROWN -> crown(canvas, cx, cy, s, false)
            }
        }
    }

    // ───────────────────────── 底盘 ─────────────────────────
    private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, r: Float, unlocked: Boolean, glow: Float) {
        if (unlocked) {
            // 高亮脉冲：外扩淡金光晕，随 glow 衰减
            if (glow > 0.01f) {
                disc.shader = null
                disc.color = ((glow * 90).toInt().coerceIn(0, 255) shl 24) or 0x00FFC107
                canvas.drawCircle(cx, cy, r * (1f + 0.35f * (1f - glow)), disc)
            }
            disc.shader = RadialGradient(
                cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
                GOLD_PALE, GOLD_DEEP, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, disc)
            disc.shader = null
            stroke.color = GOLD_DEEP
            stroke.strokeWidth = r * 0.1f
            canvas.drawCircle(cx, cy, r * 0.97f, stroke)
        } else {
            disc.shader = null
            disc.color = DISC_LOCKED
            canvas.drawCircle(cx, cy, r, disc)
            stroke.color = RING_LOCKED
            stroke.strokeWidth = r * 0.08f
            canvas.drawCircle(cx, cy, r * 0.97f, stroke)
        }
    }

    // ───────────────────────── ⏱ 沙漏 ─────────────────────────
    private fun hourglass(canvas: Canvas, cx: Float, cy: Float, s: Float, filled: Boolean) {
        val w = s * 0.62f
        val hh = s * 0.86f
        // 玻璃：上下两个对顶三角。**不能用白色描边** —— 金色底盘上白描边几乎看不见，
        // 结果只剩一堆橙沙，读不出是沙漏。深棕框 + 橙沙才有对比。
        val frame = if (filled) 0xFF6D4C41.toInt() else INK_LOCKED
        scratch.reset()
        scratch.moveTo(cx - w, cy - hh); scratch.lineTo(cx + w, cy - hh); scratch.lineTo(cx, cy); scratch.close()
        scratch.moveTo(cx - w, cy + hh); scratch.lineTo(cx + w, cy + hh); scratch.lineTo(cx, cy); scratch.close()
        stroke.color = frame
        stroke.strokeWidth = s * 0.13f
        canvas.drawPath(scratch, stroke)
        if (filled) {
            fill.color = 0xFFE65100.toInt()
            // 上仓只剩底部一小撮（正在漏）
            val up = Path()
            up.moveTo(cx - w * 0.52f, cy - hh * 0.34f)
            up.lineTo(cx + w * 0.52f, cy - hh * 0.34f)
            up.lineTo(cx, cy)
            up.close()
            canvas.drawPath(up, fill)
            // 下仓已满，堆成一个梯形
            val dn = Path()
            dn.moveTo(cx - w * 0.56f, cy + hh * 0.86f)
            dn.lineTo(cx + w * 0.56f, cy + hh * 0.86f)
            dn.lineTo(cx + w * 0.34f, cy + hh * 0.06f)
            dn.lineTo(cx - w * 0.34f, cy + hh * 0.06f)
            dn.close()
            canvas.drawPath(dn, fill)
        }
        // 顶盖与底座（两种状态都要，轮廓的一部分）
        fill.color = if (filled) 0xFF5D4037.toInt() else INK_LOCKED
        canvas.drawRect(cx - w * 1.34f, cy - hh - s * 0.13f, cx + w * 1.34f, cy - hh + s * 0.06f, fill)
        canvas.drawRect(cx - w * 1.34f, cy + hh - s * 0.06f, cx + w * 1.34f, cy + hh + s * 0.13f, fill)
    }

    // ───────────────────────── 🔥 火焰 ─────────────────────────
    private fun flame(canvas: Canvas, cx: Float, cy: Float, s: Float, filled: Boolean) {
        val w = s * 0.82f
        val hh = s * 0.96f
        scratch.reset()
        scratch.moveTo(cx, cy - hh)
        scratch.cubicTo(cx + w * 0.95f, cy - hh * 0.30f, cx + w, cy + hh * 0.25f, cx + w * 0.34f, cy + hh * 0.76f)
        scratch.cubicTo(cx - w * 0.18f, cy + hh * 1.06f, cx - w, cy + hh * 0.48f, cx - w * 0.56f, cy - hh * 0.06f)
        scratch.cubicTo(cx - w * 0.36f, cy + hh * 0.24f, cx - w * 0.20f, cy - hh * 0.12f, cx - w * 0.08f, cy + hh * 0.18f)
        scratch.close()
        if (filled) {
            fill.shader = android.graphics.LinearGradient(
                cx, cy - hh, cx, cy + hh,
                0xFFFF7043.toInt(), 0xFFD84315.toInt(), Shader.TileMode.CLAMP
            )
            canvas.drawPath(scratch, fill)
            fill.shader = null
            // 内焰（亮黄）
            fill.color = 0xFFFFD54F.toInt()
            val inner = Path()
            inner.moveTo(cx, cy - hh * 0.22f)
            inner.cubicTo(cx + w * 0.42f, cy + hh * 0.08f, cx + w * 0.34f, cy + hh * 0.62f, cx, cy + hh * 0.74f)
            inner.cubicTo(cx - w * 0.34f, cy + hh * 0.62f, cx - w * 0.42f, cy + hh * 0.08f, cx, cy - hh * 0.22f)
            inner.close()
            canvas.drawPath(inner, fill)
        } else {
            stroke.color = INK_LOCKED
            stroke.strokeWidth = s * 0.15f
            canvas.drawPath(scratch, stroke)
        }
    }

    // ───────────────────────── 🎯 靶心 ─────────────────────────
    private fun target(canvas: Canvas, cx: Float, cy: Float, s: Float, filled: Boolean) {
        // 同心环：红 / 白 / 红。识别度全靠这三圈，箭只能当点缀。
        val rings = arrayOf(
            0.96f to 0xFFE53935.toInt(),
            0.68f to 0xFFFFFDFD.toInt(),
            0.42f to 0xFFE53935.toInt()
        )
        if (filled) {
            for ((rr, col) in rings) {
                fill.color = col
                canvas.drawCircle(cx, cy, s * rr, fill)
            }
        } else {
            stroke.color = INK_LOCKED
            stroke.strokeWidth = s * 0.12f
            for ((rr, _) in rings) canvas.drawCircle(cx, cy, s * rr, stroke)
        }
        // 短箭：从盘内右上角指向靶心。
        // 曾经把杆拉长到 1.25s 且起点落在靶心内部，结果是一根斜线从红心戳穿出去，
        // 缩小后完全不像箭。改成短促的一截，只保留「指向」这个信息。
        stroke.color = if (filled) 0xFF37474F.toInt() else INK_LOCKED
        stroke.strokeWidth = s * 0.16f
        canvas.drawLine(cx + s * 1.16f, cy - s * 0.86f, cx + s * 0.48f, cy - s * 0.36f, stroke)
        val head = Path()
        head.moveTo(cx + s * 0.40f, cy - s * 0.30f)   // 箭尖，压在红心外缘
        head.lineTo(cx + s * 0.66f, cy - s * 0.44f)
        head.lineTo(cx + s * 0.34f, cy - s * 0.52f)
        head.close()
        if (filled) {
            fill.color = 0xFF37474F.toInt()
            canvas.drawPath(head, fill)
        } else {
            canvas.drawPath(head, stroke)
        }
    }

    // ───────────────────────── 👑 皇冠 ─────────────────────────
    /**
     * 皇冠轮廓（皇冠徽章与雷霆之王的金冠共用同一份路径）。
     *
     * [PetView.drawKing] 也走这里 —— 之前它在自己文件里画了一份一模一样的路径，
     * 两处各画各的，改形状必然漏改一处。现在只有这一个真源。
     */
    fun crownPath(cx: Float, cy: Float, w: Float): Path {
        val h = w * 0.85f
        val top = cy - h / 2f          // 顶冠尖的 y
        scratch.reset()
        scratch.moveTo(cx - w / 2f, top + h)
        scratch.lineTo(cx - w / 2f, top + h * 0.4f)
        scratch.lineTo(cx - w * 0.3f, top + h * 0.72f)
        scratch.lineTo(cx - w * 0.16f, top)
        scratch.lineTo(cx, top + h * 0.5f)
        scratch.lineTo(cx + w * 0.16f, top)
        scratch.lineTo(cx + w * 0.3f, top + h * 0.72f)
        scratch.lineTo(cx + w / 2f, top + h * 0.4f)
        scratch.lineTo(cx + w / 2f, top + h)
        scratch.close()
        return scratch
    }

    private fun crown(canvas: Canvas, cx: Float, cy: Float, s: Float, filled: Boolean) {
        val w = s * 1.9f
        if (filled) {
            fill.shader = android.graphics.LinearGradient(
                cx, cy - w * 0.5f, cx, cy + w * 0.5f,
                0xFFFFE082.toInt(), GOLD_DEEP, Shader.TileMode.CLAMP
            )
            canvas.drawPath(crownPath(cx, cy, w), fill)
            fill.shader = null
            // 冠底宝石
            fill.color = 0xFFE53935.toInt()
            canvas.drawCircle(cx, cy + w * 0.16f, w * 0.07f, fill)
            fill.color = 0xFF42A5F5.toInt()
            canvas.drawCircle(cx - w * 0.28f, cy + w * 0.26f, w * 0.05f, fill)
            canvas.drawCircle(cx + w * 0.28f, cy + w * 0.26f, w * 0.05f, fill)
        } else {
            stroke.color = INK_LOCKED
            stroke.strokeWidth = s * 0.12f
            canvas.drawPath(crownPath(cx, cy, w), stroke)
            stroke.color = INK_LOCKED
            stroke.strokeWidth = s * 0.08f
            canvas.drawCircle(cx, cy + w * 0.16f, w * 0.07f, stroke)
        }
    }
}
