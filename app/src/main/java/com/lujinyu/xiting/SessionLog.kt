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

    // 旧版本用墙钟差算时长，时钟跳变/跨天会记出「87小时」这类失真数据。
    // v2.9.1起时长在记录点就用单调时钟（elapsedRealtime）计算，本身不可能失真；
    // 这里只做一次性迁移：剔除历史上为负或超过24小时（一块电池物理上撑不到）的旧记录并回写存档。
    // 迁移之后读写路径不再改动任何数据——统计如实按记录展示。
    private const val LEGACY_MAX_MS = 24L * 3600 * 1000

    const val MODE_BLACK = 0
    const val MODE_SCREEN_OFF = 1

    fun sessions(context: Context): List<ListenSession> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val all = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ListenSession(
                    o.getLong("s"), o.getLong("e"),
                    o.getLong("d"), o.getInt("m")
                )
            }
            val valid = all.filter { it.durationMs in 0..LEGACY_MAX_MS }
            if (valid.size != all.size) {
                write(context, valid)
                Log.w(TAG, "统计迁移：剔除${all.size - valid.size}条旧版失真记录")
            }
            valid.sortedByDescending { it.start }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, session: ListenSession) {
        if (session.durationMs < 1000) return // 不足1秒：误触不算听剧
        val list = sessions(context).toMutableList()
        list.add(0, session)
        write(context, list)
    }

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
        File(context.filesDir, FILE).writeText(arr.toString())
    }
}
