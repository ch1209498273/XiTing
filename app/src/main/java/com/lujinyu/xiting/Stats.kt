package com.lujinyu.xiting

/**
 * 省电量估算：把累计听剧时长换算成 mAh。
 *
 * 数据源只有 sessions.json（见 SessionLog）——统计页、首页、成就、皮肤
 * 判定全部从会话记录现算，因此这里是无状态纯函数。
 *
 * 历史包袱：曾有一个 xiiting_stats 存储，每段听剧都往里写 total_ms/count，
 * 但读取它的 totals() 从未被任何地方调用，而 estimatedMah 的五个调用点
 * 传入的都是从 SessionLog 汇总出来的时长 —— 也就是说那个存储是只写不读，
 * 每次会话收尾都在白写一次 SharedPreferences。已连同 addDelta/totals 一并移除。
 */
object Stats {

    // 估算系数：息屏听剧省下的是「屏幕点亮」功耗。
    // 典型OLED看视频时屏幕约0.8W（亮度相关），按3.85V电池电压换算：
    // 0.8W ÷ 3.85V ≈ 208mA → 每分钟 ≈ 3.5mAh。仅为估算值，实际因亮度/内容而异。
    private const val MAH_PER_MINUTE = 3.5

    /** 估算省电量（mAh） */
    fun estimatedMah(totalMs: Long): Int = (totalMs / 60000.0 * MAH_PER_MINUTE).toInt()
}