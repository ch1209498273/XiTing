package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupSummary(val version: Int?, val growth: Int, val validSessionCount: Int,val createdAt:Long?)

private fun backupInteger(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    else -> null
}

internal fun backupGrowth(obj: JSONObject): Int =
    (backupInteger(obj.opt("gp")) ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

internal fun isReadableBackup(obj:JSONObject):Boolean =
    backupInteger(obj.opt("gp"))!=null && obj.optJSONArray("sessions")!=null

internal fun summarizeBackup(obj: JSONObject): BackupSummary = BackupSummary(
    backupInteger(obj.opt("v"))?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt(),
    backupGrowth(obj), validBackupSessions(obj.optJSONArray("sessions")).length(),
    backupInteger(obj.opt("ts"))?.takeIf { it in 1L..253402300799999L }
)

/** 摘要与实际恢复共用筛选：前 500 项内逐条校验，坏记录不占据“有效会话”数量。 */
internal fun validBackupSessions(arr: JSONArray?): JSONArray {
    val kept = JSONArray()
    if (arr == null) return kept
    for (i in 0 until minOf(arr.length(), 500)) {
        val obj = arr.optJSONObject(i) ?: continue
        val start = backupInteger(obj.opt("s")) ?: continue
        val end = backupInteger(obj.opt("e")) ?: continue
        val duration = backupInteger(obj.opt("d")) ?: continue
        val mode = backupInteger(obj.opt("m")) ?: continue
        // duration is monotonic; wall time can legitimately move backwards during a session.
        if (start <= 0L || end <= 0L || duration !in 60_000L..86_400_000L || mode !in 0L..1L) continue
        kept.put(JSONObject().put("s", start).put("e", end).put("d", duration).put("m", mode.toInt()))
    }
    return kept
}
