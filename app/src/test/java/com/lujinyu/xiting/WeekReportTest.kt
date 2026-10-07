package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 本周小结。
 *
 * 重点钉死两件容易算错的事：
 * 1. **周边界**。按「本周一 00:00」而不是「最近 7 天」——用户在周日看到的
 *    「本周」应该只有 1 天，滚动的 7 天会差出一整天。
 * 2. **精灵本周是否进化**用「当前累计 - 本周新增」推导，因为本项目
 *    不存任何历史快照（无云、无网络、只有当前值）。
 */
class WeekReportTest {

    private fun inTz(tz: String, block: () -> Unit) {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(tz))
        try { block() } finally { TimeZone.setDefault(old) }
    }

    private fun at(y: Int, m: Int, d: Int, hh: Int = 12, mm: Int = 0): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear(); set(y, m - 1, d, hh, mm, 0)
        }.timeInMillis

    private fun sess(start: Long, min: Long) = ListenSession(start, start + min * 60_000, min * 60_000, 0)

    private fun sum(
        sessions: List<ListenSession>, now: Long, gp: Long = 1000L
    ) = WeekReport.summarize(
        sessions, now, gp,
        mahOf = { (it / 3_600_000.0 * 100).toInt() },
        stageOfGp = { PetView.stageOf(it) }
    )

    // ── 周边界 ──

    @Test
    fun `周一零点就是本周起点`() {
        inTz("Asia/Shanghai") {
            // 2026-10-05 是周一
            val mon = at(2026, 10, 5, 0, 0)
            assertEquals(mon, WeekReport.startOfWeek(at(2026, 10, 5, 0, 0)))
            assertEquals(mon, WeekReport.startOfWeek(at(2026, 10, 5, 23, 59)))
            assertEquals(mon, WeekReport.startOfWeek(at(2026, 10, 11, 23, 59)))
        }
    }

    @Test
    fun `周日的本周只回溯到本周一而不是七天前`() {
        inTz("Asia/Shanghai") {
            val sun = at(2026, 10, 11, 15, 0)
            val start = WeekReport.startOfWeek(sun)
            // 起点应在 10-05（周一）那一周的零点，即 6 天前
            val sixDaysBefore = at(2026, 10, 5, 0, 0)
            assertEquals(sixDaysBefore, start)
        }
    }

    @Test
    fun `起点总是零点整`() {
        inTz("Asia/Shanghai") {
            val s = WeekReport.startOfWeek(at(2026, 10, 8, 17, 43))
            val cal = Calendar.getInstance().apply { timeInMillis = s }
            assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, cal.get(Calendar.MINUTE))
            assertEquals(0, cal.get(Calendar.SECOND))
        }
    }

    // ── 时长归周 ──

    @Test
    fun `本周与上周的会话分开计数`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sess(at(2026, 10, 7), 60),      // 本周三 60 分钟
                sess(at(2026, 10, 8), 30),      // 本周四 30 分钟
                sess(at(2026, 10, 3), 45)       // 上周六 45 分钟（属于上周）
            )
            val r = sum(s, at(2026, 10, 8, 20, 0))
            assertEquals(90 * 60_000L, r.weekMs)
            assertEquals(45 * 60_000L, r.prevWeekMs)
        }
    }

    @Test
    fun `上周的时长计入 prevWeek 而非本周`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sess(at(2026, 10, 8), 30),      // 本周
                sess(at(2026, 10, 3), 60)       // 上周六（属于上周）
            )
            val r = sum(s, at(2026, 10, 8, 20, 0))
            assertEquals(30 * 60_000L, r.weekMs)
            assertEquals(60 * 60_000L, r.prevWeekMs)
        }
    }

    @Test
    fun `比上周多时 delta 为正`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sess(at(2026, 10, 8), 90),
                sess(at(2026, 10, 3), 30)
            )
            val r = sum(s, at(2026, 10, 8, 20, 0))
            assertTrue(r.deltaMs > 0)
            assertEquals(2.0f, r.deltaRatio()!!, 0.01f)
        }
    }

    @Test
    fun `上周为零时不报百分比（否则除零）`() {
        inTz("Asia/Shanghai") {
            val r = sum(listOf(sess(at(2026, 10, 8), 30)), at(2026, 10, 8, 20, 0))
            assertNull(r.deltaRatio())
        }
    }

    @Test
    fun `比上周少时 delta 为负且比率为负`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sess(at(2026, 10, 8), 30),
                sess(at(2026, 10, 3), 60)
            )
            val r = sum(s, at(2026, 10, 8, 20, 0))
            assertTrue(r.deltaMs < 0)
            assertTrue(r.deltaRatio()!! < 0f)
        }
    }

    // ── 天数与成长值 ──

    @Test
    fun `同一天多次只算一天`() {
        inTz("Asia/Shanghai") {
            val s = listOf(sess(at(2026, 10, 7, 8), 30), sess(at(2026, 10, 7, 20), 40))
            assertEquals(1, sum(s, at(2026, 10, 7, 23, 0)).listenDays)
        }
    }

    @Test
    fun `本周成长值按时长换算 每分钟一点`() {
        inTz("Asia/Shanghai") {
            val r = sum(listOf(sess(at(2026, 10, 8), 90)), at(2026, 10, 8, 20, 0))
            assertEquals(90, r.weekGp)
        }
    }

    @Test
    fun `本周时长不足一分钟不给成长值`() {
        inTz("Asia/Shanghai") {
            val s = listOf(ListenSession(at(2026, 10, 8), at(2026, 10, 8) + 30_000, 30_000, 0))
            assertEquals(0, sum(s, at(2026, 10, 8, 20, 0)).weekGp)
        }
    }

    @Test
    fun `本周未进化时起止形态相同`() {
        inTz("Asia/Shanghai") {
            val r = sum(listOf(sess(at(2026, 10, 8), 30)), at(2026, 10, 8, 20, 0), gp = 5L)
            assertEquals(r.stageAtWeekStart, r.stageNow)
        }
    }

    @Test
    fun `本周成长值跨过门槛则起止形态不同`() {
        inTz("Asia/Shanghai") {
            // THRESHOLDS[1] = 30，本周 300 分钟 → +300 点，肯定跨过
            val r = sum(listOf(sess(at(2026, 10, 8), 300)), at(2026, 10, 8, 20, 0), gp = 30L)
            assertTrue("应有进化", r.stageNow > r.stageAtWeekStart)
        }
    }

    @Test
    fun `空记录不炸且全为 0`() {
        inTz("Asia/Shanghai") {
            val r = sum(emptyList(), at(2026, 10, 8, 20, 0))
            assertEquals(0L, r.weekMs)
            assertEquals(0, r.listenDays)
            assertEquals(0, r.sessionCount)
            assertEquals(0, r.weekGp)
        }
    }
}
