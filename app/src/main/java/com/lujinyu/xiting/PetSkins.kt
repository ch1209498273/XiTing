// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

/**
 * 精灵皮肤（图鉴收集）：同一套 Canvas 绘制按色相旋转换装。
 * 解锁条件基于累计听剧/连续天数/累计省电，全部离线。
 */
object PetSkins {

    data class Skin(val id: String, val hue: Float, val nameRes: Int, val condRes: Int)

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

    fun isUnlocked(ctx: Context, s: Skin, totalMs: Long, streakDays: Int, mah: Int): Boolean = when (s.id) {
        "default" -> true
        "star" -> totalMs >= 36_000_000L
        "aurora" -> totalMs >= 180_000_000L
        "sakura" -> streakDays >= 7
        "magma" -> mah >= 5000
        "jade" -> streakDays >= 30
        else -> s.id in unlocked(ctx)
    }

    /** 调试用：一键全解锁（仅通过 adb 广播触发，普通用户不可达） */
    fun unlockAllForDebug(ctx: Context) {
        val got = unlocked(ctx)
        ALL.forEach { got.add(it.id) }
        ctx.prefs()
            .edit().putStringSet(Prefs.SKINS_UNLOCKED, got).apply()
    }

    /** 条件评估并持久化，返回本次新解锁（可能为空） */
    fun evaluate(ctx: Context, totalMs: Long, streakDays: Int, mah: Int): List<Skin> {
        val got = unlocked(ctx)
        val fresh = ALL.filter { it.id !in got && isUnlocked(ctx, it, totalMs, streakDays, mah) }
        if (fresh.isNotEmpty()) {
            got.addAll(fresh.map { it.id })
            ctx.prefs()
                .edit().putStringSet(Prefs.SKINS_UNLOCKED, got).apply()
        }
        return fresh
    }

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
