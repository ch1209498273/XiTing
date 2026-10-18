package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSummaryTest {
    @Test fun `unrelated json cannot clear local growth through restore`() {
        assertFalse(isReadableBackup(JSONObject("{}")))
        assertFalse(isReadableBackup(JSONObject().put("gp","12").put("sessions",JSONArray())))
        assertTrue(isReadableBackup(JSONObject().put("v",1).put("gp",12).put("sessions",JSONArray())))
        assertTrue(isReadableBackup(JSONObject().put("v",2).put("gp",0).put("sessions",JSONArray())))
    }
    private fun session(duration: Long = 60_000L) = JSONObject()
        .put("s", 1_700_000_000_000L).put("e", 1_700_000_060_000L).put("d", duration).put("m", 0)

    @Test fun `summary counts exactly restorable sessions and supports v1`() {
        val obj = JSONObject().put("v", 1).put("gp", 120)
            .put("sessions", JSONArray().put(session()).put(session(1)).put("junk")
                .put(JSONObject().put("s", "broken").put("e", 1).put("d", 60_000).put("m", 0)))
        val summary = summarizeBackup(obj)
        assertEquals(1, summary.version)
        assertEquals(120, summary.growth)
        assertEquals(1, summary.validSessionCount)
        assertEquals(summary.validSessionCount, validBackupSessions(obj.optJSONArray("sessions")).length())
    }

    @Test fun `malformed optional fields do not invent a version or negative growth`() {
        val summary = summarizeBackup(JSONObject("""{"v":"garbage","gp":-7,"sessions":false}"""))
        assertNull(summary.version)
        assertEquals(0, summary.growth)
        assertEquals(0, summary.validSessionCount)
    }

    @Test fun `preview and restore use the same first 500 entry limit`() {
        val arr = JSONArray()
        repeat(499) { arr.put(session()) }
        arr.put("invalid at boundary").put(session())
        assertEquals(499, summarizeBackup(JSONObject().put("sessions", arr)).validSessionCount)
    }

    @Test fun `invalid mode timestamps and duration are skipped independently`() {
        val arr = JSONArray().put(session()).put(session().put("m", 99))
            .put(session().put("e", 0)).put(session().put("s", -1))
            .put(session(86_400_001L)).put(session().put("d", "60000"))
        assertEquals(1, validBackupSessions(arr).length())
        assertEquals(0, validBackupSessions(null).length())
    }
    @Test fun `wall clock rollback preserves a valid monotonic listening duration`() {
        val rollback=session().put("e",1_699_999_999_000L)
        assertEquals(1,validBackupSessions(JSONArray().put(rollback)).length())
    }
}
