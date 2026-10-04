// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 桌面小部件：真实精灵形象（PetView 离屏渲染位图）+ 成长值进度条
 * + 今日/累计时长 + 一键息屏。状态变化时由 OverlayService/MainActivity 调 refresh()。
 */
class XiTingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            try {
                val c = AppLocales.wrap(context)
                val views = RemoteViews(context.packageName, R.layout.widget_main)
                val running = OverlayService.isRunning
                views.setTextViewText(
                    R.id.widget_status,
                    c.getString(if (running) R.string.widget_active else R.string.widget_idle)
                )

                // 精灵：真实 PetView 离屏渲染位图（当前形态）
                val gp = EnergyStore.collectedTotal(context).toLong()
                val stage = PetView.stageOf(gp)

                // 成长值进度条画进精灵位图（兼容全 API），数值文案单列
                val th = PetView.THRESHOLDS
                var pct = 100
                if (stage < PetView.STAGE_KING) {
                    pct = (((gp - th[stage]) * 100) / (th[stage + 1] - th[stage])).toInt().coerceIn(0, 100)
                }
                views.setImageViewBitmap(R.id.widget_pet_img, renderPet(context, stage, pct))
                views.setTextViewText(R.id.widget_pet, PetView.stageName(c, stage))

                // 时长：今日 / 累计
                val sessions = SessionLog.sessions(context)
                val cal = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val todayStart = cal.timeInMillis
                var todayMs = 0L
                var totalMs = 0L
                for (s in sessions) {
                    totalMs += s.durationMs
                    val a = maxOf(s.start, todayStart)
                    val b = minOf(s.end, todayStart + 24L * 3600 * 1000)
                    if (b > a) todayMs += b - a
                }
                views.setTextViewText(R.id.widget_today, c.getString(R.string.widget_today_fmt, fmtMin(todayMs)))
                views.setTextViewText(R.id.widget_total, c.getString(R.string.widget_total_fmt, fmtMin(totalMs)))

                // 「息屏」：启动/复用助手并切黑幕；其余区域打开应用
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

        /** PetView 离屏渲染为位图（与统计页同源的精灵形象）+ 底部成长值进度条 */
        private fun renderPet(context: Context, stage: Int, pct: Int): Bitmap {
            val size = 160
            val barH = 26
            val bmp = Bitmap.createBitmap(size, size + barH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val pet = PetView(context)
            pet.stage = stage
            pet.hideProgress = true
            val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            pet.measure(spec, spec)
            pet.layout(0, 0, size, size)
            pet.draw(canvas)
            // 进度条：底轨 + 金色填充
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.argb(70, 255, 255, 255)
            canvas.drawRoundRect(14f, (size + 6).toFloat(), (size - 14).toFloat(), (size + 14).toFloat(),
                5f, 5f, paint)
            paint.color = android.graphics.Color.rgb(240, 200, 126)
            val fillW = 14f + (size - 28) * pct / 100f
            canvas.drawRoundRect(14f, (size + 6).toFloat(), fillW, (size + 14).toFloat(),
                5f, 5f, paint)
            return bmp
        }

        private fun fmtMin(ms: Long): String {
            val mins = (ms / 60000).toInt()
            return when {
                mins >= 60 -> {
                    val h = mins / 60; val m = mins % 60
                    if (m == 0) "${h}h" else "${h}h${m}m"
                }
                mins > 0 -> "${mins}m"
                else -> "0m"
            }
        }
    }
}
