// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * 小部件共享渲染核心：三档样式（标准4×2 / 紧凑2×2 / 大号4×3）共用同一份数据与素材。
 * 大号的近7天柱状图为预渲染位图（RemoteViews 无自绘能力，API 36 的 setProgressBar
 * 已变 4 参且旧机不兼容，故进度条与柱状图一律画进位图）。
 */
object WidgetData {

    enum class Style { STANDARD, COMPACT, LARGE }

    private val PROVIDERS = listOf(
        Triple(WidgetData::refresh, XiTingWidget::class.java, Style.STANDARD),
        Triple(WidgetData::refresh, XiTingWidgetCompact::class.java, Style.COMPACT),
        Triple(WidgetData::refresh, XiTingWidgetLarge::class.java, Style.LARGE)
    )

    /** 全部小部件统一刷新（OverlayService/MainActivity 状态变化时调用） */
    fun refreshAll(context: Context) {
        for ((fn, _, style) in PROVIDERS) fn(context, style)
    }

    fun refresh(context: Context, style: Style) {
        try {
            val c = AppLocales.wrap(context)
            val layout = when (style) {
                Style.STANDARD -> R.layout.widget_main
                Style.COMPACT -> R.layout.widget_compact
                Style.LARGE -> R.layout.widget_large
            }
            val views = RemoteViews(context.packageName, layout)
            val running = OverlayService.isRunning

            // ---- 数据 ----
            val gp = EnergyStore.collectedTotal(context).toLong()
            val stage = PetView.stageOf(gp)
            val th = PetView.THRESHOLDS
            var pct = 100
            if (stage < PetView.STAGE_KING) {
                pct = (((gp - th[stage]) * 100) / (th[stage + 1] - th[stage])).toInt().coerceIn(0, 100)
            }
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

            // ---- 通用绑定（三档布局共用同一组 id）----
            views.setImageViewBitmap(R.id.widget_pet_img, renderPet(context, stage, pct))
            views.setTextViewText(R.id.widget_pet, PetView.stageName(c, stage))
            views.setTextViewText(
                R.id.widget_growth,
                if (stage >= PetView.STAGE_KING) c.getString(R.string.widget_growth_max, gp)
                else c.getString(R.string.widget_growth_next, gp, th[stage + 1] - gp)
            )
            views.setTextViewText(R.id.widget_today, c.getString(R.string.widget_today_fmt, fmtMin(todayMs)))
            views.setTextViewText(
                R.id.widget_status,
                c.getString(if (running) R.string.widget_active else R.string.widget_idle)
            )

            // ---- 大号独有：近7天迷你柱状图 ----
            if (style == Style.LARGE) {
                views.setImageViewBitmap(R.id.widget_bars, renderBars(sessions, todayStart))
            }

            // ---- 按钮 ----
            val piBlack = PendingIntent.getForegroundService(
                context, 10,
                Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_black, piBlack)
            val piOpen = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, piOpen)

            val provider = when (style) {
                Style.STANDARD -> XiTingWidget::class.java
                Style.COMPACT -> XiTingWidgetCompact::class.java
                Style.LARGE -> XiTingWidgetLarge::class.java
            }
            AppWidgetManager.getInstance(context)
                .updateAppWidget(ComponentName(context, provider), views)
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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(14f, (size + 6).toFloat(), (size - 14).toFloat(), (size + 14).toFloat(),
            5f, 5f, paint)
        paint.color = android.graphics.Color.rgb(240, 200, 126)
        val fillW = 14f + (size - 28) * pct / 100f
        canvas.drawRoundRect(14f, (size + 6).toFloat(), fillW, (size + 14).toFloat(),
            5f, 5f, paint)
        return bmp
    }

    /** 近7天迷你柱状图位图（大号小部件专用，今日金色） */
    private fun renderBars(sessions: List<ListenSession>, todayStart: Long): Bitmap {
        val w = 560
        val h = 150
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayStart
        }
        val dayMs = 24L * 3600 * 1000
        val perDay = LongArray(7)
        for (s in sessions) {
            for (i in 0..6) {
                val dStart = todayStart - (6 - i) * dayMs
                val dEnd = dStart + dayMs
                val a = maxOf(s.start, dStart)
                val b = minOf(s.end, dEnd)
                if (b > a) perDay[i] += b - a
            }
        }
        val maxMs = perDay.max().coerceAtLeast(1L)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barW = w / 7f * 0.52f
        for (i in 0..6) {
            val bh = (perDay[i].toFloat() / maxMs * (h - 14)).coerceAtLeast(6f)
            val x = w / 7f * i + (w / 7f - barW) / 2
            paint.color = if (i == 6) android.graphics.Color.rgb(240, 200, 126)
                          else android.graphics.Color.argb(190, 120, 140, 180)
            c.drawRoundRect(x, h - bh, x + barW, h - 4f, 8f, 8f, paint)
        }
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
