// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * 悬浮球精灵头像：把已解锁的精灵形态画到 48dp 的悬浮球上（用户可选）。
 * 静态绘制（无动画，省电）；形状为各形态的简化头像版。
 */
class BubblePetView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var stage = STAGE_BALL
    var revealStyle = true   // true=金色球系(0/1) false=云系(2+)

    companion object {
        const val STAGE_SPARK = 0
        const val STAGE_BALL = 1
        const val STAGE_CLOUD = 2
        const val STAGE_STORM = 3
        const val STAGE_KING = 4
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3B47.toInt() }
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bg = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(0xB3000000.toInt())
    }

    init {
        background = bg
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 自定义 View 必须处理 wrap_content：默认基准 48dp（悬浮球标准尺寸）
        val def = (48 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(def, widthMeasureSpec),
            resolveSize(def, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val s = minOf(w, h) * 0.34f   // 头像基础尺寸

        when (stage) {
            STAGE_SPARK, STAGE_BALL -> drawOrb(canvas, cx, cy, s, stage == STAGE_SPARK)
            else -> drawCloudHead(canvas, cx, cy, s)
        }
    }

    /** 球系头像：金色圆 + 眼睛 */
    private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, s: Float, small: Boolean) {
        val r = if (small) s * 0.8f else s
        bodyPaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
            0xFFFFF9C4.toInt(), 0xFFFFA000.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bodyPaint)
        bodyPaint.shader = null
        // 星芒（电球）
        if (!small) {
            linePaint.color = 0xCCFFFFFF.toInt()
            linePaint.strokeWidth = 1.6f
            val sx = cx + r * 0.6f
            val sy = cy - r * 0.6f
            canvas.drawLine(sx - r * 0.2f, sy, sx + r * 0.2f, sy, linePaint)
            canvas.drawLine(sx, sy - r * 0.2f, sx, sy + r * 0.2f, linePaint)
        }
        drawMiniFace(canvas, cx, cy, r * 0.24f, fierce = false)
    }

    /** 云系头像：小云朵 + 眼睛（风暴带眉/之王带冠） */
    private fun drawCloudHead(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val isStorm = stage >= STAGE_STORM
        val isKing = stage == STAGE_KING
        bodyPaint.color = when {
            isKing -> 0xFFEFE6FF.toInt()
            isStorm -> 0xFFDCEAFF.toInt()
            else -> 0xFFEAF4FF.toInt()
        }
        // 简化云：3 圆 + 底弧
        canvas.drawCircle(cx - s * 0.62f, cy + s * 0.12f, s * 0.5f, bodyPaint)
        canvas.drawCircle(cx, cy - s * 0.28f, s * 0.66f, bodyPaint)
        canvas.drawCircle(cx + s * 0.62f, cy + s * 0.12f, s * 0.5f, bodyPaint)
        canvas.drawRoundRect(
            cx - s * 0.95f, cy - s * 0.05f, cx + s * 0.95f, cy + s * 0.6f,
            s * 0.3f, s * 0.3f, bodyPaint
        )
        // 云下小闪电（风暴系）
        if (isStorm) {
            boltPaint.color = if (isKing) 0xFFFFC94D.toInt() else 0xFF8FD0FF.toInt()
            val p = Path()
            val bx = cx
            val by = cy + s * 0.62f
            val bs = s * 0.42f
            p.moveTo(bx + bs * 0.25f, by)
            p.lineTo(bx - bs * 0.35f, by + bs * 0.9f)
            p.lineTo(bx + bs * 0.02f, by + bs * 0.9f)
            p.lineTo(bx - bs * 0.25f, by + bs * 1.7f)
            p.lineTo(bx + bs * 0.42f, by + bs * 0.7f)
            p.lineTo(bx + bs * 0.06f, by + bs * 0.68f)
            p.close()
            canvas.drawPath(p, boltPaint)
        }
        // 金冠（之王）
        if (isKing) {
            val cw = s * 0.9f
            val topY = cy - s * 1.15f
            val ch = cw * 0.7f
            val p = Path()
            p.moveTo(cx - cw / 2, topY + ch)
            p.lineTo(cx - cw / 2, topY + ch * 0.35f)
            p.lineTo(cx - cw * 0.26f, topY + ch * 0.66f)
            p.lineTo(cx - cw * 0.14f, topY)
            p.lineTo(cx, topY + ch * 0.45f)
            p.lineTo(cx + cw * 0.14f, topY)
            p.lineTo(cx + cw * 0.26f, topY + ch * 0.66f)
            p.lineTo(cx + cw / 2, topY + ch * 0.35f)
            p.lineTo(cx + cw / 2, topY + ch)
            p.close()
            boltPaint.color = 0xFFFFC94D.toInt()
            canvas.drawPath(p, boltPaint)
        }
        drawMiniFace(canvas, cx, cy - s * 0.1f, s * 0.2f, fierce = isStorm)
    }

    private fun drawMiniFace(canvas: Canvas, cx: Float, cy: Float, er: Float, fierce: Boolean) {
        val off = er * 1.7f
        if (fierce) {
            linePaint.color = 0xFF2E3B47.toInt()
            linePaint.strokeWidth = er * 0.4f
            canvas.drawLine(cx - off - er * 0.6f, cy - er * 1.6f, cx - off + er * 0.6f, cy - er * 1.0f, linePaint)
            canvas.drawLine(cx + off + er * 0.6f, cy - er * 1.6f, cx + off - er * 0.6f, cy - er * 1.0f, linePaint)
        }
        canvas.drawCircle(cx - off, cy, er, eyePaint)
        canvas.drawCircle(cx + off, cy, er, eyePaint)
        canvas.drawCircle(cx - off - er * 0.3f, cy - er * 0.35f, er * 0.32f, hiPaint)
        canvas.drawCircle(cx + off - er * 0.3f, cy - er * 0.35f, er * 0.32f, hiPaint)
        // 微笑
        linePaint.color = 0xFF2E3B47.toInt()
        linePaint.strokeWidth = er * 0.34f
        val p = Path()
        p.moveTo(cx - er * 0.8f, cy + er * 1.5f)
        p.quadTo(cx, cy + er * 2.4f, cx + er * 0.8f, cy + er * 1.5f)
        canvas.drawPath(p, linePaint)
    }
}
