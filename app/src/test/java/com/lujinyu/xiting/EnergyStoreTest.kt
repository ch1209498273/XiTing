package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能量体系回归：解析容错、溢出饱和、容量计算。
 */
class EnergyStoreTest {

    private fun ball(id: Long, v: Int, e: Long) = """{"id":$id,"v":$v,"e":$e}"""

    // ---------- 解析容错（与 SessionLog 同一类缺陷）----------

    @Test
    fun `单条脏记录只丢自己，其余能量球保住`() {
        val raw = "[${ball(1, 5, 9_999_999_999_999)},{\"id\":2},${ball(3, 7, 9_999_999_999_999)}]"
        val r = parsePending(raw)
        assertEquals("两条合法能量球必须保住", 2, r.items.size)
        assertEquals(1, r.skipped)
    }

    @Test
    fun `空对象与非对象元素只丢自己`() {
        val raw = "[${ball(1, 5, 9_999_999_999_999)},{},\"x\",${ball(4, 2, 9_999_999_999_999)}]"
        val r = parsePending(raw)
        assertEquals(2, r.items.size)
        assertEquals(2, r.skipped)
    }

    @Test
    fun `空数组`() {
        val r = parsePending("[]")
        assertTrue(r.items.isEmpty())
        assertEquals(0, r.skipped)
    }

    @Test(expected = Exception::class)
    fun `语法级非法交给调用方`() {
        parsePending("[not json")
    }

    // ---------- 容量计算（防 Int 溢出）----------

    @Test
    fun `空列表时 room 等于上限`() {
        assertEquals(200, roomFor(emptyList(), 200))
    }

    @Test
    fun `已用一半时 room 为剩余`() {
        val existing = List(100) { PendingEnergy(it.toLong(), 1, 0L) }
        assertEquals(100, roomFor(existing, 200))
    }

    @Test
    fun `满格时 room 为 0`() {
        val existing = List(200) { PendingEnergy(it.toLong(), 1, 0L) }
        assertEquals(0, roomFor(existing, 200))
    }

    @Test
    fun `超量时 room 不会变成负数`() {
        val existing = List(300) { PendingEnergy(it.toLong(), 1, 0L) }
        assertEquals(0, roomFor(existing, 200))
    }

    @Test
    fun `单条脏 value 接近 Int MAX 时不会溢出成负`() {
        // 旧实现用 Int 累加：2 * Int.MAX 溢出成 -2，room 变成 202 而突破 200 上限
        val existing = listOf(
            PendingEnergy(1L, Int.MAX_VALUE, 0L),
            PendingEnergy(2L, Int.MAX_VALUE, 0L)
        )
        assertEquals("溢出防护：不得返回正数让上限失效", 0, roomFor(existing, 200))
    }

    // ---------- 成长值饱和累加 ----------

    @Test
    fun `正常累加`() {
        assertEquals(250, saturatingAdd(50, 200L))
    }

    @Test
    fun `累加溢出饱和到 Int MAX 而不是负数`() {
        assertEquals(Int.MAX_VALUE, saturatingAdd(Int.MAX_VALUE - 1, 1000L))
    }

    @Test
    fun `负向累加不会跌破 0`() {
        assertEquals(0, saturatingAdd(10, -100L))
    }

    @Test
    fun `从 0 开始加脏数据中的 Int MAX`() {
        assertEquals(Int.MAX_VALUE, saturatingAdd(0, Int.MAX_VALUE.toLong()))
    }
}