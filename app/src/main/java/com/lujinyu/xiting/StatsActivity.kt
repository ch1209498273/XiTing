package com.lujinyu.xiting

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsActivity : Activity() {

    private lateinit var sessionList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        sessionList = findViewById(R.id.session_list)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sessions = SessionLog.sessions(this)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val todayStart = cal.timeInMillis
        val weekStart = todayStart - 7L * 24 * 3600 * 1000

        var todayMs = 0L; var todayCount = 0
        var weekMs = 0L; var weekCount = 0
        var allMs = 0L
        sessions.forEach { s ->
            allMs += s.durationMs
            if (s.start >= todayStart) { todayMs += s.durationMs; todayCount++ }
            if (s.start >= weekStart) { weekMs += s.durationMs; weekCount++ }
        }
        val allCount = sessions.size

        findViewById<TextView>(R.id.sum_today).text = fmtDur(todayMs)
        findViewById<TextView>(R.id.sum_today_count).text = "$todayCount 次"
        findViewById<TextView>(R.id.sum_week).text = fmtDur(weekMs)
        findViewById<TextView>(R.id.sum_week_count).text = "$weekCount 次"
        findViewById<TextView>(R.id.sum_all).text = fmtDur(allMs)
        findViewById<TextView>(R.id.sum_all_count).text = "$allCount 次"
        findViewById<TextView>(R.id.sum_mah).text =
            "累计估算省电 ≈ ${Stats.estimatedMah(allMs)} mAh（按OLED屏幕功耗估算）"

        // 近7天柱状图（含今天，共7天）
        val dayLabels = listOf("前天", "昨天", "今天").let {
            val days = ArrayList<Pair<String, Long>>()
            for (i in 6 downTo 0) {
                val dayStart = todayStart - i * 24L * 3600 * 1000
                val dayEnd = dayStart + 24L * 3600 * 1000
                var ms = 0L
                sessions.forEach { s ->
                    if (s.start >= dayStart && s.start < dayEnd) ms += s.durationMs
                }
                days.add(Pair(dayLabel(i), ms))
            }
            days
        }
        findViewById<BarChartView>(R.id.bar_chart).setData(dayLabels)

        // 最近会话明细（最多10条）
        sessionList.removeAllViews()
        val recent = sessions.take(10)
        if (recent.isEmpty()) {
            val tv = TextView(this).apply {
                text = "暂无记录——开启一次听剧后这里会出现明细"
                setTextColor(0xFF8A9099.toInt())
                textSize = 13f
            }
            sessionList.addView(tv)
            return
        }
        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val titlePaint = 0xFF111418
        recent.forEach { s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 14, 0, 14)
            }
            val mode = if (s.mode == SessionLog.MODE_SCREEN_OFF) "真息屏" else "黑幕"
            val left = TextView(this).apply {
                text = "${df.format(Date(s.start))}\n${fmtDur(s.durationMs)} · $mode"
                setTextColor(0xFF111418.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val right = TextView(this).apply {
                text = mode
                setTextColor(0xFF157A4C.toInt())
                textSize = 12f
            }
            row.addView(left)
            row.addView(right)
            sessionList.addView(row)
        }
    }

    private fun dayLabel(daysAgo: Int): String = when (daysAgo) {
        0 -> "今天"
        1 -> "昨天"
        else -> {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            String.format(Locale.getDefault(), "%02d-%02d",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }

    private fun modeBadge(mode: Int): String = if (mode == SessionLog.MODE_SCREEN_OFF) "真息屏" else "黑幕"

    private fun fmtDur(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}小时${m}分" else "${m}分钟"
    }
}
