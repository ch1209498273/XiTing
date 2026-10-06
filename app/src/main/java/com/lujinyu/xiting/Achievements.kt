// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context

/**
 * 精灵成就系统（精灵二期·一期）：依据累计听剧数据评估解锁条件，
 * 解锁状态持久化在 prefs，评估幂等可随时重跑；新解锁由调用方负责提示。
 * 文案走资源（titleRes/descRes），支持多语言。
 */
object Achievements {

    data class A(val id: String, val icon: String, val titleRes: Int, val descRes: Int)

    val ALL = listOf(
        A("first", "🌙", R.string.ach_first_t, R.string.ach_first_d),
        A("h1", "🎧", R.string.ach_h1_t, R.string.ach_h1_d),
        A("h10", "🌙", R.string.ach_h10_t, R.string.ach_h10_d),
        A("h50", "👑", R.string.ach_h50_t, R.string.ach_h50_d),
        A("c100", "💯", R.string.ach_c100_t, R.string.ach_c100_d),
        A("marathon", "🏃", R.string.ach_marathon_t, R.string.ach_marathon_d),
        A("week", "📅", R.string.ach_week_t, R.string.ach_week_d),
        A("king", "⚡", R.string.ach_king_t, R.string.ach_king_d)
    )

    fun title(ctx: Context, a: A): String = ctx.getString(a.titleRes)
    fun desc(ctx: Context, a: A): String = ctx.getString(a.descRes)

    private fun unlocked(ctx: Context): MutableSet<String> {
        val p = ctx.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)
        return (p.getStringSet("ach_unlocked", emptySet()) ?: emptySet()).toMutableSet()
    }

    /** 依据统计评估并持久化，返回本次新解锁的成就（可能为空） */
    fun evaluate(ctx: Context, allMs: Long, count: Int, maxMs: Long, days: Int, stage: Int): List<A> {
        val cond = mapOf(
            "first" to (allMs > 0),
            "h1" to (allMs >= 3_600_000L),
            "h10" to (allMs >= 36_000_000L),
            "h50" to (allMs >= 180_000_000L),
            "c100" to (count >= 100),
            "marathon" to (maxMs >= 3_600_000L),
            "week" to (days >= 7),
            "king" to (stage >= 4)
        )
        val got = unlocked(ctx)
        val fresh = ALL.filter { it.id !in got && cond[it.id] == true }
        if (fresh.isNotEmpty()) {
            got.addAll(fresh.map { it.id })
            ctx.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)
                .edit().putStringSet("ach_unlocked", got).apply()
        }
        return fresh
    }

    /** 成就卡摘要：(已解锁数, 最新一条描述) */
    fun summary(ctx: Context): Pair<Int, String> {
        val got = unlocked(ctx)
        val latest = ALL.lastOrNull { it.id in got }
        val sub = when {
            got.isEmpty() -> ctx.getString(R.string.ach_sub_empty)
            else -> ctx.getString(R.string.ach_sub_latest, title(ctx, latest!!))
        }
        return got.size to sub
    }
}
