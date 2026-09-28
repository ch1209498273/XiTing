package com.lujinyu.xiting

import android.content.Context
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

    const val MODE_BLACK = 0
    const val MODE_SCREEN_OFF = 1

    fun sessions(context: Context): List<ListenSession> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ListenSession(
                    o.getLong("s"), o.getLong("e"),
                    o.getLong("d"), o.getInt("m")
                )
            }.sortedByDescending { it.start }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, session: ListenSession) {
        if (session.durationMs < 1000) return // 太短不计
        val list = sessions(context).toMutableList()
        list.add(0, session)
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
