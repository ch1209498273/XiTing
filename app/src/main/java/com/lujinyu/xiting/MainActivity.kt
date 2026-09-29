// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 单Activity三页签结构：应用栏 + 内容区 + 底部导航。
 * 首页=主操作与服务；统计=节能统计（原StatsActivity）；关于=应用说明（原AboutActivity）。
 */
class MainActivity : Activity() {

    companion object {
        private const val TAB_HOME = 0
        private const val TAB_STATS = 1
        private const val TAB_ABOUT = 2

        private const val PAGE_SIZE = 10
        private const val RANGE_TODAY = 0
        private const val RANGE_WEEK = 1
        private const val RANGE_ALL = 2
        private const val SHOW_DAYS = 30L
    }

    // 应用栏与导航
    private lateinit var appbarTitle: TextView
    private lateinit var pillStatus: TextView
    private lateinit var pageHome: View
    private lateinit var pageStats: View
    private lateinit var pageAbout: View
    private lateinit var navHome: LinearLayout
    private lateinit var navStats: LinearLayout
    private lateinit var navAbout: LinearLayout
    private var tab = TAB_HOME

    // 首页控件
    private lateinit var cardBlack: LinearLayout
    private lateinit var heroTitle: TextView
    private lateinit var homeToday: TextView
    private lateinit var homeWeek: TextView
    private lateinit var homeAll: TextView

    // 统计页状态
    private var range = RANGE_ALL
    private var listPage = 0
    private var todayMs = 0L
    private var weekMs = 0L
    private var allMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appbarTitle = findViewById(R.id.appbar_title)
        pillStatus = findViewById(R.id.pill_status)
        pageHome = findViewById(R.id.page_home)
        pageStats = findViewById(R.id.page_stats)
        pageAbout = findViewById(R.id.page_about)
        navHome = findViewById(R.id.nav_home)
        navStats = findViewById(R.id.nav_stats)
        navAbout = findViewById(R.id.nav_about)
        navHome.setOnClickListener { switchTab(TAB_HOME) }
        navStats.setOnClickListener { switchTab(TAB_STATS) }
        navAbout.setOnClickListener { switchTab(TAB_ABOUT) }

        bindHome()
        bindStats()
        bindAbout()
        refreshModeUI()
        switchTab(TAB_HOME)
    }

    // ───────────────────────── 导航 ─────────────────────────

    private fun switchTab(target: Int) {
        tab = target
        pageHome.visibility = if (target == TAB_HOME) View.VISIBLE else View.GONE
        pageStats.visibility = if (target == TAB_STATS) View.VISIBLE else View.GONE
        pageAbout.visibility = if (target == TAB_ABOUT) View.VISIBLE else View.GONE
        appbarTitle.text = when (target) {
            TAB_HOME -> "息屏听剧"
            TAB_STATS -> "节能统计"
            else -> "关于"
        }
        val sel = 0xFF1E8E5A.toInt()
        val unsel = 0xFF8A9099.toInt()
        tintNav(findViewById(R.id.nav_icon_home), findViewById(R.id.nav_label_home), target == TAB_HOME, sel, unsel)
        tintNav(findViewById(R.id.nav_icon_stats), findViewById(R.id.nav_label_stats), target == TAB_STATS, sel, unsel)
        tintNav(findViewById(R.id.nav_icon_about), findViewById(R.id.nav_label_about), target == TAB_ABOUT, sel, unsel)
        if (target == TAB_STATS) renderStats()
    }

    private fun tintNav(icon: ImageView, label: TextView, selected: Boolean, sel: Int, unsel: Int) {
        icon.setColorFilter(if (selected) sel else unsel)
        label.setTextColor(if (selected) sel else unsel)
    }

    // ───────────────────────── 首页 ─────────────────────────

    private fun bindHome() {
        cardBlack = pageHome.findViewById(R.id.card_black)
        heroTitle = pageHome.findViewById(R.id.hero_title)
        homeToday = pageHome.findViewById(R.id.home_today)
        homeWeek = pageHome.findViewById(R.id.home_week)
        homeAll = pageHome.findViewById(R.id.home_all)
        val rowOverlay = pageHome.findViewById<LinearLayout>(R.id.row_overlay)
        val rowBattery = pageHome.findViewById<LinearLayout>(R.id.row_battery)
        val rowNotify = pageHome.findViewById<LinearLayout>(R.id.row_notify)

        // 「查看明细」与统计条 → 切到统计页签
        pageHome.findViewById<View>(R.id.stats_more).setOnClickListener { switchTab(TAB_STATS) }
        pageHome.findViewById<View>(R.id.stats_card).setOnClickListener { switchTab(TAB_STATS) }

        rowOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            } else {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

        rowBattery.setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            } else {
                Toast.makeText(this, "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
        }

        rowNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

        // 主按钮=助手服务控制（启动/停止）：看剧时的动作在悬浮球上
        cardBlack.setOnClickListener {
            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
                Toast.makeText(this, "助手已停止", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "助手已启动，看剧时点悬浮球即可", Toast.LENGTH_SHORT).show()
            }
            postRefresh()
        }

        // 模式卡=选择器：选中的模式决定悬浮球的行为
        pageHome.findViewById<View>(R.id.tile_black).setOnClickListener { selectMode(0) }
        pageHome.findViewById<View>(R.id.tile_screen).setOnClickListener { selectMode(1) }

        val footer = pageHome.findViewById<TextView>(R.id.tv_footer)
        footer.text = "完全离线 · 不收集任何数据 · v${BuildConfig.VERSION_NAME} · ID ${BuildConfig.BUILD_ID}"
    }

    /** 选择听剧模式（0=黑幕 1=真息屏）：悬浮球按它执行 */
    private fun selectMode(mode: Int) {
        getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
            .edit().putInt("listen_mode", mode).apply()
        refreshModeUI()
        refreshStates()
        if (mode == 1) {
            // 选真息屏：未激活一键锁屏时立刻引导激活，避免"选了但悬浮球没反应"
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(this, LockReceiver::class.java)
            if (!dpm.isAdminActive(admin)) {
                startActivity(
                    android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "激活后：看剧时点悬浮球即锁屏听剧（真息屏），声音自动恢复。"
                        )
                    }
                )
                Toast.makeText(this, "请点击「激活」完成一键锁屏授权", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "已选真息屏模式：看剧时点悬浮球即锁屏听剧", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "已选黑幕模式：看剧时点悬浮球即全屏黑幕", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshModeUI() {
        val mode = getSharedPreferences("xiiting_prefs", MODE_PRIVATE).getInt("listen_mode", 0)
        pageHome.findViewById<LinearLayout>(R.id.tile_black).setBackgroundResource(
            if (mode == 0) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        pageHome.findViewById<LinearLayout>(R.id.tile_screen).setBackgroundResource(
            if (mode == 1) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        pageHome.findViewById<View>(R.id.badge_black).visibility = if (mode == 0) View.VISIBLE else View.GONE
        pageHome.findViewById<View>(R.id.badge_screen).visibility = if (mode == 1) View.VISIBLE else View.GONE
        pageHome.findViewById<TextView>(R.id.hint_line).text =
            if (mode == 1)
                "已选真息屏模式：看剧时点悬浮球锁屏（首次需激活一键锁屏），或直接按电源键"
            else
                "已选黑幕模式：看剧时点悬浮球即可全屏黑幕"
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
        refreshHomeStats()
        if (tab == TAB_STATS) renderStats()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 黑幕窗口盖上/拿走都会改变本窗口焦点：恰好作为状态刷新时机，
        // 否则从黑幕上解锁回来，胶囊会停留在「黑幕开启中」
        if (hasFocus) {
            refreshStates()
            refreshModeUI()
        }
    }

    private fun postRefresh() {
        Handler(Looper.getMainLooper()).postDelayed({ refreshStates(); refreshHomeStats() }, 400)
    }

    private fun refreshStates() {
        val running = OverlayService.isRunning
        if (running) {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_on)
            pillStatus.setTextColor(0xFF157A4C.toInt())
            pillStatus.text = "运行中"
        } else {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_off)
            pillStatus.setTextColor(0xFF5F6570.toInt())
            pillStatus.text = "未运行"
        }
        // 主按钮=助手控制：文案随运行状态与所选模式变化
        val mode = getSharedPreferences("xiiting_prefs", MODE_PRIVATE).getInt("listen_mode", 0)
        heroTitle.text = if (running) "息屏听剧运行中" else "启动息屏听剧"
        pageHome.findViewById<TextView>(R.id.hero_sub).text = when {
            !running -> "启动后，看剧时点悬浮球即可息屏听剧"
            mode == 1 -> "看剧时点悬浮球即锁屏 · 点此停止助手"
            else -> "看剧时点悬浮球即全屏黑幕 · 点此停止助手"
        }

        val overlayOk = Settings.canDrawOverlays(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifyOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        setPill(pageHome.findViewById(R.id.pill_overlay), overlayOk, "已开启", "去开启")
        setPill(pageHome.findViewById(R.id.pill_battery), batteryOk, "已加白", "去加白")
        setPill(pageHome.findViewById(R.id.pill_notify), notifyOk, "已开启", "去开启")
    }

    private fun setPill(pill: TextView, on: Boolean, onText: String, offText: String) {
        pill.text = if (on) onText else offText
        pill.setBackgroundResource(if (on) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
        pill.setTextColor(if (on) 0xFF157A4C.toInt() else 0xFF5F6570.toInt())
    }

    private fun refreshHomeStats() {
        val sessions = SessionLog.sessions(this)
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val weekStart = todayStart - 7L * 24 * 3600 * 1000
        var todayMs = 0L
        var weekMs = 0L
        var allMs = 0L
        sessions.forEach { s ->
            allMs += s.durationMs
            if (s.start >= todayStart) todayMs += s.durationMs
            if (s.start >= weekStart) weekMs += s.durationMs
        }
        homeToday.text = fmtDur(todayMs)
        homeWeek.text = fmtDur(weekMs)
        homeAll.text = fmtDur(allMs)
    }

    // ───────────────────────── 统计页签 ─────────────────────────

    private fun bindStats() {
        pageStats.findViewById<View>(R.id.card_today).setOnClickListener { selectRange(RANGE_TODAY) }
        pageStats.findViewById<View>(R.id.card_week).setOnClickListener { selectRange(RANGE_WEEK) }
        pageStats.findViewById<View>(R.id.card_all).setOnClickListener { selectRange(RANGE_ALL) }
        pageStats.findViewById<View>(R.id.btn_prev).setOnClickListener {
            if (listPage > 0) {
                listPage--
                renderList()
            }
        }
        pageStats.findViewById<View>(R.id.btn_next).setOnClickListener {
            listPage++
            renderList()
        }
    }

    private fun selectRange(r: Int) {
        range = r
        pageStats.findViewById<LinearLayout>(R.id.card_today).setBackgroundResource(
            if (r == RANGE_TODAY) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        pageStats.findViewById<LinearLayout>(R.id.card_week).setBackgroundResource(
            if (r == RANGE_WEEK) R.drawable.bg_card_selected else R.drawable.bg_card
        )
        pageStats.findViewById<LinearLayout>(R.id.card_all).setBackgroundResource(
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
        pageStats.findViewById<TextView>(R.id.sum_mah).text =
            "${label}估算省电 ≈ ${Stats.estimatedMah(ms)} mAh（按OLED屏幕功耗估算）"
    }

    private fun renderStats() {
        val sessions = SessionLog.sessions(this)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
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

        pageStats.findViewById<TextView>(R.id.sum_today).text = fmtDur(todayMs)
        pageStats.findViewById<TextView>(R.id.sum_today_count).text = "$todayCount 次"
        pageStats.findViewById<TextView>(R.id.sum_week).text = fmtDur(weekMs)
        pageStats.findViewById<TextView>(R.id.sum_week_count).text = "$weekCount 次"
        pageStats.findViewById<TextView>(R.id.sum_all).text = fmtDur(allMs)
        pageStats.findViewById<TextView>(R.id.sum_all_count).text = "$allCount 次"
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
        pageStats.findViewById<BarChartView>(R.id.bar_chart).setData(dayLabels)

        renderList()
    }

    /** 会话列表：仅最近30天，分页每页10条 */
    private fun renderList() {
        val sessionList = pageStats.findViewById<LinearLayout>(R.id.session_list)
        val now = System.currentTimeMillis()
        val cutoff = now - SHOW_DAYS * 24L * 3600 * 1000
        val visible = SessionLog.sessions(this).filter { it.start >= cutoff }

        val totalPages = ((visible.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        if (listPage >= totalPages) listPage = totalPages - 1
        if (listPage < 0) listPage = 0

        sessionList.removeAllViews()
        if (visible.isEmpty()) {
            val tv = TextView(this).apply {
                text = "暂无记录——开启一次听剧后这里会出现明细"
                setTextColor(0xFF8A9099.toInt())
                textSize = 13f
            }
            sessionList.addView(tv)
            pageStats.findViewById<View>(R.id.pager_bar).visibility = View.GONE
            return
        }

        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        visible.drop(listPage * PAGE_SIZE).take(PAGE_SIZE).forEach { s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
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

        pageStats.findViewById<View>(R.id.pager_bar).visibility = View.VISIBLE
        pageStats.findViewById<TextView>(R.id.pager_label).text =
            "第 ${listPage + 1} / $totalPages 页 · 共 ${visible.size} 条"
        pageStats.findViewById<TextView>(R.id.btn_prev).alpha = if (listPage == 0) 0.35f else 1f
        pageStats.findViewById<TextView>(R.id.btn_next).alpha = if (listPage >= totalPages - 1) 0.35f else 1f
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

    // ───────────────────────── 关于页签 ─────────────────────────

    private fun bindAbout() {
        pageAbout.findViewById<TextView>(R.id.about_version).text =
            "版本 ${BuildConfig.VERSION_NAME} · 构建ID ${BuildConfig.BUILD_ID}"
    }

    // ───────────────────────── 通用 ─────────────────────────

    private fun fmtDur(ms: Long): String {
        if (ms < 60000) return "${ms / 1000}秒"
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}小时${m}分" else "${m}分钟"
    }
}
