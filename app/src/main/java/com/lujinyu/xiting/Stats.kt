package com.lujinyu.xiting

import android.content.Context

/**
 * 节能统计：累计「息屏听剧」时长（黑幕模式 + 真息屏模式合计），
 * 并按典型OLED屏幕功耗估算省电量。
 *
 * 数据全部保存在本地，不上传。
 */
object Stats {

    // 估算系数：息屏听剧省下的是「屏幕点亮」功耗。
    // 典型OLED看视频时屏幕约0.8W（亮度相关），按3.85V电池电压换算：
    // 0.8W ÷ 3.85V ≈ 208mA → 每分钟 ≈ 3.5mAh。仅为估算值，实际因亮度/内容而异。
    private const val MAH_PER_MINUTE = 3.5

    /** 累计一段听剧时长（毫秒）；不足1秒忽略 */
    fun addDelta(context: Context, deltaMs: Long) {
        if (deltaMs < 1000) return
        val sp = context.prefsStats()
        sp.edit()
            .putLong(Prefs.STATS_TOTAL_MS, sp.getLong(Prefs.STATS_TOTAL_MS, 0) + deltaMs)
            .putInt(Prefs.STATS_COUNT, sp.getInt(Prefs.STATS_COUNT, 0) + 1)
            .apply()
    }

    /** 累计毫秒数 + 次数 */
    fun totals(context: Context): Pair<Long, Int> {
        val sp = context.prefsStats()
        return Pair(sp.getLong(Prefs.STATS_TOTAL_MS, 0), sp.getInt(Prefs.STATS_COUNT, 0))
    }

    /** 估算省电量（mAh） */
    fun estimatedMah(totalMs: Long): Int = (totalMs / 60000.0 * MAH_PER_MINUTE).toInt()
}
