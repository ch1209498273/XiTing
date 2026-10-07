package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * 连续天数 / 听剧日去重。
 *
 * 为什么这些要钉死：
 * 1. **Streaks 决定首页「连续 N 天」和皮肤解锁**，错了用户会看到自相矛盾的数字。
 * 2. 旧实现用 `SimpleDateFormat("yyyyMMdd").format()` 再 distinct()，
 *    而格式化结果**受 locale 影响**：th-TH 用佛历（2569 而非 2026），
 *    阿拉伯语等 locale 可能输出非 ASCII 数字 —— 同一天被 distinct 成两天，
 *    「持之以恒」成就因此提前或延后解锁。改用 epochDayOf 后此风险消失。
 * 3. `compute` 里的时区/跨天逻辑以前一行测试都没有。
 *
 * 测试里显式固定 TimeZone，避免结果随 CI 机器时区漂移。
 */
class StreaksTest {

    private fun sessionAt(ms: Long) = ListenSession(ms, ms + 60_000, 60_000, 0)

    /** 在指定时区下跑，避免结果依赖机器默认时区 */
    private fun <T> inTz(tz: String, block: () -> T): T {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(tz))
        return try { block() } finally { TimeZone.setDefault(old) }
    }

    private fun ms(y: Int, m: Int, d: Int, hh: Int = 12, mm: Int = 0): Long =
        java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(y, m - 1, d, hh, mm, 0)
        }.timeInMillis

    // ── epochDayOf：同一天必须映射到同一个值 ──

    @Test
    fun `同一天的早晚时刻归为同一天`() {
        inTz("Asia/Shanghai") {
            val early = Streaks.epochDayOf(ms(2026, 10, 7, 0, 1))
            val late = Streaks.epochDayOf(ms(2026, 10, 7, 23, 59))
            assertEquals(early, late)
        }
    }

    @Test
    fun `相邻两天是不同的天`() {
        inTz("Asia/Shanghai") {
            val d7 = Streaks.epochDayOf(ms(2026, 10, 7, 12))
            val d8 = Streaks.epochDayOf(ms(2026, 10, 8, 12))
            assertEquals(d7 + 1, d8)
        }
    }

    @Test
    fun `日界线是本地零点而不是 UTC 零点`() {
        inTz("Asia/Shanghai") {
            // UTC 10-06 16:30 = 上海 10-07 00:30  → 属于 10-07
            val a = utc(2026, 10, 6, 16, 30)
            // UTC 10-06 17:30 = 上海 10-07 01:30  → 也属于 10-07
            val b = utc(2026, 10, 6, 17, 30)
            // UTC 10-06 15:30 = 上海 10-06 23:30  → 属于 10-06（不同的天）
            val c = utc(2026, 10, 6, 15, 30)
            assertEquals(Streaks.epochDayOf(a), Streaks.epochDayOf(b))
            assertTrue("15:30Z 已过上海 15 日的日界线", Streaks.epochDayOf(c) != Streaks.epochDayOf(a))
        }
    }

    /** 构造 UTC 时刻的毫秒 */
    private fun utc(y: Int, m: Int, d: Int, hh: Int, mm: Int): Long =
        java.time.LocalDateTime.of(y, m, d, hh, mm)
            .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `时区不同会得到不同的天号 - 因此实现不能硬编码 UTC`() {
        // 上海 10-08 00:30（UTC+8）= UTC 10-07 16:30
        val t = utc(2026, 10, 7, 16, 30)
        val sh = inTz("Asia/Shanghai") { Streaks.epochDayOf(t) }
        val la = inTz("America/Los_Angeles") { Streaks.epochDayOf(t) }
        // 上海已入 10-08，洛杉矶（UTC-7）还是 10-07
        assertTrue("sh=$sh la=$la", sh != la)
        assertEquals(sh, la + 1)
    }

    // ── distinctListenDays ──

    @Test
    fun `同一天的多条会话只算一天`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sessionAt(ms(2026, 10, 7, 8)),
                sessionAt(ms(2026, 10, 7, 12)),
                sessionAt(ms(2026, 10, 7, 23))
            )
            assertEquals(1, Streaks.distinctListenDays(s))
        }
    }

    @Test
    fun `跨天分别计数`() {
        inTz("Asia/Shanghai") {
            val s = listOf(
                sessionAt(ms(2026, 10, 6, 23, 50)),
                sessionAt(ms(2026, 10, 7, 0, 10))
            )
            assertEquals(2, Streaks.distinctListenDays(s))
        }
    }

    @Test
    fun `空列表为 0 天`() {
        assertEquals(0, Streaks.distinctListenDays(emptyList()))
    }

    // ── compute：连续性 ──

    @Test
    fun `今天还没听时 从昨天往前数且不算今天`() {
        inTz("Asia/Shanghai") {
            val today = Streaks.epochDayOf(System.currentTimeMillis())
            // 造「昨天、前天、大前天」三条（用相对天号，不依赖具体日期）
            val base = today * 86_400_000L
            val s = listOf(
                sessionAt(base - 1 * 86_400_000L),
                sessionAt(base - 2 * 86_400_000L),
                sessionAt(base - 3 * 86_400_000L)
            )
            val info = Streaks.compute(s)
            assertEquals("今天没听，current 应为 3", 3, info.current)
            assertTrue("今天没听", !info.todayDone)
        }
    }

    @Test
    fun `今天听了则 current 含今天`() {
        inTz("Asia/Shanghai") {
            val now = System.currentTimeMillis()
            val s = listOf(
                sessionAt(now - 60_000),
                sessionAt(now - 86_400_000L),
                sessionAt(now - 2 * 86_400_000L)
            )
            val info = Streaks.compute(s)
            assertEquals(3, info.current)
            assertTrue(info.todayDone)
        }
    }

    @Test
    fun `中断一天则连续计数断开`() {
        inTz("Asia/Shanghai") {
            val now = System.currentTimeMillis()
            val d = 86_400_000L
            // 今天、昨天有；前天、大前天空缺 → current = 2
            val s = listOf(sessionAt(now - 60_000), sessionAt(now - d))
            assertEquals(2, Streaks.compute(s).current)
        }
    }

    @Test
    fun `best 记录历史最长 即使已中断`() {
        inTz("Asia/Shanghai") {
            val now = System.currentTimeMillis()
            val d = 86_400_000L
            val s = listOf(
                sessionAt(now - 60_000),            // 今天
                sessionAt(now - 3 * d),             // 3 天前
                sessionAt(now - 4 * d),             // 4 天前
                sessionAt(now - 5 * d)              // 5 天前 → 连续 3
            )
            val info = Streaks.compute(s)
            assertEquals(1, info.current)
            assertEquals("历史上连续过 3 天", 3, info.best)
        }
    }

    @Test
    fun `无记录时全为 0`() {
        val info = Streaks.compute(emptyList())
        assertEquals(0, info.current)
        assertEquals(0, info.best)
        assertTrue(!info.todayDone)
    }
}
