// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

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
        private const val REQ_RESTORE = 2001
        private const val REQ_EXPORT = 2002
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
        // 卸载重装恢复：启动后检查本机备份（设备ID匹配且本地为空）
        window.decorView.postDelayed({ checkRestore() }, 600)
    }

    // ───────────────────────── 导航 ─────────────────────────

    private fun switchTab(target: Int) {
        tab = target
        pageHome.visibility = if (target == TAB_HOME) View.VISIBLE else View.GONE
        pageStats.visibility = if (target == TAB_STATS) View.VISIBLE else View.GONE
        pageSettings.visibility = if (target == TAB_SETTINGS) View.VISIBLE else View.GONE
        appbarTitle.text = when (target) {
            TAB_HOME -> getString(R.string.app_name)
            TAB_STATS -> getString(R.string.title_stats)
            else -> getString(R.string.title_settings)
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
            val permErr = prefs()
                .getBoolean(Prefs.BUBBLE_PERM_ERROR, false)
            if (!Settings.canDrawOverlays(this) || permErr) {
                Toast.makeText(
                    this,
                    if (permErr) getString(R.string.perm_fix_hint) else getString(R.string.perm_need_overlay),
                    Toast.LENGTH_LONG
                ).show()
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
                Toast.makeText(this, getString(R.string.toast_overlay_granted), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.toast_battery_ok), Toast.LENGTH_SHORT).show()
            }
        }

        rowNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                Toast.makeText(this, getString(R.string.toast_notify_granted), Toast.LENGTH_SHORT).show()
            }
        }

        // 主按钮=助手服务控制（启动/停止）：看剧时的动作在悬浮球上
        // 通知栏「退出助手」/磁贴退出同样视为用户主动停止
        // （标志在服务端ACTION_EXIT里清除）

        cardBlack.setOnClickListener {
            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
                prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED, false).apply()
                Toast.makeText(this, getString(R.string.toast_assistant_stopped), Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
                prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED, true).apply()
                Toast.makeText(this, getString(R.string.toast_assistant_started), Toast.LENGTH_SHORT).show()
            }
            postRefresh()
        }

        // 定时关闭：听完这集 / 15/30/60分钟，到点自动收黑幕
        pageHome.findViewById<View>(R.id.timer_chip).setOnClickListener {
            val items = arrayOf(getString(R.string.timer_item_episode), getString(R.string.timer_15), getString(R.string.timer_30), getString(R.string.timer_60), getString(R.string.timer_cancel))
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.timer_title))
                .setItems(items) { _, which ->
                    if (which == 0) {
                        finishThisEpisode()
                        return@setItems
                    }
                    val minutes = when (which) {
                        1 -> 15L; 2 -> 30L; 3 -> 60L; else -> 0L
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
        val prefs = prefs()
        if (!OverlayService.isRunning && prefs.getBoolean(Prefs.ASSISTANT_WANTED, false)) {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        refreshStates()
        refreshHomeStats()
        if (tab == TAB_STATS) renderStats()
    }

    override fun onStop() {
        super.onStop()
        // 静默备份到公共下载目录（卸载不删除；设备ID绑定，重装可恢复）。
        // 挪到后台线程：save() 会读整个 sessions.json + 走 MediaStore
        // query/delete/insert 再写文件，每次切后台都在主线程做一遍会掉帧，
        // 甚至在低端机上触发 ANR。这里只发一次任务，不等待结果。
        // 传 applicationContext 而非 Activity：save() 只用 prefs/filesDir/MediaStore，
        // 都不需要 Activity，后台线程也不必持有正在销毁的 Activity。
        Thread({ BackupManager.save(applicationContext) }, "XiTing-backup").start()
    }

    /**
 * 重装恢复引导：**仅当本地无数据、且下载目录确有本机备份时**才提示。
 *
 * 原先只判 localGp==0 就弹窗，于是全新用户首次启动也会看到
 * 「你以前用过息屏听剧吗」的恢复引导——而他根本没有备份可恢复。
 * findBackup() 早就是为此写好的（查 Download/XiTing 并校验 ANDROID_ID），
 * 但一直没被调用，这里接通。
 *
 * findBackup 已在内部按设备过滤：非本机备份返回 null，同样不弹。
 * 它只在启动时跑一次（save() 是每次 onStop 都跑，所以那个才需要挪出主线程）。
 */
private fun checkRestore() {
        if (isFinishing) return
        if (EnergyStore.collectedTotal(this) > 0) return
        if (BackupManager.findBackup(this) == null) return
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_title))
            .setMessage(
                getString(R.string.restore_msg)
            )
            .setPositiveButton(getString(R.string.restore_pick)) { _, _ -> openBackupPicker() }
            .setNegativeButton(getString(R.string.restore_no), null)
            .show()
    }

    /** 打开系统文件选择器（初始定位到下载目录/XiTing） */
    private fun openBackupPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(
                    android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FXiTing")
                )
            }
            startActivityForResult(intent, REQ_RESTORE)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_picker_fail), Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val ok = BackupManager.exportToUri(this, uri)
            Toast.makeText(this, if (ok) getString(R.string.toast_export_ok) else getString(R.string.toast_export_fail), Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode == REQ_RESTORE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val obj = BackupManager.readFromUri(this, uri)
            if (obj == null) {
                Toast.makeText(this, getString(R.string.toast_invalid_backup), Toast.LENGTH_SHORT).show()
                return
            }
            val gp = obj.optInt("gp", 0)
            if (BackupManager.isSameDevice(this, obj)) {
                doRestore(obj, gp)
            } else {
                android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.restore_other_title))
                    .setMessage(getString(R.string.restore_other_msg, gp))
                    .setPositiveButton(getString(R.string.restore_ok_btn)) { _, _ -> doRestore(obj, gp) }
                    .setNegativeButton(getString(R.string.dlg_cancel), null)
                    .show()
            }
        }
    }

    private fun doRestore(obj: org.json.JSONObject, gp: Int) {
        if (BackupManager.restore(this, obj)) {
            Toast.makeText(this, getString(R.string.toast_restore_done, gp), Toast.LENGTH_LONG).show()
            refreshStates(); refreshHomeStats()
            if (tab == TAB_STATS) renderStats()
        } else {
            Toast.makeText(this, getString(R.string.toast_restore_fail), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 黑幕窗口盖上/拿走都会改变本窗口焦点：恰好作为状态刷新时机，
        // 否则从黑幕上解锁回来，胶囊会停留在「黑幕开启中」
        if (hasFocus) refreshStates()
    }

    /** 会话在 [from, to) 区间内的实际时长（跨天会话按天拆分，归属不串） */
    private fun overlapMs(s: ListenSession, from: Long, to: Long): Long {
        val a = maxOf(s.start, from)
        val b = minOf(s.end, to)
        return (b - a).coerceAtLeast(0)
    }

    private fun postRefresh() {
        Handler(Looper.getMainLooper()).postDelayed({ refreshStates(); refreshHomeStats() }, 400)
    }

    private fun refreshStates() {
        WidgetData.refreshAll(this) // 小部件状态同步
        val running = OverlayService.isRunning
        if (running) {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_on)
            pillStatus.setTextColor(0xFF157A4C.toInt())
            pillStatus.text = getString(R.string.status_running)
        } else {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_off)
            pillStatus.setTextColor(0xFF5F6570.toInt())
            pillStatus.text = getString(R.string.status_idle)
        }
        // 主按钮=助手控制：文案随运行状态变化
        heroTitle.text = if (running) getString(R.string.hero_title_stop) else getString(R.string.hero_title_start)
        pageHome.findViewById<TextView>(R.id.hero_sub).text =
            if (running) getString(R.string.hero_sub_stop)
            else getString(R.string.hero_sub_start)

        // 自愈：服务在跑、悬浮球未隐藏但球丢失（ColorOS 偶发吞掉纯浮窗）→ 自动重建
        OverlayService.instance?.let { svc ->
            val bubbleHidden = prefs()
                .getBoolean(Prefs.BUBBLE_HIDDEN, false)
            if (!svc.isBubbleVisible() && !bubbleHidden) {
                svc.rebuildBubble()
            }
        }
        val overlayOk = Settings.canDrawOverlays(this)
        val permError = prefs()
            .getBoolean(Prefs.BUBBLE_PERM_ERROR, false)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifyOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (permError) {
            // 权限表征与实际不一致（重装后 ColorOS）：引导关闭再重新开启悬浮窗
            setPill(pageHome.findViewById(R.id.pill_overlay), false, "", getString(R.string.pill_abnormal))
            pageHome.findViewById<TextView>(R.id.pill_overlay).setTextColor(0xFFD84315.toInt())
        } else {
            setPill(pageHome.findViewById(R.id.pill_overlay), overlayOk, getString(R.string.pill_on), getString(R.string.pill_off))
        }
        setPill(pageHome.findViewById(R.id.pill_battery), batteryOk, getString(R.string.pill_battery_on), getString(R.string.pill_battery_off))
        setPill(pageHome.findViewById(R.id.pill_notify), notifyOk, getString(R.string.pill_on), getString(R.string.pill_off))

        val remain = OverlayService.instance?.timerRemainingMs() ?: 0
        pageHome.findViewById<TextView>(R.id.timer_state).text =
            if (remain > 0) getString(R.string.timer_remain_fmt, remain / 60000 + 1) else getString(R.string.timer_none)
    }

    /** 防杀保活指南：ColorOS后台限制的分步设置引导 */
    private fun showKeepAliveGuide() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.keepalive_dlg_title))
            .setMessage(
                getString(
                    R.string.keepalive_msg,
                    if (batteryOk) getString(R.string.state_done) else getString(R.string.state_todo)
                )
            )
            .setPositiveButton(getString(R.string.keepalive_btn_battery)) { _, _ ->
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

    /** 听完这集再关：读取正在播放会话的进度，定时到本集片尾（个性化·定时关闭选项） */
    private fun finishThisEpisode() {
        val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val comp = ComponentName(this, NotificationListener::class.java)
        val sessions = try {
            mgr?.getActiveSessions(comp)
        } catch (e: SecurityException) {
            null // 未授予通知使用权
        }
        if (sessions == null) {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.locale_dlg_title))
                .setMessage(getString(R.string.locale_dlg_msg))
                .setPositiveButton(getString(R.string.locale_dlg_go)) { _, _ ->
                    try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
                }
                .setNegativeButton(getString(R.string.locale_dlg_fallback)) { _, _ ->
                    startService(
                        Intent(this, OverlayService::class.java)
                            .setAction(OverlayService.ACTION_SET_TIMER)
                            .putExtra(OverlayService.EXTRA_MINUTES, 30L)
                    )
                }
                .show()
            return
        }
        var bestRemaining = -1L
        for (c in sessions) {
            val st = c.playbackState ?: continue
            if (st.state != PlaybackState.STATE_PLAYING) continue
            val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (dur <= 0) continue
            val speed = st.playbackSpeed
            val pos = st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * speed).toLong()
            val rem = dur - pos
            if (rem > 0 && (bestRemaining < 0 || rem < bestRemaining)) bestRemaining = rem
        }
        if (bestRemaining <= 0) {
            Toast.makeText(this, getString(R.string.toast_no_progress), Toast.LENGTH_LONG).show()
            return
        }
        val endAt = System.currentTimeMillis() + bestRemaining + 4000 // 集尾缓冲4秒
        startService(
            Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_TIMER)
                .putExtra(OverlayService.EXTRA_END_AT, endAt)
        )
        val mins = bestRemaining / 60000
        val secs = bestRemaining % 60000 / 1000
        Toast.makeText(this, getString(R.string.toast_episode_done, mins, secs), Toast.LENGTH_LONG).show()
    }

    /** 成就明细弹窗 */
    private fun showAchievements() {
        val unlocked = prefs()
            .getStringSet(Prefs.ACH_UNLOCKED, emptySet()) ?: emptySet()
        val gotCount = unlocked.size
        val msg = Achievements.ALL.joinToString("\n\n") { a ->
            (if (a.id in unlocked) "✅ " else "🔒 ") + a.icon + " " + Achievements.title(this, a) + " · " + Achievements.desc(this, a)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.ach_dlg_title_fmt, gotCount, Achievements.ALL.size))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.dlg_ok), null)
            .show()
    }

    // ───────────────────────── 省电实测校准向导 ─────────────────────────

    private val calibHandler = Handler(Looper.getMainLooper())

    /** 皮肤图鉴弹窗：6 款皮肤，已解锁可穿戴 */
    private fun showSkinGallery() {
        val totalMs = SessionLog.sessions(this).sumOf { it.durationMs }
        val streakInfo = Streaks.compute(SessionLog.sessions(this))
        val mah = Stats.estimatedMah(totalMs)
        val active = PetSkins.active(this)
        val msg = PetSkins.ALL.joinToString("\n\n") { s ->
            val unlocked = PetSkins.isUnlocked(this, s, totalMs, streakInfo.current, mah)
            val wearing = s.id == active.id
            val prefix = when {
                wearing -> "⭐ "
                unlocked -> "✅ "
                else -> "🔒 "
            }
            prefix + PetSkins.name(this, s) + " · " + PetSkins.cond(this, s)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.gallery_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.dlg_ok), null)
            .show()
    }

    private fun onMahCardClick() {
        val calib = PowerCalib.calibrated(this)
        val ambient = PowerCalib.ambientStats(this)
        val msg = StringBuilder().apply {
            append(
                if (calib != null) {
                    val rate = (calib.first - calib.second) / 1000.0
                    getString(R.string.calib_msg_calibrated,
                        SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(calib.third)),
                        rate.toFloat())
                } else {
                    getString(R.string.calib_msg_uncalibrated)
                }
            )
            ambient?.let { (ma, n) ->
                append(getString(R.string.calib_msg_ambient, ma.toFloat(), n))
            }
            append(getString(R.string.calib_msg_flow))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.calib_title))
            .setMessage(msg)
            .setPositiveButton(if (calib != null) getString(R.string.calib_btn_restart) else getString(R.string.calib_btn_start)) { _, _ -> calibStep1() }
            .setNegativeButton(getString(R.string.calib_btn_close), null)
            .show()
    }

    /** 步骤1：亮屏播放视频，采样 3 分钟 */
    private fun calibStep1() {
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.calib_step1_title))
            .setMessage(getString(R.string.calib_step1_init))
            .setCancelable(false)
            .create()
        dlg.show()
        var left = 180
        var sum = 0L
        var n = 0
        val tick = object : Runnable {
            override fun run() {
                PowerCalib.sampleNow(this@MainActivity)?.let { sum += it; n++ }
                left -= 30
                if (left <= 0) {
                    dlg.dismiss()
                    if (n < 4) {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_calib_too_few), Toast.LENGTH_LONG).show()
                        return
                    }
                    if (!OverlayService.isRunning) {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_calib_need_service), Toast.LENGTH_LONG).show()
                        return
                    }
                    startService(
                        Intent(this@MainActivity, OverlayService::class.java)
                            .setAction(OverlayService.ACTION_CALIB_START)
                            .putExtra(OverlayService.EXTRA_CALIB_ON_UA, sum / n)
                    )
                    waitForCalibration()
                } else {
                    dlg.setMessage("请保持亮屏播放视频，不要操作手机\n剩余 ${left / 60}:${"%02d".format(left % 60)}（已采样 $n 次）")
                    calibHandler.postDelayed(this, 30_000)
                }
            }
        }
        calibHandler.postDelayed(tick, 30_000)
    }

    /** 步骤2由服务完成（自动进黑屏采样后自动退出）；这里轮询结果 */
    private fun waitForCalibration() {
        val before = PowerCalib.calibrated(this)?.third ?: 0L
        var waited = 0
        val poll = object : Runnable {
            override fun run() {
                waited += 20
                val c = PowerCalib.calibrated(this@MainActivity)
                if (c != null && c.third != before) {
                    val rate = (c.first - c.second) / 1000.0
                    renderStats()
                    Toast.makeText(this@MainActivity, getString(R.string.toast_calib_done, rate.toFloat()), Toast.LENGTH_LONG).show()
                } else if (waited < 330) {
                    calibHandler.postDelayed(this, 20_000)
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_calib_timeout), Toast.LENGTH_SHORT).show()
                }
            }
        }
        calibHandler.postDelayed(poll, 20_000)
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
        val nowMs = System.currentTimeMillis()
        sessions.forEach { s ->
            allMs += s.durationMs
            todayMs += overlapMs(s, todayStart, todayStart + 24L * 3600 * 1000)
            weekMs += overlapMs(s, weekStart, nowMs)
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
        pageStats.findViewById<View>(R.id.card_ach).setOnClickListener { showAchievements() }
        pageStats.findViewById<View>(R.id.card_mah).setOnClickListener { onMahCardClick() }
        pageStats.findViewById<View>(R.id.btn_gallery).setOnClickListener { showSkinGallery() }
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

    /** 每日分享任务：每天首次分享 +30 成长值（精灵成长值 = 听剧分钟 + 分享奖励） */
    private fun sharePetStats() {
        val sessions = SessionLog.sessions(this)
        val allMs = sessions.sumOf { it.durationMs }
        val mah = Stats.estimatedMah(allMs)
        val pet = pageStats.findViewById<PetView>(R.id.pet_view)
        val text = getString(R.string.share_text, fmtDur(allMs), mah, PetView.stageName(this, pet.stage))
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.share_chooser)
            )
        )
        // 每日任务结算：每天仅一次
        val prefs = prefs()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val last = prefs.getString(Prefs.LAST_SHARE_DATE, "")
        if (last != today) {
            prefs.edit().putString(Prefs.LAST_SHARE_DATE, today).apply()
            EnergyStore.add(this, SHARE_GP_PER_DAY) // 分享产生待收成长值
            Toast.makeText(
                this,
                getString(R.string.toast_share_done, SHARE_GP_PER_DAY),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_share_claimed), Toast.LENGTH_SHORT).show()
        }
        renderStats()
    }

    /** 成长值体系：成长值=已收集能量；听剧/分享产生能量球待收集（3天过期） */
    private fun refreshPetPanel(allMs: Long, sessions: List<ListenSession>, now: Long) {
        val pet = pageStats.findViewById<PetView>(R.id.pet_view)
        val prefs = prefs()
        // 旧版成长值（听剧分钟+分享奖励）一次性迁入已收集总量。
        // share_bonus_gp 是「只读遗留键」：当前版本从不写入它，但早期版本写过，
        // 仍在老用户的 prefs 里。不读取会让这些用户的迁移加成静默归零，
        // 因此这行必须保留——它不是死代码，是升级兼容点。
        EnergyStore.migrateIfNeeded(this, allMs / 60000, prefs.getInt(Prefs.SHARE_BONUS_GP_LEGACY, 0))
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
        // 成长值装载与收取回调（点击左侧条=收取全部；回调在飞入动画完成后触发）
        pet.pending = EnergyStore.pending(this)
        pet.onCollectAll = {
            val v = EnergyStore.collectAll(this)
            if (v > 0) {
                Toast.makeText(this, getString(R.string.toast_charge, v), Toast.LENGTH_SHORT).show()
            }
            renderStats()
        }
        // ? 说明：成长值机制细节（统一口径：成长值 = 待收 + 已收）
        pet.onHelp = {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.help_title))
                .setMessage(getString(R.string.help_msg))
                .setPositiveButton(getString(R.string.keepalive_btn_ok), null)
                .show()
        }

        // 进化提示（仅当上次记录的形态更低时弹一次）
        val seen = prefs.getInt(Prefs.LAST_SEEN_STAGE, -1)
        if (seen in 0 until stage) {
            Toast.makeText(
                this,
                "🎉 进化！${PetView.stageName(this, seen)} → ${PetView.stageName(this, stage)}",
                Toast.LENGTH_LONG
            ).show()
        }
        if (seen != stage) prefs.edit().putInt(Prefs.LAST_SEEN_STAGE, stage).apply()

        // 设置页分享行状态：今天是否还能领
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        pageSettings.findViewById<TextView>(R.id.share_sub).text =
            if (prefs.getString(Prefs.LAST_SHARE_DATE, "") == todayStr) getString(R.string.share_sub_claimed)
            else getString(R.string.share_sub_avail, SHARE_GP_PER_DAY)
        // 文案
        pageStats.findViewById<TextView>(R.id.pet_caption).text =
            if (pet.sleepy) {
                getString(R.string.pet_caption_sleepy, PetView.stageName(this, stage))
            } else if (stage >= PetView.STAGE_KING) {
                getString(R.string.pet_caption_max, PetView.stageName(this, stage), gp)
            } else {
                getString(R.string.pet_caption_progress, PetView.stageName(this, stage), gp, PetView.THRESHOLDS[stage + 1])
            }
        // 分享入口在「设置」页（统计页不再重复）
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
            // FrameLayout：全彩缩略图 + 角落锁标识（未解锁不影响查看）
            val frame = android.widget.FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = (5 * density).toInt()
                    marginEnd = (5 * density).toInt()
                }
                if (i == previewStage || (previewStage < 0 && i == currentStage)) {
                    setBackgroundResource(R.drawable.bg_thumb_selected)
                }
            }
            val pv = PetView(this).apply {
                stage = i
                thumbMode = true
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size)
                setOnClickListener {
                    previewStage = if (i == currentStage) -1 else i
                    galleryBuiltStage = -1 // 强制重建（刷新选中框）
                    renderStats()
                }
            }
            frame.addView(pv)
            if (i > currentStage) {
                // 小锁角标（右下角，不遮挡主体）
                frame.addView(
                    TextView(this).apply {
                        text = "🔒"
                        textSize = 9f
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.BOTTOM or android.view.Gravity.END
                        )
                        setBackgroundColor(0x66000000)
                    }
                )
            }
            row.addView(frame)
        }
        // 图鉴选中说明合并进主文案（上方 caption 已显示，不另占行）
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
            RANGE_TODAY -> getString(R.string.label_today) to todayMs
            RANGE_WEEK -> getString(R.string.label_week) to weekMs
            else -> getString(R.string.label_total) to allMs
        }
        val calib = PowerCalib.calibrated(this)
        val mah = calib?.let { PowerCalib.calibratedSavingMah(this, ms) } ?: Stats.estimatedMah(ms)
        val basis = if (calib != null) getString(R.string.saved_basis_measured) else getString(R.string.saved_basis_model)
        pageStats.findViewById<TextView>(R.id.sum_mah).text =
            getString(R.string.sum_saved_fmt, label, mah, basis)
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
            val tv = overlapMs(s, todayStart, todayStart + 24L * 3600 * 1000)
            if (tv > 0) { todayMs += tv; todayCount++ }
            val wv = overlapMs(s, weekStart, now)
            if (wv > 0) { weekMs += wv; weekCount++ }
        }
        val allCount = sessions.size

        pageStats.findViewById<TextView>(R.id.sum_today).text = fmtDur(todayMs)
        pageStats.findViewById<TextView>(R.id.sum_today_count).text = getString(R.string.fmt_sessions, todayCount)
        pageStats.findViewById<TextView>(R.id.sum_week).text = fmtDur(weekMs)
        pageStats.findViewById<TextView>(R.id.sum_week_count).text = getString(R.string.fmt_sessions, weekCount)
        pageStats.findViewById<TextView>(R.id.sum_all).text = fmtDur(allMs)
        pageStats.findViewById<TextView>(R.id.sum_all_count).text = getString(R.string.fmt_sessions, allCount)
        pageStats.findViewById<TextView>(R.id.sum_extra).text =
            getString(R.string.sum_extra_fmt, fmtDur(sessions.maxOfOrNull { it.durationMs } ?: 0L), allCount)
        updateMah()

        // 精灵成就（精灵二期·一期）：依据统计评估解锁并渲染
        val gpNow = EnergyStore.collectedTotal(this)
        val stageNow = PetView.stageOf(gpNow.toLong())
        val listenDays = sessions
            .map { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.start)) }
            .distinct().size
        val freshAch = Achievements.evaluate(
            this, allMs, sessions.size,
            sessions.maxOfOrNull { it.durationMs } ?: 0L, listenDays, stageNow
        )
        freshAch.take(2).forEach {
            Toast.makeText(this, getString(R.string.ach_toast_fmt, it.icon, Achievements.title(this, it)), Toast.LENGTH_LONG).show()
        }
        val (gotCount, achSub) = Achievements.summary(this)
        pageStats.findViewById<TextView>(R.id.ach_title).text = getString(R.string.ach_dlg_title_fmt, gotCount, Achievements.ALL.size)
        pageStats.findViewById<TextView>(R.id.ach_sub).text = achSub
        // streak（先于皮肤评估：皮肤条件依赖连续天数）
        val streakInfo = Streaks.compute(sessions)

        // 精灵皮肤：应用所穿皮肤的色相
        val petView = pageStats.findViewById<PetView>(R.id.pet_view)
        val activeSkin = PetSkins.active(this)
        petView.skinHue = activeSkin.hue
        val mahSaved = Stats.estimatedMah(allMs)
        val freshSkins = PetSkins.evaluate(this, allMs, streakInfo.current, mahSaved)
        freshSkins.take(2).forEach {
            Toast.makeText(this, getString(R.string.toast_skin_unlock, PetSkins.name(this, it)), Toast.LENGTH_LONG).show()
        }
        // 注：card_ach / card_mah / btn_gallery 的点击监听统一在 bindStats() 注册一次，
        // 不在这里重复绑定——renderStats() 会被切页、onResume、onCollectAll、恢复备份
        // 以及校准轮询多次触发，每次都重新 setOnClickListener 是纯浪费
        // （btn_gallery 此前甚至被绑了两遍，内容完全相同）。

        // streak 展示
        var streakText = getString(R.string.streak_fmt, streakInfo.current)
        if (streakInfo.best > streakInfo.current) {
            streakText += " · " + getString(R.string.streak_best_fmt, streakInfo.best)
        }
        pageStats.findViewById<TextView>(R.id.streak_chip).text = streakText

        // 电能精灵：成长值驱动的五形态养成（听剧分钟 + 每日分享奖励）
        refreshPetPanel(allMs, sessions, now)

        // 近7天柱状图（含今天，共7天）
        val dayLabels = ArrayList<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val dayStart = todayStart - i * 24L * 3600 * 1000
            val dayEnd = dayStart + 24L * 3600 * 1000
            var ms = 0L
            sessions.forEach { s ->
                ms += overlapMs(s, dayStart, dayEnd)
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
                text = getString(R.string.list_empty)
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
            getString(R.string.pager_info_fmt, listPage + 1, totalPages, visible.size)
        pageStats.findViewById<TextView>(R.id.btn_prev).alpha = if (listPage == 0) 0.35f else 1f
        pageStats.findViewById<TextView>(R.id.btn_next).alpha = if (listPage >= totalPages - 1) 0.35f else 1f
    }

    private fun dayLabel(daysAgo: Int): String = when (daysAgo) {
        0 -> getString(R.string.day_today)
        1 -> getString(R.string.day_yesterday)
        else -> {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            String.format(Locale.getDefault(), "%02d-%02d",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }

    // ───────────────────────── 设置页签 ─────────────────────────

    private fun bindSettings() {
        val prefs = prefs()
        val page = pageSettings

        // 黑幕轻点直接解锁（默认关，防误触优先）
        val switchDirect = page.findViewById<android.widget.Switch>(R.id.switch_direct)
        switchDirect.isChecked = prefs.getBoolean(Prefs.DIRECT_UNLOCK, false)
        switchDirect.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.DIRECT_UNLOCK, checked).apply()
        }

        // 黑幕显示时间与电量（默认关；开启后重锁保持暗态夜钟）
        val switchInfo = page.findViewById<android.widget.Switch>(R.id.switch_info)
        switchInfo.isChecked = prefs.getBoolean(Prefs.BLACK_INFO_SHOW, false)
        switchInfo.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.BLACK_INFO_SHOW, checked).apply()
            OverlayService.instance?.reapplyBlack() // 黑幕显示中即时生效（无闪屏重挂）
        }

        // 黑幕播放控制（默认关：防误触）
        val switchMedia = page.findViewById<android.widget.Switch>(R.id.switch_media)
        switchMedia.isChecked = prefs.getBoolean(Prefs.BLACK_MEDIA_CONTROLS, false)
        switchMedia.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.BLACK_MEDIA_CONTROLS, checked).apply()
            if (checked) {
                Toast.makeText(this, getString(R.string.toast_media_on), Toast.LENGTH_SHORT).show()
            }
            OverlayService.instance?.reapplyBlack()
        }

        // 悬浮球样式：默认「息屏」文字，可换为已解锁的精灵形态
        page.findViewById<View>(R.id.row_bubble_style).setOnClickListener { showBubbleStyleDialog() }

        // 耳机拔出自动返回视频（默认开）
        val switchHeadset = page.findViewById<android.widget.Switch>(R.id.switch_headset)
        switchHeadset.isChecked = prefs.getBoolean(Prefs.SWITCH_HEADSET, true)
        switchHeadset.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.SWITCH_HEADSET, checked).apply()
        }

        // 显示悬浮球（隐藏后助手照常运行，通知栏/此处均可恢复）
        val switchBubble = page.findViewById<android.widget.Switch>(R.id.switch_bubble)
        switchBubble.isChecked = !prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false)
        switchBubble.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.BUBBLE_HIDDEN, !checked).apply()
            OverlayService.instance?.setBubbleVisible(checked)
        }

        // 导出数据备份（SAF 手动备份）
        page.findViewById<View>(R.id.row_export).setOnClickListener {
            val name = "XiTing-backup-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.json"
            try {
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, name),
                    REQ_EXPORT
                )
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_picker_fail), Toast.LENGTH_SHORT).show()
            }
        }

        // 从备份文件恢复（复用恢复弹窗与设备校验）
        page.findViewById<View>(R.id.row_restore).setOnClickListener { openBackupPicker() }

        // 检查更新：跳转 GitHub Release 页面（App 保持零网络权限，由浏览器打开）
        page.findViewById<View>(R.id.row_update).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ch1209498273/XiTing/releases/latest"))
                )
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_browser_fail), Toast.LENGTH_SHORT).show()
            }
        }
        page.findViewById<TextView>(R.id.update_value).text = getString(R.string.update_value_fmt, BuildConfig.VERSION_NAME)

        // 分享给朋友（计入每日分享任务）
        page.findViewById<View>(R.id.row_share).setOnClickListener { sharePetStats() }

        // 关于（二级页面：介绍/隐私/开源/许可）
        page.findViewById<View>(R.id.row_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        refreshBubbleStyleValue()
    }

    /** 悬浮球样式选择：带实时预览（每项直接显示该样式的实际长相） */
    private fun showBubbleStyleDialog() {
        val prefs = prefs()
        val gp = EnergyStore.collectedTotal(this).toLong()
        val unlocked = PetView.stageOf(gp)
        val labels = ArrayList<String>()
        val values = ArrayList<String>()
        labels.add(getString(R.string.bubble_default))
        values.add("text")
        // 全部形态都列出（含未解锁：可预览外观，但不能选中）
        for (i in 0..4) {
            labels.add(
                if (i <= unlocked) PetView.stageName(this, i)
                else getString(R.string.bubble_locked_fmt, PetView.stageName(this, i), PetView.THRESHOLDS[i])
            )
            values.add("pet_$i")
        }
        val current = prefs.getString(Prefs.BUBBLE_STYLE, "text") ?: "text"
        val density = resources.displayMetrics.density
        val adapter = object : android.widget.ArrayAdapter<String>(this, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                    if (values[position] == current) setBackgroundColor(0x1A1E8E5A)
                }
                if (position == 0) {
                    // 「息屏」文字样式预览
                    row.addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.bubble_label_off)
                            textSize = 13f
                            setTextColor(0xFFFFFFFF.toInt())
                            gravity = android.view.Gravity.CENTER
                            val bg = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setColor(0xB3000000.toInt())
                            }
                            background = bg
                            layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
                        }
                    )
                } else {
                    // 全部形态显示真实彩色效果（未解锁用 🔒 标签区分，不灰化——要看就看真实样子）
                    row.addView(
                        BubblePetView(this@MainActivity).apply {
                            stage = position - 1
                            layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
                        }
                    )
                }
                row.addView(
                    TextView(this@MainActivity).apply {
                        text = labels[position]
                        textSize = 15f
                        setTextColor(0xFF111418.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = (16 * density).toInt() }
                    }
                )
                return row
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.bubble_dlg_title))
            .setAdapter(adapter) { d, which ->
                val stageOfItem = which - 1
                if (stageOfItem in 0..4 && stageOfItem > unlocked) {
                    // 未解锁：可预览外观但不能选中
                    Toast.makeText(
                        this,
                        getString(R.string.toast_bubble_locked, PetView.stageName(this, stageOfItem), PetView.THRESHOLDS[stageOfItem]),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setAdapter
                }
                prefs.edit().putString(Prefs.BUBBLE_STYLE, values[which]).apply()
                OverlayService.instance?.rebuildBubble()
                refreshBubbleStyleValue()
                val name = if (which == 0) getString(R.string.bubble_label_off) else PetView.stageName(this, stageOfItem)
                Toast.makeText(this, getString(R.string.toast_bubble_set, name), Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.dlg_cancel), null)
            .show()
    }

    private fun refreshBubbleStyleValue() {
        val style = prefs()
            .getString(Prefs.BUBBLE_STYLE, "text") ?: "text"
        // 行内直接显示当前悬浮球的真实样子 + 名称
        val box = pageSettings.findViewById<LinearLayout>(R.id.bubble_style_preview)
        box.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        if (style.startsWith("pet_")) {
            val st = (style.removePrefix("pet_").toIntOrNull() ?: 0).coerceIn(0, 4)
            box.addView(
                BubblePetView(this).apply {
                    stage = st
                    layoutParams = LinearLayout.LayoutParams(size, size)
                }
            )
            pageSettings.findViewById<TextView>(R.id.bubble_style_value).text = PetView.stageName(this, st)
        } else {
            box.addView(
                TextView(this).apply {
                    text = getString(R.string.bubble_label_off)
                    textSize = 12f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = android.view.Gravity.CENTER
                    val bg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(0xB3000000.toInt())
                    }
                    background = bg
                    layoutParams = LinearLayout.LayoutParams(size, size)
                }
            )
            pageSettings.findViewById<TextView>(R.id.bubble_style_value).text = getString(R.string.bubble_value_default)
        }
    }

    // ───────────────────────── 通用 ─────────────────────────

    private fun fmtDur(ms: Long): String {
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
}
