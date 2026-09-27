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
    private lateinit var rowAds: LinearLayout
    private lateinit var pillOverlay: TextView
    private lateinit var pillBattery: TextView
    private lateinit var pillNotify: TextView
    private lateinit var pillA11y: TextView
    private lateinit var pillAds: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rowOverlay = findViewById(R.id.row_overlay)
        rowBattery = findViewById(R.id.row_battery)
        rowNotify = findViewById(R.id.row_notify)
        rowA11y = findViewById(R.id.row_a11y)
        rowAds = findViewById(R.id.row_ads)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillBattery = findViewById(R.id.pill_battery)
        pillNotify = findViewById(R.id.pill_notify)
        pillA11y = findViewById(R.id.pill_a11y)
        pillAds = findViewById(R.id.pill_ads)
        btnStart = findViewById(R.id.btn_start)

        // github公开版：广告跳过引擎已剥离，⑥行隐藏
        rowAds.visibility = if (BuildConfig.ADS_ENABLED) View.VISIBLE else View.GONE

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
            val enabled = isA11yEnabled()
            if (!enabled) {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                }
            } else {
                Toast.makeText(this, "无障碍层已开启，黑幕可盖住手势条", Toast.LENGTH_SHORT).show()
            }
        }

        rowAds.setOnClickListener {
            val sp = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
            val next = (sp.getInt(XiTingA11yService.KEY_ADS_MODE, XiTingA11yService.MODE_CURATED) + 1) % 3
            sp.edit().putInt(XiTingA11yService.KEY_ADS_MODE, next).apply()
            val label = when (next) {
                XiTingA11yService.MODE_OFF -> "已关闭"
                XiTingA11yService.MODE_CURATED -> "主流视频App"
                else -> "全部App（含开屏广告）"
            }
            Toast.makeText(this, "广告自动跳过：$label", Toast.LENGTH_SHORT).show()
            refreshStates()
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
        setPill(pillA11y, isA11yEnabled(), "已开启", "去开启")

        if (BuildConfig.ADS_ENABLED) {
            val mode = getSharedPreferences("xiiting_prefs", MODE_PRIVATE)
                .getInt(XiTingA11yService.KEY_ADS_MODE, XiTingA11yService.MODE_CURATED)
            pillAds.setBackgroundResource(R.drawable.bg_pill_off)
            pillAds.setTextColor(getColor(android.R.color.darker_gray))
            pillAds.text = when (mode) {
                XiTingA11yService.MODE_OFF -> "已关闭"
                XiTingA11yService.MODE_CURATED -> "视频App"
                else -> "全部App"
            }
        }
    }
}
