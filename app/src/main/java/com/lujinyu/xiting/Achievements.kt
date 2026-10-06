// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context

/**
 * 精灵成就。
 *
 * 从 8 条砍到 4 条：原先 first / h1 / h10 / h50 四条里，三条是同一根「累计时长」
 * 轴上的刻度（1 / 10 / 50 小时），还有 first 是打开 App 就自动亮的 —— 挂着没意义。
 * 保留的四条各自代表一种不同维度，互不重复：
 *   单次时长 / 连续性 / 频次 / 阶段
 *
 * 已解锁集合 ach_unlocked 是持久化的，砍掉的 id 会被 [knownUnlocked] 直接忽略，
 * 不会在成就弹窗里变成「永远打不开的锁」。
 */
object Achievements {

    data class A(
        val id: String,
        val icon: String,
        val titleRes: Int,
        val descRes: Int,
        /** 精灵左侧点亮的那枚徽章；[icon] 仍用于 toast 文本 */
        val badge: Badges.Kind
    )

    /**
     * 顺序即徽章格位顺序：**第 1 项在最下、难度最低**，往上依次递进。
     * 改这个列表的顺序会同时改掉徽章列的排列和成就弹窗的排列，两处永远一致。
     */
    val ALL = listOf(
        A("marathon", "🏃", R.string.ach_marathon_t, R.string.ach_marathon_d, Badges.Kind.HOURGLASS),
        A("week", "📅", R.string.ach_week_t, R.string.ach_week_d, Badges.Kind.FLAME),
        A("c100", "🎧", R.string.ach_c100_t, R.string.ach_c100_d, Badges.Kind.TARGET),
        A("king", "⚡", R.string.ach_king_t, R.string.ach_king_d, Badges.Kind.CROWN)
    )

    /** 精灵徽章列的当前状态（按 [ALL] 顺序） */
    fun badgeStates(ctx: Context): List<Badges.Badge> = badgeList(unlocked(ctx))

    fun title(ctx: Context, a: A): String = ctx.getString(a.titleRes)
    fun desc(ctx: Context, a: A): String = ctx.getString(a.descRes)

    private fun unlocked(ctx: Context): MutableSet<String> {
        val p = ctx.prefs()
        return (p.getStringSet(Prefs.ACH_UNLOCKED, emptySet()) ?: emptySet()).toMutableSet()
    }

    /** 依据统计评估并持久化，返回本次新解锁的成就（可能为空） */
    fun evaluate(ctx: Context, count: Int, maxMs: Long, days: Int, stage: Int): List<A> {
        val cond = mapOf(
            "marathon" to (maxMs >= 3_600_000L),
            "week" to (days >= 7),
            "c100" to (count >= 100),
            "king" to (stage >= 4)
        )
        val got = unlocked(ctx)
        val fresh = ALL.filter { it.id !in got && cond[it.id] == true }
        if (fresh.isNotEmpty()) {
            got.addAll(fresh.map { it.id })
            ctx.prefs()
                .edit().putStringSet(Prefs.ACH_UNLOCKED, got).apply()
        }
        return fresh
    }

    /** 成就卡摘要：(已解锁数, 最新一条描述) */
    fun summary(ctx: Context): Pair<Int, String> {
        val known = knownUnlocked(unlocked(ctx))
        val sub = if (known.isEmpty()) {
            ctx.getString(R.string.ach_sub_empty)
        } else {
            ctx.getString(R.string.ach_sub_latest, title(ctx, known.last()))
        }
        return known.size to sub
    }
}

/**
 * 徽章列状态映射（纯函数，可单测）。
 *
 * 与 [knownUnlocked] 同一套防御：传进来的残留 id 只会让对应格子保持未点亮，
 * 不会平白多出一枚徽章，也不会让格数超过 [Achievements.ALL] 的大小。
 */
internal fun badgeList(got: Set<String>): List<Badges.Badge> =
    Achievements.ALL.map { Badges.Badge(it.badge, it.id in got) }

/**
 * 已知成就与已解锁集合的交集（纯函数，可单测）。
 *
 * 只认 ALL 里存在的 id：ach_unlocked 是持久化字符串集合，早期版本改过成就 id、
 * 或文件被外部改写，都可能留下 ALL 里已不存在的残留项。原实现直接
 * ALL.lastOrNull { it.id in got } 后 latest!!，集合非空但全是残留 id 时
 * latest 为 null，统计页当场 NPE。
 */
internal fun knownUnlocked(got: Set<String>): List<Achievements.A> =
    Achievements.ALL.filter { it.id in got }
