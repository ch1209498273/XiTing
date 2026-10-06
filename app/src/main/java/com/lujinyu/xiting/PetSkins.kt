// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * 皮肤（图鉴收集）：同一套 Canvas 绘制换个色相重画。
 *
 * 解锁维度与成就系统**刻意不重叠**：成就已占用「单次时长、会话 100 次、
 * 连续 7 天、满级」，皮肤改用省电量、单次更长时长、更长连续天数、
 * 以及满级后继续储备的成长值。此前皮肤条件是 10h / 50h / 7 天，
 * 与 h10 / h50 / week 三个成就同一时刻点亮 —— 同一个里程碑拿两次奖励，
 * 用户没有任何额外获得感。
 *
 * 全部离线，不涉及网络。
 */
object PetSkins {

    data class Skin(
        val id: String,
        val hue: Float,
        /**
         * 饱和度系数。**必须 >= 1.0**：精灵主体本就接近白色，
         * 一旦降饱和（<1）就直接洗成纯白，轮廓和五官全看不见（用户实测反馈）。
         * 所以这里只用来「放大」色相差异，不用来淡化。
         */
        val saturation: Float,
        val nameRes: Int,
        val condRes: Int
    )

    /**
     * 六款配色：色相按**名字**定，不是按 60° 均分。
     *
     * 均分色相（0/60/120/180/240/300）在旧的色相旋转方案下已经让四款皮肤名不副实：
     * 「翡翠」渲染成洋红、「樱雨」是青、「熔岩」是蓝、「星夜」是黄。
     * 旋转方案把这层混乱盖住了（旧版它们各自还保留了一部分原始底色），
     * 但换成「色相直接决定颜色」之后就藏不住了 —— 所以这里按名字重新定色相。
     *
     * 饱和度全部 >= 1.0：只用来放大彩度，不用来淡化（<1 会把浅色主体洗成纯白）。
     */
    val ALL = listOf(
        Skin("default", 0f, 1.00f, R.string.skin_default_n, R.string.skin_cond_default),
        Skin("star", 245f, 1.30f, R.string.skin_star_n, R.string.skin_cond_star),      // 星夜：深蓝紫
        Skin("aurora", 160f, 1.35f, R.string.skin_aurora_n, R.string.skin_cond_aurora),  // 极光：青绿
        Skin("sakura", 330f, 1.15f, R.string.skin_sakura_n, R.string.skin_cond_sakura),  // 樱雨：粉
        Skin("magma", 15f, 1.45f, R.string.skin_magma_n, R.string.skin_cond_magma),      // 熔岩：橙红
        Skin("jade", 105f, 1.20f, R.string.skin_jade_n, R.string.skin_cond_jade)         // 翡翠：翠绿
    )

    /**
     * 换肤解锁所需的统计快照。
     *
     * 用具名字段而不是一串位置参数：五个同型参数（三个 Long、两个 Int）迟早会被
     * 调用方传错顺序，而传错顺序不会编译失败，只会安静地给出错误的解锁判定。
     */
    data class SkinProgress(
        val totalMs: Long,
        val maxSingleMs: Long,
        val streakDays: Int,
        val mah: Int,
        val gp: Long
    )

    /** 满级形态在 THRESHOLDS 中的门槛，用于「满级后储备」类条件 */
    private val KING_AT = PetView.THRESHOLDS[PetView.STAGE_KING]

    /** 满级后需额外储备的成长值（承接「已至巅峰·继续储备」设定） */
    private const val KING_RESERVE = 2000L

    fun name(ctx: Context, s: Skin): String = ctx.getString(s.nameRes)
    fun cond(ctx: Context, s: Skin): String = ctx.getString(s.condRes)

    private fun unlocked(ctx: Context): MutableSet<String> {
        val p = ctx.prefs()
        return (p.getStringSet(Prefs.SKINS_UNLOCKED, setOf("default")) ?: setOf("default")).toMutableSet()
    }

    /**
     * 各皮肤的解锁判定。
     *
     * 与成就零重叠：省电量、单次 2 小时、连续 14/60 天、满级后储备 2000 成长值。
     * 已解锁过的皮肤一律保持解锁（`else -> id in unlocked`），避免条件调低后
     * 老用户反而掉解锁。
     */
    fun isUnlocked(s: Skin, p: SkinProgress): Boolean = when (s.id) {
        "default" -> true
        "star" -> p.mah >= 1000
        "aurora" -> p.maxSingleMs >= 2 * 3600_000L
        "sakura" -> p.streakDays >= 14
        "magma" -> p.gp >= KING_AT + KING_RESERVE
        "jade" -> p.streakDays >= 60
        else -> false
    }

    /** 调试用：一键全解锁（仅通过 adb 广播触发，普通用户不可达） */
    fun unlockAllForDebug(ctx: Context) {
        val got = unlocked(ctx)
        ALL.forEach { got.add(it.id) }
        ctx.prefs()
            .edit().putStringSet(Prefs.SKINS_UNLOCKED, got).apply()
    }

    /** 条件评估并持久化，返回本次新解锁（可能为空） */
    fun evaluate(ctx: Context, p: SkinProgress): List<Skin> {
        val got = unlocked(ctx)
        // 条件已满足或历史已解锁的都要计入，否则调低条件后老用户会掉解锁
        val fresh = ALL.filter { it.id !in got && isUnlocked(it, p) }
        if (fresh.isNotEmpty()) {
            got.addAll(fresh.map { it.id })
            ctx.prefs()
                .edit().putStringSet(Prefs.SKINS_UNLOCKED, got).apply()
        }
        return fresh
    }

    /** 已解锁集合（图鉴用：置灰未解锁项） */
    fun unlockedIds(ctx: Context): Set<String> = unlocked(ctx).toSet()

    /** 当前穿戴的皮肤（默认=经典） */
    fun active(ctx: Context): Skin {
        val id = ctx.prefs()
            .getString(Prefs.PET_SKIN, "default") ?: "default"
        return ALL.firstOrNull { it.id == id } ?: ALL[0]
    }

    fun wear(ctx: Context, s: Skin) {
        ctx.prefs()
            .edit().putString(Prefs.PET_SKIN, s.id).apply()
    }

    /**
     * 换肤已于 2026-10 重构：不再是「画完对整张位图做色相旋转」的后处理滤镜，
     * 而是**把色相变成绘制参数**（见 [PetPalette]）。旧的 `skinFilter()` 已删除。
     *
     * 原因：色相旋转会让青翼、紫影、金冠各自转到不同位置（撞色被完整保留，
     * 等于每次换肤重新掷一次骰子），而且会把承载语义的灰白色元素染色。
     * 改成按色相现算颜色后，一只精灵身上所有颜色都出自同一个 hue。
     */

    /** 离屏渲染指定形态 + 指定配色的精灵位图（图鉴缩略图 / 小组件共用） */
    fun snapshot(
        context: Context,
        stage: Int,
        skin: Skin,
        size: Int,
        withBar: Boolean,
        pct: Int
    ): Bitmap {
        val barH = if (withBar) 26 else 0
        val bmp = Bitmap.createBitmap(size, size + barH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pet = PetView(context)
        pet.stage = stage
        pet.applySkin(skin)
        // 必须置 thumbMode：否则 PetView 会把交互用的界面元素一并画进来 ——
        // 左侧能量条、右下角「?」说明图标、以及 30fps 动画循环。
        // 图鉴缩略图曾因此每张都糊着一条黄色能量条和一个问号，挤得看不清精灵本身。
        // 小部件需要的进度条由本函数在下方单独绘制（withBar），不依赖 PetView 自己那条。
        pet.thumbMode = true
        pet.hideProgress = true
        val spec = android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
        pet.measure(spec, spec)
        pet.layout(0, 0, size, size)
        pet.draw(canvas)
        if (withBar && pct in 1..99) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.argb(70, 255, 255, 255)
            canvas.drawRoundRect(14f, (size + 6).toFloat(), (size - 14).toFloat(), (size + 14).toFloat(),
                5f, 5f, paint)
            paint.color = android.graphics.Color.rgb(240, 200, 126)
            val fillW = 14f + (size - 28) * pct / 100f
            canvas.drawRoundRect(14f, (size + 6).toFloat(), fillW, (size + 14).toFloat(),
                5f, 5f, paint)
        }
        return bmp
    }
}
