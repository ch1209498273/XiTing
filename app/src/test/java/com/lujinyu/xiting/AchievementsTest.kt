package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就摘要回归。
 *
 * 对应曾经的真实缺陷：ach_unlocked 是持久化集合，早期版本改过成就 id、
 * 或文件被外部改写都可能留下 ALL 里不存在的残留项。原实现
 * `ALL.lastOrNull { it.id in got }!!` 在「集合非空但全是残留 id」时
 * latest 为 null，统计页当场 NPE。
 */
class AchievementsTest {

    @Test
    fun `全空时返回空列表`() {
        assertTrue(knownUnlocked(emptySet()).isEmpty())
    }

    @Test
    fun `全是残留 id 时返回空列表而非抛异常`() {
        val got = setOf("legacy_removed_a", "legacy_removed_b", "totally_unknown_c")
        val known = knownUnlocked(got)
        assertTrue("残留 id 必须被忽略", known.isEmpty())
        assertEquals(0, known.size)
    }

    @Test
    fun `残留与正常 id 混合时只保留正常 id`() {
        // 残留 id 模拟两类真实情况：早期版本已砍掉的成就、以及文件被外部改写
        val got = setOf("legacy_removed_a", "marathon", "another_ghost", "week")
        val known = knownUnlocked(got)
        assertEquals(2, known.size)
        assertEquals(listOf("marathon", "week"), known.map { it.id })
    }

    @Test
    fun `保持 ALL 的声明顺序，latest 取最后一条`() {
        val got = setOf("king", "marathon")
        val known = knownUnlocked(got)
        assertEquals("marathon", known.first().id)
        assertEquals("king", known.last().id)
    }

    @Test
    fun `砍掉旧成就后残留集合不会让统计虚高`() {
        // 老用户 prefs 里仍留着 first/h1/h10/h50 这些已删除的 id，
        // 统计条不能把它们算进去，否则会显示「6/4」这种数字
        assertEquals(2, knownUnlocked(setOf("first", "h1", "h10", "h50", "week", "c100")).size)
        assertEquals(0, knownUnlocked(setOf("first", "h1", "h10", "h50")).size)
        // 且已删的 id 不会残留在 ALL 里
        val ids = Achievements.ALL.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("marathon", "week", "c100", "king")))
        assertTrue(ids.none { it in setOf("first", "h1", "h10", "h50") })
    }

    @Test
    fun `全部解锁时数量等于 ALL 大小`() {
        val all = Achievements.ALL.map { it.id }.toSet()
        assertEquals(Achievements.ALL.size, knownUnlocked(all).size)
    }
}