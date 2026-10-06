package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 备份导入的会话筛选回归。
 *
 * 对应曾经的真实缺陷：restore() 把备份里的 sessions 数组原样落盘，
 * 超大或畸形的数组会在之后 SessionLog 解析时被主线程全量处理，
 * 轻则 ANR 重则 OOM。
 */
class BackupImportTest {

    private fun session(i: Int) = JSONObject().put("s", i).put("e", i + 1).put("d", i + 2).put("m", 0)

    private fun arrOf(vararg items: Any?): JSONArray {
        val a = JSONArray()
        items.forEach { a.put(it) }
        return a
    }

    @Test
    fun `结构完整的记录原样放行`() {
        val kept = filterImportSessions(arrOf(session(1), session(2)), 500)
        assertEquals(2, kept.length())
    }

    @Test
    fun `非对象元素被丢弃`() {
        val kept = filterImportSessions(arrOf(session(1), "junk", 42, session(2)), 500)
        assertEquals(2, kept.length())
    }

    @Test
    fun `缺字段的记录被丢弃`() {
        val partial = JSONObject().put("s", 1).put("e", 2)
        val kept = filterImportSessions(arrOf(session(1), partial), 500)
        assertEquals(1, kept.length())
    }

    @Test
    fun `超过上限时截断`() {
        val many = arrOf(*Array(1000) { session(it) })
        val kept = filterImportSessions(many, 500)
        assertEquals(500, kept.length())
    }

    @Test
    fun `上限为 0 时不保留任何记录`() {
        val kept = filterImportSessions(arrOf(session(1), session(2)), 0)
        assertEquals(0, kept.length())
    }

    @Test
    fun `空数组`() {
        assertEquals(0, filterImportSessions(JSONArray(), 500).length())
    }

    @Test
    fun `全是非对象时不抛异常`() {
        val kept = filterImportSessions(arrOf("a", "b", "c"), 500)
        assertEquals(0, kept.length())
    }
}