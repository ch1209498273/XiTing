// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.Locale

/**
 * MainActivity 瘦身第一刀（2026-10-11）：与视图树无关的纯计算 / 格式化助手。
 *
 * 这些函数不读任何视图字段，只依赖 Context（取本地化文案），所以可以脱离
 * Activity 类以顶层扩展函数存在 —— MainActivity 里的调用点一行不用改
 * （同包顶层声明免 import；BarChartView 里同名的 private fmtDur 是成员，
 * 成员优先于扩展，互不干扰）。
 *
 * 规矩：MainActivity 只留生命周期、导航、视图绑定胶水；新功能代码进独立文件。
 */

/** 会话在 [from, to) 区间内的实际时长（跨天会话按天拆分，归属不串） */
internal fun overlapMs(s: ListenSession, from: Long, to: Long): Long {
    val a = maxOf(s.start, from)
    val b = minOf(s.end, to)
    return (b - a).coerceAtLeast(0)
}

/** 时长 → 「X时Y分」文案。Context 扩展：单位取本地化字符串。 */
internal fun Context.fmtDur(ms: Long): String {
    if (ms < 60000) return "${ms / 1000}${getString(R.string.unit_s)}"
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h >= 100 -> "$h${getString(R.string.unit_h)}"   // 超长丢分钟，保证三卡单行不换行
        h > 0 -> "$h${getString(R.string.unit_h)}$m${getString(R.string.unit_m)}"
        else -> "$m${getString(R.string.unit_m_full)}"
    }
}

/** 近 7 天柱状图 X 轴：N 天前的标签（今天/昨天给词，更早给 MM-dd） */
internal fun Context.dayLabel(daysAgo: Int): String = when (daysAgo) {
    0 -> getString(R.string.day_today)
    1 -> getString(R.string.day_yesterday)
    else -> {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        String.format(Locale.getDefault(), "%02d-%02d",
            c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}

/** 定时 Intent（抽出来给预设与自定义两处共用） */
internal fun Context.setTimerIntent(minutes: Long): Intent =
    Intent(this, OverlayService::class.java)
        .setAction(OverlayService.ACTION_SET_TIMER)
        .putExtra(OverlayService.EXTRA_MINUTES, minutes)
