package com.lujinyu.xiting

import org.junit.Assert.*
import org.junit.Test

class HistoryDeletionTest {
    @Test fun deletionPreservesArchivedMonthsAndRecomputesMaximum() {
        val old = Triple(8_000_000L, 12, 2_000_000L)
        val a = ListenSession(100, 200, 3_000_000, 0)
        val b = ListenSession(300, 400, 1_000_000, 1)
        val before = HistorySnapshot(12_000_000, 14, setOf(1L, 2L), mapOf("old" to old, "new" to Triple(4_000_000, 2, 3_000_000)))
        val after = deleteFromHistory(before, listOf(a,b), a, {"new"}, {2})!!
        assertEquals(9_000_000L, after.totalMs)
        assertEquals(13L, after.count)
        assertEquals(old, after.months["old"])
        assertEquals(2_000_000L, after.maxSingleMs)
        assertEquals(setOf(1L, 2L), after.days)
    }
    @Test fun lastSessionRemovesDayButKeepsArchivedDays() {
        val a = ListenSession(100, 200, 60_000, 0)
        val before = HistorySnapshot(120_000, 2, setOf(1L,2L), mapOf("old" to Triple(60_000,1,60_000), "new" to Triple(60_000,1,60_000)))
        val after = deleteFromHistory(before,listOf(a),a,{"new"},{2})!!
        assertEquals(setOf(1L),after.days)
        assertFalse(after.months.containsKey("new"))
    }
    @Test fun partialLegacyMonthCannotInventUnknownRunnerUpOrDayCounts() {
        val a = ListenSession(100,200,60_000,0)
        val before = HistorySnapshot(180_000,3,setOf(1L),mapOf("new" to Triple(180_000,3,60_000)))
        assertNull(deleteFromHistory(before,listOf(a),a,{"new"},{1}))
    }
    @Test fun duplicateRowsDeleteExactlyOneAndRetainDay() {
        val a = ListenSession(100,200,60_000,0)
        val before = HistorySnapshot(120_000,2,setOf(1L),mapOf("new" to Triple(120_000,2,60_000)))
        val after = deleteFromHistory(before,listOf(a,a),a,{"new"},{1})!!
        assertEquals(1L,after.count)
        assertEquals(setOf(1L),after.days)
    }
}
