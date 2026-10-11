package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 月报聚合（monthStats）。
 *
 * 钉死：月归属按会话**开始时间**、同一天多次去重 listenDays、上月环比口径、
 * 跨年一月（上月 = 去年十二月）。时区用固定时区钉死，与 WeekReportTest 同款。
 */
class MonthReportTest {

    private fun inTz(tz: String, block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(tz))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    private fun at(y: Int, mo: Int, d: Int, hh: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, hh, 0, 0) }.timeInMillis

    private fun sess(y: Int, mo: Int, d: Int, min: Long) =
        ListenSession(at(y, mo, d), at(y, mo, d) + min * 60_000, min * 60_000, 0)

    @Test
    fun monthAggregationAndPrevMonthComparison() = inTz("Asia/Shanghai") {
        val s = listOf(
            sess(2026, 10, 3, 60), sess(2026, 10, 3, 30), // 同一天两次：listenDays 去重
            sess(2026, 10, 9, 90),
            sess(2026, 9, 20, 45)                          // 上月：只进环比
        )
        val st = monthStats(s, at(2026, 10, 15))
        assertEquals(180L, st.totalMs / 60_000)
        assertEquals(3, st.count)
        assertEquals(90L, st.maxSingleMs / 60_000)
        assertEquals(2, st.listenDays)
        assertEquals(45L, st.prevTotalMs / 60_000)
    }

    @Test
    fun januaryPrevMonthIsDecemberOfLastYear() = inTz("Asia/Shanghai") {
        val s = listOf(sess(2026, 1, 5, 60), sess(2025, 12, 30, 30))
        val st = monthStats(s, at(2026, 1, 10))
        assertEquals(60L, st.totalMs / 60_000)
        assertEquals(30L, st.prevTotalMs / 60_000)
    }

    @Test
    fun emptyMonthYieldsZeros() = inTz("Asia/Shanghai") {
        val st = monthStats(listOf(sess(2026, 8, 3, 60)), at(2026, 11, 2))
        assertEquals(0L, st.totalMs)
        assertEquals(0, st.count)
        assertEquals(0L, st.prevTotalMs)
    }
}
