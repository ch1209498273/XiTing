// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context

/**
 * 精灵成就系统（精灵二期·一期）：依据累计听剧数据评估解锁条件，
 * 解锁状态持久化在 prefs，评估幂等可随时重跑；新解锁由调用方负责提示。
 */
object Achievements {

    data class A(val id: String, val icon: String, val title: String, val desc: String)

    val ALL = listOf(
        A("first", "🌙", "初次息屏", "完成第一次息屏听剧"),
        A("h1", "🎧", "一小时俱乐部", "累计听剧满 1 小时"),
        A("h10", "🌙", "十小时之约", "累计听剧满 10 小时"),
        A("h50", "👑", "五十小时王朝", "累计听剧满 50 小时"),
        A("c100", "💯", "百次成习", "累计息屏 100 次"),
        A("marathon", "🏃", "马拉松", "单次连续听剧满 1 小时"),
        A("week", "📅", "持之以恒", "累计 7 个不同日期有息屏记录"),
        A("king", "⚡", "雷霆加冕", "精灵进化至雷霆之王")
    )

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
            got.isEmpty() -> "听剧积累，逐一点亮"
            else -> "最新解锁：${latest?.icon} ${latest?.title}"
        }
        return got.size to sub
    }
}
