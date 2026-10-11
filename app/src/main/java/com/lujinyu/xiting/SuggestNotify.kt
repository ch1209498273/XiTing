// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 「一键息屏」建议通知（v4.0）。
 *
 * 与被否掉的「白名单自动息屏」的分界：自动息屏是**替用户执行**（误触发打断），
 * 这里只是**递一张纸条**——点不点、什么时候点都由用户决定。
 * 节流：同一天最多一条（epoch day 去重）；点击直接 ACTION_TOGGLE 开黑幕。
 */
object SuggestNotify {

    private const val CHANNEL_ID = "xiiting_suggest"
    private const val NOTIF_ID = 21
    private const val REQ_TOGGLE = 2102

    /** 发一条建议（调用方保证开关已开）；每天最多一次 */
    fun fire(ctx: Context) {
        val today = Streaks.epochDayOf(System.currentTimeMillis()).toString()
        val p = ctx.prefs()
        if (p.getString(Prefs.SUGGEST_LAST_DAY, null) == today) return
        p.edit().putString(Prefs.SUGGEST_LAST_DAY, today).apply()

        ensureChannel(ctx)
        // 点击 = 直接开黑幕（服务没跑也会被拉起）；不想息屏忽略通知即可
        val toggle = PendingIntent.getForegroundService(
            ctx, REQ_TOGGLE,
            Intent(ctx, OverlayService::class.java).setAction(OverlayService.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(ctx.getString(R.string.notif_suggest_title))
                .setContentText(ctx.getString(R.string.notif_suggest_msg))
                .setContentIntent(toggle)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, ctx.getString(R.string.channel_suggest),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
