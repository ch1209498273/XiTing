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
    private lateinit var rowA11y: LinearLayout
    private lateinit var pillOverlay: TextView
    private lateinit var pillBattery: TextView
    private lateinit var pillNotify: TextView
    private lateinit var pillA11y: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rowOverlay = findViewById(R.id.row_overlay)
        rowBattery = findViewById(R.id.row_battery)
        rowNotify = findViewById(R.id.row_notify)
        rowA11y = findViewById(R.id.row_a11y)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillBattery = findViewById(R.id.pill_battery)
        pillNotify = findViewById(R.id.pill_notify)
        pillA11y = findViewById(R.id.pill_a11y)
        btnStart = findViewById(R.id.btn_start)

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

        rowA11y.setOnClickListener {
            val enabled = isA11yEnabled() && XiTingA11yService.instance != null
            if (!enabled) {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                }
            } else {
                Toast.makeText(this, "无障碍层已开启，黑幕可盖住手势条", Toast.LENGTH_SHORT).show()
            }
        }

        val footer = findViewById<TextView>(R.id.tv_footer)
        footer.text = "完全离线 · 不收集任何数据 · v${BuildConfig.VERSION_NAME} · ID ${BuildConfig.BUILD_ID}"

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
        val settingsOn = isA11yEnabled()
        val bound = XiTingA11yService.instance != null
        when {
            settingsOn && bound -> setPill(pillA11y, true, "已开启", "已开启")
            settingsOn -> setPill(pillA11y, false, "需重新开关", "需重新开关")
            else -> setPill(pillA11y, false, "去开启", "去开启")
        }
    }
}
