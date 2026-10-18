package com.lujinyu.xiting

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 听剧会话记录（本地JSON存档，不上传）。
 * 每次黑幕或真息屏听剧记录一条：起止时间、时长、模式。
 */
data class ListenSession(
    val start: Long,      // 墙钟毫秒
    val end: Long,
    val durationMs: Long,
    val mode: Int         // 0=黑幕模式 1=真息屏模式
)

/** 解析结果：合法条目 + 被跳过的损坏条目数 */
data class ParseResult<T>(val items: List<T>, val skipped: Int)

/**
 * 解析存档文本（纯函数，可单测）。
 *
 * 逐条 try/catch 跳过损坏记录，而不是整份文件一起放弃——这里曾用 map()，
 * 一条脏记录就让整个 map 抛出、外层 catch 返回 emptyList()，随后 add()
 * 拿着这个空列表做读-改-写，把全部历史覆盖成仅剩的一条。
 * 单条损坏只丢那一条，其余 499 条必须照常读出。
 *
 * 整个文本语法级非法时抛给调用方处理（那是另一个层面的问题，走留底策略）。
 */
internal fun parseSessions(text: String): ParseResult<ListenSession> {
    val arr = JSONArray(text)
    var skipped = 0
    val items = (0 until arr.length()).mapNotNull { i ->
        try {
            val o = arr.getJSONObject(i)
            ListenSession(
                o.getLong("s"), o.getLong("e"),
                o.getLong("d"), o.getInt("m")
            )
        } catch (e: Exception) {
            skipped++
            null
        }
    }
    return ParseResult(items, skipped)
}

object SessionLog {

    private const val FILE = "sessions.json"
    private const val MAX = 500
    private const val TAG = "XiTing"

    /**
     * sessions.json 的读写串行锁。
     *
     * 存档虽小（最多500条），但历史上被多个入口并发读：统计页 renderStats、
     * 会话列表 renderList、图鉴弹窗，以及 BlackOverlay.hide() 每次黑幕收尾的 add()。
     * 读-改-写不是原子操作，两个线程同时进入会互相覆盖。
     */
    private val lock = Any()
    internal fun <T> withDataLock(block:()->T):T=synchronized(lock,block)

    // 旧版本用墙钟差算时长，时钟跳变/跨天会产生失真记录；不足1分钟属误触。
    // 两者在读取时过滤：历史秒级测试数据不再出现在统计与列表中。
    // v2.9.1起时长在记录点就用单调时钟（elapsedRealtime）计算，本身不可能失真；
    // 这里只做一次性迁移：剔除历史上为负或超过24小时（一块电池物理上撑不到）的旧记录并回写存档。
    // 迁移之后读写路径不再改动任何数据——统计如实按记录展示。
    private const val LEGACY_MAX_MS = 24L * 3600 * 1000

    const val MODE_BLACK = 0
    const val MODE_SCREEN_OFF = 1

    fun sessions(context: Context): List<ListenSession> = synchronized(lock) {
        check(completePendingDeletion(context)) { "History deletion recovery failed" }
        val list = read(context)
        // 一次性迁移：老版本没有全量聚合，从现存明细算基线（幂等，一个 boolean 读）
        HistoryStore.ensureMigrated(context, list)
        list
    }

    /**
     * 解析存档文件。语法级损坏（整个 JSON 非法）走留底；元素级损坏逐条跳过。
     */
    private fun read(context: Context): List<ListenSession> {
        val f = File(context.filesDir, FILE)
        if (!f.exists() && !File(context.filesDir,"$FILE.bak").exists()) return emptyList()
        val parsed = try {
            parseSessions(android.util.AtomicFile(f).openRead().bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            // 整体语法解析不了：先把原件留底再当空存档处理。
            // 不留底的话，下一次 add() 会拿着空列表把文件覆盖掉，原始数据彻底消失。
            preserveCorrupt(f)
            return emptyList()
        }
        if (parsed.skipped > 0) {
            Log.w(TAG, "跳过 ${parsed.skipped} 条损坏记录，其余 ${parsed.items.size} 条照常读取")
        }
        val valid = parsed.items.filter { it.durationMs >= 60_000 && it.durationMs <= LEGACY_MAX_MS }
        if (valid.size != parsed.items.size) {
            write(context, valid)
            Log.w(TAG, "统计迁移：剔除${parsed.items.size - valid.size}条旧版失真记录")
        }
        return valid.sortedByDescending { it.start }
    }

    fun add(context: Context, session: ListenSession) {
        if (session.durationMs < 60_000) return // 不足1分钟：误触/测试无统计意义
        // 读-改-写整体在锁内完成：否则并发 add() 会各自基于同一份旧列表写回，互相覆盖
        synchronized(lock) {
            check(completePendingDeletion(context)) { "History deletion recovery failed" }
            val list = read(context).toMutableList()
            HistoryStore.ensureMigrated(context, list)
            list.add(0, session)
            if(!write(context, list)) return
            // 聚合同步累加（明细与聚合一致性的唯一写入点）
            HistoryStore.record(context, session)
        }
    }

    fun canDelete(context: Context, session: ListenSession): Boolean = synchronized(lock) {
        if(!completePendingDeletion(context)) return@synchronized false
        val list=read(context)
        HistoryStore.ensureMigrated(context,list)
        deleteFromHistory(HistorySnapshot.read(context),list,session)!=null
    }

    /** Replayable local transaction prevents a process death from separating details and totals. */
    fun delete(context: Context, session: ListenSession): Boolean = synchronized(lock) {
        if(!completePendingDeletion(context)) return@synchronized false
        val list=read(context)
        HistoryStore.ensureMigrated(context,list)
        val after=deleteFromHistory(HistorySnapshot.read(context),list,session) ?: return@synchronized false
        val remaining=list.toMutableList().apply { removeAt(indexOf(session)) }
        val journal=android.util.AtomicFile(File(context.filesDir,"history-delete.json"))
        val payload=JSONObject().put("sessions",JSONArray(remaining.map {
            JSONObject().put("s",it.start).put("e",it.end).put("d",it.durationMs).put("m",it.mode)
        })).put("history",after.json()).toString().toByteArray(Charsets.UTF_8)
        var stream: java.io.FileOutputStream? = null
        try {
            stream=journal.startWrite();stream.write(payload);journal.finishWrite(stream)
        } catch(e: Exception) {
            journal.failWrite(stream);Log.w(TAG,"Deletion transaction write failed",e)
            return@synchronized false
        }
        completePendingDeletion(context)
    }

    private fun completePendingDeletion(context: Context): Boolean {
        val journal=android.util.AtomicFile(File(context.filesDir,"history-delete.json"))
        if(!journal.baseFile.exists() && !File(context.filesDir,"history-delete.json.bak").exists())return true
        return try {
            val payload=JSONObject(journal.openRead().bufferedReader().use { it.readText() })
            val sessions=parseSessions(payload.getJSONArray("sessions").toString())
            check(sessions.skipped==0)
            val history=HistorySnapshot.fromJson(payload.getJSONObject("history"))
            if(!write(context,sessions.items) || !history.persist(context))return false
            journal.delete()
            true
        } catch(e: Exception) {
            Log.w(TAG,"Deletion recovery failed; journal retained",e)
            false
        }
    }

    /** 损坏存档改名留底（最多留 3 份，滚动清理），不静默丢弃 */
    private fun preserveCorrupt(f: File) {
        try {
            if (!f.exists() || f.length() == 0L) return
            val stamp = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val kept = File(f.parentFile, "$FILE.corrupt-$stamp")
            if (!f.renameTo(kept)) {
                f.copyTo(kept, overwrite = true)
                f.delete()
            }
            // 只保留最近 3 份，旧的清掉，避免无限堆积
            f.parentFile?.listFiles { d -> d.name.startsWith("$FILE.corrupt-") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { it.delete() }
            Log.w(TAG, "存档损坏，已留底：${kept.name}")
        } catch (e: Exception) {
            Log.w(TAG, "存档损坏且留底失败: $e")
        }
    }

    /**
     * 落盘：先写临时文件再原子改名。
     *
     * 原先直接 File.writeText（截断写），进程在写到一半被杀就会留下半截 JSON；
     * 下次读取解析失败返回空列表，接着 add() 就把半截内容覆盖成单条，历史全丢。
     * rename 在同一目录内是原子操作，读者要么看到旧的完整文件，要么看到新的完整文件。
     */
    private fun write(context: Context, list: List<ListenSession>): Boolean {
        // v4.0：明细只保留 60 天（列表/周报/月报本月够用），全量口径在 HistoryStore 聚合。
        // MAX=500 保留作条数兜底。聚合是派生数据，降级到 v3.x 会看到累计缩水（已知取舍）。
        val cutoff = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        val arr = JSONArray()
        list.take(MAX).forEach {
            if (it.start < cutoff) return@forEach
            arr.put(
                JSONObject()
                    .put("s", it.start)
                    .put("e", it.end)
                    .put("d", it.durationMs)
                    .put("m", it.mode)
            )
        }
        val target = android.util.AtomicFile(File(context.filesDir, FILE))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = target.startWrite()
            stream.write(arr.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
            return true
        } catch (e: Exception) {
            target.failWrite(stream)
            Log.w(TAG, "会话存档写入失败: $e")
            return false
        }
    }
}
