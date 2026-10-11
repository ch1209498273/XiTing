// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import java.util.Calendar

/**
 * 全量历史聚合（v4.0）：明细 sessions.json 只保留 60 天（列表/周报/月报本月够用），
 * 「累计时长/次数、最长单次、连续天数、月口径」全部住在这里。
 *
 * **聚合是派生数据**，不进备份文件：BackupManager.restore 尾部调 [rebuildFromSessions]
 * 从恢复出的明细重算；重装/清数据的迁移同一条路（[ensureMigrated]）。
 * 每条新会话经 SessionLog.add（锁内）同步累加 [record]——明细与聚合永远一致。
 *
 * 为什么不继续堆明细：听一年 ≈ 1000+ 条，每次 onResume 全量 JSON 解析是可感知的
 * 卡顿源，且 500 条上限会让「累计」「年度」口径悄悄失真（用户 2026-10-11 拍板重设计）。
 */
object HistoryStore {

    /** 明细保留天数：列表（30 天）+ 周报（14 天）+ 完整本月，余量取整 */
    private const val DETAIL_DAYS = 60L

    // ── 读 ──

    fun totalMs(ctx: Context): Long = ctx.prefs().getLong(Prefs.STATS_TOTAL_MS, 0L)
    fun totalCount(ctx: Context): Long = ctx.prefs().getLong(Prefs.STATS_TOTAL_COUNT, 0L)
    fun maxSingleMs(ctx: Context): Long = ctx.prefs().getLong(Prefs.STATS_MAX_SINGLE, 0L)

    /** 听过剧的 epoch day 集合（连续天数从这里算，不依赖明细） */
    fun listenDays(ctx: Context): Set<Long> =
        (ctx.prefs().getStringSet(Prefs.LISTEN_DAYS, emptySet()) ?: emptySet())
            .mapTo(HashSet()) { it.toLong() }

    /** 月聚合："yyyy-MM" → (ms, count)。读时容错：条目损坏的月份直接跳过 */
    fun months(ctx: Context): Map<String, Pair<Long, Int>> {
        val out = HashMap<String, Pair<Long, Int>>()
        for (entry in ctx.prefs().getStringSet(Prefs.MONTH_STATS, emptySet()) ?: emptySet()) {
            val p = entry.split(":")
            if (p.size != 3) continue
            try {
                out[p[0]] = p[1].toLong() to p[2].toInt()
            } catch (_: Exception) {
            }
        }
        return out
    }

    /** 月份 key（yyyy-MM，US locale——内部 key 与展示无关） */
    fun monthKey(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(ms))

    // ── 写（SessionLog.add 锁内调用）──

    fun record(ctx: Context, session: ListenSession) {
        val p = ctx.prefs().edit()
        p.putLong(Prefs.STATS_TOTAL_MS, totalMs(ctx) + session.durationMs)
        p.putLong(Prefs.STATS_TOTAL_COUNT, totalCount(ctx) + 1)
        if (session.durationMs > maxSingleMs(ctx)) {
            p.putLong(Prefs.STATS_MAX_SINGLE, session.durationMs)
        }
        val days = listenDays(ctx).toMutableSet()
        days.add(Streaks.epochDayOf(session.start))
        p.putStringSet(Prefs.LISTEN_DAYS, days.mapTo(HashSet()) { it.toString() })

        val key = monthKey(session.start)
        val months = months(ctx).toMutableMap()
        val old = months[key] ?: (0L to 0)
        months[key] = (old.first + session.durationMs) to (old.second + 1)
        p.putStringSet(Prefs.MONTH_STATS, months.mapTo(HashSet()) { "${it.key}:${it.value.first}:${it.value.second}" })
        p.apply()
    }

    // ── 重建 / 迁移 ──

    /**
     * 从明细重算全部聚合并清零重来。两个调用点：
     * 1. 一次性迁移（老版本 sessions.json 的历史算进来，见 [ensureMigrated]）
     * 2. 备份恢复之后（sessions 换了，聚合跟着重算）
     */
    fun rebuildFromSessions(ctx: Context, sessions: List<ListenSession>) {
        val p = ctx.prefs().edit()
        var total = 0L
        var count = 0L
        var max = 0L
        val days = HashSet<String>()
        val months = HashMap<String, Pair<Long, Int>>()
        for (s in sessions) {
            total += s.durationMs
            count++
            if (s.durationMs > max) max = s.durationMs
            days.add(Streaks.epochDayOf(s.start).toString())
            val key = monthKey(s.start)
            val old = months[key] ?: (0L to 0)
            months[key] = (old.first + s.durationMs) to (old.second + 1)
        }
        p.putLong(Prefs.STATS_TOTAL_MS, total)
        p.putLong(Prefs.STATS_TOTAL_COUNT, count)
        p.putLong(Prefs.STATS_MAX_SINGLE, max)
        p.putStringSet(Prefs.LISTEN_DAYS, days)
        p.putStringSet(Prefs.MONTH_STATS, months.mapTo(HashSet()) { "${it.key}:${it.value.first}:${it.value.second}" })
        p.putBoolean(Prefs.STATS_MIGRATED, true)
        p.apply()
    }

    /**
     * 一次性迁移：老版本没有聚合，从现存明细（≤500 条）算出全量基线。
     * 之后新会话走 [record] 增量累加，本函数不再做任何事（一个 boolean 读的开销）。
     * 由 SessionLog.sessions() 在锁内调用（sessions 已读好，直接传参避免重入）。
     */
    fun ensureMigrated(ctx: Context, sessions: List<ListenSession>) {
        if (ctx.prefs().getBoolean(Prefs.STATS_MIGRATED, false)) return
        rebuildFromSessions(ctx, sessions)
    }

    /** 明细裁剪到 [DETAIL_DAYS] 天（聚合已接管全量口径，老明细没有保留价值） */
    fun detailCutoff(ctx: Context): Long =
        System.currentTimeMillis() - DETAIL_DAYS * 24 * 3600 * 1000

    /** 本月与上月的聚合（月报用）；无记录的月为 0 */
    fun thisAndLastMonth(ctx: Context, now: Long): Pair<Long, Long> {
        val months = months(ctx)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val thisKey = monthKey(now)
        val lastCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
        }
        val lastKey = monthKey(lastCal.timeInMillis)
        return (months[thisKey]?.first ?: 0L) to (months[lastKey]?.first ?: 0L)
    }
}
