package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AchievementHistoryTest {
    @Test fun `latest follows recording order rather than badge order or clock`() {
        val old = listOf(AchievementRecord("king", 200L))
        val history = AchievementHistory.record(old, setOf("king"), listOf("marathon"), 100L)
        assertEquals("marathon", AchievementHistory.latestRecorded(history)?.id)
        assertEquals(listOf("king", "marathon"), history.map { it.id })
    }

    @Test fun `legacy unlock dates stay unknown and rerecord never changes first time`() {
        val legacy = AchievementHistory.record(emptyList(), setOf("king"), listOf("marathon"), 50L)
        assertNull(legacy.first().recordedAt)
        assertEquals(50L, legacy.last().recordedAt)
        assertEquals(legacy, AchievementHistory.record(legacy, setOf("king", "marathon"), listOf("marathon"), 80L))
        assertNull(AchievementHistory.latestRecorded(listOf(AchievementRecord("king", null))))
    }

    @Test fun `malformed history skips invalid ids duplicates and non objects`() {
        val parsed = AchievementHistory.parse("""[null,3,{"id":"ghost","recorded_at":5},{"id":"week","recorded_at":"bad"},{"id":"week","recorded_at":10},{"id":"king","recorded_at":-3}]""")
        assertEquals(listOf(AchievementRecord("week", null), AchievementRecord("king", null)), parsed)
        assertTrue(AchievementHistory.parse("not json").isEmpty())
        assertEquals(parsed, AchievementHistory.parse(AchievementHistory.encode(parsed)))
    }

    @Test fun `old or malformed backup preserves local history and unrelated unlocks`() {
        val local = listOf(AchievementRecord("king", 10L))
        for (backup in listOf(JSONObject("{\"v\":1}"), JSONObject("{\"ach_history\":4,\"ach_unlocked\":[42,\"ghost\"]}"))) {
            val merged = AchievementHistory.mergeBackup(local, setOf("king"), backup)
            assertEquals(local, merged.records)
            assertEquals(setOf("king"), merged.unlocked)
        }
    }

    @Test fun `backup merge keeps local first record imports order and unknown legacy`() {
        val backup = JSONObject().put("v", 2).put("ach_unlocked", JSONArray(listOf("week", "c100")))
            .put("ach_history", JSONArray("""[{"id":"week","recorded_at":20},{"id":"king","recorded_at":30}]"""))
        val merged = AchievementHistory.mergeBackup(listOf(AchievementRecord("king", 10L)), setOf("king"), backup)
        assertEquals(listOf(AchievementRecord("king", 10L), AchievementRecord("week", 20L), AchievementRecord("c100", null)), merged.records)
        assertEquals(setOf("king", "week", "c100"), merged.unlocked)
    }

    @Test fun `progress exposes actual values and clamps only percentage`() {
        val p = Achievements.progress("week", 130L, 4_000_000L, 10, 4)
        assertEquals(10L, p.current)
        assertEquals(7L, p.target)
        assertEquals(100, p.percent)
        assertEquals(0L, Achievements.progress("marathon", -1L, -1L, -1, -1).current)
        assertEquals(3_600_000L, Achievements.progress("marathon", 0L, 0L, 0, 0).target)
    }
}
