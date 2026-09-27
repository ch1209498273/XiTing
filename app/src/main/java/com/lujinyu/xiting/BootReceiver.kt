package com.lujinyu.xiting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** 开机自启：只拉起悬浮球服务，不自动进黑幕（ColorOS需在自启动管理里放行本App） */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                context.startForegroundService(Intent(context, OverlayService::class.java))
                Log.d("XiTing", "boot completed: service started")
            } catch (e: Exception) {
                Log.d("XiTing", "boot start failed: $e")
            }
        }
    }
}
