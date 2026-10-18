package com.lujinyu.xiting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class HistorySnapshot(
    val totalMs: Long, val count: Long, val days: Set<Long>,
    val months: Map<String, Triple<Long, Int, Long>>
) {
    val maxSingleMs: Long get() = months.values.maxOfOrNull { it.third } ?: 0L
    fun persist(ctx: Context): Boolean = ctx.prefs().edit()
        .putLong(Prefs.STATS_TOTAL_MS,totalMs).putLong(Prefs.STATS_TOTAL_COUNT,count)
        .putLong(Prefs.STATS_MAX_SINGLE,maxSingleMs)
        .putStringSet(Prefs.LISTEN_DAYS,days.mapTo(HashSet()) { it.toString() })
        .putStringSet(Prefs.MONTH_STATS,months.mapTo(HashSet()) { "${it.key}:${it.value.first}:${it.value.second}:${it.value.third}" })
        .putBoolean(Prefs.STATS_MIGRATED,true).commit()

    fun json(): JSONObject = JSONObject().put("total",totalMs).put("count",count)
        .put("days",JSONArray(days.toList())).put("months",JSONArray(months.map {
            JSONObject().put("key",it.key).put("ms",it.value.first).put("count",it.value.second).put("max",it.value.third)
        }))
    companion object {
        fun read(ctx: Context) = HistorySnapshot(HistoryStore.totalMs(ctx),HistoryStore.totalCount(ctx),HistoryStore.listenDays(ctx),HistoryStore.months(ctx))
        fun fromJson(o: JSONObject): HistorySnapshot {
            val days=o.getJSONArray("days");val months=o.getJSONArray("months")
            return HistorySnapshot(o.getLong("total"),o.getLong("count"),
                (0 until days.length()).mapTo(HashSet()) { days.getLong(it) },
                (0 until months.length()).associate {
                    val m=months.getJSONObject(it)
                    m.getString("key") to Triple(m.getLong("ms"),m.getInt("count"),m.getLong("max"))
                })
        }
    }
}

/** Subtract one retained session, never rebuild archived months from a truncated details file. */
internal fun deleteFromHistory(
    before: HistorySnapshot, details: List<ListenSession>, target: ListenSession,
    monthOf: (Long) -> String = HistoryStore::monthKey,
    dayOf: (Long) -> Long = Streaks::epochDayOf
): HistorySnapshot? {
    val index=details.indexOf(target)
    if(index<0)return null
    val key=monthOf(target.start)
    val month=before.months[key] ?: return null
    val detailMonth=details.filter { monthOf(it.start)==key }
    // The old schema has no second-highest duration/day counts after detail pruning.
    // Reject an ambiguous deletion instead of inventing those values or destroying archives.
    if(month.second!=detailMonth.size || month.first!=detailMonth.sumOf { it.durationMs } ||
        month.third!=detailMonth.maxOfOrNull { it.durationMs })return null
    val remaining=details.toMutableList().apply { removeAt(index) }
    val remainingMonth=remaining.filter { monthOf(it.start)==key }
    val months=before.months.toMutableMap()
    if(remainingMonth.isEmpty())months.remove(key)
    else months[key]=Triple(remainingMonth.sumOf { it.durationMs },remainingMonth.size,remainingMonth.maxOf { it.durationMs })
    val days=before.days.toMutableSet()
    val day=dayOf(target.start)
    if(remaining.none { dayOf(it.start)==day })days.remove(day)
    if(before.totalMs<target.durationMs || before.count<1)return null
    return HistorySnapshot(before.totalMs-target.durationMs,before.count-1,days,months)
}
