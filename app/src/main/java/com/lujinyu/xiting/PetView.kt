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
 * 电能精灵：由成长值驱动的五形态养成宠物（全 Canvas 手绘，零图片资源）。
 *
 * 成长值 = 听剧分钟数 + 每日分享奖励（+30/天，每天一次）
 * 进化链：电火花(0) → 电球(30) → 雷云精灵(120) → 风暴之灵(360) → 雷霆之王(1200)
 * 体型随形态放大（视图高度的 15% → 38%），每阶配色/细节升级
 * 状态：最近3天无听剧=打瞌睡；点击=放电+展示储能
 */
class PetView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    companion object {
        const val STAGE_SPARK = 0
        const val STAGE_BALL = 1
        const val STAGE_CLOUD = 2
        const val STAGE_STORM = 3
        const val STAGE_KING = 4

        val THRESHOLDS = longArrayOf(0, 30, 120, 360, 1200)

        fun stageOf(gp: Long): Int = when {
            gp >= THRESHOLDS[4] -> STAGE_KING
            gp >= THRESHOLDS[3] -> STAGE_STORM
            gp >= THRESHOLDS[2] -> STAGE_CLOUD
            gp >= THRESHOLDS[1] -> STAGE_BALL
            else -> STAGE_SPARK
        }

        fun stageName(stage: Int): String = when (stage) {
            STAGE_SPARK -> "电火花"
            STAGE_BALL -> "电球"
            STAGE_CLOUD -> "雷云精灵"
            STAGE_STORM -> "风暴之灵"
            else -> "雷霆之王"
        }
    }

    var stage = STAGE_SPARK
    var sleepy = false
    var totalMah = 0
    var progress = 0f      // 距下一形态进度 0..1（final 时无用）

    private var bornAt = System.currentTimeMillis()
    private var blinkUntil = 0L
    private var nextBlinkAt = System.currentTimeMillis() + 2500
    private var dischargeUntil = 0L
    private var energyPopUntil = 0L

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3B47.toInt() }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE082.toInt() }
    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E8E5A.toInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A9099.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFECEEF1.toInt() }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setOnClickListener {
            dischargeUntil = System.currentTimeMillis() + 500
            energyPopUntil = System.currentTimeMillis() + 1600
        }
    }

    fun startAnimating() = postInvalidateDelayed(33)

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
        val cy = height / 2f - 20f + sin(t * 2.2f) * 8f

        // 半径按视图高度比例：形态越大身体越大（15% → 38%）
        val h = height.toFloat()
        val r = h * when (stage) {
            STAGE_SPARK -> 0.15f
            STAGE_BALL -> 0.21f
            STAGE_CLOUD -> 0.27f
            STAGE_STORM -> 0.33f
            else -> 0.38f
        }

        // 眨眼
        if (now > nextBlinkAt) {
            blinkUntil = now + 160
            nextBlinkAt = now + 2200 + (abs(now % 1800)).toInt()
        }
        val blinking = now < blinkUntil

        // 光晕（雷霆之王为脉冲呼吸光晕）
        val king = stage == STAGE_KING && !sleepy
        val glowR = if (king) r * (1.7f + 0.15f * sin(t * 3f)) else r * 1.8f
        glowPaint.shader = RadialGradient(
            cx, cy, glowR,
            when {
                sleepy -> 0x14222930.toInt()
                stage >= STAGE_STORM -> 0x50B39DDB.toInt()
                stage >= STAGE_CLOUD -> 0x40B3D4FF.toInt()
                else -> 0x3EFFE082.toInt()
            },
            Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, glowPaint)

        // 环绕微粒（形态越高越多）
        if (!sleepy && stage >= STAGE_BALL) {
            val n = 2 + stage   // 3~6 颗
            for (i in 0 until n) {
                val ang = (6.2831855f * i / n) + t * 0.5f * (if (i % 2 == 0) 1f else -0.8f)
                val rr = r * 1.45f
                val px = cx + cos(ang) * rr
                val py = cy + sin(ang) * rr * 0.85f
                val tw = 0.5f + 0.5f * sin(t * 2.6f + i * 1.3f)
                starPaint.alpha = (150 * tw).toInt().coerceIn(25, 190)
                canvas.drawCircle(px, py, r * 0.035f + 1.5f, starPaint)
            }
            starPaint.alpha = 255
        }

        // 放电动画
        if (now < dischargeUntil) {
            val k = (now % 500) / 500f
            linePaint.color = if (stage >= STAGE_STORM) 0xFFB388FF.toInt() else 0xFFFFE082.toInt()
            linePaint.strokeWidth = 5f
            for (i in 0 until 8) {
                val ang = (6.2831855f * i / 8) + k * 1.5f
                val len = r * (0.5f + 0.4f * ((i % 3) + 1) * k)
                canvas.drawLine(
                    cx + cos(ang) * r * 0.95f, cy + sin(ang) * r * 0.95f,
                    cx + cos(ang) * (r + len), cy + sin(ang) * (r + len), linePaint
                )
            }
        }

        // 雷霆之王：双层旋转电环（土星环）
        if (stage == STAGE_KING) {
            linePaint.strokeWidth = 4.5f
            val ring1 = RectF(cx - r * 1.22f, cy - r * 0.42f, cx + r * 1.22f, cy + r * 0.42f)
            linePaint.color = 0xAAFFC94D.toInt()
            canvas.drawArc(ring1, t * 40, 150f, false, linePaint)
            canvas.drawArc(ring1, 180 + t * 40, 150f, false, linePaint)
            linePaint.color = 0x88B388FF.toInt()
            val ring2 = RectF(cx - r * 1.32f, cy - r * 0.36f, cx + r * 1.32f, cy + r * 0.36f)
            canvas.drawArc(ring2, -t * 30, 120f, false, linePaint)
            canvas.drawArc(ring2, 180 - t * 30, 120f, false, linePaint)
        }

        // 本体
        when (stage) {
            STAGE_SPARK -> drawSpark(canvas, cx, cy, r, t, blinking)
            STAGE_BALL -> drawBall(canvas, cx, cy, r, t, blinking)
            else -> drawCloud(canvas, cx, cy, r, t, blinking)
        }

        // 打瞌睡
        if (sleepy) {
            val zt = (t * 0.7f) % 3f
            zzzPaint.textSize = 30f + zt * 10f
            zzzPaint.alpha = (255 * (1f - zt / 3f)).toInt().coerceIn(0, 255)
            canvas.drawText("z", cx + r * 0.8f + zt * 14f, cy - r - 8f - zt * 16f, zzzPaint)
            canvas.drawText("Z", cx + r * 1.0f + zt * 22f, cy - r - 30f - zt * 20f, zzzPaint)
            zzzPaint.alpha = 255
        }

        // 点击储能弹字
        if (now < energyPopUntil) {
            popPaint.alpha = ((energyPopUntil - now).coerceAtMost(1000) / 10).toInt().coerceIn(0, 255)
            canvas.drawText("⚡ 已储存 $totalMah mAh", cx, cy - r * 1.5f - 26f, popPaint)
            popPaint.alpha = 255
        }

        // 成长进度条（底部，最终形态不显示）
        if (stage < STAGE_KING) {
            val barW = width * 0.55f
            val barH = 12f
            val left = cx - barW / 2
            val top = height - 30f
            val rr = barH / 2
            canvas.drawRoundRect(RectF(left, top, left + barW, top + barH), rr, rr, barTrackPaint)
            if (progress > 0.01f) {
                barFillPaint.shader = android.graphics.LinearGradient(
                    left, top, left + barW, top,
                    0xFFFFC107.toInt(), 0xFFFF9800.toInt(), Shader.TileMode.CLAMP
                )
                val fw = barW * progress.coerceIn(0f, 1f)
                canvas.drawRoundRect(RectF(left, top, left + fw.coerceAtLeast(barH), top + barH), rr, rr, barFillPaint)
                barFillPaint.shader = null
            }
        }

        postInvalidateDelayed(33)
    }

    // ─────────────────── 形态一：电火花 ───────────────────
    private fun drawSpark(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        // 放射刺
        linePaint.color = 0xFFFFD54F.toInt()
        linePaint.strokeWidth = if (sleepy) 3f else 5f
        for (i in 0 until 8) {
            val ang = (6.2831855f * i / 8) + t * 0.8f
            val long = i % 2 == 0
            val r1 = r * 1.08f
            val r2 = r * if (long) 1.5f else 1.28f
            linePaint.alpha = if (sleepy) 110 else 255
            canvas.drawLine(
                cx + cos(ang) * r1, cy + sin(ang) * r1,
                cx + cos(ang) * r2, cy + sin(ang) * r2, linePaint
            )
        }
        linePaint.alpha = 255
        // 身体
        bodyPaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
            if (sleepy) 0xFFCFD8DC.toInt() else 0xFFFFF9C4.toInt(),
            if (sleepy) 0xFF90A4AE.toInt() else 0xFFFFB300.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bodyPaint)
        bodyPaint.shader = null
        drawFace(canvas, cx, cy, r * 0.24f, blinking, fierce = false)
    }

    // ─────────────────── 形态二：电球 ───────────────────
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        // 轨道环（2 段旋转弧）
        linePaint.color = 0xFFFFC107.toInt()
        linePaint.strokeWidth = 5f
        linePaint.alpha = if (sleepy) 90 else 230
        val ring = RectF(cx - r * 1.35f, cy - r * 1.35f, cx + r * 1.35f, cy + r * 1.35f)
        canvas.drawArc(ring, t * 70, 120f, false, linePaint)
        canvas.drawArc(ring, 180 + t * 70, 120f, false, linePaint)
        linePaint.alpha = 255
        // 身体（径向渐变 + 内部亮核）
        bodyPaint.shader = RadialGradient(
            cx - r * 0.32f, cy - r * 0.35f, r * 1.5f,
            if (sleepy) 0xFFCFD8DC.toInt() else 0xFFFFFDE7.toInt(),
            if (sleepy) 0xFF8FA3B0.toInt() else 0xFFFFA000.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bodyPaint)
        bodyPaint.shader = null
        // 内亮核
        bodyPaint.shader = RadialGradient(
            cx - r * 0.2f, cy - r * 0.25f, r * 0.75f,
            0x66FFFFFF, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - r * 0.15f, cy - r * 0.2f, r * 0.7f, bodyPaint)
        bodyPaint.shader = null
        // 左上边缘高光
        linePaint.color = 0x99FFFFFF.toInt()
        linePaint.strokeWidth = 4f
        canvas.drawArc(
            RectF(cx - r * 0.95f, cy - r * 0.95f, cx + r * 0.95f, cy + r * 0.95f),
            205f, 70f, false, linePaint
        )
        drawFace(canvas, cx, cy, r * 0.24f, blinking, fierce = false)
    }

    // ─────────────────── 形态三/四/五：云系 ───────────────────
    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val isStorm = stage >= STAGE_STORM
        val isKing = stage == STAGE_KING

        val mainColor = when {
            sleepy -> 0xFFCFD8DC.toInt()
            isKing -> 0xFFF2ECFF.toInt()
            isStorm -> 0xFFDCEAFF.toInt()
            else -> 0xFFEAF4FF.toInt()
        }
        val shadowColor = when {
            sleepy -> 0xFF90A4AE.toInt()
            isKing -> 0xFFB49CE8.toInt()
            isStorm -> 0xFF9FB6E8.toInt()
            else -> 0xFFBCD8F0.toInt()
        }

        // 云的形状（5 圆 + 底矩形）：先画阴影层（下移），再画主体
        val puffs = arrayOf(
            floatArrayOf(-0.78f, 0.10f, 0.46f),
            floatArrayOf(-0.32f, -0.22f, 0.60f),
            floatArrayOf(0.22f, -0.32f, 0.64f),
            floatArrayOf(0.72f, -0.02f, 0.52f),
            floatArrayOf(1.02f, 0.22f, 0.38f)
        )
        fun drawPuffs(dy: Float, paint: Paint) {
            for (p in puffs) {
                canvas.drawCircle(cx + p[0] * r, cy + p[1] * r + dy, p[2] * r, paint)
            }
            canvas.drawRoundRect(
                RectF(cx - r * 1.05f, cy - r * 0.1f + dy, cx + r * 1.05f, cy + r * 0.55f + dy),
                r * 0.3f, r * 0.3f, paint
            )
        }
        shadowPaint.color = shadowColor
        drawPuffs(r * 0.10f, shadowPaint)
        bodyPaint.color = mainColor
        drawPuffs(0f, bodyPaint)

        // 顶部边缘高光
        if (!sleepy) {
            linePaint.color = if (isKing) 0xAAFFE082.toInt() else 0x88FFFFFF.toInt()
            linePaint.strokeWidth = 3.5f
            canvas.drawArc(
                RectF(cx - r * 0.9f, cy - r * 0.95f, cx + r * 0.35f, cy + r * 0.3f),
                185f, 85f, false, linePaint
            )
        }

        // 风暴云腹
        if (isStorm && !sleepy) {
            bodyPaint.color = if (isKing) 0x338A6FC9 else 0x2E5E7BAE
            canvas.drawRoundRect(
                RectF(cx - r * 0.95f, cy + r * 0.12f, cx + r * 0.95f, cy + r * 0.5f),
                r * 0.24f, r * 0.24f, bodyPaint
            )
        }

        // 云下闪电：普通1道 / 风暴3道 / 之王大金闪电+双紫电
        if (!sleepy) {
            when {
                isKing -> {
                    boltPaint.color = 0xFFFFC94D.toInt()
                    drawBolt(canvas, cx, cy + r * 0.5f, r * 0.62f)
                    boltPaint.color = 0xFFB388FF.toInt()
                    drawBolt(canvas, cx - r * 0.62f, cy + r * 0.55f, r * 0.4f)
                    drawBolt(canvas, cx + r * 0.62f, cy + r * 0.6f, r * 0.36f)
                }
                isStorm -> {
                    boltPaint.color = 0xFF8FD0FF.toInt()
                    val sway = sin(t * 2.6f) * r * 0.04f
                    drawBolt(canvas, cx + sway, cy + r * 0.5f, r * 0.46f)
                    drawBolt(canvas, cx - r * 0.58f, cy + r * 0.55f, r * 0.34f)
                    drawBolt(canvas, cx + r * 0.58f, cy + r * 0.58f, r * 0.3f)
                }
                else -> {
                    boltPaint.color = 0xFF7FC4FF.toInt()
                    val sway = sin(t * 2.6f) * r * 0.04f
                    drawBolt(canvas, cx + sway, cy + r * 0.5f, r * 0.42f)
                }
            }
        }

        // 风暴光圈（断裂弧环）
        if (isStorm && !sleepy) {
            linePaint.strokeWidth = 4.5f
            linePaint.color = if (isKing) 0x88FFC94D.toInt() else 0x88A8C8FF.toInt()
            val aura = RectF(cx - r * 1.3f, cy - r * 0.9f, cx + r * 1.3f, cy + r * 0.9f)
            for (i in 0 until 4) {
                canvas.drawArc(aura, t * 50 + i * 90f, 55f, false, linePaint)
            }
        }

        // 之王：金冠
        if (isKing && !sleepy) {
            drawCrown(canvas, cx, cy - r * 0.72f, r * 0.5f)
        } else if (isStorm && !sleepy) {
            // 风暴之灵：头顶火花冠
            linePaint.color = 0xFF8FD0FF.toInt()
            linePaint.strokeWidth = 4f
            for (i in 0 until 5) {
                val ang = 3.1415927f + (1.5707964f * (i + 1) / 6)
                val bx = cx + cos(ang) * r * 0.8f
                val by = cy + sin(ang) * r * 0.55f - r * 0.18f
                canvas.drawLine(bx, by, bx + cos(ang) * r * 0.16f, by + sin(ang) * r * 0.16f, linePaint)
            }
        }

        // 脸（风暴起带眉毛=帅气）
        drawFace(canvas, cx, cy - r * 0.12f, r * 0.2f, blinking, fierce = isStorm)
    }

    // ─────────────────── 金冠 ───────────────────
    private fun drawCrown(canvas: Canvas, cx: Float, topY: Float, w: Float) {
        val h = w * 0.85f
        val p = Path()
        p.moveTo(cx - w / 2, topY + h)
        p.lineTo(cx - w / 2, topY + h * 0.4f)
        p.lineTo(cx - w * 0.3f, topY + h * 0.72f)
        p.lineTo(cx - w * 0.16f, topY)
        p.lineTo(cx, topY + h * 0.5f)
        p.lineTo(cx + w * 0.16f, topY)
        p.lineTo(cx + w * 0.3f, topY + h * 0.72f)
        p.lineTo(cx + w / 2, topY + h * 0.4f)
        p.lineTo(cx + w / 2, topY + h)
        p.close()
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC94D.toInt() }
        canvas.drawPath(p, gold)
        // 冠底宝石
        val jewel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
        canvas.drawCircle(cx, topY + h * 0.62f, w * 0.09f, jewel)
        jewel.color = 0xFF42A5F5.toInt()
        canvas.drawCircle(cx - w * 0.28f, topY + h * 0.78f, w * 0.06f, jewel)
        canvas.drawCircle(cx + w * 0.28f, topY + h * 0.78f, w * 0.06f, jewel)
    }

    // ─────────────────── 表情 ───────────────────
    private fun drawFace(
        canvas: Canvas, cx: Float, cy: Float, er: Float, blinking: Boolean, fierce: Boolean
    ) {
        val off = er * 1.5f
        // 眉毛（帅气形态）
        if (fierce && !blinking && !sleepy) {
            linePaint.color = 0xFF2E3B47.toInt()
            linePaint.strokeWidth = er * 0.34f
            canvas.drawLine(cx - off - er * 0.6f, cy - er * 1.7f, cx - off + er * 0.5f, cy - er * 1.15f, linePaint)
            canvas.drawLine(cx + off + er * 0.6f, cy - er * 1.7f, cx + off - er * 0.5f, cy - er * 1.15f, linePaint)
        }
        if (sleepy || blinking) {
            linePaint.color = 0xFF2E3B47.toInt()
            linePaint.strokeWidth = er * 0.3f
            canvas.drawLine(cx - off - er * 0.55f, cy, cx - off + er * 0.55f, cy, linePaint)
            canvas.drawLine(cx + off - er * 0.55f, cy, cx + off + er * 0.55f, cy, linePaint)
        } else {
            canvas.drawCircle(cx - off, cy, er, eyePaint)
            canvas.drawCircle(cx + off, cy, er, eyePaint)
            val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(cx - off - er * 0.3f, cy - er * 0.32f, er * 0.3f, hi)
            canvas.drawCircle(cx + off - er * 0.3f, cy - er * 0.32f, er * 0.3f, hi)
        }
        // 微笑（睡觉时平滑）
        linePaint.color = 0xFF2E3B47.toInt()
        linePaint.strokeWidth = er * 0.26f
        val my = cy + er * 1.35f
        if (sleepy) {
            canvas.drawLine(cx, my, cx + er * 0.9f, my, linePaint)
        } else {
            val p = Path()
            p.moveTo(cx - er * 0.75f, my)
            p.quadTo(cx, my + er * 0.85f, cx + er * 0.75f, my)
            canvas.drawPath(p, linePaint)
        }
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, s: Float) {
        val p = Path()
        p.moveTo(x + s * 0.25f, y)
        p.lineTo(x - s * 0.35f, y + s * 0.9f)
        p.lineTo(x + s * 0.02f, y + s * 0.9f)
        p.lineTo(x - s * 0.25f, y + s * 1.8f)
        p.lineTo(x + s * 0.42f, y + s * 0.75f)
        p.lineTo(x + s * 0.06f, y + s * 0.72f)
        p.close()
        canvas.drawPath(p, boltPaint)
    }
}
