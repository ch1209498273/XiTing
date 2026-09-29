// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

/**
 * 黑幕未读通知计数：NotificationListener统计后写入这里，
 * BlackOverlay注册回调实时刷新角标。
 */
object NotificationBadge {
    @Volatile
    var count: Int = 0
        private set

    private val listeners = mutableListOf<(Int) -> Unit>()

    fun update(n: Int) {
        if (count == n) return
        count = n
        synchronized(listeners) { listeners.toList() }.forEach { try { it(n) } catch (_: Exception) {} }
    }

    fun register(listener: (Int) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
        listener(count)
    }

    fun unregister(listener: (Int) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }
}
