package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompleteBackupTest {
    @Test fun `unbounded or inconsistent lifetime totals cannot bypass detail rebuild`() {
        val raw=JSONObject().put(Prefs.STATS_TOTAL_MS,Long.MAX_VALUE).put(Prefs.STATS_TOTAL_COUNT,1)
            .put(Prefs.STATS_MAX_SINGLE,60_000).put(Prefs.LISTEN_DAYS,JSONArray().put("1"))
            .put(Prefs.MONTH_STATS,JSONArray().put("2026-10:60000:1:60000"))
        assertFalse(CompleteBackup.hasAggregate(raw))
        raw.put(Prefs.STATS_TOTAL_MS,60000)
        assertTrue(CompleteBackup.hasAggregate(raw))
        raw.put(Prefs.MONTH_STATS,JSONArray().put("broken"))
        assertFalse(CompleteBackup.hasAggregate(raw))
    }
    private fun sanitize(raw:JSONArray,now:Long):JSONArray {
        val type=Class.forName("com.lujinyu.xiting.CompleteBackup")
        return type.getMethod("pending",JSONArray::class.java,Long::class.javaPrimitiveType)
            .invoke(type.getField("INSTANCE").get(null),raw,now) as JSONArray
    }
    @Test fun `pending import keeps original expiry skips expired duplicates and invalid rewards`() {
        val raw=JSONArray("""[{"id":1,"v":120,"e":2000},{"id":1,"v":20,"e":2000},{"id":2,"v":20,"e":999},{"id":3,"v":-2,"e":2000},{"id":4,"v":100,"e":2000}]""")
        val result=sanitize(raw,1000)
        assertEquals(2,result.length());assertEquals(2000L,result.getJSONObject(0).getLong("e"))
        assertEquals(80,result.getJSONObject(1).getInt("v"))
    }
    @Test fun `strings floats and unbounded timestamps cannot manufacture energy`() {
        assertEquals(0,sanitize(JSONArray("""[{"id":"1","v":10,"e":2000},{"id":2,"v":1.5,"e":2000},{"id":3,"v":10,"e":9223372036854775807}]"""),1000).length())
    }
    @Test fun `recovery never treats a different boot or regressed uptime as a live timer`() {
        val type=Class.forName("com.lujinyu.xiting.RecoveryPolicy")
        val method=type.getMethod("remaining",Long::class.javaPrimitiveType,Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
        val instance=type.getField("INSTANCE").get(null)
        assertEquals(500L,method.invoke(instance,1500L,1000L,3,3))
        assertEquals(0L,method.invoke(instance,1500L,1000L,3,4))
        assertEquals(0L,method.invoke(instance,500L,1000L,3,3))
    }
}
