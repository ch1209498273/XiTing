package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 备份导入的会话筛选回归，守的是**恢复与摘要实际共用的**那条 `validBackupSessions`。
 *
 * 这组用例原来压在 `filterImportSessions` 上，而恢复路径早在 v6.0.0（3b5db09）就改走
 * `validBackupSessions` —— 等于 7 条用例一直在测一条没人走的路，真正会落盘的那条反而没测。
 * 现在转守活路径，并补上它特有的判据（时长区间、类型必须是整数、多余字段不带入）。
 *
 * 对应的历史真实缺陷：restore() 曾把备份里的 sessions 数组原样落盘，
 * 超大或畸形的数组会在之后 SessionLog 解析时被主线程全量处理，轻则 ANR 重则 OOM。
 */
class BackupImportTest {

    // 一条结构完整、时长合法的会话；i 用来制造互不相同的记录
    private fun session(i: Int) = JSONObject()
        .put("s", 1_700_000_000_000L + i)
        .put("e", 1_700_000_060_000L + i)
        .put("d", 60_000L + i)
        .put("m", 0)

    private fun arrOf(vararg items: Any?): JSONArray {
        val a = JSONArray()
        items.forEach { a.put(it) }
        return a
    }

    @Test
    fun `结构完整的记录原样放行`() {
        assertEquals(2, validBackupSessions(arrOf(session(1), session(2))).length())
    }

    @Test
    fun `非对象元素被丢弃`() {
        assertEquals(2, validBackupSessions(arrOf(session(1), "junk", 42, session(2))).length())
    }

    @Test
    fun `缺字段的记录被丢弃`() {
        val partial = JSONObject().put("s", 1).put("e", 2)
        assertEquals(1, validBackupSessions(arrOf(session(1), partial)).length())
    }

    @Test
    fun `超过 500 项时截断`() {
        val many = arrOf(*Array(1000) { session(it) })
        assertEquals(500, validBackupSessions(many).length())
    }

    @Test
    fun `空数组`() {
        assertEquals(0, validBackupSessions(JSONArray()).length())
    }

    @Test
    fun `null 输入不抛异常`() {
        assertEquals(0, validBackupSessions(null).length())
    }

    @Test
    fun `全是非对象时不抛异常`() {
        assertEquals(0, validBackupSessions(arrOf("a", "b", "c")).length())
    }

    @Test
    fun `时长不足一分钟被丢弃`() {
        val short = JSONObject().put("s", 1L).put("e", 2L).put("d", 59_999L).put("m", 0)
        assertEquals(0, validBackupSessions(arrOf(short)).length())
    }

    @Test
    fun `时长超过一天被丢弃`() {
        val long = JSONObject().put("s", 1L).put("e", 2L).put("d", 86_400_001L).put("m", 0)
        assertEquals(0, validBackupSessions(arrOf(long)).length())
    }

    @Test
    fun `起止时间非正数被丢弃`() {
        val zeroStart = JSONObject().put("s", 0L).put("e", 2L).put("d", 60_000L).put("m", 0)
        val negativeEnd = JSONObject().put("s", 1L).put("e", -2L).put("d", 60_000L).put("m", 0)
        assertEquals(0, validBackupSessions(arrOf(zeroStart, negativeEnd)).length())
    }

    @Test
    fun `模式超出允许范围被丢弃`() {
        val badMode = JSONObject().put("s", 1L).put("e", 2L).put("d", 60_000L).put("m", 2)
        assertEquals(0, validBackupSessions(arrOf(badMode)).length())
    }

    // backupInteger 只认 Long/Int：备份里写成字符串数字也不能放过，否则坏值会一路带到落盘
    @Test
    fun `字符串数字不被当作整数接受`() {
        val asText = JSONObject().put("s", 1L).put("e", 2L).put("d", "60000").put("m", 0)
        assertEquals(0, validBackupSessions(arrOf(asText)).length())
    }

    @Test
    fun `墙钟时间倒走不影响保留`() {
        // e < s 是合法场景（时钟被改过），时长才是单调量
        val rewound = JSONObject().put("s", 5_000L).put("e", 1_000L).put("d", 60_000L).put("m", 1)
        assertEquals(1, validBackupSessions(arrOf(rewound)).length())
    }

    @Test
    fun `多余字段不进入恢复结果`() {
        val dirty = session(1).put("junk", "x")
        val kept = validBackupSessions(arrOf(dirty))
        assertEquals(1, kept.length())
        assertFalse(kept.getJSONObject(0).has("junk"))
    }
}
