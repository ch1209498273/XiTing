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
     * 配色色相（度），0 = 经典配色。与主页精灵/小组件共用同一套皮肤值。
     *
     * 旧实现是「画完对整层做色相旋转」，现已改为**按色相现算颜色**（见 [PetPalette]），
     * 下方不再需要 saveLayer + ColorMatrixColorFilter。
     */
    var skinHue = 0f
        set(value) { if (field != value) { field = value; paletteCache = null; invalidate() } }

    /** 配色：饱和度系数，1.0=原样。与色相一起构成一套配色 */
    var skinSat = 1f
        set(value) { if (field != value) { field = value; paletteCache = null; invalidate() } }

    /**
     * 一次性套用整套配色 —— 只设色相而漏掉饱和度是最难查的一类问题：
     * 界面不崩，只是「这款皮肤看起来和别的没区别」。
     */
    fun applySkin(skin: PetSkins.Skin) {
        skinHue = skin.hue
        skinSat = skin.saturation
    }

    /** 缓存键含 stage：形态变了色相可能变，不能只按 skinHue 缓存 */
    private var paletteCache: PetPalette.Palette? = null
    private var paletteKey = Int.MIN_VALUE to (Float.NaN to Float.NaN)

    // 与 PetView 同样的复用策略：悬浮球 10fps 常驻重绘，每帧新建 Path/RectF/Shader
    // 就是每秒 200 个以上的对象。（黑幕盖住时 OverlayService 会 setAnimating(false) 停帧，
    // 但用户不播放的绝大多数时间它都在跑。）
    private val scratchRect = RectF()
    private val pathA = Path()
    private val pathB = Path()
    private val pathC = Path()
    private val shaderCache = HashMap<String, android.graphics.Shader>(8)
    private var shaderKeyW = Int.MIN_VALUE
    private var shaderKeyH = Int.MIN_VALUE

    private fun shaderCacheCheck() {
        if (shaderKeyW == width && shaderKeyH == height) return
        shaderKeyW = width
        shaderKeyH = height
        shaderCache.clear()
    }

    private fun cachedShader(key: String, make: () -> android.graphics.Shader): android.graphics.Shader =
        shaderCache.getOrPut(key) { make() }

    private fun palette(): PetPalette.Palette {
        val key = stage to (skinHue to skinSat)
        paletteCache?.let { if (key == paletteKey) return it }
        paletteKey = key
        shaderCache.clear()
        return PetPalette.of(stage, skinHue, skinSat).also { paletteCache = it }
    }

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
        val pal = palette()
        shaderCacheCheck()

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
        val pal = palette()
        val p = pathA
        p.reset()
        val spin = t * 0.5f
        for (i in 0 until 16) {
            val ang = (6.2831855f * i / 16) + spin
            val rad = if (i % 2 == 0) s * 1.25f else s * 0.55f
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        bodyPaint.shader = cachedShader("spark") {
            android.graphics.LinearGradient(
                cx - s, cy - s, cx + s, cy + s,
                pal.bodyLight, pal.shade, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(p, bodyPaint)
        bodyPaint.shader = null
        drawMiniFace(canvas, cx, cy, s * 0.26f, blinking)
    }

    // 形态二：等离子球 + 旋转环
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        bodyPaint.shader = cachedShader("ball") {
            RadialGradient(
                cx - s * 0.3f, cy - s * 0.3f, s * 1.5f,
                pal.bodyLight, pal.deep, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, s, bodyPaint)
        bodyPaint.shader = null
        // 内部电弧
        linePaint.color = pal.accentSoft
        linePaint.strokeWidth = s * 0.09f
        val ap = pathA
        ap.reset()
        for (k in 0 until 5) {
            val kk = k / 4f
            val ax = cx - s * 0.6f + s * 1.2f * kk
            val ay = cy + sin(t * 2f + kk * 8f) * s * 0.3f
            if (k == 0) ap.moveTo(ax, ay) else ap.lineTo(ax, ay)
        }
        canvas.drawPath(ap, linePaint)
        // 旋转环（替换原来的青蓝环）
        linePaint.color = pal.accent
        linePaint.strokeWidth = s * 0.12f
        scratchRect.set(cx - s * 1.35f, cy - s * 1.35f, cx + s * 1.35f, cy + s * 1.35f)
        canvas.drawArc(scratchRect, -t * 90, 100f, false, linePaint)
        canvas.drawArc(scratchRect, 180 - t * 90, 100f, false, linePaint)
        drawMiniFace(canvas, cx, cy, s * 0.24f, blinking)
    }

    // 形态三：底部平坦的积云（与主页同剪影）
    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        val baseY = cy + s * 0.35f
        val lobes = arrayOf(
            floatArrayOf(-0.55f, 0.40f, -0.28f),
            floatArrayOf(-0.12f, 0.52f, -0.52f),
            floatArrayOf(0.36f, 0.44f, -0.40f),
            floatArrayOf(0.70f, 0.30f, -0.18f)
        )
        for (l in lobes) canvas.drawCircle(cx + l[0] * s, baseY + l[2] * s, l[1] * s, bodyPaint.apply {
            color = pal.body
        })
        canvas.drawRect(cx - s * 0.80f, baseY - s * 0.32f, cx + s * 0.80f, baseY, bodyPaint.apply {
            color = pal.body
        })
        boltPaint.color = pal.accent
        drawBolt(canvas, cx, baseY - s * 0.02f, s * 0.38f)
        drawMiniFace(canvas, cx, cy - s * 0.06f, s * 0.22f, blinking)
    }

    // 形态四：小龙卷风（与主页同做法：重叠圆堆，无硬边）
    private fun drawStorm(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        val n = 7
        val topY = cy - s * 0.58f
        val h = s * 1.16f
        for (i in 0 until n) {
            val tt = i / (n - 1f)
            val rad = s * (0.15f + 0.30f * tt)
            val y = topY + tt * h
            val off = sin(t * 1.5f - tt * 2.4f) * s * (0.05f + 0.16f * tt)
            bodyPaint.shader = cachedShader("stormBall$i") {
                android.graphics.LinearGradient(
                    cx + off, y - rad, cx + off, y + rad,
                    pal.bodyLight, pal.shade, Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx + off, y, rad, bodyPaint)
            bodyPaint.shader = null
        }
        drawMiniFace(canvas, cx, topY + s * 0.24f, s * 0.18f, blinking, fierce = true)
    }

    private fun r0(s: Float) = s * 0.19f

    // 形态五：六芒能量核心 + 土星环（与主页同剪影；翅膀与皇冠已去掉）
    private fun drawKing(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        // 上半环在核心之前、下半环在核心之后，与主页一致
        fun ringHalf(i: Int, start: Float) {
            val rot = if (i == 0) -16f + sin(t * 0.5f) * 2.5f else 14f
            val rx = if (i == 0) s * 1.66f else s * 1.32f
            val ry = if (i == 0) s * 0.32f else s * 0.23f
            canvas.save()
            canvas.rotate(rot, cx, cy)
            linePaint.strokeCap = android.graphics.Paint.Cap.BUTT
            linePaint.strokeWidth = if (i == 0) s * 0.15f else s * 0.10f
            linePaint.color = if (i == 0) pal.accent else pal.accentSoft
            linePaint.alpha = if (i == 0) 225 else 150
            scratchRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
            canvas.drawArc(scratchRect, start, 180f, false, linePaint)
            canvas.restore()
        }
        ringHalf(0, 180f)
        ringHalf(1, 180f)
        // 六芒核心
        val core = pathA
        core.reset()
        for (i in 0 until 12) {
            val ang = 6.2831855f * i / 12 - 1.5708f
            val rad = s * (if (i % 2 == 0) 1.05f else 0.45f)
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad * 0.94f
            if (i == 0) core.moveTo(x, y) else core.lineTo(x, y)
        }
        core.close()
        bodyPaint.shader = cachedShader("king") {
            RadialGradient(
                cx - s * 0.25f, cy - s * 0.3f, s * 1.7f, pal.highlight, pal.deep, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(core, bodyPaint)
        bodyPaint.shader = null
        // 下半环盖在核心之上
        ringHalf(0, 0f)
        ringHalf(1, 0f)
        linePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        linePaint.alpha = 255
        // 头顶星芒（四尖，ww 必须远小于 hh）
        val sy = cy - s * 1.08f
        val hh = s * 0.38f
        val ww = s * 0.08f
        boltPaint.color = pal.highlight
        val sp = pathB
        sp.reset()
        sp.moveTo(cx, sy - hh)
        sp.lineTo(cx + ww, sy - ww)
        sp.lineTo(cx + hh, sy)
        sp.lineTo(cx + ww, sy + ww)
        sp.lineTo(cx, sy + hh)
        sp.lineTo(cx - ww, sy + ww)
        sp.lineTo(cx - hh, sy)
        sp.lineTo(cx - ww, sy - ww)
        sp.close()
        canvas.drawPath(sp, boltPaint)
        boltPaint.color = pal.accent
        drawBolt(canvas, cx, cy + s * 0.44f, s * 0.42f)
        drawMiniFace(canvas, cx, cy - s * 0.04f, s * 0.20f, blinking, fierce = true)
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
        val p = pathC
        p.reset()
        p.moveTo(cx - er * 0.8f, cy + er * 1.5f)
        p.quadTo(cx, cy + er * 2.3f, cx + er * 0.8f, cy + er * 1.5f)
        canvas.drawPath(p, linePaint)
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, sz: Float) {
        val p = pathC
        p.reset()
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
