// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import java.util.TimeZone

/**
 * 连续听剧天数（streak）：从会话记录推导，无需额外状态。
 * 「听剧日」= 当天有 ≥1 条会话记录；连续中断一天即断（今天没听不打断，宽限到明天）。
 */
object Streaks {

    data class Info(val current: Int, val best: Int, val todayDone: Boolean)

    /**
     * 某时间戳属于「本地时区的第几天」（epoch day）。
     *
     * ⚠ **不要改用 `SimpleDateFormat("yyyyMMdd").format(...)` 再 distinct()**：
     * 1. 慢：每条会话都要新建一个 SimpleDateFormat（要解析 pattern、建 Calendar），
     *    500 条记录就是 500 次。
     * 2. **会算错**：格式化的数字受 locale 影响 —— `th-TH` 用佛历（2569 而非 2026），
     *    阿拉伯语等 locale 可能输出非 ASCII 数字，于是**同一天被 distinct 成两天**，
     *    直接影响「持之以恒」成就的解锁。
     *
     * 这里用「毫秒 + 该时刻的时区偏移，再整除一天」得到一个整数天号，
     * 无 locale 依赖，且是纯函数（可单测）。
     */
    fun epochDayOf(ms: Long): Long {
        val tz = TimeZone.getDefault()
        return (ms + tz.getOffset(ms)) / 86_400_000L
    }

    /** 不同的「听剧日」数量（用于「累计 N 天」类统计，不看连续性） */
    fun distinctListenDays(sessions: List<ListenSession>): Int =
        sessions.mapTo(HashSet()) { epochDayOf(it.start) }.size

    fun compute(sessions: List<ListenSession>): Info =
        computeFromDays(sessions.mapTo(HashSet()) { epochDayOf(it.start) })

    /**
     * 从「听剧日集合」算连续天数（v4.0：明细只留 60 天，全量口径走
     * HistoryStore.listenDays，必须用这个入口）。
     * today 参数默认取当前时刻；单测传固定值钉死边界。
     */
    fun computeFromDays(days: Set<Long>, today: Long = epochDayOf(System.currentTimeMillis())): Info {
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
}
