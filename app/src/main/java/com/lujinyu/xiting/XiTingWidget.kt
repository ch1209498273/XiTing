// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 桌面小部件：一键息屏 + 今日时长 + 精灵形态。
 * 「息屏」按钮 = 启动助手（若未运行）并直接切黑幕，一击到位；
 * 点其余区域打开应用。状态由 OverlayService/MainActivity 在状态变化时调用 refresh() 刷新。
 */
class XiTingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_main)
                val running = OverlayService.isRunning
                val c = AppLocales.wrap(context)
                views.setTextViewText(
                    R.id.widget_status,
                    c.getString(if (running) R.string.widget_active else R.string.widget_idle)
                )
                views.setTextViewText(R.id.widget_today, todayText(c))
                val gp = EnergyStore.collectedTotal(context)
                views.setTextViewText(
                    R.id.widget_pet,
                    c.getString(R.string.widget_pet_fmt, PetView.stageName(c, PetView.stageOf(gp.toLong())))
                )

                // 「息屏」：启动/复用助手并切黑幕（getForegroundService 需 API 26+，即本项目 minSdk）
                val piBlack = PendingIntent.getForegroundService(
                    context, 10,
                    Intent(context, OverlayService::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_black, piBlack)
                val piOpen = PendingIntent.getActivity(
                    context, 11,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, piOpen)

                AppWidgetManager.getInstance(context)
                    .updateAppWidget(ComponentName(context, XiTingWidget::class.java), views)
            } catch (_: Exception) {
            }
        }

        private fun todayText(context: Context): String {
            val sessions = SessionLog.sessions(context)
            val cal = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val todayStart = cal.timeInMillis
            var ms = 0L
            for (s in sessions) {
                val a = maxOf(s.start, todayStart)
                val b = minOf(s.end, todayStart + 24L * 3600 * 1000)
                if (b > a) ms += b - a
            }
            val mins = (ms / 60000).toInt()
            return context.getString(R.string.widget_today_fmt, fmtMin(mins))
        }

        private fun fmtMin(mins: Int): String = when {
            mins >= 60 -> {
                val h = mins / 60; val m = mins % 60
                if (m == 0) "${h}h" else "${h}h${m}m"
            }
            mins > 0 -> "${mins}m"
            else -> "0m"
        }
    }
}
