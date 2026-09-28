// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import java.util.Calendar
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {

    private lateinit var rowOverlay: LinearLayout
    private lateinit var rowBattery: LinearLayout
    private lateinit var rowNotify: LinearLayout
    private lateinit var pillOverlay: TextView
    private lateinit var pillBattery: TextView
    private lateinit var pillNotify: TextView
    private lateinit var pillStatus: TextView
    private lateinit var btnOverlayToggle: Button
    private lateinit var btnService: Button
    private lateinit var statsLine1: TextView
    private lateinit var statsLine2: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rowOverlay = findViewById(R.id.row_overlay)
        rowBattery = findViewById(R.id.row_battery)
        rowNotify = findViewById(R.id.row_notify)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillBattery = findViewById(R.id.pill_battery)
        pillNotify = findViewById(R.id.pill_notify)
        pillStatus = findViewById(R.id.pill_status)
        btnOverlayToggle = findViewById(R.id.btn_overlay_toggle)
        btnService = findViewById(R.id.btn_service)
        statsLine1 = findViewById(R.id.stats_line1)
        statsLine2 = findViewById(R.id.stats_line2)
        findViewById<LinearLayout>(R.id.stats_card).setOnClickListener { openStats() }

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

        btnOverlayToggle.setOnClickListener {
            when {
                !OverlayService.isRunning ->
                    Toast.makeText(this, "请先点下方「启动助手」", Toast.LENGTH_SHORT).show()
                OverlayService.instance?.isAnyBlackShowing() == true -> OverlayService.instance?.toggleOverlay()
                else -> OverlayService.instance?.toggleOverlay()
            }
            postRefresh()
        }

        btnService.setOnClickListener {
            if (OverlayService.isRunning) {
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
            } else {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
            postRefresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
        refreshStats()
    }

    private fun openStats() {
        startActivity(Intent(this, StatsActivity::class.java))
    }

    private fun refreshStats() {
        val sessions = SessionLog.sessions(this)
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        var todayMs = 0L
        var allMs = 0L
        sessions.forEach { s ->
            allMs += s.durationMs
            if (s.start >= todayStart) todayMs += s.durationMs
        }
        val todayMin = todayMs / 60000
        statsLine1.text = "今日息屏听剧 ${todayMin} 分钟"
        statsLine2.text = "累计 ${fmtDur(allMs)} · 估算省电 ≈ ${Stats.estimatedMah(allMs)} mAh · 点看明细"
    }

    private fun fmtDur(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}小时${m}分" else "${m}分钟"
    }

    private fun isA11yEnabled(): Boolean =
        Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.contains(packageName) == true

    private fun setPill(pill: TextView, on: Boolean, onText: String, offText: String) {
        pill.text = if (on) onText else offText
        pill.setBackgroundResource(if (on) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
        pill.setTextColor(if (on) 0xFF157A4C.toInt() else 0xFF5F6570.toInt())
    }

    private fun refreshStates() {
        // 头部状态胶囊
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

        // 黑幕模式按钮
        when {
            !running -> {
                btnOverlayToggle.text = "先启动助手"
                btnOverlayToggle.isEnabled = false
                btnOverlayToggle.alpha = 0.5f
            }
            OverlayService.instance?.isAnyBlackShowing() == true -> {
                btnOverlayToggle.text = "解除黑幕"
                btnOverlayToggle.isEnabled = true
                btnOverlayToggle.alpha = 1f
            }
            else -> {
                btnOverlayToggle.text = "开启黑幕"
                btnOverlayToggle.isEnabled = true
                btnOverlayToggle.alpha = 1f
            }
        }

        // 助手开关按钮
        btnService.text = if (running) "停止助手" else "启动助手"

        // 权限胶囊
        setPill(pillOverlay, Settings.canDrawOverlays(this), "已开启", "去开启")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        setPill(pillBattery, pm.isIgnoringBatteryOptimizations(packageName), "已加白", "去加白")
        val notifyOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        setPill(pillNotify, notifyOk, "已开启", "去开启")

        // 页脚水印
        val footer = findViewById<TextView>(R.id.tv_footer)
        footer.text = "完全离线 · 不收集任何数据 · v${BuildConfig.VERSION_NAME} · ID ${BuildConfig.BUILD_ID}"
    }

    private fun postRefresh() {
        Handler(Looper.getMainLooper()).postDelayed({ refreshStates() }, 400)
    }
}
