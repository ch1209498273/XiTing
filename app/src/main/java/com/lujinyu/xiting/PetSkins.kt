// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

/**
 * 皮肤（图鉴收集）：同一套 Canvas 绘制按色相旋转换装。
 *
 * 解锁维度与成就系统**刻意不重叠**：成就已占用「累计时长 1/10/50 小时、
 * 会话 100 次、单次 1 小时、连续 7 天、满级」，皮肤改用省电量、单次更长时长、
 * 更长连续天数、以及满级后继续储备的成长值。此前皮肤条件是
 * 10h / 50h / 7 天，与 h10 / h50 / week 三个成就同一时刻点亮 ——
 * 同一个里程碑拿两次奖励，用户没有任何额外获得感。
 *
 * 全部离线，不涉及网络。
 */
object PetSkins {

    data class Skin(val id: String, val hue: Float, val nameRes: Int, val condRes: Int)

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

    val ALL = listOf(
        Skin("default", 0f, R.string.skin_default_n, R.string.skin_cond_default),
        Skin("star", 40f, R.string.skin_star_n, R.string.skin_cond_star),
        Skin("aurora", 110f, R.string.skin_aurora_n, R.string.skin_cond_aurora),
        Skin("sakura", 190f, R.string.skin_sakura_n, R.string.skin_cond_sakura),
        Skin("magma", 200f, R.string.skin_magma_n, R.string.skin_cond_magma),
        Skin("jade", 290f, R.string.skin_jade_n, R.string.skin_cond_jade)
    )

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

    /** 色相旋转滤镜（度）。换肤的核心：绘制时对整只精灵做色相旋转，白底与高光不受影响 */
    fun hueFilter(deg: Float): ColorMatrixColorFilter {
        val rad = Math.toRadians(deg.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        val m = floatArrayOf(
            lr + cos * (1 - lr) + sin * -lr, lg + cos * -lg + sin * -lg, lb + cos * -lb + sin * (1 - lb), 0f, 0f,
            lr + cos * -lr + sin * 0.143f, lg + cos * (1 - lg) + sin * 0.140f, lb + cos * -lb + sin * -0.283f, 0f, 0f,
            lr + cos * -lr + sin * -(1 - lr), lg + cos * -lg + sin * lg, lb + cos * lb + sin * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        return ColorMatrixColorFilter(m)
    }

    /** 离屏渲染指定形态+皮肤的精灵位图（小部件/图鉴共用） */
    fun snapshot(context: Context, stage: Int, hue: Float, size: Int, withBar: Boolean, pct: Int): Bitmap {
        val barH = if (withBar) 26 else 0
        val bmp = Bitmap.createBitmap(size, size + barH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pet = PetView(context)
        pet.stage = stage
        pet.skinHue = hue
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
