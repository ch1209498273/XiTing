// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 黑幕字幕采集（v4.1 实验性）：无障碍服务监听前台视频 App 的文本变更，
 * 用启发式锁定「字幕节点」，把文本镜像到黑幕（CaptionBus → BlackOverlay）。
 *
 * ## 启发式（跨 App 通用，不做 per-app 选择器）
 * 字幕节点的行为特征是「原地高频换文本」：每次 TYPE_WINDOW_CONTENT_CHANGED
 * 取变更节点的 text，过五道滤网（见 [accept]）——弹幕/进度条数字/评论区不在
 * 节点树里（自绘 Surface/TextureView 对无障碍不可见），天然不会污染。
 *
 * ## 边界（必须如实告知用户）
 * - 硬字幕（烧进画面）读不到：B站 CC / YouTube 自动字幕 / 网课类 ✓，爱优腾正版剧 ✗
 * - 文本仅实时显示，不存储不上传（零网络根基不变）
 *
 * 状态机防抖：同一文本不重复发；间隔 <350ms 的连发合并（字幕换行分片）。
 */
class CaptionService : android.accessibilityservice.AccessibilityService() {

    companion object {
        private const val TAG = "Caption"
        private const val MIN_LEN = 2
        private const val MAX_LEN = 120
        private const val DEBOUNCE_MS = 350L
        /** 系统 UI/桌面等非字幕源：时钟、电量这类高频文本变更全是噪声 */
        private val NOISE_PACKAGES = setOf(
            "com.android.systemui", "com.android.launcher", "com.android.settings",
            "com.coloros.safecenter", "com.oplus.battery"
        )

        private var lastText: String? = null
        private var lastAt = 0L

        @JvmStatic
        internal var instance: CaptionService? = null

        /** 无障碍开关状态（设置页展示用）：系统 secure 设置是唯一事实源 */
        fun isEnabled(ctx: android.content.Context): Boolean {
            val v = android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return v.contains("${ctx.packageName}/.$javaClass.simpleName") ||
                v.contains("${ctx.packageName}/$javaClass.name")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理文本变更；自己 App 的窗口（黑幕字幕层）绝不回读，防反馈环
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        // 前置条件：当前有活跃媒体会话，且事件来自会话所属 App ——
        // 字幕只可能出现在「正在播的那个视频 App」里，其他 App 的文本全是噪声
        val np = try {
            queryNowPlaying()
        } catch (_: Exception) {
            null
        }
        if (np == null || np.pkg != pkg) return
        // 系统包噪声：状态栏时钟/电量等文本高频变更但绝不是字幕
        if (pkg in NOISE_PACKAGES) return

        val text = readText(event.source)
            // source 为空时降级：扫活动窗口找最新文本节点（部分 App 事件不带节点）
            ?: scanRoot()
            ?: return
        if (!accept(text)) return

        val now = System.currentTimeMillis()
        if (text == lastText) {
            // 同一条字幕持续显示：不重复发
            return
        }
        if (now - lastAt < DEBOUNCE_MS && !lastText.isNullOrEmpty()) {
            // 换行分片：短间隔到达的片段拼到上一条后面（B站 CC 会把一句拆两次事件）
            CaptionBus.post(lastText.orEmpty() + text)
        } else {
            CaptionBus.post(text)
        }
        android.util.Log.i(TAG, "caption[$pkg]: $text")
        lastText = text
        lastAt = now
    }

    /** 字幕文本滤网：长度合理 + 有实义字符（排除纯符号/分隔线） */
    private fun accept(text: String): Boolean {
        val t = text.trim()
        if (t.length < MIN_LEN || t.length > MAX_LEN) return false
        if (t.all { it.isDigit() || it in ":.％% " }) return false // 纯数字/时间串
        return t.any { it.isLetterOrDigit() || it.code > 0x2E7F }
    }

    /** 变更可能发生在容器上：向下找第一个带文本的子节点（≤3 层） */
    private fun readText(node: AccessibilityNodeInfo?): String? {
        var n = node ?: return null
        var hops = 0
        while (n != null && hops < 3) {
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) return t
            n = n.getChild(0) ?: break
            hops++
        }
        return null
    }

    /** source 为空的降级：广扫活动窗口取最新可用文本 */
    private fun scanRoot(): String? {
        val root = rootInActiveWindow ?: return null
        var best: String? = null
        var hops = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && hops < 40) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length >= MIN_LEN && accept(t)) best = t
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            hops++
        }
        return best
    }

    override fun onInterrupt() {
        CaptionBus.post("")
    }

    override fun onServiceConnected() {
        android.util.Log.i(TAG, "caption service connected")
        instance = this
    }

    override fun onDestroy() {
        instance = null
        CaptionBus.post("")
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        lastText = null
        return super.onUnbind(intent)
    }
}

/**
 * 字幕流的进程内总线：CaptionService 生产，BlackOverlay 消费。
 * 与 NotificationBadge 同一套注册/反注册模式。
 */
object CaptionBus {
    private val listeners = ArrayList<(String) -> Unit>()

    fun register(l: (String) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
    }

    fun unregister(l: (String) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    fun post(text: String) {
        synchronized(listeners) { listeners.toList() }.forEach { it(text) }
    }
}
