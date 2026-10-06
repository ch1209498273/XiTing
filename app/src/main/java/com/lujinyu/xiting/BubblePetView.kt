// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 悬浮球精灵头像：与精灵五形态视觉绑定（同款画法迷你版）+ 呼吸/闪烁动画。
 * 呼吸微缩放、电弧环旋转、每3秒眨眼——悬浮球不再是一张死图。
 */
class BubblePetView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var stage = STAGE_BALL

    companion object {
        /**
         * 重绘间隔。
         *
         * 原为 50ms（20fps）。呼吸是 ±4% 慢摆、眨眼 140ms，对帧率不敏感，
         * 100ms（10fps）肉眼无差别，日常待机开销直接减半。
         */
        private const val FRAME_MS = 100L

        const val STAGE_SPARK = 0
        const val STAGE_BALL = 1
        const val STAGE_CLOUD = 2
        const val STAGE_STORM = 3
        const val STAGE_KING = 4
    }

    private val bornAt = System.currentTimeMillis()
    private var nextBlinkAt = System.currentTimeMillis() + 2500
    private var blinkUntil = 0L

    /**
     * 动画是否运行。
     *
     * 黑幕是一整块不透明全屏窗，把悬浮球完全盖住，此时它在背后继续 10fps 重绘
     * 是纯粹的空转——一次两小时的听剧就是十几万帧看不见的开销。
     * 黑幕显示时由 OverlayService 调 setAnimating(false) 停掉，收幕时恢复。
     */
    private var animating = true

    fun setAnimating(on: Boolean) {
        if (animating == on) return
        animating = on
        // 停帧之后 onDraw 不再续挂，这里必须补一次；启动时同理
        if (on) postInvalidateDelayed(FRAME_MS)
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3B47.toInt() }
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bg = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(0xB3000000.toInt())
    }

    init {
        background = bg
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val def = (48 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(def, widthMeasureSpec),
            resolveSize(def, heightMeasureSpec)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animating) postInvalidateDelayed(FRAME_MS)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val t = (now - bornAt) / 1000f
        val cx = width / 2f
        val cy = height / 2f

        // 呼吸（±4%）
        val breathe = 1f + 0.04f * sin(t * 2.2f)
        val s = minOf(width, height) * 0.30f * breathe

        // 眨眼
        if (now > nextBlinkAt) {
            blinkUntil = now + 140
            nextBlinkAt = now + 2600 + (abs(now % 1600)).toInt()
        }
        val blinking = now < blinkUntil

        when (stage) {
            STAGE_SPARK -> drawSpark(canvas, cx, cy, s, t, blinking)
            STAGE_BALL -> drawBall(canvas, cx, cy, s, t, blinking)
            STAGE_CLOUD -> drawCloud(canvas, cx, cy, s, t, blinking)
            STAGE_STORM -> drawStorm(canvas, cx, cy, s, t, blinking)
            else -> drawKing(canvas, cx, cy, s, t, blinking)
        }

        if (animating) postInvalidateDelayed(FRAME_MS)
    }

    // 形态一：四角星
    private fun drawSpark(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val p = Path()
        val spin = t * 0.5f
        for (i in 0 until 16) {
            val ang = (6.2831855f * i / 16) + spin
            val rad = if (i % 2 == 0) s * 1.25f else s * 0.55f
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        bodyPaint.shader = android.graphics.LinearGradient(
            cx - s, cy - s, cx + s, cy + s,
            0xFFFFF176.toInt(), 0xFFFFA000.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawPath(p, bodyPaint)
        bodyPaint.shader = null
        drawMiniFace(canvas, cx, cy, s * 0.26f, blinking)
    }

    // 形态二：紫色等离子球 + 青蓝环
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        bodyPaint.shader = RadialGradient(
            cx - s * 0.3f, cy - s * 0.3f, s * 1.5f,
            0xFFB388FF.toInt(), 0xFF3F1D96.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, s, bodyPaint)
        bodyPaint.shader = null
        // 内部电弧
        linePaint.color = 0xCCB2EBF2.toInt()
        linePaint.strokeWidth = 1.8f
        val ap = Path()
        for (k in 0 until 5) {
            val kk = k / 4f
            val ax = cx - s * 0.6f + s * 1.2f * kk
            val ay = cy + sin(t * 2f + kk * 8f) * s * 0.3f
            if (k == 0) ap.moveTo(ax, ay) else ap.lineTo(ax, ay)
        }
        canvas.drawPath(ap, linePaint)
        // 青蓝环（旋转）
        linePaint.color = 0xCC00E5FF.toInt()
        linePaint.strokeWidth = 2.5f
        val ring = RectF(cx - s * 1.35f, cy - s * 1.35f, cx + s * 1.35f, cy + s * 1.35f)
        canvas.drawArc(ring, -t * 90, 100f, false, linePaint)
        canvas.drawArc(ring, 180 - t * 90, 100f, false, linePaint)
        drawMiniFace(canvas, cx, cy, s * 0.24f, blinking)
    }

    // 形态三：白云 + 闪电
    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val puffs = arrayOf(
            floatArrayOf(-0.55f, 0.1f, 0.42f), floatArrayOf(0f, -0.25f, 0.55f), floatArrayOf(0.55f, 0.1f, 0.42f)
        )
        bodyPaint.color = 0xFFEAF4FF.toInt()
        for (p in puffs) canvas.drawCircle(cx + p[0] * s, cy + p[1] * s, p[2] * s, bodyPaint)
        canvas.drawRoundRect(RectF(cx - s * 0.8f, cy - s * 0.05f, cx + s * 0.8f, cy + s * 0.5f), s * 0.25f, s * 0.25f, bodyPaint)
        boltPaint.color = 0xFF7FC4FF.toInt()
        drawBolt(canvas, cx, cy + s * 0.55f, s * 0.4f)
        drawMiniFace(canvas, cx, cy - s * 0.1f, s * 0.22f, blinking)
    }

    // 形态四：小龙卷风
    private fun drawStorm(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val layers = 5
        for (i in 0 until layers) {
            val k = i / (layers - 1f)
            val w = s * (1.05f - 0.75f * k)
            val y = cy - s * 0.7f + k * s * 1.4f
            val hh = s * 0.26f * (1f - 0.4f * k)
            bodyPaint.color = if (i < 3) 0xFFDCEAFF.toInt() else 0xFF9FB6E8.toInt()
            canvas.drawOval(RectF(cx - w, y - hh / 2, cx + w, y + hh / 2), bodyPaint)
        }
        boltPaint.color = 0xFF8FD0FF.toInt()
        drawBolt(canvas, cx + s * 0.85f, cy, s * 0.32f)
        drawMiniFace(canvas, cx, cy - s * 0.28f, s * 0.2f, blinking, fierce = true)
    }

    // 形态五：紫云 + 金冠 + 雷电双翼
    private fun drawKing(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        // 双翼（3根锯齿，翼尖微扇动）
        val flap = 0.06f * sin(t * 3f)
        val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC94D.toInt() }
        for (side in intArrayOf(-1, 1)) {
            for (f in 0 until 3) {
                val fp = Path()
                val baseX = cx + side * s * 0.8f
                val baseY = cy - s * 0.15f + f * s * 0.28f
                val tipX = cx + side * s * (1.35f + f * 0.1f + flap)
                val tipY = baseY - s * 0.5f + f * s * 0.15f
                val midX = (baseX + tipX) / 2 + side * s * 0.12f
                val midY = (baseY + tipY) / 2
                fp.moveTo(baseX, baseY)
                fp.lineTo(midX, midY - s * 0.12f)
                fp.lineTo(midX + side * s * 0.08f, midY)
                fp.lineTo(tipX, tipY)
                fp.lineTo(midX, midY + s * 0.14f)
                fp.lineTo(baseX, baseY + s * 0.18f)
                fp.close()
                canvas.drawPath(fp, wingPaint)
            }
        }
        // 紫云
        val puffs = arrayOf(
            floatArrayOf(-0.5f, 0.1f, 0.4f), floatArrayOf(0f, -0.28f, 0.52f), floatArrayOf(0.5f, 0.1f, 0.4f)
        )
        bodyPaint.color = 0xFFF2ECFF.toInt()
        for (p in puffs) canvas.drawCircle(cx + p[0] * s, cy + p[1] * s, p[2] * s, bodyPaint)
        canvas.drawRoundRect(RectF(cx - s * 0.75f, cy - s * 0.05f, cx + s * 0.75f, cy + s * 0.48f), s * 0.24f, s * 0.24f, bodyPaint)
        // 中央金闪电
        boltPaint.color = 0xFFFFC94D.toInt()
        drawBolt(canvas, cx, cy + s * 0.52f, s * 0.45f)
        // 金冠
        val cw = s * 0.85f
        val topY = cy - s * 1.0f
        val ch = cw * 0.66f
        val p = Path()
        p.moveTo(cx - cw / 2, topY + ch)
        p.lineTo(cx - cw / 2, topY + ch * 0.35f)
        p.lineTo(cx - cw * 0.26f, topY + ch * 0.64f)
        p.lineTo(cx - cw * 0.14f, topY)
        p.lineTo(cx, topY + ch * 0.44f)
        p.lineTo(cx + cw * 0.14f, topY)
        p.lineTo(cx + cw * 0.26f, topY + ch * 0.64f)
        p.lineTo(cx + cw / 2, topY + ch * 0.35f)
        p.lineTo(cx + cw / 2, topY + ch)
        p.close()
        boltPaint.color = 0xFFFFC94D.toInt()
        canvas.drawPath(p, boltPaint)
        drawMiniFace(canvas, cx, cy - s * 0.08f, s * 0.2f, blinking, fierce = true)
    }

    private fun drawMiniFace(
        canvas: Canvas, cx: Float, cy: Float, er: Float, blinking: Boolean, fierce: Boolean = false
    ) {
        val off = er * 1.7f
        if (fierce && !blinking) {
            linePaint.color = 0xFF2E3B47.toInt()
            linePaint.strokeWidth = er * 0.4f
            canvas.drawLine(cx - off - er * 0.6f, cy - er * 1.6f, cx - off + er * 0.6f, cy - er * 1.0f, linePaint)
            canvas.drawLine(cx + off + er * 0.6f, cy - er * 1.6f, cx + off - er * 0.6f, cy - er * 1.0f, linePaint)
        }
        if (blinking) {
            linePaint.color = 0xFF2E3B47.toInt()
            linePaint.strokeWidth = er * 0.36f
            canvas.drawLine(cx - off - er * 0.5f, cy, cx - off + er * 0.5f, cy, linePaint)
            canvas.drawLine(cx + off - er * 0.5f, cy, cx + off + er * 0.5f, cy, linePaint)
        } else {
            canvas.drawCircle(cx - off, cy, er, eyePaint)
            canvas.drawCircle(cx + off, cy, er, eyePaint)
            canvas.drawCircle(cx - off - er * 0.3f, cy - er * 0.35f, er * 0.32f, hiPaint)
            canvas.drawCircle(cx + off - er * 0.3f, cy - er * 0.35f, er * 0.32f, hiPaint)
        }
        linePaint.color = 0xFF2E3B47.toInt()
        linePaint.strokeWidth = er * 0.32f
        val p = Path()
        p.moveTo(cx - er * 0.8f, cy + er * 1.5f)
        p.quadTo(cx, cy + er * 2.3f, cx + er * 0.8f, cy + er * 1.5f)
        canvas.drawPath(p, linePaint)
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, sz: Float) {
        val p = Path()
        p.moveTo(x + sz * 0.25f, y)
        p.lineTo(x - sz * 0.35f, y + sz * 0.9f)
        p.lineTo(x + sz * 0.02f, y + sz * 0.9f)
        p.lineTo(x - sz * 0.25f, y + sz * 1.7f)
        p.lineTo(x + sz * 0.42f, y + sz * 0.7f)
        p.lineTo(x + sz * 0.06f, y + sz * 0.68f)
        p.close()
        canvas.drawPath(p, boltPaint)
    }
}
