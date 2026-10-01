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
class MainActivity : Activity() { // MARKER_TEST_9271

    companion object {
        private const val TAB_HOME = 0
        private const val TAB_STATS = 1
        private const val TAB_SETTINGS = 2

        private const val PAGE_SIZE = 10
        private const val RANGE_TODAY = 0
        private const val RANGE_WEEK = 1
        private const val RANGE_ALL = 2
        private const val SHOW_DAYS = 30L
        private const val SHARE_GP_PER_DAY = 5
    }

    // 应用栏与导航
    private lateinit var appbarTitle: TextView
    private lateinit var pillStatus: TextView
    private lateinit var pageHome: View
    private lateinit var pageStats: View
    private lateinit var pageSettings: View
    private lateinit var navHome: LinearLayout
    private lateinit var navStats: LinearLayout
    private lateinit var navSettings: LinearLayout
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
    private var galleryBuiltStage = -1
    private var previewStage = -1   // -1=显示当前形态；>=0=图鉴预览的形态
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
        pageSettings = findViewById(R.id.page_settings)
        navHome = findViewById(R.id.nav_home)
        navStats = findViewById(R.id.nav_stats)
        navSettings = findViewById(R.id.nav_settings)
        navHome.setOnClickListener { switchTab(TAB_HOME) }
        navStats.setOnClickListener { switchTab(TAB_STATS) }
        navSettings.setOnClickListener { switchTab(TAB_SETTINGS) }

        bindHome()
        bindStats()
        bindSettings()
        switchTab(TAB_HOME)
    }

    // ───────────────────────── 导航 ─────────────────────────

    private fun switchTab(target: Int) {
        tab = target
        pageHome.visibility = if (target == TAB_HOME) View.VISIBLE else View.GONE
        pageStats.visibility = if (target == TAB_STATS) View.VISIBLE else View.GONE
        pageSettings.visibility = if (target == TAB_SETTINGS) View.VISIBLE else View.GONE
        appbarTitle.text = when (target) {
            TAB_HOME -> "息屏听剧"
            TAB_STATS -> "节能统计"
            else -> "设置"
        }
        val sel = 0xFF1E8E5A.toInt()
        val unsel = 0xFF8A9099.toInt()
        tintNav(findViewById(R.id.nav_icon_home), findViewById(R.id.nav_label_home), target == TAB_HOME, sel, unsel)
        tintNav(findViewById(R.id.nav_icon_stats), findViewById(R.id.nav_label_stats), target == TAB_STATS, sel, unsel)
        tintNav(findViewById(R.id.nav_icon_settings), findViewById(R.id.nav_label_settings), target == TAB_SETTINGS, sel, unsel)
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
        // 通知栏「退出助手」/磁贴退出同样视为用户主动停止
        // （标志在服务端ACTION_EXIT里清除）

        cardBlack.setOnClickListener {
            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
                getSharedPreferences("xiiting_prefs", MODE_PRIVATE).edit().putBoolean("assistant_wanted", false).apply()
                Toast.makeText(this, "助手已停止", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                getSharedPreferences("xiiting_prefs", MODE_PRIVATE).edit().putBoolean("assistant_wanted", true).apply()
                Toast.makeText(this, "助手已启动，看剧时点悬浮球即可", Toast.LENGTH_SHORT).show()
            }
            postRefresh()
        }

        // 定时关闭：15/30/60分钟，到点自动收黑幕
        pageHome.findViewById<View>(R.id.timer_chip).setOnClickListener {
            val items = arrayOf("15分钟", "30分钟", "60分钟", "取消定时")
            android.app.AlertDialog.Builder(this)
                .setTitle("定时关闭")
                .setItems(items) { _, which ->
                    val minutes = when (which) {
                        0 -> 15L; 1 -> 30L; 2 -> 60L; else -> 0L
                    }
                    startService(
                        Intent(this, OverlayService::class.java)
                            .setAction(OverlayService.ACTION_SET_TIMER)
                            .putExtra(OverlayService.EXTRA_MINUTES, minutes)
                    )
                    postRefresh()
                }
                .show()
        }

        // 防杀保活指南
        pageHome.findViewById<View>(R.id.row_keepalive).setOnClickListener { showKeepAliveGuide() }

        // 页脚水印移至「设置」页签；主页不再放关于入口（与底部导航重复）
    }

    override fun onResume() {
        super.onResume()
        // 助手被系统清理后（更新/后台清理），打开App时自动恢复；
        // 用户主动停止的（assistant_wanted=false）不复活
        val prefs = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
        if (!OverlayService.isRunning && prefs.getBoolean("assistant_wanted", false)) {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        refreshStates()
        refreshHomeStats()
        if (tab == TAB_STATS) renderStats()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 黑幕窗口盖上/拿走都会改变本窗口焦点：恰好作为状态刷新时机，
        // 否则从黑幕上解锁回来，胶囊会停留在「黑幕开启中」
        if (hasFocus) refreshStates()
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
        // 主按钮=助手控制：文案随运行状态变化
        heroTitle.text = if (running) "息屏听剧运行中" else "启动息屏听剧"
        pageHome.findViewById<TextView>(R.id.hero_sub).text =
            if (running) "看剧时点悬浮球，黑屏听剧声音继续 · 点此停止助手"
            else "启动后，看剧时点悬浮球即可息屏听剧"

        val overlayOk = Settings.canDrawOverlays(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifyOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        setPill(pageHome.findViewById(R.id.pill_overlay), overlayOk, "已开启", "去开启")
        setPill(pageHome.findViewById(R.id.pill_battery), batteryOk, "已加白", "去加白")
        setPill(pageHome.findViewById(R.id.pill_notify), notifyOk, "已开启", "去开启")

        val remain = OverlayService.instance?.timerRemainingMs() ?: 0
        pageHome.findViewById<TextView>(R.id.timer_state).text =
            if (remain > 0) "剩余 ${remain / 60000 + 1} 分钟" else "未设置"
    }

    /** 防杀保活指南：ColorOS后台限制的分步设置引导 */
    private fun showKeepAliveGuide() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        android.app.AlertDialog.Builder(this)
            .setTitle("防杀保活设置")
            .setMessage(
                "系统会清理后台应用导致悬浮球消失，按以下三步设置后可长期稳定：\n\n" +
                    "1. 电池白名单（${if (batteryOk) "已完成 ✓" else "未完成"}）——点下方「去电池设置」\n\n" +
                    "2. 自启动：点「去应用详情」→ 耗电管理 → 允许自启动/完全后台行为\n\n" +
                    "3. 最近任务加锁：下拉最近任务，在息屏听剧卡片上点锁图标"
            )
            .setPositiveButton("去电池设置") { _, _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
            .setNeutralButton("去应用详情") { _, _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("知道了", null)
            .show()
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
        // 分享战绩：系统分享面板 + 分享任务（解锁精灵装扮）
        pageStats.findViewById<View>(R.id.pet_share).setOnClickListener { sharePetStats() }
    }

    /** 每日分享任务：每天首次分享 +30 成长值（精灵成长值 = 听剧分钟 + 分享奖励） */
    private fun sharePetStats() {
        val sessions = SessionLog.sessions(this)
        val allMs = sessions.sumOf { it.durationMs }
        val mah = Stats.estimatedMah(allMs)
        val pet = pageStats.findViewById<PetView>(R.id.pet_view)
        val text = "⚡ 我用「息屏听剧」黑屏听了 ${fmtDur(allMs)}，估算省电 $mah mAh\n" +
            "我的电能精灵已经进化到「${PetView.stageName(pet.stage)}」了\n" +
            "完全免费无广告的息屏听剧神器（0.9MB 离线运行）\n" +
            "https://github.com/ch1209498273/XiTing"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "分享到"
            )
        )
        // 每日任务结算：每天仅一次
        val prefs = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val last = prefs.getString("last_share_date", "")
        if (last != today) {
            prefs.edit().putString("last_share_date", today).apply()
            EnergyStore.add(this, SHARE_GP_PER_DAY) // 分享产生能量球，回统计页收集
            Toast.makeText(this, "每日分享完成 · 获得 $SHARE_GP_PER_DAY 能量，回统计页收集 ⚡", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "今日分享奖励已领取，明天再来（每天一次）", Toast.LENGTH_SHORT).show()
        }
        renderStats()
    }

    /** 成长值体系：成长值=已收集能量；听剧/分享产生能量球待收集（3天过期） */
    private fun refreshPetPanel(allMs: Long, sessions: List<ListenSession>, now: Long) {
        val pet = pageStats.findViewById<PetView>(R.id.pet_view)
        val prefs = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
        // 旧版成长值（听剧分钟+分享奖励）一次性迁入已收集总量
        EnergyStore.migrateIfNeeded(this, allMs / 60000, prefs.getInt("share_bonus_gp", 0))
        val gp = EnergyStore.collectedTotal(this).toLong()
        val stage = PetView.stageOf(gp)
        pet.stage = stage
        val lastSessionAt = sessions.maxOfOrNull { it.start } ?: 0L
        pet.sleepy = lastSessionAt > 0 && (now - lastSessionAt) / (24L * 3600 * 1000) >= 3
        pet.totalMah = Stats.estimatedMah(allMs)
        pet.progress = if (stage < PetView.STAGE_KING) {
            val lo = PetView.THRESHOLDS[stage]
            val hi = PetView.THRESHOLDS[stage + 1]
            ((gp - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
        } else 1f

        // 图鉴预览：选中非当前形态时主精灵切换为该形态
        pet.stage = if (previewStage >= 0) previewStage else stage
        pet.hideProgress = previewStage >= 0 && previewStage != stage
        // 能量球装载与收集回调（回调在飞入动画完成后触发）
        pet.pending = EnergyStore.pending(this)
        pet.onCollect = { id ->
            val v = EnergyStore.collect(this, id)
            if (v > 0) {
                Toast.makeText(this, "充能 +$v 成长值 ⚡", Toast.LENGTH_SHORT).show()
            }
            renderStats()
        }

        // 进化提示（仅当上次记录的形态更低时弹一次）
        val seen = prefs.getInt("last_seen_stage", -1)
        if (seen in 0 until stage) {
            Toast.makeText(
                this,
                "🎉 进化！${PetView.stageName(seen)} → ${PetView.stageName(stage)}",
                Toast.LENGTH_LONG
            ).show()
        }
        if (seen != stage) prefs.edit().putInt("last_seen_stage", stage).apply()

        // 文案
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val sharedToday = prefs.getString("last_share_date", "") == today
        pageStats.findViewById<TextView>(R.id.pet_caption).text =
            if (pet.sleepy) {
                "${PetView.stageName(stage)} 打瞌睡了 · 听一集唤醒它"
            } else if (stage >= PetView.STAGE_KING) {
                "${PetView.stageName(stage)} · 成长值 $gp · 已至巅峰"
            } else {
                "${PetView.stageName(stage)} · 成长值 $gp / ${PetView.THRESHOLDS[stage + 1]}"
            }
        pageStats.findViewById<TextView>(R.id.pet_share).text =
            if (sharedToday) "今日分享已完成 ✓ · 明天再来 ›"
            else "每日分享 · 得 $SHARE_GP_PER_DAY 能量 ›"
        pet.startAnimating()
        buildGallery(stage)
    }

    /** 形态图鉴：点击缩略图预览该形态（含未解锁），再点一次恢复当前形态 */
    private fun buildGallery(currentStage: Int) {
        if (galleryBuiltStage == currentStage && galleryPreviewBuilt == previewStage) return
        galleryBuiltStage = currentStage
        galleryPreviewBuilt = previewStage
        val row = pageStats.findViewById<LinearLayout>(R.id.thumb_row)
        row.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (56 * density).toInt()
        for (i in 0..4) {
            val pv = PetView(this).apply {
                stage = i
                thumbMode = true
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = (5 * density).toInt()
                    marginEnd = (5 * density).toInt()
                }
                // 未解锁：灰暗；选中的预览项：绿框高亮
                if (i > currentStage) alpha = 0.25f
                if (i == previewStage || (previewStage < 0 && i == currentStage)) {
                    setBackgroundResource(R.drawable.bg_thumb_selected)
                }
                setOnClickListener {
                    previewStage = if (i == currentStage) -1 else i
                    galleryBuiltStage = -1 // 强制重建（刷新选中框）
                    renderStats()
                }
            }
            row.addView(pv)
        }
        // 图鉴说明文字
        pageStats.findViewById<TextView>(R.id.gallery_info).text = when {
            previewStage < 0 -> "点图鉴可预览后续形态"
            previewStage == currentStage -> "${PetView.stageName(previewStage)} · 当前形态"
            previewStage < currentStage -> "预览中：${PetView.stageName(previewStage)}（已解锁）"
            else -> "预览中：${PetView.stageName(previewStage)}（未解锁 · 需 ${PetView.THRESHOLDS[previewStage]} 成长值）"
        }
    }

    private var galleryPreviewBuilt = -2

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
        pageStats.findViewById<TextView>(R.id.sum_extra).text =
            "最长单次 ${fmtDur(sessions.maxOfOrNull { it.durationMs } ?: 0L)} · 共 $allCount 次息屏"
        updateMah()

        // 电能精灵：成长值驱动的五形态养成（听剧分钟 + 每日分享奖励）
        refreshPetPanel(allMs, sessions, now)

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
                setPadding(0, 12, 0, 12)
            }
            val left = TextView(this).apply {
                text = df.format(Date(s.start))
                setTextColor(0xFF444B54.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val right = TextView(this).apply {
                text = fmtDur(s.durationMs)
                setTextColor(0xFF111418.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
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

    // ───────────────────────── 设置页签 ─────────────────────────

    private fun bindSettings() {
        val prefs = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
        val page = pageSettings

        // 黑幕轻点直接解锁（默认关，防误触优先）
        val switchDirect = page.findViewById<android.widget.Switch>(R.id.switch_direct)
        switchDirect.isChecked = prefs.getBoolean("direct_unlock", false)
        switchDirect.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("direct_unlock", checked).apply()
        }

        // 黑幕显示时间与电量（默认关；开启后重锁保持暗态夜钟）
        val switchInfo = page.findViewById<android.widget.Switch>(R.id.switch_info)
        switchInfo.isChecked = prefs.getBoolean("black_info_show", false)
        switchInfo.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("black_info_show", checked).apply()
            OverlayService.instance?.reapplyBlack() // 黑幕显示中即时生效（无闪屏重挂）
        }

        // 黑幕播放控制（默认关：防误触）
        val switchMedia = page.findViewById<android.widget.Switch>(R.id.switch_media)
        switchMedia.isChecked = prefs.getBoolean("black_media_controls", false)
        switchMedia.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("black_media_controls", checked).apply()
            if (checked) {
                Toast.makeText(this, "已开启：黑幕唤醒后显示 ⏮ ⏸ ⏭ 控制键", Toast.LENGTH_SHORT).show()
            }
            OverlayService.instance?.reapplyBlack()
        }

        // 悬浮球样式：默认「息屏」文字，可换为已解锁的精灵形态
        page.findViewById<View>(R.id.row_bubble_style).setOnClickListener { showBubbleStyleDialog() }

        // 检查更新：跳转 GitHub Release 页面（App 保持零网络权限，由浏览器打开）
        page.findViewById<View>(R.id.row_update).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ch1209498273/XiTing/releases/latest"))
                )
            } catch (_: Exception) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }
        page.findViewById<TextView>(R.id.update_value).text = "当前 v${BuildConfig.VERSION_NAME}"

        // 分享给朋友（计入每日分享任务）
        page.findViewById<View>(R.id.row_share).setOnClickListener { sharePetStats() }

        // 开源地址点击
        page.findViewById<TextView>(R.id.about_repo).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ch1209498273/XiTing"))
                )
            } catch (_: Exception) {
            }
        }

        refreshBubbleStyleValue()
    }

    /** 悬浮球样式选择：默认「息屏」文字 + 已解锁形态 */
    private fun showBubbleStyleDialog() {
        val prefs = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
        val gp = allMs / 60000 + prefs.getInt("share_bonus_gp", 0)
        val unlocked = PetView.stageOf(gp)
        val labels = ArrayList<String>()
        val values = ArrayList<String>()
        labels.add("息屏（默认）")
        values.add("text")
        for (i in 0..unlocked) {
            labels.add(PetView.stageName(i))
            values.add("pet_$i")
        }
        val current = prefs.getString("bubble_style", "text") ?: "text"
        val checked = values.indexOf(current)
        android.app.AlertDialog.Builder(this)
            .setTitle("悬浮球样式")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which ->
                prefs.edit().putString("bubble_style", values[which]).apply()
                OverlayService.instance?.rebuildBubble()
                refreshBubbleStyleValue()
                Toast.makeText(this, "悬浮球已切换为「${labels[which]}」", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshBubbleStyleValue() {
        val style = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
            .getString("bubble_style", "text") ?: "text"
        pageSettings.findViewById<TextView>(R.id.bubble_style_value).text =
            if (style.startsWith("pet_")) {
                PetView.stageName(style.removePrefix("pet_").toIntOrNull() ?: 0) + "头像"
            } else "息屏"
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
