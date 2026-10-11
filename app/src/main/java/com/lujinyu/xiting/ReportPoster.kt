// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 月报：海报数据组装 + 分享 + 跨月本地推送。
 *
 * 「推送」是**本地通知**，不是服务器推送——本项目无 INTERNET 权限是立身之本，
 * 服务端推送意味着后端 + 联网，属于根基级变更（用户 2026-10-11 确认走本地推送）。
 * 体验等价：跨月首次打开 App（或从后台回来）时，检测到上月有听剧记录就发一条
 * 可点击通知，直达月报海报预览。
 */
object ReportPoster {

    const val EXTRA_SHOW_REPORT = "show_report"
    const val SHOW_MONTH = "month"

    private const val CHANNEL_ID = "xiiting_report"
    private const val NOTIF_ID = 20
    private const val REQ_REPORT = 2101

    /** 月报海报数据；本月无听剧记录时返回 null（无内容可报） */
    fun monthData(
        ctx: Context,
        sessions: List<ListenSession> = SessionLog.sessions(ctx)
    ): SharePoster.Data? {
        val st = monthStats(sessions, System.currentTimeMillis())
        if (st.totalMs <= 0L) return null
        val stage = PetForm.selected(ctx)
        val skin = PetSkins.active(ctx)
        val best = Streaks.compute(sessions).best
        val lines = buildList {
            add(
                ctx.getString(R.string.poster_row_days_week) to
                    ctx.getString(R.string.fmt_days_sessions, st.listenDays, st.count)
            )
            add(ctx.getString(R.string.poster_row_max) to ctx.fmtDur(st.maxSingleMs))
            if (st.prevTotalMs > 0L) {
                val pct = ((st.totalMs - st.prevTotalMs) * 100 / st.prevTotalMs).toInt()
                add(ctx.getString(R.string.poster_row_vs_last) to if (pct >= 0) "+$pct%" else "$pct%")
            }
            add(ctx.getString(R.string.poster_row_streak) to ctx.getString(R.string.fmt_days, best))
        }
        return SharePoster.Data(
            stage = stage,
            skin = skin,
            stageName = PetView.stageName(ctx, stage),
            headline = ctx.fmtDur(st.totalMs),
            headlineUnit = ctx.getString(R.string.poster_unit_month),
            hint = ctx.getString(R.string.poster_hint_month),
            lines = lines,
            footer = ctx.getString(R.string.poster_footer_month),
            shareText = ctx.getString(
                R.string.share_text, ctx.fmtDur(st.totalMs),
                Stats.estimatedMah(st.totalMs), PetView.stageName(ctx, stage)
            ),
            badge = if (best >= 2) ctx.getString(R.string.poster_badge_streak, best) else null
        )
    }

    /** 弹出月报海报预览（通知点击 / 分享菜单共用） */
    fun shareMonthReport(activity: MainActivity) {
        val d = monthData(activity)
        if (d == null) {
            android.widget.Toast.makeText(
                activity, activity.getString(R.string.report_empty_toast), android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        activity.sharePoster(d, "month")
    }

    /**
     * 跨月检测 + 本地通知。onResume 调用：每次回到前台都会跑，但一个自然月
     * 只发一次（REPORT_LAST_MONTH 记录的是「本月已经处理过」）。
     */
    fun maybeNotifyMonthly(ctx: Context) {
        val cur = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            .format(java.util.Date())
        val prefs = ctx.prefs()
        if (prefs.getString(Prefs.REPORT_LAST_MONTH, null) == cur) return
        prefs.edit().putString(Prefs.REPORT_LAST_MONTH, cur).apply()

        val st = monthStats(SessionLog.sessions(ctx), System.currentTimeMillis())
        if (st.prevTotalMs <= 0L) return // 上月没听剧：没有值得报告的内容，不打扰

        ensureChannel(ctx)
        // 月名取「上月中点」的本地化全称（月初/月末边界都落在同一个月）
        val prevName = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis() - 15L * 24 * 3600 * 1000))
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_REPORT, SHOW_MONTH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, REQ_REPORT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(ctx.getString(R.string.notif_report_title))
                .setContentText(ctx.getString(R.string.notif_report_msg_fmt, prevName))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, ctx.getString(R.string.channel_report),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
