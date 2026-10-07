// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

/**
 * 本周小结。
 *
 * 统计页原来只有「今日 / 近7天 / 累计」三个数字——都��绝对值，看不出习惯。
 * 这里给出的是**有对比的**信息：这周听了多久、比上周多还是少、连续几天、
 * 精灵这周的变化。数据全部来自已有的会话记录，不新增任何存储。
 *
 * 全部是纯函数（只吃 [sessions] 和时间戳），所以可以单测 —— 时间窗口
 * 算错是这类报表最常见也最难发现的 bug。
 */
object WeekReport {

    data class Summary(
        val weekMs: Long,
        val prevWeekMs: Long,
        val listenDays: Int,        // 本周有听剧记录的天数（0..7）
        val sessionCount: Int,
        val mahSaved: Int,          // 本周省电估算（mAh）
        val stageNow: Int,          // 本周结束时的形态
        val stageAtWeekStart: Int,  // 本周开始时的形态
        val weekGp: Int             // 本周获得的成长值（听剧每分钟 1 点）
    ) {
        /** 与上周相比的增量（ms）。上周为 0 时不报百分比，否则会除零。 */
        val deltaMs: Long get() = weekMs - prevWeekMs

        fun deltaRatio(): Float? = when {
            prevWeekMs <= 0L -> null
            else -> deltaMs.toFloat() / prevWeekMs
        }
    }

    /**
     * 统计 [sessions] 中落在本周与上周的时长。
     *
     * 「本周」= 从本周一 00:00（本地时区）到现在。
     * 为什么按周而不是「最近 7 天」：用户心里「这周」的边界是周一，
     * 「近7天」是滚动的 —— 两者在周日会差出整整一天。
     */
    fun summarize(
        sessions: List<ListenSession>,
        nowMs: Long,
        currentGp: Long,
        mahOf: (Long) -> Int,
        stageOfGp: (Long) -> Int,
    ): Summary {
        val weekStart = startOfWeek(nowMs)
        val prevWeekStart = weekStart - 7L * 86_400_000L

        var weekMs = 0L
        var prevWeekMs = 0L
        var count = 0
        val weekDays = HashSet<Long>()

        for (s in sessions) {
            // 按**开始时间**归周：跨午夜的长会话整体算在前一天开始的那周，
            // 比按中点切分更容易解释（"我周日晚上听的"）。
            if (s.start >= weekStart) {
                weekMs += s.durationMs
                count++
                weekDays.add(Streaks.epochDayOf(s.start))
            } else if (s.start >= prevWeekStart) {
                prevWeekMs += s.durationMs
            }
        }

        // 精灵本周有没有进化。
        //
        // ⚠ 不去查「本周开始时」的累计成长值——那需要历史快照，而本项目不存
        //   任何历史状态（无云、无网络、只有当前值）。改用可推导的等价物：
        //   听剧每满 1 分钟得 1 点成长值，所以本周成长值 = 本周时长 / 60000，
        //   本周开始时的累计值 = 当前累计 - 本周新增。
        val weekGp = (weekMs / 60_000L).toInt()
        val stageThen = stageOfGp((currentGp - weekGp).coerceAtLeast(0L))
        val stageNow = stageOfGp(currentGp)

        return Summary(
            weekMs = weekMs,
            prevWeekMs = prevWeekMs,
            listenDays = weekDays.size,
            sessionCount = count,
            mahSaved = mahOf(weekMs),
            stageNow = stageNow,
            stageAtWeekStart = stageThen,
            weekGp = weekGp
        )
    }

    /** 本周一 00:00（本地时区）的毫秒数。nowMs 之前的最近一个周一。 */
    fun startOfWeek(nowMs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        // Calendar.DAY_OF_WEEK: 周日=1 … 周六=7。转成「距周一的偏移」。
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val daysSinceMonday = (dow + 5) % 7
        cal.add(java.util.Calendar.DAY_OF_MONTH, -daysSinceMonday)
        return cal.timeInMillis
    }
}
