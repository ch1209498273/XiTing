package com.lujinyu.xiting

import android.app.Activity
import android.view.View
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 节能统计页：
 *  · 今日/近7天/累计 三卡可点选，省电估算跟随所选范围
 *  · 会话记录分页展示（每页10条），仅保留最近30天
 */
class StatsActivity : Activity() {

    private lateinit var sessionList: LinearLayout

    private var range = RANGE_ALL   // 当前选中范围（默认累计）
    private var page = 0            // 会话列表当前页

    // render()算出后供省电行按范围取用
    private var todayMs = 0L
    private var weekMs = 0L
    private var allMs = 0L

    companion object {
        private const val PAGE_SIZE = 10
        private const val RANGE_TODAY = 0
        private const val RANGE_WEEK = 1
        private const val RANGE_ALL = 2
        private const val SHOW_DAYS = 30L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        sessionList = findViewById(R.id.session_list)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.card_today).setOnClickListener { selectRange(RANGE_TODAY) }
        findViewById<View>(R.id.card_week).setOnClickListener { selectRange(RANGE_WEEK) }
        findViewById<View>(R.id.card_all).setOnClickListener { selectRange(RANGE_ALL) }
        findViewById<View>(R.id.btn_prev).setOnClickListener {
            if (page > 0) {
                page--
                renderList()
            }
        }
        findViewById<View>(R.id.btn_next).setOnClickListener {
            page++
            renderList()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun selectRange(r: Int) {
        range = r
        findViewById<LinearLayout>(R.id.card_today).setBackgroundResource(
            if (r == RANGE_TODAY) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        findViewById<LinearLayout>(R.id.card_week).setBackgroundResource(
            if (r == RANGE_WEEK) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        findViewById<LinearLayout>(R.id.card_all).setBackgroundResource(
            if (r == RANGE_ALL) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        updateMah()
    }

    private fun updateMah() {
        val (label, ms) = when (range) {
            RANGE_TODAY -> "今日" to todayMs
            RANGE_WEEK -> "近7天" to weekMs
            else -> "累计" to allMs
        }
        findViewById<TextView>(R.id.sum_mah).text =
            "${label}估算省电 ≈ ${Stats.estimatedMah(ms)} mAh（按OLED屏幕功耗估算）"
    }

    private fun render() {
        val sessions = SessionLog.sessions(this)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val todayStart = cal.timeInMillis
        val weekStart = todayStart - 7L * 24 * 3600 * 1000

        todayMs = 0L; var todayCount = 0
        weekMs = 0L; var weekCount = 0
        allMs = 0L
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
        updateMah()

        // 近7天柱状图（含今天，共7天）
        val dayLabels = ArrayList<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val dayStart = todayStart - i * 24L * 3600 * 1000
            val dayEnd = dayStart + 24L * 3600 * 1000
            var ms = 0L
            sessions.forEach { s ->
                if (s.start >= dayStart && s.start < dayEnd) ms += s.durationMs
            }
            dayLabels.add(Pair(dayLabel(i), ms))
        }
        findViewById<BarChartView>(R.id.bar_chart).setData(dayLabels)

        renderList()
    }

    /** 会话列表：仅最近30天，分页每页10条 */
    private fun renderList() {
        val now = System.currentTimeMillis()
        val cutoff = now - SHOW_DAYS * 24L * 3600 * 1000
        val visible = SessionLog.sessions(this).filter { it.start >= cutoff }

        val totalPages = ((visible.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        sessionList.removeAllViews()
        if (visible.isEmpty()) {
            val tv = TextView(this).apply {
                text = "暂无记录——开启一次听剧后这里会出现明细"
                setTextColor(0xFF8A9099.toInt())
                textSize = 13f
            }
            sessionList.addView(tv)
            findViewById<View>(R.id.pager_bar).visibility = View.GONE
            return
        }

        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        visible.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEach { s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 14, 0, 14)
            }
            val mode = modeBadge(s.mode)
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

        findViewById<View>(R.id.pager_bar).visibility = View.VISIBLE
        findViewById<TextView>(R.id.pager_label).text = "第 ${page + 1} / $totalPages 页 · 共 ${visible.size} 条"
        findViewById<TextView>(R.id.btn_prev).alpha = if (page == 0) 0.35f else 1f
        findViewById<TextView>(R.id.btn_next).alpha = if (page >= totalPages - 1) 0.35f else 1f
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
        if (totalMin < 1) return "<1分钟"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}小时${m}分" else "${m}分钟"
    }
}
