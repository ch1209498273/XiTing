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
import android.view.MotionEvent
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

        val THRESHOLDS = longArrayOf(0, 30, 300, 1500, 6000)

        fun stageOf(gp: Long): Int = when {
            gp >= THRESHOLDS[4] -> STAGE_KING
            gp >= THRESHOLDS[3] -> STAGE_STORM
            gp >= THRESHOLDS[2] -> STAGE_CLOUD
            gp >= THRESHOLDS[1] -> STAGE_BALL
            else -> STAGE_SPARK
        }

        fun stageName(context: Context, stage: Int): String = when (stage) {
            STAGE_SPARK -> context.getString(R.string.pet_spark)
            STAGE_BALL -> context.getString(R.string.pet_ball)
            STAGE_CLOUD -> context.getString(R.string.pet_cloud)
            STAGE_STORM -> context.getString(R.string.pet_storm)
            else -> context.getString(R.string.pet_king)
        }
    }

    var stage = STAGE_SPARK
    var sleepy = false
    var totalMah = 0
    var progress = 0f      // 距下一形态进度 0..1（final 时无用）
    var thumbMode = false  // 图鉴缩略模式：静态单帧、无粒子/进度条/光晕动画
    var hideProgress = false               // 预览非当前形态时隐藏进度条
    var pending: List<PendingEnergy> = emptyList()   // 待收集能量球
    var onCollectAll: (() -> Unit)? = null           // 收集全部回调（延迟到飞入动画后）
    var onHelp: (() -> Unit)? = null                 // ? 说明图标点击回调

    // 收集动画内部状态
    private data class FlyBall(val value: Int, val sx: Float, val sy: Float, val startAt: Long)
    private data class Popup(val text: String, val startAt: Long)
    private val flyBalls = mutableListOf<FlyBall>()
    private val popups = mutableListOf<Popup>()
    private var pulseUntil = 0L
    private var downX = 0f
    private var downY = 0f
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        if (!thumbMode) startAnimating()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimating()
    }

    /** 当前帧精灵几何（绘制与点击命中共用） */
    private fun geometry(): Triple<Float, Float, Float> {
        val t = (System.currentTimeMillis() - bornAt) / 1000f
        val cx = width / 2f
        val cy = height / 2f - 20f + (if (thumbMode) 20f else sin(t * 2.2f) * 8f)
        val h = height.toFloat()
        val r = h * if (thumbMode) {
            arrayOf(0.19f, 0.24f, 0.29f, 0.33f, 0.37f)[stage]
        } else when (stage) {
            STAGE_SPARK -> 0.11f
            STAGE_BALL -> 0.15f
            STAGE_CLOUD -> 0.19f
            STAGE_STORM -> 0.22f
            else -> 0.25f
        }
        return Triple(cx, cy, r)
    }

    /** 左侧常驻能量条（电池：容量 200，点击即充能） */
    private fun energyBar(): RectF {
        val h = height.toFloat()
        val bw = (h * 0.055f).coerceAtLeast(20f)
        val bh = h * 0.5f
        val bx = width * 0.06f
        val by = (h - bh) / 2f
        return RectF(bx, by, bx + bw, by + bh)
    }

    /** ? 问号说明图标中心（点击弹出能量机制说明） */
    private fun helpCenter(): Pair<Float, Float> {
        val bar = energyBar()
        return Pair(bar.centerX(), bar.bottom + 46f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (thumbMode) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = Math.abs(event.x - downX) > 30 || Math.abs(event.y - downY) > 30
                if (!moved) {
                    // ? 说明图标命中
                    val (hx, hy) = helpCenter()
                    val hdx = event.x - hx
                    val hdy = event.y - hy
                    if (hdx * hdx + hdy * hdy < 60f * 60f) {
                        onHelp?.invoke()
                        return true
                    }
                    // 命中能量条（外扩点击区）：点击即充能
                    val bar = energyBar()
                    if (event.x in (bar.left - 50f)..(bar.right + 70f) &&
                        event.y in (bar.top - 80f)..(bar.bottom + 60f)
                    ) {
                        val total = pending.sumOf { it.value }
                        if (total > 0) {
                            flyBalls.add(FlyBall(total, bar.centerX(), bar.centerY(), System.currentTimeMillis()))
                            postDelayed({ onCollectAll?.invoke() }, 340)
                        }
                        return true
                    }
                    performClick() // 未命中：原有放电效果
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> return true // 交给外层 ScrollView 拦截滚动
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val t = (now - bornAt) / 1000f
        val (cx, cy, r) = geometry()

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
        if (!thumbMode) canvas.drawCircle(cx, cy, glowR, glowPaint)

        // 环绕微粒（形态越高越多）
        if (!thumbMode && !sleepy && stage >= STAGE_BALL) {
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

        // 放电动画（每个形态互动不同）
        if (!thumbMode && now < dischargeUntil) {
            val k = (now % 500) / 500f
            when (stage) {
                STAGE_SPARK -> {
                    // 星芒爆闪：放射短刺延长+整体旋转
                    linePaint.color = 0xFFFFE082.toInt()
                    linePaint.strokeWidth = 5f
                    for (i in 0 until 8) {
                        val ang = (6.2831855f * i / 8) + k * 3.5f
                        val len = r * (0.4f + 0.9f * k)
                        canvas.drawLine(
                            cx + cos(ang) * r * 1.1f, cy + sin(ang) * r * 1.1f,
                            cx + cos(ang) * (r * 1.1f + len), cy + sin(ang) * (r * 1.1f + len), linePaint
                        )
                    }
                }
                STAGE_BALL -> {
                    // 等离子爆裂：锯齿电弧四射
                    linePaint.color = 0xFF00E5FF.toInt()
                    linePaint.strokeWidth = 4f
                    for (i in 0 until 6) {
                        val ang = (6.2831855f * i / 6) + k * 2f
                        val p = Path()
                        p.moveTo(cx + cos(ang) * r * 1.0f, cy + sin(ang) * r * 1.0f)
                        p.lineTo(cx + cos(ang + 0.2f) * r * (1.25f + 0.5f * k), cy + sin(ang + 0.2f) * r * (1.25f + 0.5f * k))
                        p.lineTo(cx + cos(ang - 0.15f) * r * (1.5f + 0.7f * k), cy + sin(ang - 0.15f) * r * (1.5f + 0.7f * k))
                        canvas.drawPath(p, linePaint)
                    }
                    // 扩散电环
                    linePaint.color = 0xFF80DEEA.toInt()
                    canvas.drawCircle(cx, cy, r * (1.1f + 0.9f * k), linePaint)
                }
                STAGE_CLOUD -> {
                    // 雷阵雨：云下三重闪电快速闪 + 扩散圈
                    boltPaint.color = 0xFF7FC4FF.toInt()
                    val big = r * (0.5f + 0.5f * k)
                    drawBolt(canvas, cx, cy + r * 0.5f, big)
                    if ((now / 100) % 2 == 0L) {
                        drawBolt(canvas, cx - r * 0.55f, cy + r * 0.55f, big * 0.7f)
                    } else {
                        drawBolt(canvas, cx + r * 0.55f, cy + r * 0.55f, big * 0.7f)
                    }
                    linePaint.color = 0x88A8C8FF.toInt()
                    linePaint.strokeWidth = 4f
                    canvas.drawCircle(cx, cy, r * (1.1f + 1.1f * k), linePaint)
                }
                STAGE_STORM -> {
                    // 龙卷加速：螺旋速度线狂转 + 双闪电
                    linePaint.color = 0xFFB0C4E8.toInt()
                    linePaint.strokeWidth = 3.5f
                    for (i in 0 until 4) {
                        val pk = 0.15f + i * 0.2f
                        val w = r * (1.3f - 0.95f * pk)
                        val y = cy - r * 0.85f + pk * r * 1.6f
                        canvas.drawArc(
                            RectF(cx - w, y - r * 0.14f, cx + w, y + r * 0.14f),
                            k * 720f, 110f, false, linePaint
                        )
                    }
                    boltPaint.color = 0xFF8FD0FF.toInt()
                    drawBolt(canvas, cx + r * 0.95f, cy - r * 0.1f, r * (0.3f + 0.3f * k))
                    drawBolt(canvas, cx - r * 0.98f, cy + r * 0.25f, r * (0.25f + 0.25f * k))
                }
                else -> {
                    // 王者之怒：12 道金闪电环射 + 双层金环扩散 + 翼展甩动
                    linePaint.color = 0xFFFFC94D.toInt()
                    linePaint.strokeWidth = 4.5f
                    for (i in 0 until 12) {
                        val ang = (6.2831855f * i / 12) + k * 1.2f
                        val p = Path()
                        p.moveTo(cx + cos(ang) * r * 0.9f, cy + sin(ang) * r * 0.9f)
                        p.lineTo(cx + cos(ang + 0.12f) * r * (1.3f + 0.4f * k), cy + sin(ang + 0.12f) * r * (1.3f + 0.4f * k))
                        p.lineTo(cx + cos(ang) * r * (1.7f + 0.8f * k), cy + sin(ang) * r * (1.7f + 0.8f * k))
                        canvas.drawPath(p, linePaint)
                    }
                    linePaint.color = 0xAAFFC94D.toInt()
                    canvas.drawCircle(cx, cy, r * (1.2f + 0.7f * k), linePaint)
                    linePaint.color = 0x66B388FF.toInt()
                    canvas.drawCircle(cx, cy, r * (1.5f + 1.0f * k), linePaint)
                }
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
            STAGE_CLOUD -> drawCloudSimple(canvas, cx, cy, r, t, blinking)
            STAGE_STORM -> drawTornado(canvas, cx, cy, r, t, blinking)
            else -> drawKing(canvas, cx, cy, r, t, blinking)
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

        // ───────── 能量系统：左侧常驻电池条（容量200）+ 充能动画 ─────────
        if (!thumbMode) {
            val bar = energyBar()
            val total = pending.sumOf { it.value }.coerceAtMost(EnergyStore.MAX_PENDING)
            val nearing = pending
                .filter { it.expireAt - now < EnergyStore.NEAR_EXPIRE_MS }
                .sumOf { it.value }.coerceAtMost(total)
            val rr = bar.width() / 2f
            // 轨道（常驻显示）
            barTrackPaint.color = 0xFFECEEF1.toInt()
            canvas.drawRoundRect(bar, rr, rr, barTrackPaint)
            // 填充（从底部向上，按 200 容量比例）
            val fillH = bar.height() * total / EnergyStore.MAX_PENDING.toFloat()
            if (fillH > 1f) {
                barFillPaint.color = 0xFFFFC94D.toInt()
                canvas.drawRoundRect(
                    RectF(bar.left, bar.bottom - fillH, bar.right, bar.bottom), rr, rr, barFillPaint
                )
                // 临期段（底部，最早产生的最先过期）：红色警示
                val redH = bar.height() * nearing / EnergyStore.MAX_PENDING.toFloat()
                if (redH > 1f) {
                    barFillPaint.color = 0xFFFF5252.toInt()
                    canvas.drawRoundRect(
                        RectF(bar.left, bar.bottom - redH, bar.right, bar.bottom), rr, rr, barFillPaint
                    )
                }
            }
            // 数值（条上方单行，统一字号两段色）
            popPaint.alpha = 255
            popPaint.textSize = 24f
            val numStr = "$total"
            val maxStr = "/200"
            val w1 = popPaint.measureText(numStr)
            val w2 = popPaint.measureText(maxStr)
            val startX = bar.centerX() - (w1 + w2) / 2f
            popPaint.color = if (nearing > 0) 0xFFE64A19.toInt() else 0xFFB8860B.toInt()
            canvas.drawText(numStr, startX + w1 / 2f, bar.top - 16f, popPaint)
            popPaint.color = 0xFF9AA1AA.toInt()
            canvas.drawText(maxStr, startX + w1 + w2 / 2f, bar.top - 16f, popPaint)
            popPaint.color = 0xFF1E8E5A.toInt()
            popPaint.textSize = 30f
            // ? 说明图标
            val (hx, hy) = helpCenter()
            bodyPaint.color = 0xFFDDE1E6.toInt()
            canvas.drawCircle(hx, hy, 24f, bodyPaint)
            popPaint.color = 0xFF5F6570.toInt()
            popPaint.textSize = 30f
            popPaint.isFakeBoldText = true
            canvas.drawText("?", hx, hy + 10f, popPaint)
            popPaint.color = 0xFF1E8E5A.toInt()

            // 飞入动画：从能量条飞向精灵中心
            flyBalls.removeAll { now - it.startAt > 320 }
            flyBalls.forEach { fb ->
                val k = ((now - fb.startAt) / 300f).coerceIn(0f, 1f)
                val ease = k * k * (3 - 2 * k)
                val fx = fb.sx + (cx - fb.sx) * ease
                val fy = fb.sy + (cy - fb.sy) * ease
                val fr = (height * 0.09f) * (1f - 0.45f * ease)
                ballPaint.color = 0xFFFFC94D.toInt()
                ballPaint.alpha = (255 * (1f - 0.55f * ease)).toInt()
                canvas.drawCircle(fx, fy, fr, ballPaint)
                ballPaint.alpha = 255
                if (k >= 1f) {
                    pulseUntil = now + 450
                    popups.add(Popup("+${fb.value}", now))
                }
            }

            // 充能脉冲：扩散光环
            if (now < pulseUntil) {
                val pt = 1f - (pulseUntil - now) / 450f
                linePaint.color = 0xFFFFE082.toInt()
                linePaint.alpha = (200 * (1f - pt)).toInt().coerceIn(0, 255)
                linePaint.strokeWidth = 6f * (1f - pt) + 2.5f
                canvas.drawCircle(cx, cy, r * (1.0f + 0.85f * pt), linePaint)
                linePaint.alpha = 255
            }

            // 浮字 +N
            popups.removeAll { now - it.startAt > 900 }
            popups.forEach { pu ->
                val k = ((now - pu.startAt) / 900f).coerceIn(0f, 1f)
                popPaint.alpha = (255 * (1f - k)).toInt().coerceIn(0, 255)
                popPaint.textSize = 34f
                canvas.drawText(pu.text, cx, cy - r - 20f - k * 70f, popPaint)
                popPaint.alpha = 255
                popPaint.textSize = 30f
            }
        }

        // 成长进度条（底部，最终形态不显示）
        if (!thumbMode && !hideProgress && stage < STAGE_KING) {
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

        if (!thumbMode) postInvalidateDelayed(33)
    }

    // ─────────────────── 形态一：电火花（四角星形，区别于太阳） ───────────────────
    private fun drawSpark(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        // 八角星轮廓：长尖+短凹交替（闪烁感），随时间轻微旋转呼吸
        val spin = t * 0.3f
        val breathe = 1f + 0.04f * sin(t * 3f)
        val p = Path()
        for (i in 0 until 16) {
            val ang = (6.2831855f * i / 16) + spin
            val rad = (if (i % 2 == 0) r * 1.15f else r * 0.5f) * breathe
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        bodyPaint.shader = android.graphics.LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            if (sleepy) 0xFFCFD8DC.toInt() else 0xFFFFF176.toInt(),
            if (sleepy) 0xFF90A4AE.toInt() else 0xFFFFA000.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(p, bodyPaint)
        bodyPaint.shader = null
        // 外描边亮边
        linePaint.color = 0x88FFFFFF.toInt()
        linePaint.strokeWidth = 2.5f
        canvas.drawPath(p, linePaint)
        // 中心亮核
        bodyPaint.shader = RadialGradient(
            cx, cy, r * 0.75f,
            0xFFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.72f, bodyPaint)
        bodyPaint.shader = null
        drawFace(canvas, cx, cy, r * 0.16f, blinking, fierce = false)
    }

    // ─────────────────── 形态二：电球（蓝紫等离子球，区别于太阳） ───────────────────
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        // 深空球体：中心亮紫蓝 → 边缘深紫
        bodyPaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.32f, r * 1.55f,
            if (sleepy) 0xFFCFD8DC.toInt() else 0xFFB388FF.toInt(),
            if (sleepy) 0xFF78909C.toInt() else 0xFF3F1D96.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bodyPaint)
        bodyPaint.shader = null
        // 内部等离子电弧（两道锯齿电弧，随时间滑动）
        linePaint.color = if (sleepy) 0x66E0E0E0.toInt() else 0xCCB2EBF2.toInt()
        linePaint.strokeWidth = 4f
        for (arc in 0 until 2) {
            val phase = t * 1.6f + arc * 2.4f
            val ap = Path()
            var first = true
            for (k in 0 until 7) {
                val kk = k / 6f
                val ax = cx - r * 0.75f + r * 1.5f * kk
                val ay = cy + sin(phase + kk * 9f) * r * 0.34f + (arc - 0.5f) * r * 0.4f
                if (first) { ap.moveTo(ax, ay); first = false } else ap.lineTo(ax, ay)
            }
            canvas.drawPath(ap, linePaint)
        }
        // 外环绕电弧环（青蓝色，两段旋转）
        linePaint.color = if (sleepy) 0x5580DEEA.toInt() else 0xCC00E5FF.toInt()
        linePaint.strokeWidth = 5f
        val ring = RectF(cx - r * 1.32f, cy - r * 1.32f, cx + r * 1.32f, cy + r * 1.32f)
        canvas.drawArc(ring, -t * 80, 100f, false, linePaint)
        canvas.drawArc(ring, 180 - t * 80, 100f, false, linePaint)
        // 左上高光
        linePaint.color = 0x99FFFFFF.toInt()
        linePaint.strokeWidth = 3.5f
        canvas.drawArc(
            RectF(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f),
            205f, 62f, false, linePaint
        )
        drawFace(canvas, cx, cy, r * 0.17f, blinking, fierce = false)
    }

    // ─────────────────── 形态三：雷云精灵（积云 + 闪电） ───────────────────
    private fun drawCloudSimple(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val mainColor = if (sleepy) 0xFFCFD8DC.toInt() else 0xFFEAF4FF.toInt()
        val shadowColor = if (sleepy) 0xFF90A4AE.toInt() else 0xFFBCD8F0.toInt()
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
        if (!sleepy) {
            linePaint.color = 0x88FFFFFF.toInt()
            linePaint.strokeWidth = 3.5f
            canvas.drawArc(
                RectF(cx - r * 0.9f, cy - r * 0.95f, cx + r * 0.35f, cy + r * 0.3f),
                185f, 85f, false, linePaint
            )
            boltPaint.color = 0xFF7FC4FF.toInt()
            val sway = sin(t * 2.6f) * r * 0.04f
            drawBolt(canvas, cx + sway, cy + r * 0.5f, r * 0.42f)
        }
        drawFace(canvas, cx, cy - r * 0.12f, r * 0.16f, blinking, fierce = false)
    }

    // ─────────────────── 形态四：风暴之灵（龙卷风——完全区别于云） ───────────────────
    private fun drawTornado(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        // 龙卷：多层水平椭圆，宽度从顶到底递减，形成漏斗旋涡
        val layers = 8
        for (i in 0 until layers) {
            val k = i / (layers - 1f)                 // 0=顶 1=底
            val w = r * (1.28f - 0.98f * k) * (1f + 0.05f * sin(t * 5f + i))  // 呼吸旋涡
            val y = cy - r * 0.85f + k * r * 1.6f
            val hh = r * 0.30f * (1f - 0.45f * k)
            val shade = 0.55f + 0.45f * (1f - k)      // 顶部亮底部深
            bodyPaint.color = if (sleepy) 0xFFB0BEC5.toInt() else
                android.graphics.Color.rgb(
                    (0xD8 * shade + 40).toInt().coerceIn(0, 255),
                    (0xE6 * shade + 30).toInt().coerceIn(0, 255),
                    245
                )
            canvas.drawOval(RectF(cx - w, y - hh / 2, cx + w, y + hh / 2), bodyPaint)
        }
        // 旋涡速度线（斜向弧，旋转感）
        if (!sleepy) {
            linePaint.color = 0x88FFFFFF.toInt()
            linePaint.strokeWidth = 3f
            for (i in 0 until 3) {
                val k = 0.25f + i * 0.25f
                val w = r * (1.22f - 0.95f * k)
                val y = cy - r * 0.85f + k * r * 1.6f
                canvas.drawArc(RectF(cx - w, y - r * 0.12f, cx + w, y + r * 0.12f), 200f + t * 60, 90f, false, linePaint)
            }
            // 侧向闪电
            boltPaint.color = 0xFF8FD0FF.toInt()
            drawBolt(canvas, cx + r * 0.95f, cy - r * 0.1f, r * 0.36f)
            drawBolt(canvas, cx - r * 0.98f, cy + r * 0.25f, r * 0.3f)
        }
        // 脸在龙卷中上部
        drawFace(canvas, cx, cy - r * 0.3f, r * 0.15f, blinking, fierce = true)
    }

    // ─────────────────── 形态五：雷霆之王（雷电双翼 + 金冠 + 大金闪电） ───────────────────
    private fun drawKing(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val mainColor = if (sleepy) 0xFFCFD8DC.toInt() else 0xFFF2ECFF.toInt()
        val shadowColor = if (sleepy) 0xFF90A4AE.toInt() else 0xFFB49CE8.toInt()
        // 雷电双翼（左右各 3 根锯齿羽翼，从云侧向外展开）
        if (!sleepy) {
            val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC94D.toInt() }
            val wingPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6B388FF.toInt() }
            for (side in intArrayOf(-1, 1)) {
                for (f in 0 until 3) {
                    val fp = Path()
                    val baseX = cx + side * r * 0.85f
                    val baseY = cy - r * 0.15f + f * r * 0.30f
                    val tipX = cx + side * r * (1.55f + f * 0.12f)
                    val tipY = baseY - r * 0.55f + f * r * 0.18f
                    val midX = (baseX + tipX) / 2 + side * r * 0.15f
                    val midY = (baseY + tipY) / 2
                    // 锯齿羽翼：三段折线
                    fp.moveTo(baseX, baseY)
                    fp.lineTo(midX, midY - r * 0.14f)
                    fp.lineTo(midX + side * r * 0.1f, midY + r * 0.02f)
                    fp.lineTo(tipX, tipY)
                    fp.lineTo(midX + side * r * 0.05f, midY + r * 0.16f)
                    fp.lineTo(baseX, baseY + r * 0.2f)
                    fp.close()
                    canvas.drawPath(fp, if (f % 2 == 0) wingPaint else wingPaint2)
                }
            }
        }
        // 主体云
        val puffs = arrayOf(
            floatArrayOf(-0.68f, 0.10f, 0.44f),
            floatArrayOf(-0.25f, -0.24f, 0.58f),
            floatArrayOf(0.25f, -0.30f, 0.60f),
            floatArrayOf(0.68f, 0.02f, 0.5f)
        )
        fun drawPuffs(dy: Float, paint: Paint) {
            for (p in puffs) {
                canvas.drawCircle(cx + p[0] * r, cy + p[1] * r + dy, p[2] * r, paint)
            }
            canvas.drawRoundRect(
                RectF(cx - r * 0.95f, cy - r * 0.1f + dy, cx + r * 0.95f, cy + r * 0.5f + dy),
                r * 0.28f, r * 0.28f, paint
            )
        }
        shadowPaint.color = shadowColor
        drawPuffs(r * 0.10f, shadowPaint)
        bodyPaint.color = mainColor
        drawPuffs(0f, bodyPaint)
        // 金边高光
        if (!sleepy) {
            linePaint.color = 0xAAFFE082.toInt()
            linePaint.strokeWidth = 3.5f
            canvas.drawArc(
                RectF(cx - r * 0.85f, cy - r * 0.9f, cx + r * 0.3f, cy + r * 0.25f),
                185f, 80f, false, linePaint
            )
        }
        // 中央大金闪电 + 侧紫电
        if (!sleepy) {
            boltPaint.color = 0xFFFFC94D.toInt()
            drawBolt(canvas, cx, cy + r * 0.45f, r * 0.58f)
            boltPaint.color = 0xFFB388FF.toInt()
            drawBolt(canvas, cx - r * 0.5f, cy + r * 0.5f, r * 0.36f)
            drawBolt(canvas, cx + r * 0.5f, cy + r * 0.55f, r * 0.32f)
        }
        // 金冠
        if (!sleepy) drawCrown(canvas, cx, cy - r * 0.78f, r * 0.58f)
        // 脸（王者自信）
        drawFace(canvas, cx, cy - r * 0.1f, r * 0.15f, blinking, fierce = true)
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
            // 瞳孔：深色主体 + 底部反光 + 双高光（精致有神）
            canvas.drawCircle(cx - off, cy, er, eyePaint)
            canvas.drawCircle(cx + off, cy, er, eyePaint)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            canvas.drawCircle(cx - off + er * 0.25f, cy + er * 0.3f, er * 0.55f, glow)
            canvas.drawCircle(cx + off + er * 0.25f, cy + er * 0.3f, er * 0.55f, glow)
            val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(cx - off - er * 0.34f, cy - er * 0.36f, er * 0.34f, hi)
            canvas.drawCircle(cx + off - er * 0.34f, cy - er * 0.36f, er * 0.34f, hi)
            canvas.drawCircle(cx - off + er * 0.3f, cy + er * 0.34f, er * 0.14f, hi)
            canvas.drawCircle(cx + off + er * 0.3f, cy + er * 0.34f, er * 0.14f, hi)
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
