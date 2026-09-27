package com.lujinyu.xiting

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.WindowManager

/**
 * 无障碍服务（公开发布版）：仅用于提供 TYPE_ACCESSIBILITY_OVERLAY 层级的黑幕窗口。
 * 该层级位于系统栏之上，能把状态栏、导航条、手势条全部盖黑。
 * 不读取窗口内容，不包含任何广告跳过逻辑。
 */
class XiTingA11yService : AccessibilityService() {

    companion object {
        private const val TAG = "XiTing"
        var instance: XiTingA11yService? = null

        // 保留常量以维持与自用版同一份MainActivity（跳过引擎本身已剥离）
        const val KEY_ADS_MODE = "ads_mode"
        const val MODE_OFF = 0
        const val MODE_CURATED = 1
        const val MODE_ALL = 2
    }

    private var black: BlackOverlay? = null

    val isBlackShowing: Boolean get() = black?.isShowing == true

    fun toggleBlack() {
        Log.d(TAG, "a11y toggleBlack, showing=${black?.isShowing}")
        if (black?.isShowing == true) {
            hideBlack()
        } else {
            black = BlackOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            black?.show { }
        }
    }

    fun hideBlack() {
        black?.hide()
        black = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "a11y service connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 公开发布版：无广告跳过引擎，无障碍仅用于黑幕层级
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideBlack()
        instance = null
        super.onDestroy()
    }
}
