// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知监听：黑幕期间统计外部未读通知数（排除本App与常驻类），
 * 需用户在系统「通知使用权」中授权，未授权时角标不显示、零打扰。
 */
class NotificationListener : NotificationListenerService() {

    private fun refresh() {
        val n = try {
            activeNotifications.count {
                it.packageName != packageName && !it.isOngoing
            }
        } catch (_: Exception) {
            0
        }
        NotificationBadge.update(n)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()
    override fun onListenerConnected() = refresh()
}
