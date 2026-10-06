package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话存档解析容错回归。
 *
 * 对应曾经的真实缺陷：解析用 map()，任意一条损坏记录就让整个 map 抛出，
 * 外层 catch 返回 emptyList()，随后 add() 拿着空列表做读-改-写，
 * 把 500 条历史覆盖成仅剩 1 条。
 */
class SessionLogParseTest {

    private fun session(start: Long, durMs: Long = 3_600_000L) =
        """{"s":$start,"e":${start + durMs},"d":$durMs,"m":0}"""

    @Test
    fun `全部合法时原样读出`() {
        val text = "[" + listOf(session(1L), session(2L), session(3L)).joinToString(",") + "]"
        val r = parseSessions(text)
        assertEquals(3, r.items.size)
        assertEquals(0, r.skipped)
        assertEquals(1L, r.items[0].start)
    }

    @Test
    fun `缺字段的记录只丢自己，其余照常读出`() {
        val text = "[${session(1L)},{\"s\":111,\"e\":222},${session(3L)}]"
        val r = parseSessions(text)
        assertEquals("两条合法记录必须保住", 2, r.items.size)
        assertEquals(1, r.skipped)
        assertEquals(listOf(1L, 3L), r.items.map { it.start })
    }

    @Test
    fun `类型错误的记录只丢自己`() {
        val text = "[${session(1L)},{\"s\":333,\"e\":444,\"d\":\"not-a-number\",\"m\":0},${session(3L)}]"
        val r = parseSessions(text)
        assertEquals(2, r.items.size)
        assertEquals(1, r.skipped)
    }

    @Test
    fun `空对象与非对象元素都只丢自己`() {
        val text = "[${session(1L)},{},\"just-a-string\",${session(4L)}]"
        val r = parseSessions(text)
        assertEquals(2, r.items.size)
        assertEquals(2, r.skipped)
    }

    @Test
    fun `全损坏时返回空且计数正确（不会静默当成成功）`() {
        val r = parseSessions("[{},\"x\",123]")
        assertTrue(r.items.isEmpty())
        assertEquals(3, r.skipped)
    }

    @Test
    fun `空数组`() {
        val r = parseSessions("[]")
        assertTrue(r.items.isEmpty())
        assertEquals(0, r.skipped)
    }

    @Test(expected = Exception::class)
    fun `语法级非法交给调用方抛出不吞`() {
        // 整体语法非法不是 mapNotNull 能救的——解析器自己就失败，
        // 必须冒泡给 read() 走 preserveCorrupt 留底策略
        parseSessions("[{ broken not json")
    }

    @Test
    fun `字段值完整保留`() {
        val r = parseSessions("""[{"s":100,"e":200,"d":300,"m":1}]""")
        val s = r.items.single()
        assertEquals(100L, s.start)
        assertEquals(200L, s.end)
        assertEquals(300L, s.durationMs)
        assertEquals(1, s.mode)
    }
}