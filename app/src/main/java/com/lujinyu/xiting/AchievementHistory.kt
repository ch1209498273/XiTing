package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject

/** 首次记录时间不是历史达成时间；旧版已解锁项无法追溯，保持 null。列表顺序才是记录顺序。 */
internal data class AchievementRecord(val id: String, val recordedAt: Long?)

internal object AchievementHistory {
    private val knownIds get() = Achievements.ALL.map { it.id }.toSet()
    data class Restored(val unlocked: Set<String>, val records: List<AchievementRecord>)

    fun parse(text: String?): List<AchievementRecord> = try {
        val arr = JSONArray(text ?: "[]")
        val out = LinkedHashMap<String, AchievementRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.opt("id") as? String ?: continue
            if (id !in knownIds || id in out) continue
            val raw = obj.opt("recorded_at")
            val at = when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                else -> null
            }?.takeIf { it in 1L..253402300799999L }
            out[id] = AchievementRecord(id, at)
            if (out.size == knownIds.size) break
        }
        out.values.toList()
    } catch (_: Exception) { emptyList() }

    fun encode(records: List<AchievementRecord>): String = JSONArray().apply {
        records.forEach { r ->
            put(JSONObject().put("id", r.id).put("recorded_at", r.recordedAt ?: JSONObject.NULL))
        }
    }.toString()

    fun record(
        records: List<AchievementRecord>,
        alreadyUnlocked: Set<String>,
        freshIds: List<String>,
        now: Long
    ): List<AchievementRecord> {
        val out = LinkedHashMap<String, AchievementRecord>()
        records.filter { it.id in knownIds }.forEach { out.putIfAbsent(it.id, it) }
        // Set 不携带历史顺序。迁移使用稳定列表顺序，仅填未知日期，不伪造“最新”。
        Achievements.ALL.filter { it.id in alreadyUnlocked }.forEach {
            out.putIfAbsent(it.id, AchievementRecord(it.id, null))
        }
        freshIds.filter { it in knownIds }.forEach {
            out.putIfAbsent(it, AchievementRecord(it, now.takeIf { n -> n > 0 }))
        }
        return out.values.toList()
    }

    fun latestRecorded(records: List<AchievementRecord>): AchievementRecord? =
        records.lastOrNull { it.recordedAt != null }

    /** 可选新增字段，v1/v2 均可读；损坏字段不清空本机状态。导入顺序追加，重复项保留本机首记录。 */
    fun mergeBackup(local: List<AchievementRecord>, unlocked: Set<String>, obj: JSONObject): Restored {
        val base = record(local, unlocked, emptyList(), 0L)
        val imported = parse(obj.optJSONArray("ach_history")?.toString())
        val importedIds = obj.optJSONArray("ach_unlocked")?.toStringSet().orEmpty().intersect(knownIds)
        val mergedIds = unlocked.intersect(knownIds) + base.map { it.id } + importedIds + imported.map { it.id }
        val combined = (base + imported).distinctBy { it.id }
        return Restored(mergedIds, record(combined, mergedIds, emptyList(), 0L))
    }
}
