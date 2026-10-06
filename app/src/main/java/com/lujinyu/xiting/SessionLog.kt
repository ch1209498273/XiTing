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

    // 旧版本用墙钟差算时长，时钟跳变/跨天会产生失真记录；不足1分钟属误触。
    // 两者在读取时过滤：历史秒级测试数据不再出现在统计与列表中。
    // v2.9.1起时长在记录点就用单调时钟（elapsedRealtime）计算，本身不可能失真；
    // 这里只做一次性迁移：剔除历史上为负或超过24小时（一块电池物理上撑不到）的旧记录并回写存档。
    // 迁移之后读写路径不再改动任何数据——统计如实按记录展示。
    private const val LEGACY_MAX_MS = 24L * 3600 * 1000

    const val MODE_BLACK = 0
    const val MODE_SCREEN_OFF = 1

    fun sessions(context: Context): List<ListenSession> = synchronized(lock) { read(context) }

    /**
     * 解析存档。
     *
     * 逐条 try/catch 跳过损坏记录，而不是整份文件一起放弃——这里曾用 map()，
     * 一条脏记录就让整个 map 抛出、外层 catch 返回 emptyList()，随后 add()
     * 拿着这个空列表做读-改-写，把全部历史覆盖成仅剩的一条。
     * 单条损坏只丢那一条，其余 499 条必须照常读出。
     */
    private fun read(context: Context): List<ListenSession> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        val arr = try {
            JSONArray(f.readText())
        } catch (e: Exception) {
            // 整体解析不了：先把原件留底再当空存档处理。
            // 不留底的话，下一次 add() 会拿着空列表把文件覆盖掉，原始数据彻底消失。
            preserveCorrupt(f)
            return emptyList()
        }
        var skipped = 0
        val all = (0 until arr.length()).mapNotNull { i ->
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
        if (skipped > 0) Log.w(TAG, "跳过 $skipped 条损坏记录，其余 ${all.size} 条照常读取")
        val valid = all.filter { it.durationMs >= 60_000 && it.durationMs <= LEGACY_MAX_MS }
        if (valid.size != all.size) {
            write(context, valid)
            Log.w(TAG, "统计迁移：剔除${all.size - valid.size}条旧版失真记录")
        }
        valid.sortedByDescending { it.start }
    }

    fun add(context: Context, session: ListenSession) {
        if (session.durationMs < 60_000) return // 不足1分钟：误触/测试无统计意义
        // 读-改-写整体在锁内完成：否则并发 add() 会各自基于同一份旧列表写回，互相覆盖
        synchronized(lock) {
            val list = read(context).toMutableList()
            list.add(0, session)
            write(context, list)
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
    private fun write(context: Context, list: List<ListenSession>) {
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(
                JSONObject()
                    .put("s", it.start)
                    .put("e", it.end)
                    .put("d", it.durationMs)
                    .put("m", it.mode)
            )
        }
        val target = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        try {
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(target)) {
                // 极少数文件系统 rename 失败：退回直接写，至少不留下临时文件
                target.writeText(arr.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "会话存档写入失败: $e")
        } finally {
            tmp.delete()
        }
    }
}
