package com.lujinyu.xiting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ───────── 徽章化：徽章格位与 ALL 严格一一对应 ─────────

    @Test
    fun `每条成就都有徽章且四枚互不重复`() {
        val badges = Achievements.ALL.map { it.badge }
        assertEquals("每条成就必须配一枚徽章", Achievements.ALL.size, badges.size)
        assertEquals("四枚徽章不能撞车，否则精灵身上会出现两个一样的图案", badges.size, badges.toSet().size)
    }

    @Test
    fun `badgeList 顺序与 ALL 一致`() {
        val list = badgeList(emptySet())
        assertEquals(Achievements.ALL.size, list.size)
        list.forEachIndexed { i, b -> assertEquals(Achievements.ALL[i].badge, b.kind) }
    }

    @Test
    fun `badgeList 按 id 点亮，不受残留 id 影响`() {
        val list = badgeList(setOf("marathon", "legacy_removed_a", "h50"))
        // 格子顺序固定为 marathon/week/c100/king → 只有第 1 格亮
        assertEquals(listOf(true, false, false, false), list.map { it.unlocked })
        assertEquals(Achievements.ALL.size, list.size)
    }

    @Test
    fun `badgeList 全解锁时全部点亮`() {
        val all = Achievements.ALL.map { it.id }.toSet()
        assertTrue(badgeList(all).all { it.unlocked })
    }

    // ───────── 2026-10-10 口径拍板：「持之以恒」= 连续 7 天（不再用累计日期数） ─────────

    @Test
    fun `持之以恒按历史最长连续解锁 边界在 7 天`() {
        assertFalse("6 天不该亮", Achievements.shouldUnlock(0, 0L, 6, 0).contains("week"))
        assertTrue("7 天必须亮", Achievements.shouldUnlock(0, 0L, 7, 0).contains("week"))
        assertTrue("更长的连续也必须亮", Achievements.shouldUnlock(0, 0L, 30, 0).contains("week"))
    }

    @Test
    fun `其余三条阈值锚定 马拉松1小时 百次 王者阶段4`() {
        assertTrue("每项都差一点时一条都不该亮", Achievements.shouldUnlock(99, 3_599_999L, 6, 3).isEmpty())
        assertTrue("马拉松", "marathon" in Achievements.shouldUnlock(0, 3_600_000L, 0, 0))
        assertTrue("百次", "c100" in Achievements.shouldUnlock(100, 0L, 0, 0))
        assertTrue("王者", "king" in Achievements.shouldUnlock(0, 0L, 0, 4))
    }
}