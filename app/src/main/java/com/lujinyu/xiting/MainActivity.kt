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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var rowOverlay: LinearLayout
    private lateinit var rowBattery: LinearLayout
    private lateinit var rowNotify: LinearLayout
    private lateinit var pillOverlay: TextView
    private lateinit var pillBattery: TextView
    private lateinit var pillNotify: TextView
    private lateinit var btnStart: Button
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
        btnStart = findViewById(R.id.btn_start)
        statsLine1 = findViewById(R.id.stats_line1)
        statsLine2 = findViewById(R.id.stats_line2)

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

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "悬浮球已显示，去视频App里点它吧", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
        refreshStats()
    }

    private fun refreshStats() {
        val (totalMs, count) = Stats.totals(this)
        val minutes = totalMs / 60000
        val h = minutes / 60
        val m = minutes % 60
        val dur = if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
        statsLine1.text = "累计息屏听剧 $dur（$count 次）"
        statsLine2.text = "估算省电 ≈ ${Stats.estimatedMah(totalMs)} mAh（按OLED屏幕功耗估算）"
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
        setPill(pillOverlay, Settings.canDrawOverlays(this), "已开启", "去开启")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        setPill(pillBattery, pm.isIgnoringBatteryOptimizations(packageName), "已加白", "去加白")
        val notifyOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        setPill(pillNotify, notifyOk, "已开启", "去开启")
    }
}
