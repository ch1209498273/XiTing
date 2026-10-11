// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.os.SystemClock
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View

/** Five evolving characters with per-stage gestures. Growth/storage IDs stay unchanged.
 * Panel and static snapshots delegate their character to the shared PetRenderer. */
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

        /** Legacy geometry constants retained for compatibility; renderer no longer draws the old glow. */
        val GLOW_RATIO = floatArrayOf(0.20f, 0.28f, 0.34f, 0.40f, 0.475f)

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
        fun interactionHint(context:Context,stage:Int):String = context.getString(when(stage) {
            0 -> R.string.pet_hint_spark
            1 -> R.string.pet_hint_ball
            2 -> R.string.pet_hint_cloud
            3 -> R.string.pet_hint_storm
            else -> R.string.pet_hint_king
        })
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

    /** 交互高亮：让指定格徽章脉冲一下（成就弹窗点某条时调用，复用新解锁的 glow 通道） */
    fun pulseBadge(index: Int) {
        if (index !in badges.indices) return
        badgeGlowIndex = index
        badgeGlowAt = System.currentTimeMillis()
        invalidate()
    }

    // 收集动画内部状态
    private data class FlyBall(val value: Int, val sx: Float, val sy: Float, val startAt: Long)
    private data class Popup(val text: String, val startAt: Long)
    private val flyBalls = mutableListOf<FlyBall>()
    private val popups = mutableListOf<Popup>()
    private var pulseUntil = 0L
    private var downX = 0f
    private var downY = 0f
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val renderer = PetRenderer()
    private val animationBornAt = SystemClock.uptimeMillis()
    private var activeAction = PetAction.NONE
    private var actionAt = -10_000L
    private var actionStrength = 1f
    private var downAt = 0L
    private var touchTarget = PetTarget.NONE
    private var collectionAnimating = false
    private var tapHeldMs = 0L
    private var tapDx = 0f
    private var tapDy = 0f
    private var hasGesture = false
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE }
    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand)
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bar_track) }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Reused geometry for energy/progress UI.
    private val scratchRect = RectF()
    private val scratchRect2 = RectF()
    /** 能量条几何：energyBar() 以前每次调用都 new 一个 RectF，而一帧里要被调用 3~4 次 */
    private val energyScratch = RectF()
    /** 进度条渐变缓存：几何随 view 尺寸而定，尺寸不变就不必每帧重建 */
    private var barGradient: android.graphics.LinearGradient? = null
    private var barGradientKey = 0L

    init {
        setOnClickListener { poke() }
    }

    private val visibleRect=Rect()
    private val scrollListener=android.view.ViewTreeObserver.OnScrollChangedListener { startAnimating() }
    private fun shouldAnimate() = !thumbMode && isAttachedToWindow && isShown &&
        windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && getLocalVisibleRect(visibleRect)
    private val animationTick = object : Runnable {
        override fun run() {
            if (shouldAnimate()) { invalidate(); postDelayed(this, 33) }
        }
    }
    fun startAnimating() {
        removeCallbacks(animationTick)
        if (shouldAnimate()) post(animationTick)
        invalidate()
    }
    fun poke() {
        val now = SystemClock.uptimeMillis()
        if (now - actionAt < 160) return
        activeAction = if (hasGesture) PetMotion.action(stage, tapHeldMs, tapDx, tapDy)
            else PetMotion.action(stage, 0, 0f, 0f)
        hasGesture = false
        if (activeAction == PetAction.NONE) return
        actionAt = now
        actionStrength = if (activeAction == PetAction.CHARGE) (tapHeldMs / 900f).coerceIn(.45f, 1f) else 1f
        startAnimating()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        startAnimating()
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(animationTick)
        if(viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        touchTarget = PetTarget.NONE
        super.onDetachedFromWindow()
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        super.onLayout(changed,left,top,right,bottom)
        startAnimating()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        startAnimating()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        startAnimating()
    }

    /** 当前帧精灵几何（绘制与点击命中共用） */
    private fun geometry(): Triple<Float, Float, Float> {
        val cx = width / 2f
        val cy = height / 2f
        val h = height.toFloat()
        val r = h * if (thumbMode) {
            arrayOf(0.22f, 0.26f, 0.29f, 0.32f, 0.34f)[stage.coerceIn(0, 4)]
        } else when (stage) {
            STAGE_SPARK -> 0.15f
            STAGE_BALL -> 0.18f
            STAGE_CLOUD -> 0.21f
            STAGE_STORM -> 0.24f
            else -> 0.27f
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

    private fun targetAt(x: Float, y: Float): PetTarget {
        val (hx, hy) = helpCenter()
        val d = resources.displayMetrics.density
        val help = (x-hx)*(x-hx)+(y-hy)*(y-hy) < 20f*d*20f*d
        val bar = energyBar()
        val energy = x in (bar.left-16*d)..(bar.right+24*d) &&
            y in (bar.top-24*d)..(bar.bottom+16*d)
        val (cx, cy, r) = geometry()
        val body = kotlin.math.abs(x-cx) <= r*1.4f && kotlin.math.abs(y-cy) <= r*1.25f
        return PetMotion.target(help, energy, body)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (thumbMode) return super.onTouchEvent(event)
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downAt = SystemClock.uptimeMillis()
                touchTarget = targetAt(event.x, event.y)
                if (touchTarget == PetTarget.NONE) return false
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x-downX)/density; val dy = (event.y-downY)/density
                if (kotlin.math.abs(dy)>18f && kotlin.math.abs(dy)>=kotlin.math.abs(dx)) {
                    touchTarget = PetTarget.NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (stage == STAGE_STORM && touchTarget == PetTarget.PET && kotlin.math.abs(dx)>18f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val target = touchTarget; touchTarget = PetTarget.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                val dx = (event.x-downX)/density; val dy = (event.y-downY)/density
                val stayed = kotlin.math.abs(dx)<=18f && kotlin.math.abs(dy)<=18f
                when (target) {
                    PetTarget.HELP -> if (stayed && targetAt(event.x,event.y)==PetTarget.HELP) onHelp?.invoke()
                    PetTarget.ENERGY -> if (stayed && targetAt(event.x,event.y)==PetTarget.ENERGY && !collectionAnimating) {
                        val total = pending.sumOf { it.value }
                        if (total>0) {
                            collectionAnimating = true
                            val bar=energyBar()
                            flyBalls.add(FlyBall(total,bar.centerX(),bar.centerY(),System.currentTimeMillis()))
                            postDelayed({ collectionAnimating=false; onCollectAll?.invoke() },340)
                        }
                    }
                    PetTarget.PET -> {
                        tapHeldMs=SystemClock.uptimeMillis()-downAt;tapDx=dx;tapDy=dy
                        if (PetMotion.action(stage,tapHeldMs,dx,dy)!=PetAction.NONE) {
                            hasGesture=true;performClick();hasGesture=false
                        }
                    }
                    else -> Unit
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchTarget=PetTarget.NONE;hasGesture=false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate();return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }

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
        val (cx, cy, r) = geometry()
        val animate = !thumbMode && ValueAnimator.areAnimatorsEnabled()
        val clock = SystemClock.uptimeMillis()
        val heldCharge = if (touchTarget == PetTarget.PET && stage == STAGE_BALL && animate)
            ((clock-downAt)/900f).coerceIn(0f,1f) else 0f
        renderer.draw(canvas, stage, cx, cy, r, (clock-animationBornAt)/1000f,
            skinHue, skinSat, sleepy, static=!animate, action=activeAction,
            elapsed=clock-actionAt, strength=actionStrength, charge=heldCharge)

        // ───────── 能量系统：左侧常驻电池条（容量200）+ 充能动画 ─────────
        if (!thumbMode) {
            val bar = energyBar()
            val total = pending.sumOf { it.value }.coerceAtMost(EnergyStore.MAX_PENDING)
            val nearing = pending
                .filter { it.expireAt - now < EnergyStore.NEAR_EXPIRE_MS }
                .sumOf { it.value }.coerceAtMost(total)
            val rr = bar.width() / 2f
            // 轨道（常驻显示）
            barTrackPaint.color = context.getColor(R.color.bar_track)
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
            popPaint.color = if (nearing > 0) context.getColor(R.color.pet_warn) else context.getColor(R.color.gold_text)
            canvas.drawText(numStr, startX + w1 / 2f, bar.top - 16f, popPaint)
            popPaint.color = 0xFF9AA1AA.toInt()
            canvas.drawText(maxStr, startX + w1 + w2 / 2f, bar.top - 16f, popPaint)
            popPaint.color = context.getColor(R.color.brand)
            popPaint.textSize = 30f
            // ? 说明图标
            // ⚠️ helpCenter() 内部会调 energyBar()，而 energyBar() 返回的是共享的
            // energyScratch —— 调用它等于覆写 `bar`。此处是全函数最后一次用到 `bar`
            // （上一次是上面 maxStr 那行 drawText），所以顺序安全。
            // 以后若在下面新增用到 `bar` 的代码，必须先把它拷进另一个 RectF。
            val (hx, hy) = helpCenter()
            bodyPaint.color = context.getColor(R.color.pet_help_circle)
            canvas.drawCircle(hx, hy, 24f, bodyPaint)
            popPaint.color = context.getColor(R.color.text_secondary)
            popPaint.textSize = 30f
            popPaint.isFakeBoldText = true
            canvas.drawText("?", hx, hy + 10f, popPaint)
            popPaint.color = context.getColor(R.color.brand)

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

        // Animation scheduling is owned by one visibility-aware runnable.
    }

}
