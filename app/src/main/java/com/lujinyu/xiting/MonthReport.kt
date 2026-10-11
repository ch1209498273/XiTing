// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import java.util.Calendar

/**
 * 月报聚合（纯计算，可单测）。
 *
 * 会话按**开始时间**的月归属（跨月会话算给开始那天，与列表/连续天数的日归属一致）。
 * 上月数据只取总时长做环比；无上月记录时 prevTotalMs=0，海报不放环比行。
 *
 * ⚠ 年度口径依赖全部历史，而 sessions.json 只保留最近 500 条（SessionLog.MAX）——
 * 年报必须等 #3 聚合重设计（30 天明细 + 全量聚合块）落地后再做，月报 500 条足够。
 */
data class MonthStats(
    val totalMs: Long,
    val count: Int,
    val maxSingleMs: Long,
    val listenDays: Int,
    val prevTotalMs: Long
)

fun monthStats(sessions: List<ListenSession>, now: Long): MonthStats {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val month = cal.get(Calendar.MONTH)
    val year = cal.get(Calendar.YEAR)
    val prevMonth = if (month == Calendar.JANUARY) Calendar.DECEMBER else month - 1
    val prevYear = if (month == Calendar.JANUARY) year - 1 else year

    var total = 0L
    var count = 0
    var max = 0L
    var prevTotal = 0L
    val days = HashSet<Int>()
    for (s in sessions) {
        val c = Calendar.getInstance().apply { timeInMillis = s.start }
        val sy = c.get(Calendar.YEAR)
        val sm = c.get(Calendar.MONTH)
        if (sy == year && sm == month) {
            total += s.durationMs
            count++
            if (s.durationMs > max) max = s.durationMs
            days.add(Streaks.epochDayOf(s.start).toInt())
        } else if (sy == prevYear && sm == prevMonth) {
            prevTotal += s.durationMs
        }
    }
    return MonthStats(total, count, max, days.size, prevTotal)
}
