// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 电能精灵：由真实听剧数据驱动的程序化养成宠物（全 Canvas 手绘，无图片资源）。
 *
 * 进化链（按累计听剧时长）：电火花 → 电球 → 雷云精灵 → 风暴之灵
 * 状态：最近2天有听剧=精神饱满；≥3天没听=打瞌睡（提醒回归）
 * 交互：点击=放电动画 + 储能展示
 */
class PetView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        const val STAGE_SPARK = 0   // 电火花
        const val STAGE_BALL = 1    // 电球
        const val STAGE_CLOUD = 2   // 雷云精灵
        const val STAGE_STORM = 3   // 风暴之灵

        fun stageName(stage: Int): String = when (stage) {
            STAGE_SPARK -> "电火花"
            STAGE_BALL -> "电球"
            STAGE_CLOUD -> "雷云精灵"
            else -> "风暴之灵"
        }

        fun shareLevelName(level: Int): String = when (level) {
            1 -> "星光"
            2 -> "彩虹"
            3 -> "传奇"
            else -> "无装扮"
        }
    }

    var stage = STAGE_SPARK
    var sleepy = false
    var totalMah = 0
    var shareLevel = 0   // 分享任务装扮：0=无 1=星光 2=彩虹 3=传奇

    private var bornAt = System.currentTimeMillis()
    private var blinkUntil = 0L            // 眨眼窗口
    private var nextBlinkAt = System.currentTimeMillis() + 2500
    private var dischargeUntil = 0L        // 放电动画截止
    private var energyPopUntil = 0L        // 储能文字展示截止

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE082.toInt()
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF176.toInt()
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37474F.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A9099.toInt()
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }
    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E8E5A.toInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        setOnClickListener {
            dischargeUntil = System.currentTimeMillis() + 500
            energyPopUntil = System.currentTimeMillis() + 1600
        }
    }

    fun startAnimating() {
        postInvalidateDelayed(33)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimating()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimating()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val t = (now - bornAt) / 1000f
        val cx = width / 2f
        val cy = height / 2f + sin(t * 2.2f) * 8f   // 漂浮上下浮动
        val r = when (stage) {
            STAGE_SPARK -> 26f
            STAGE_BALL -> 44f
            else -> 58f
        }

        // 周期眨眼
        if (now > nextBlinkAt) {
            blinkUntil = now + 160
            nextBlinkAt = now + 2200 + (abs(t.hashCode()) % 1800)
        }
        val blinking = now < blinkUntil

        // 光晕（传奇装扮：脉冲呼吸光晕）
        val legend = shareLevel >= 3 && !sleepy
        val glowR = if (legend) r * (2.4f + 0.25f * sin(t * 3f)) else r * 2.4f
        glowPaint.shader = RadialGradient(
            cx, cy, glowR,
            if (sleepy) 0x14222930.toInt()
            else if (legend) 0x44FFE082.toInt()
            else 0x2EFFE082.toInt(),
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, glowPaint)

        // 装扮·星光（≥1）：环绕小星星，缓慢旋转+闪烁
        if (shareLevel >= 1 && !sleepy) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE082.toInt() }
            for (i in 0 until 6) {
                val ang = (6.2831855f * i / 6) + t * 0.35f
                val rr = r * 1.65f
                val sx = cx + cos(ang) * rr
                val sy = cy + sin(ang) * rr
                val tw = 0.5f + 0.5f * sin(t * 2.4f + i * 1.1f)  // 闪烁
                starPaint.alpha = (170 * tw).toInt().coerceIn(30, 200)
                drawStar(canvas, sx, sy, r * 0.16f * (0.8f + 0.3f * tw), starPaint)
            }
        }

        // 装扮·彩虹（≥2）：本体外圈彩虹弧
        if (shareLevel >= 2 && !sleepy) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
            }
            val ring = RectF(cx - r * 1.4f, cy - r * 1.4f, cx + r * 1.4f, cy + r * 1.4f)
            val colors = intArrayOf(
                0xFFFF5252.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF176.toInt(),
                0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(), 0xFFAB47BC.toInt()
            )
            for (i in 0 until 6) {
                ringPaint.color = colors[i]
                val start = (t * 60 + i * 60f) % 360f
                canvas.drawArc(ring, start, 42f, false, ringPaint)
            }
        }

        // 放电动画：四周放射小闪电
        if (now < dischargeUntil) {
            val k = (now % 500) / 500f
            for (i in 0 until 8) {
                val ang = (6.2831855f * i / 8) + k * 1.5f
                val len = r * (0.7f + 0.5f * ((i % 3) + 1) * k)
                val ex = cx + (cos(ang) * (r + len)).toFloat()
                val ey = cy + (sin(ang) * (r + len)).toFloat()
                canvas.drawLine(
                    cx + cos(ang) * r * 0.9f, cy + sin(ang) * r * 0.9f,
                    ex, ey, sparkPaint
                )
            }
            sparkPaint.alpha = 120
            canvas.drawCircle(cx, cy, r * (1.1f + k * 0.6f), sparkPaint)
            sparkPaint.alpha = 255
        }

        // 本体
        when (stage) {
            STAGE_SPARK -> {
                // 电火花：小圆核 + 放射短刺
                bodyPaint.color = if (sleepy) 0xFFB0BEC5.toInt() else 0xFFFFD54F.toInt()
                canvas.drawCircle(cx, cy, r, bodyPaint)
                for (i in 0 until 7) {
                    val ang = (6.2831855f * i / 7) + t * 0.8f
                    val x1 = cx + cos(ang) * (r + 4f)
                    val y1 = cy + sin(ang) * (r + 4f)
                    val x2 = cx + cos(ang) * (r + 14f)
                    val y2 = cy + sin(ang) * (r + 14f)
                    canvas.drawLine(x1, y1, x2, y2, sparkPaint)
                }
                drawFace(canvas, cx, cy, r * 0.34f, blinking)
            }
            STAGE_BALL -> {
                // 电球：发光球体 + 旋转电弧环
                bodyPaint.shader = RadialGradient(
                    cx - r * 0.3f, cy - r * 0.3f, r * 1.6f,
                    if (sleepy) 0xFFCFD8DC.toInt() else 0xFFFFF59D.toInt(),
                    if (sleepy) 0xFF90A4AE.toInt() else 0xFFFFB300.toInt(),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, r, bodyPaint)
                bodyPaint.shader = null
                sparkPaint.alpha = if (sleepy) 90 else 200
                val arc = RectF(cx - r * 1.25f, cy - r * 1.25f, cx + r * 1.25f, cy + r * 1.25f)
                for (i in 0 until 3) {
                    val start = (t * 90 + i * 120) % 360
                    canvas.drawArc(arc, start, 70f, false, sparkPaint)
                }
                sparkPaint.alpha = 255
                drawFace(canvas, cx, cy, r * 0.36f, blinking)
            }
            else -> {
                // 雷云精灵 / 风暴之灵：云朵 + 云下小闪电 + 云上眼睛
                bodyPaint.color = if (sleepy) 0xFFB0BEC5.toInt() else 0xFFECEFF1.toInt()
                val cr = r * 0.52f
                canvas.drawCircle(cx - cr * 1.2f, cy + cr * 0.2f, cr, bodyPaint)
                canvas.drawCircle(cx, cy - cr * 0.35f, cr * 1.15f, bodyPaint)
                canvas.drawCircle(cx + cr * 1.2f, cy + cr * 0.2f, cr, bodyPaint)
                canvas.drawRect(
                    cx - cr * 1.4f, cy + cr * 0.2f,
                    cx + cr * 1.4f, cy + cr * 0.75f, bodyPaint
                )
                // 云下闪电（雷云1道，风暴2道，随时间轻摆）
                val sway = sin(t * 3f) * 6f
                drawBolt(canvas, cx - (if (stage == STAGE_STORM) r * 0.35f else 0f) + sway, cy + r * 0.7f, r * 0.45f)
                if (stage == STAGE_STORM) {
                    drawBolt(canvas, cx + r * 0.45f - sway, cy + r * 0.75f, r * 0.38f)
                    // 风暴之灵的火花冠
                    for (i in 0 until 5) {
                        val ang = 3.1415927f + (1.5707964f * i / 4)
                        val x1 = cx + cos(ang).toFloat() * r * 1.15f
                        val y1 = cy - cr * 0.4f + sin(ang).toFloat() * r * 0.5f
                        val x2 = cx + cos(ang).toFloat() * r * 1.32f
                        val y2 = cy - cr * 0.4f + sin(ang).toFloat() * r * 0.62f
                        canvas.drawLine(x1, y1, x2, y2, sparkPaint)
                    }
                }
                drawFace(canvas, cx, cy - cr * 0.1f, r * 0.3f, blinking)
            }
        }

        // 瞌睡：Z z z
        if (sleepy) {
            val zt = (t * 0.7f) % 3f
            textPaint.alpha = (255 * (1f - zt / 3f)).toInt().coerceIn(0, 255)
            textPaint.textSize = 30f + zt * 10f
            canvas.drawText("z", cx + r * 0.9f + zt * 14f, cy - r - 10f - zt * 16f, textPaint)
            canvas.drawText("Z", cx + r * 1.15f + zt * 22f, cy - r - 34f - zt * 20f, textPaint)
            textPaint.alpha = 255
            textPaint.textSize = 34f
        }

        // 点击放电后的储能文字
        if (now < energyPopUntil) {
            popPaint.alpha = ((energyPopUntil - now).coerceAtMost(1000) / 10).toInt().coerceIn(0, 255)
            canvas.drawText("⚡ 已储存 $totalMah mAh", cx, cy - r * 1.9f, popPaint)
            popPaint.alpha = 255
        }

        // 持续动画
        postInvalidateDelayed(33)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, er: Float, blinking: Boolean) {
        val off = er * 0.85f
        if (sleepy || blinking) {
            // 闭眼：两条向下弧线
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF37474F.toInt()
                style = Paint.Style.STROKE
                strokeWidth = er * 0.28f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(cx - off - er * 0.5f, cy, cx - off + er * 0.5f, cy, p)
            canvas.drawLine(cx + off - er * 0.5f, cy, cx + off + er * 0.5f, cy, p)
        } else {
            canvas.drawCircle(cx - off, cy, er, eyePaint)
            canvas.drawCircle(cx + off, cy, er, eyePaint)
            // 眼睛高光
            val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(cx - off - er * 0.28f, cy - er * 0.3f, er * 0.3f, hi)
            canvas.drawCircle(cx + off - er * 0.28f, cy - er * 0.3f, er * 0.3f, hi)
        }
        // 微笑
        val m = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF37474F.toInt()
            style = Paint.Style.STROKE
            strokeWidth = er * 0.22f
            strokeCap = Paint.Cap.ROUND
        }
        val mw = er * 0.7f
        canvas.drawLine(cx - mw * 0.5f, cy + er * 1.15f, cx, cy + er * 1.35f, m)
        canvas.drawLine(cx, cy + er * 1.35f, cx + mw * 0.5f, cy + er * 1.15f, m)
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, s: Float) {
        val p = android.graphics.Path()
        p.moveTo(x + s * 0.25f, y)
        p.lineTo(x - s * 0.35f, y + s * 0.9f)
        p.lineTo(x + s * 0.02f, y + s * 0.9f)
        p.lineTo(x - s * 0.25f, y + s * 1.8f)
        p.lineTo(x + s * 0.42f, y + s * 0.75f)
        p.lineTo(x + s * 0.06f, y + s * 0.72f)
        p.close()
        canvas.drawPath(p, boltPaint)
    }

    /** 四角星（星光装扮用） */
    private fun drawStar(canvas: Canvas, x: Float, y: Float, s: Float, paint: Paint) {
        val p = android.graphics.Path()
        p.moveTo(x, y - s)
        p.quadTo(x + s * 0.22f, y - s * 0.22f, x + s, y)
        p.quadTo(x + s * 0.22f, y + s * 0.22f, x, y + s)
        p.quadTo(x - s * 0.22f, y + s * 0.22f, x - s, y)
        p.quadTo(x - s * 0.22f, y - s * 0.22f, x, y - s)
        p.close()
        canvas.drawPath(p, paint)
    }
}
