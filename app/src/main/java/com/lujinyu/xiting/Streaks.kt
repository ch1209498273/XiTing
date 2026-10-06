// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import java.util.TimeZone

/**
 * 连续听剧天数（streak）：从会话记录推导，无需额外状态。
 * 「听剧日」= 当天有 ≥1 条会话记录；连续中断一天即断（今天没听不打断，宽限到明天）。
 */
object Streaks {

    data class Info(val current: Int, val best: Int, val todayDone: Boolean)

    fun compute(sessions: List<ListenSession>): Info {
        val tz = TimeZone.getDefault()
        val days = HashSet<Long>()
        for (s in sessions) {
            days.add((s.start + tz.getOffset(s.start)) / 86_400_000L)
        }
        val today = epochDay(System.currentTimeMillis())

        var current = 0
        var cursor = today
        if (cursor !in days) cursor -= 1 // 今天还没听：从昨天往前数，今天听了会补上
        while (cursor in days) {
            current += 1
            cursor -= 1
        }

        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE / 2
        days.sorted().forEach { d ->
            run = if (d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return Info(current, best, today in days)
    }

    private fun epochDay(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L
}
