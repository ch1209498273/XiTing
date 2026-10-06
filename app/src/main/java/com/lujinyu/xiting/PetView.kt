// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Bitmap
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

        /** 新点亮徽章的脉冲高亮时长（ms） */
        private const val BADGE_GLOW_MS = 1400L

        /**
         * 光晕**渐变**的半径（= 视图高度 × 本系数），必须是渐变淡出到透明的半径。
         *
         * 而实际画出来的光晕圆半径是 `r × 1.8`（雷霆之王还会脉动到 `r × 1.85`），
         * r 最大为 `0.25 × height` → 最远 `0.4625 × height`。
         * 渐变半径一旦小于它，光晕就会被硬生生截断成一个可见的圆盘边界。
         * [GLOW_CIRCLE_MAX_RATIO] 把这个不变量显式化（并有单测钉住），
         * 以后改形态尺寸时就会立刻报错，而不是「看着有点怪但说不清」。
         */
        const val GLOW_GRADIENT_RATIO = 0.475f

        /** 光晕圆半径相对视图高度的最大值（雷霆之王脉动峰值） */
        const val GLOW_CIRCLE_MAX_RATIO = 0.25f * 1.85f

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

    /**
     * 成就徽章列（自下而上，难度递进）。空列表 = 不显示。
     *
     * 这里刻意不让 PetView 自己按「下标 = 第几格」去推断徽章种类 —— 那样徽章顺序
     * 就会同时存在于 Achievements.ALL 和本文件里，加一条成就漏改一处就是错位。
     * 改成由调用方直接给 [Badges.Badge] 列表，本类只负责画。
     *
     * setter 顺手记下「本次新点亮的最靠上那一格」，给它一段脉冲高亮。
     */
    var badges: List<Badges.Badge> = emptyList()
        set(value) {
            if (field == value) return
            // 只对「本次新点亮」记高亮；已点亮的重复赋值（每次 refreshPetPanel 都会调）不该闪
            val prev = field.map { it.kind to it.unlocked }.toSet()
            var newly = -1
            value.forEachIndexed { i, b -> if (b.unlocked && (b.kind to true) !in prev) newly = i }
            field = value
            if (newly >= 0) {
                badgeGlowIndex = newly
                badgeGlowAt = System.currentTimeMillis()
            }
            invalidate()
        }

    private var badgeGlowIndex = -1
    private var badgeGlowAt = 0L

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

    // ---- onDraw 热路径复用对象 ----
    // 精灵在统计页以 30fps 自续挂重绘（onDraw 末尾 postInvalidateDelayed）。
    // 以前每次 drawMiniFace 都要 new 两个 Paint（五个形态每帧都调它），
    // 进度条每帧还要 new 两个 RectF 和一个 LinearGradient。
    // 这些颜色/几何在一次布局内是常量，复用后把每帧的对象分配降到 0。
    private val faceGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val faceHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val scratchRect = RectF()
    private val scratchRect2 = RectF()
    /** 能量条几何：energyBar() 以前每次调用都 new 一个 RectF，而一帧里要被调用 3~4 次 */
    private val energyScratch = RectF()
    /** 螺旋飘带 / 星芒的复用 Path（每帧新建会持续给 GC 添压力，见上方注释） */
    private val ribbon = Path()
    private val pathA = Path()
    private val pathB = Path()
    private val pathC = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * 着色器缓存。
     *
     * 之前每帧 new 一个 LinearGradient / RadialGradient（精灵本体 6~7 个，悬浮球另有 4 个），
     * 统计页 30fps 就是每秒 200 个以上的 native 对象。这些几何只随 **view 尺寸** 变化，
     * 与动画无关，所以按 (width, height) 缓存即可。
     *
     * 底部进度条的渐变早就是这个做法，这里把它推广到其余全部。
     */
    private val shaderCache = HashMap<String, android.graphics.Shader>(8)
    private var shaderKeyW = Int.MIN_VALUE
    private var shaderKeyH = Int.MIN_VALUE

    /** 尺寸变化时清空着色器缓存（尺寸不变就不清，否则动画中也会反复重建） */
    private fun shaderCacheCheck() {
        if (shaderKeyW == width && shaderKeyH == height) return
        shaderKeyW = width
        shaderKeyH = height
        shaderCache.clear()
    }

    /** 进度条渐变缓存：几何随 view 尺寸而定，尺寸不变就不必每帧重建 */
    private var barGradient: android.graphics.LinearGradient? = null
    private var barGradientKey = 0L

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
        // thumbMode 是静态图（缩略图/图鉴），不参与动画循环 ——
        // 与 onAttachedToWindow、onDraw 末尾的判断保持一致
        if (visibility == VISIBLE && !thumbMode) startAnimating()
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

    /**
     * 左侧常驻能量条（电池：容量 200，点击即充能）。
     *
     * 以前直接 return new RectF(...)，而它一帧里要被 energyBar/badgeColumn/helpCenter/
     * onTouchEvent 调 3~4 次 —— 单这一项就是每帧 3~4 个对象。
     * 现在写入共享的 [energyScratch]，**调用方不得跨调用持有返回值**。
     */
    private fun energyBar(): RectF {
        val h = height.toFloat()
        val bw = (h * 0.055f).coerceAtLeast(20f)
        val bh = h * 0.5f
        val bx = width * 0.06f
        val by = (h - bh) / 2f
        energyScratch.set(bx, by, bx + bw, by + bh)
        return energyScratch
    }

    /** ? 问号说明图标中心（点击弹出能量机制说明） */
    private fun helpCenter(): Pair<Float, Float> {
        val bar = energyBar()
        return Pair(bar.centerX(), bar.bottom + 46f)
    }

    /**
     * 徽章列几何：能量条右侧、纵向与能量条居中对齐。
     *
     * **为什么不是叠在 ? 按钮上方**：PetView 高 180dp，能量条独占 [0.25h, 0.75h]，
     * 其上方只剩 0.25h —— 4 枚徽章均分的话直径上限仅 11dp，手绘皇冠在里面会糊成
     * 一坨色块。挪到能量条右侧后那段（到精灵身体之间约 250px）本来就是空的，
     * 徽章直径能到 15dp，与 ? 按钮同量级，图形才认得出。
     *
     * **徽章不可点**：这一列完整落在能量条的既有点击区里，若再挂点击命中，
     * 「点徽章看成就」就会变成「误触发收取全部能量」。所以点击语义保持不变。
     */
    private fun badgeColumn(): Triple<Float, Float, Float> {
        val h = height.toFloat()
        val bar = energyBar()
        val r = h * Badges.RADIUS_RATIO
        val gap = h * Badges.GAP_RATIO
        val x = bar.right + gap + r          // 圆心 = 条右缘 + 间隙 + 半径，保证不压到条
        return Triple(x, bar.centerY(), r)
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

    /**
     * 换肤：色相（度），0 = 经典配色。
     *
     * 现在色相是**绘制参数**而非后处理滤镜，见 [PetPalette]。
     */
    var skinHue = 0f
        set(value) { if (field != value) { field = value; invalidate() } }

    /** 换肤：饱和度系数，1.0=原样。与色相一起构成一套配色 —— 见 [PetPalette] */
    var skinSat = 1f
        set(value) { if (field != value) { field = value; invalidate() } }

    /**
     * 一次性套用整套配色。
     *
     * 让调用点只设 skinHue 而漏掉饱和度是迟早的事，而漏掉之后界面不会崩、
     * 只会「这款皮肤看起来和别的没区别」——正是最难查的一类问题。
     */
    fun applySkin(skin: PetSkins.Skin) {
        skinHue = skin.hue
        skinSat = skin.saturation
    }

    /**
     * 当前形态 + 皮肤的调色板。
     *
     * 缓存键含 stage：形态一变色相就可能变，不能只按 skinHue 缓存。
     * 精灵在统计页 30fps 自续挂重绘，每帧现算 8 个颜色不划算。
     */
    private var paletteCache: PetPalette.Palette? = null
    private var paletteKey = Int.MIN_VALUE to (Float.NaN to Float.NaN)

    private fun palette(): PetPalette.Palette {
        val key = stage to (skinHue to skinSat)
        paletteCache?.let { if (key == paletteKey) return it }
        paletteKey = key
        // 调色板变了，缓存的着色器（内含具体颜色）全部作废
        shaderCache.clear()
        return PetPalette.of(stage, skinHue, skinSat).also { paletteCache = it }
    }

    private fun cachedShader(key: String, make: () -> android.graphics.Shader): android.graphics.Shader =
        shaderCache.getOrPut(key) { make() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 旧实现在这里开离屏位图 + ColorMatrixColorFilter 做换肤，已整体移除：
        // 色相现在是绘制参数（见 PetPalette），精灵身上每个颜色都直接从同一 hue 派生。
        // 附带好处：不再需要每帧分配/回收全尺寸位图，也不会再染色任何承载语义的元素。
        drawBody(canvas)
        if (!thumbMode) drawBadgeColumn(canvas)
    }

    /** 成就徽章列（自下而上）。几何与 [badgeColumn] 一致，颜色恒定不随换肤变化。 */
    private fun drawBadgeColumn(canvas: Canvas) {
        if (badges.isEmpty()) return
        val (bx, by, br) = badgeColumn()
        val ys = Badges.columnCenters(badges.size, by, br, height * Badges.GAP_RATIO)
        val now = System.currentTimeMillis()
        badges.forEachIndexed { i, b ->
            val glow = if (i == badgeGlowIndex && badgeGlowAt > 0L) {
                val dt = now - badgeGlowAt
                if (dt in 0 until BADGE_GLOW_MS) 1f - dt / BADGE_GLOW_MS.toFloat() else 0f
            } else 0f
            Badges.draw(canvas, bx, ys[i], br, b.kind, b.unlocked, glow)
        }
    }

    private fun drawBody(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val t = (now - bornAt) / 1000f
        val (cx, cy, r) = geometry()
        val pal = palette()
        shaderCacheCheck()

        // 眨眼
        if (now > nextBlinkAt) {
            blinkUntil = now + 160
            nextBlinkAt = now + 2200 + (abs(now % 1800)).toInt()
        }
        val blinking = now < blinkUntil

        // 光晕（雷霆之王为脉冲呼吸光晕）
        val king = stage == STAGE_KING && !sleepy
        val glowR = if (king) r * (1.7f + 0.15f * sin(t * 3f)) else r * 1.8f
        // 光晕颜色也从调色板取：之前是按形态硬编码的五种蓝紫，
        // 换肤时又会被色相旋转搅乱，现在与主体同源。
        val glowColor = if (sleepy) 0x14222930.toInt()
        else (pal.glow and 0x00FFFFFF) or (0x50 shl 24)
        if (!thumbMode) {
            // 渐变以本点为圆心建立，再靠 canvas 平移绘制。
            // 这样它只随尺寸变化 → 可缓存；同时精灵的上下浮动仍能正确带动光晕
            // （直接用带偏移的 cy 建渐变就既不能缓存、也跟着一起动不了）。
            glowPaint.shader = cachedShader("glow$sleepy") {
                RadialGradient(
                    0f, 0f, height * GLOW_GRADIENT_RATIO,
                    glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
            }
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, glowR, glowPaint)
            canvas.restore()
        }

        // 环绕微粒（形态越高越多）
        if (!thumbMode && !sleepy && stage >= STAGE_BALL) {
            val n = 2 + stage   // 3~6 颗
            for (i in 0 until n) {
                val ang = (6.2831855f * i / n) + t * 0.5f * (if (i % 2 == 0) 1f else -0.8f)
                val rr = r * 1.45f
                val px = cx + cos(ang) * rr
                val py = cy + sin(ang) * rr * 0.85f
                val tw = 0.5f + 0.5f * sin(t * 2.6f + i * 1.3f)
                starPaint.color = pal.accent
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
                    val arcPath = pathA
                    for (i in 0 until 6) {
                        val ang = (6.2831855f * i / 6) + k * 2f
                        arcPath.reset()
                        arcPath.moveTo(cx + cos(ang) * r * 1.0f, cy + sin(ang) * r * 1.0f)
                        arcPath.lineTo(cx + cos(ang + 0.2f) * r * (1.25f + 0.5f * k), cy + sin(ang + 0.2f) * r * (1.25f + 0.5f * k))
                        arcPath.lineTo(cx + cos(ang - 0.15f) * r * (1.5f + 0.7f * k), cy + sin(ang - 0.15f) * r * (1.5f + 0.7f * k))
                        canvas.drawPath(arcPath, linePaint)
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
                        scratchRect.set(cx - w, y - r * 0.14f, cx + w, y + r * 0.14f)
                        canvas.drawArc(scratchRect, k * 720f, 110f, false, linePaint)
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
                        val ray = pathA
                        ray.reset()
                        ray.moveTo(cx + cos(ang) * r * 0.9f, cy + sin(ang) * r * 0.9f)
                        ray.lineTo(cx + cos(ang + 0.12f) * r * (1.3f + 0.4f * k), cy + sin(ang + 0.12f) * r * (1.3f + 0.4f * k))
                        ray.lineTo(cx + cos(ang) * r * (1.7f + 0.8f * k), cy + sin(ang) * r * (1.7f + 0.8f * k))
                        canvas.drawPath(ray, linePaint)
                    }
                    linePaint.color = 0xAAFFC94D.toInt()
                    canvas.drawCircle(cx, cy, r * (1.2f + 0.7f * k), linePaint)
                    linePaint.color = 0x66B388FF.toInt()
                    canvas.drawCircle(cx, cy, r * (1.5f + 1.0f * k), linePaint)
                }
            }
        }

        // 雷霆之王的两层旋转光环已移入 drawKing（要画在核心之前才能被遮挡一部分）

        // 本体
        when (stage) {
            STAGE_SPARK -> drawSpark(canvas, cx, cy, r, t, blinking)
            STAGE_BALL -> drawBall(canvas, cx, cy, r, t, blinking)
            STAGE_CLOUD -> drawCloud(canvas, cx, cy, r, t, blinking)
            STAGE_STORM -> drawTornado(canvas, cx, cy, r, t, blinking)
            else -> drawKing(canvas, cx, cy, r, t, blinking)
        }

        // 打瞌睡：灰调 + z（z 用低饱和度灰色，不受配色影响）
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
                scratchRect2.set(bar.left, bar.bottom - fillH, bar.right, bar.bottom)
                canvas.drawRoundRect(scratchRect2, rr, rr, barFillPaint)
                // 临期段（底部，最早产生的最先过期）：红色警示
                val redH = bar.height() * nearing / EnergyStore.MAX_PENDING.toFloat()
                if (redH > 1f) {
                    barFillPaint.color = 0xFFFF5252.toInt()
                    scratchRect.set(bar.left, bar.bottom - redH, bar.right, bar.bottom)
                    canvas.drawRoundRect(scratchRect, rr, rr, barFillPaint)
                }
            }
            // 数值（条上方单行，统一字号两段色）
            popPaint.alpha = 255
            popPaint.textSize = 24f
            val numStr = "$total"
            // 分母必须与填充用的是同一个常量，否则 MAX_PENDING 一改文案就在撒谎
            val maxStr = "/${EnergyStore.MAX_PENDING}"
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
            // ⚠️ helpCenter() 内部会调 energyBar()，而 energyBar() 返回的是共享的
            // energyScratch —— 调用它等于覆写 `bar`。此处是全函数最后一次用到 `bar`
            // （上一次是上面 maxStr 那行 drawText），所以顺序安全。
            // 以后若在下面新增用到 `bar` 的代码，必须先把它拷进另一个 RectF。
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
            scratchRect.set(left, top, left + barW, top + barH)
            canvas.drawRoundRect(scratchRect, rr, rr, barTrackPaint)
            if (progress > 0.01f) {
                // 渐变几何只随 view 尺寸变化，按 key 缓存，避免 30fps 每帧重建 shader
                val key = (left.toLong() shl 32) or (top.toLong() and 0xFFFFFFFFL)
                if (key != barGradientKey) {
                    barGradientKey = key
                    barGradient = android.graphics.LinearGradient(
                        left, top, left + barW, top,
                        0xFFFFC107.toInt(), 0xFFFF9800.toInt(), Shader.TileMode.CLAMP
                    )
                }
                barFillPaint.shader = barGradient
                val fw = barW * progress.coerceIn(0f, 1f)
                scratchRect2.set(left, top, left + fw.coerceAtLeast(barH), top + barH)
                canvas.drawRoundRect(scratchRect2, rr, rr, barFillPaint)
                barFillPaint.shader = null
            }
        }

        if (!thumbMode) postInvalidateDelayed(33)
    }

    // ─────────────────── 形态一：电火花（四角星形，区别于太阳） ───────────────────
    private fun drawSpark(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        // 八角星轮廓：长尖+短凹交替（闪烁感），随时间轻微旋转呼吸
        val spin = t * 0.3f
        val breathe = 1f + 0.04f * sin(t * 3f)
        val p = pathA
        p.reset()
        for (i in 0 until 16) {
            val ang = (6.2831855f * i / 16) + spin
            val rad = (if (i % 2 == 0) r * 1.15f else r * 0.5f) * breathe
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        bodyPaint.shader = cachedShader("spark$sleepy") {
            android.graphics.LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                if (sleepy) 0xFFCFD8DC.toInt() else pal.bodyLight,
                if (sleepy) 0xFF90A4AE.toInt() else pal.shade,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(p, bodyPaint)
        bodyPaint.shader = null
        // 外描边亮边
        linePaint.color = if (sleepy) 0xFFB0BEC5.toInt() else pal.highlight
        linePaint.strokeWidth = r * 0.06f
        canvas.drawPath(p, linePaint)
        // 中心亮核
        val core = if (sleepy) 0xFFCFD8DC.toInt() else pal.highlight
        bodyPaint.shader = cachedShader("sparkCore$sleepy") {
            RadialGradient(
                cx, cy, r * 0.75f,
                core, core and 0x00FFFFFF, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r * 0.72f, bodyPaint)
        bodyPaint.shader = null
        drawFace(canvas, cx, cy, r * 0.16f, blinking, fierce = false)
    }

    // ─────────────────── 形态二：电球（蓝紫等离子球，区别于太阳） ───────────────────
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        // 深空球体：中心亮 → 边缘深
        bodyPaint.shader = cachedShader("ball$sleepy") {
            RadialGradient(
                cx - r * 0.3f, cy - r * 0.32f, r * 1.55f,
                if (sleepy) 0xFFCFD8DC.toInt() else pal.bodyLight,
                if (sleepy) 0xFF78909C.toInt() else pal.deep,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r, bodyPaint)
        bodyPaint.shader = null
        // 内部等离子电弧（两道锯齿电弧，随时间滑动）
        linePaint.color = if (sleepy) 0x66E0E0E0.toInt() else pal.accentSoft
        linePaint.strokeWidth = r * 0.09f
        val ap = pathA
        for (arc in 0 until 2) {
            val phase = t * 1.6f + arc * 2.4f
            ap.reset()
            var first = true
            for (k in 0 until 7) {
                val kk = k / 6f
                val ax = cx - r * 0.75f + r * 1.5f * kk
                val ay = cy + sin(phase + kk * 9f) * r * 0.34f + (arc - 0.5f) * r * 0.4f
                if (first) { ap.moveTo(ax, ay); first = false } else ap.lineTo(ax, ay)
            }
            canvas.drawPath(ap, linePaint)
        }
        // 外环绕电弧环（两段旋转）
        linePaint.color = if (sleepy) 0x5580DEEA.toInt() else pal.accent
        linePaint.strokeWidth = r * 0.11f
        scratchRect.set(cx - r * 1.32f, cy - r * 1.32f, cx + r * 1.32f, cy + r * 1.32f)
        canvas.drawArc(scratchRect, -t * 80, 100f, false, linePaint)
        canvas.drawArc(scratchRect, 180 - t * 80, 100f, false, linePaint)
        // 左上高光
        linePaint.color = if (sleepy) 0x99FFFFFF.toInt() else pal.highlight
        linePaint.strokeWidth = r * 0.08f
        scratchRect2.set(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f)
        canvas.drawArc(scratchRect2, 205f, 62f, false, linePaint)
        drawFace(canvas, cx, cy, r * 0.17f, blinking, fierce = false)
    }

    // ─────────────────── 形态三：雷云精灵（底部平坦的积云） ───────────────────
    /**
     * 旧版是上下都是圆弧的“汉堡”，和雷霆之王几乎分不出来。
     * 真实积云的特征是**顶部圆鼓、底部切平**，这里就按这个做，
     * 并加一道雨帘和单道闪电，一眼能读出「雷雨云」。
     */
    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        val baseY = cy + r * 0.40f
        // 四个圆鼓，底端统一坐在 baseY 上
        val lobes = arrayOf(
            floatArrayOf(-0.60f, 0.42f, -0.30f),
            floatArrayOf(-0.16f, 0.58f, -0.58f),
            floatArrayOf(0.34f, 0.50f, -0.46f),
            floatArrayOf(0.72f, 0.34f, -0.22f)
        )
        fun lobesAt(dy: Float, paint: Paint) {
            for (l in lobes) canvas.drawCircle(cx + l[0] * r, baseY + l[2] * r + dy, l[1] * r, paint)
            canvas.drawRect(cx - r * 0.90f, baseY - r * 0.36f + dy, cx + r * 0.90f, baseY + dy, paint)
        }
        shadowPaint.color = if (sleepy) 0xFF90A4AE.toInt() else pal.shade
        lobesAt(r * 0.10f, shadowPaint)
        if (sleepy) {
            bodyPaint.color = 0xFFCFD8DC.toInt()
        } else {
            bodyPaint.shader = cachedShader("cloud") {
                android.graphics.LinearGradient(
                    cx, baseY - r * 1.2f, cx, baseY + r * 0.2f,
                    pal.bodyLight, pal.shade, Shader.TileMode.CLAMP
                )
            }
        }
        lobesAt(0f, bodyPaint)
        bodyPaint.shader = null
        if (!sleepy) {
            // 受光边缘：只勾上半圈，下缘不描，避免和雨帘粘连
            linePaint.color = pal.highlight
            linePaint.strokeWidth = r * 0.07f
            for (l in lobes) {
                scratchRect.set(
                    cx + l[0] * r - l[1] * r, baseY + l[2] * r - l[1] * r,
                    cx + l[0] * r + l[1] * r, baseY + l[2] * r + l[1] * r
                )
                canvas.drawArc(scratchRect, 196f, 148f, false, linePaint)
            }
            // 雨帘
            linePaint.color = pal.accentSoft
            linePaint.strokeWidth = r * 0.055f
            for (i in 0 until 6) {
                val fx = cx - r * 0.80f + i * r * 0.32f
                val ph = sin(t * 3f + i * 0.9f) * r * 0.05f
                canvas.drawLine(fx, baseY + r * 0.08f, fx - r * 0.06f + ph, baseY + r * 0.42f, linePaint)
            }
            boltPaint.color = pal.accent
            val sway = sin(t * 2.6f) * r * 0.04f
            drawBolt(canvas, cx + sway, baseY - r * 0.02f, r * 0.40f)
        }
        drawFace(canvas, cx - r * 0.06f, cy - r * 0.10f, r * 0.15f, blinking, fierce = false)
    }

    // ─────────────────── 形态四：风暴之灵（旋转弧叠出的旋涡） ───────────────────
    /**
     * 走过两轮弯路：
     *  · 初版：8 个**实心椭圆**堆叠 → 看成一叠盘子。
     *  · 二版：实心漏斗 + 3 条螺旋飘带 → 飘带在中间交叉成一个大 X，
     *    甩出漏斗外的尾端像几根杂毛；而且实心漏斗把旋涡全糊死了。
     *
     * 现在：漏斗体只留很淡的体积感，旋涡交给**六层错位旋转的弧** ——
     * 每层逐次加宽、逐次多转一点，叠起来才有旋转感。每一层只画两段弧、留出缺口，
     * 缺口正是让下层露出来的关键。
     */
    private fun drawTornado(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val pal = palette()
        val topY = cy - r * 0.90f
        val h = r * 1.80f

        // 极淡的漏斗体，只给体量感
        val funnel = pathA
        funnel.reset()
        funnel.moveTo(cx - r * 0.26f, topY)
        funnel.cubicTo(cx - r * 0.34f, topY + h * 0.36f, cx - r * 0.68f, topY + h * 0.70f, cx - r * 1.00f, topY + h)
        funnel.lineTo(cx + r * 1.00f, topY + h)
        funnel.cubicTo(cx + r * 0.68f, topY + h * 0.70f, cx + r * 0.34f, topY + h * 0.36f, cx + r * 0.26f, topY)
        funnel.close()
        bodyPaint.shader = cachedShader("tornado${if (sleepy) 1 else 0}") {
            android.graphics.LinearGradient(
                cx, topY, cx, topY + h, pal.bodyLight, pal.deep, Shader.TileMode.CLAMP
            )
        }
        bodyPaint.alpha = if (sleepy) 170 else 95
        canvas.drawPath(funnel, bodyPaint)
        bodyPaint.shader = null
        bodyPaint.alpha = 255

        if (!sleepy) {
            // 涡眼（画在脸下方，两者不重叠）
            bodyPaint.color = pal.highlight
            canvas.drawCircle(cx, topY + r * 0.16f, r * 0.19f, bodyPaint)
            linePaint.strokeCap = Paint.Cap.ROUND
            val n = 6
            for (i in 0 until n) {
                val tt = i / (n - 1f)
                val w = r * (0.30f + 0.74f * tt)
                val y = topY + r * 0.18f + tt * (h - r * 0.18f)
                val ry = r * 0.15f * (0.75f + 0.45f * tt)
                canvas.save()
                canvas.rotate(t * 26f + tt * 52f, cx, y)
                scratchRect.set(cx - w, y - ry, cx + w, y + ry)
                linePaint.strokeWidth = r * 0.15f * (1f - 0.40f * tt)
                linePaint.color = if (i % 2 == 0) pal.accent else pal.accentSoft
                linePaint.alpha = (225 - tt * 80).toInt().coerceIn(0, 255)
                canvas.drawArc(scratchRect, 28f, 124f, false, linePaint)
                canvas.drawArc(scratchRect, 208f, 124f, false, linePaint)
                canvas.restore()
            }
            linePaint.alpha = 255
        } else {
            // 打瞌睡：给几条静态横弧，至少别是个空锥
            linePaint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 4) {
                val tt = i / 3f
                val w = r * (0.34f + 0.68f * tt)
                val y = topY + r * 0.3f + tt * (h - r * 0.4f)
                linePaint.color = 0xFFB0BEC5.toInt()
                linePaint.strokeWidth = r * 0.14f
                scratchRect.set(cx - w, y - r * 0.13f, cx + w, y + r * 0.13f)
                canvas.drawArc(scratchRect, 30f, 120f, false, linePaint)
                canvas.drawArc(scratchRect, 210f, 120f, false, linePaint)
            }
        }
        drawFace(canvas, cx, topY + r * 0.64f, r * 0.15f, blinking, fierce = true)
    }

    // ─────────────────── 形态五：雷霆之王（尖锐能量核心 + 倾斜光环 + 头顶星芒） ───────────────────
    /**
     * 旧版是「云 + 6 片锯齿纸片翅膀 + 锯齿皇冠」：翅膀硬戳在身体两侧像两排刀片，
     * 皇冠浮在头顶，三者各画各的、凑不到一起，而且剪影和雷云精灵高度相似。
     *
     * 现在只保留一个主体：**尖锐的六芒能量核心**（轮廓终于有尖角可读），
     * 翅膀换成两道倾斜光环（与主体同源、有前后遮挡关系），皇冠换成一颗小星芒。
     */
    private fun drawKing(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float, blinking: Boolean) {
        val pal = palette()

        // 土星环：上半环画在核心**之前**、下半环画在核心**之后**，于是环从背后穿过。
        // 之前用的是「两段 150° 弧 + 圆头笔帽」—— 弧的端点成了几个孤零零的圆点，
        // 两段弧之间的缺口也没有任何东西遮挡，看着像随手划的几根线。
        val ringRot = floatArrayOf(-16f + sin(t * 0.5f) * 2.5f, 14f - sin(t * 0.5f) * 2.5f)
        val ringRx = floatArrayOf(r * 1.72f, r * 1.36f)
        val ringRy = floatArrayOf(r * 0.34f, r * 0.24f)

        fun drawRingHalf(i: Int, start: Float) {
            canvas.save()
            canvas.rotate(ringRot[i], cx, cy)
            scratchRect.set(cx - ringRx[i], cy - ringRy[i], cx + ringRx[i], cy + ringRy[i])
            linePaint.strokeCap = Paint.Cap.BUTT
            linePaint.strokeWidth = if (i == 0) r * 0.13f else r * 0.085f
            linePaint.color = if (i == 0) pal.accent else pal.accentSoft
            linePaint.alpha = if (i == 0) 225 else 150
            canvas.drawArc(scratchRect, start, 180f, false, linePaint)
            canvas.restore()
        }

        if (!sleepy) {
            drawRingHalf(0, 180f)
            drawRingHalf(1, 180f)
        }

        // 六芒能量核心
        val core = pathA
        core.reset()
        for (i in 0 until 12) {
            val ang = 6.2831855f * i / 12 - 1.5708f
            val rad = r * (if (i % 2 == 0) 1.02f else 0.44f)
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad * 0.94f
            if (i == 0) core.moveTo(x, y) else core.lineTo(x, y)
        }
        core.close()
        if (sleepy) {
            bodyPaint.color = 0xFFCFD8DC.toInt()
            canvas.drawPath(core, bodyPaint)
        } else {
            bodyPaint.shader = cachedShader("king$sleepy") {
                RadialGradient(
                    cx - r * 0.25f, cy - r * 0.30f, r * 1.7f,
                    pal.highlight, pal.deep, Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(core, bodyPaint)
            bodyPaint.shader = null
        }
        // 边缘亮线
        linePaint.color = if (sleepy) 0xFFB0BEC5.toInt() else pal.highlight
        linePaint.strokeWidth = r * 0.045f
        linePaint.alpha = if (sleepy) 255 else 190
        canvas.drawPath(core, linePaint)
        linePaint.alpha = 255
        // 核心亮核
        if (!sleepy) {
            bodyPaint.color = 0xFFFFFFFF.toInt()
            bodyPaint.alpha = 200
            canvas.drawCircle(cx, cy - r * 0.05f, r * 0.24f, bodyPaint)
            bodyPaint.alpha = 255
        }

        // 下半环盖在核心之上，形成前后遮挡
        if (!sleepy) {
            drawRingHalf(0, 0f)
            drawRingHalf(1, 0f)
            linePaint.strokeCap = Paint.Cap.ROUND
            linePaint.alpha = 255
        }

        // 头顶星芒（替代锯齿皇冠）
        if (!sleepy) {
            val sy = cy - r * 1.08f
            // ww 必须远小于 hh，否则四角星会退化成一个圆疙瘩（第一版就是这么糊的）
            val hh = r * 0.40f
            val ww = r * 0.085f
            fillPaint.color = pal.highlight
            fillPaint.alpha = 235
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
            canvas.drawPath(sp, fillPaint)
            fillPaint.alpha = 255
        }

        // 三道闪电从核心下方发出（起点抬进核心内，否则看着是贴在下面而非从里面长出来）
        if (!sleepy) {
            boltPaint.color = pal.accent
            drawBolt(canvas, cx, cy + r * 0.50f, r * 0.56f)
            boltPaint.color = pal.accentSoft
            drawBolt(canvas, cx - r * 0.56f, cy + r * 0.34f, r * 0.32f)
            drawBolt(canvas, cx + r * 0.56f, cy + r * 0.38f, r * 0.29f)
        }
        drawFace(canvas, cx, cy - r * 0.04f, r * 0.16f, blinking, fierce = true)
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
            val glow = faceGlowPaint
            canvas.drawCircle(cx - off + er * 0.25f, cy + er * 0.3f, er * 0.55f, glow)
            canvas.drawCircle(cx + off + er * 0.25f, cy + er * 0.3f, er * 0.55f, glow)
            val hi = faceHiPaint
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
            val p = pathC
            p.reset()
            p.moveTo(cx - er * 0.75f, my)
            p.quadTo(cx, my + er * 0.85f, cx + er * 0.75f, my)
            canvas.drawPath(p, linePaint)
        }
    }

    private fun drawBolt(canvas: Canvas, x: Float, y: Float, s: Float) {
        val p = pathC
        p.reset()
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
