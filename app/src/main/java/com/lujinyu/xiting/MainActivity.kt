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
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnBattery: Button
    private lateinit var btnNotify: Button
    private lateinit var btnA11y: Button
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btn_overlay_perm)
        btnBattery = findViewById(R.id.btn_battery_perm)
        btnNotify = findViewById(R.id.btn_notify_perm)
        btnA11y = findViewById(R.id.btn_a11y_perm)
        btnStart = findViewById(R.id.btn_start)

        btnOverlay.setOnClickListener {
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

        btnBattery.setOnClickListener {
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

        btnNotify.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            } else {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            }
        }

        btnA11y.setOnClickListener {
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

    private fun refreshStates() {
        btnOverlay.text =
            if (Settings.canDrawOverlays(this)) "悬浮窗权限：已授予 ✓" else "① 悬浮窗权限：未授予（点此开启）"

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        btnBattery.text =
            if (pm.isIgnoringBatteryOptimizations(packageName)) "电池后台：已加白 ✓" else "② 电池后台：未加白（点此申请）"

        val notifyOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        btnNotify.text =
            if (notifyOk) "通知权限：已授予 ✓" else "③ 通知权限：未授予（点此申请）"

        btnA11y.text =
            if (isA11yEnabled()) "④ 无障碍全屏黑幕：已开启 ✓" else "④ 无障碍全屏黑幕（推荐，盖住手势条）：点此开启"
    }
}
