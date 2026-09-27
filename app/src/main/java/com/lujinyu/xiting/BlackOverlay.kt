// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/trace.json
package com.lujinyu.xiting

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 黑屏遮罩：全屏纯黑窗口，吞掉所有触摸防误触。
 *
 * 交互（两段式解锁）：
 *   锁定态：全黑 + 背光物理关闭（screenBrightness=OFF）
 *   轻点屏幕 → 解除锁定：背光恢复到用户亮度，浮现「点击返回视频」按钮
 *   点按钮 → 返回视频；5秒无操作 → 自动重新锁定（背光再关）
 *
 * 系统栏隐藏（实测重要）：insets隐藏只做一次、绝不周期性重复调用——
 * ColorOS 16上反复调用hide()反而会让系统栏重新显示（真机A/B实测结论）。
 *
 * @param windowType TYPE_APPLICATION_OVERLAY（普通悬浮窗）或
 *                   TYPE_ACCESSIBILITY_OVERLAY（无障碍服务层）
 */
class BlackOverlay(private val context: Context, private val windowType: Int) {

    companion object {
        private const val TAG = "XiTing"
        private const val RELLOCK_DELAY_MS = 5000L
    }

    private val wm =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    var isShowing = false
        private set

    private var frame: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var unlockPill: TextView? = null
    private var awake = false
    private val relockRunnable = Runnable { sleep() }

    fun show(onDismiss: () -> Unit) {
        if (isShowing) return

        val density = context.resources.displayMetrics.density
        val f = FrameLayout(context)
        f.setBackgroundColor(Color.BLACK)

        val hint = TextView(context).apply {
            text = "息屏听剧中 · 轻点屏幕解除锁定"
            textSize = 15f
            setTextColor(0x66FFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        f.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val pill = TextView(context).apply {
            text = "🔓 点击返回视频"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = unlockPillBg(context)
            visibility = View.INVISIBLE
            alpha = 0f
            setPadding(
                (30 * density).toInt(), (18 * density).toInt(),
                (30 * density).toInt(), (18 * density).toInt()
            )
        }
        pill.setOnClickListener {
            hide()
            onDismiss()
        }
        f.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            ).apply { bottomMargin = (110 * density).toInt() }
        )

        val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (awake) sleep() else wake()
                return true
            }
        })
        f.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            true // 吞掉所有触摸，防止误触底下的视频App
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            lp.setFitInsetsTypes(0)
        }
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        lp.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF

        try {
            wm.addView(f, lp)
            frame = f
            this.lp = lp
            unlockPill = pill
            isShowing = true
            Log.i(TAG, "black overlay added, type=$windowType, backlight override OFF")
            hideSystemBars(f)
            f.post {
                hint.animate().alpha(0f).setStartDelay(2500).setDuration(800).start()
            }
        } catch (e: Exception) {
            Log.d(TAG, "black overlay add FAILED: $e")
        }
    }

    /** 系统栏隐藏（v1.6.0验证过的方式：insets申请一次即持续生效，绝不周期重复调用） */
    private fun hideSystemBars(f: FrameLayout) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                f.windowInsetsController?.let { c ->
                    c.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsets.Type.systemBars())
                }
            }
            @Suppress("DEPRECATION")
            f.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        } catch (e: Exception) {
            Log.d(TAG, "hideSystemBars failed: $e")
        }
    }

    fun hide() {
        main.removeCallbacks(relockRunnable)
        awake = false
        frame?.let { f -> try { wm.removeView(f) } catch (_: Exception) {} }
        frame = null
        lp = null
        unlockPill = null
        isShowing = false
    }

    /** 解除锁定：背光恢复到用户亮度，浮现返回按钮 */
    private fun wake() {
        if (awake) return
        awake = true
        val f = frame ?: return
        val l = lp ?: return
        l.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        try { wm.updateViewLayout(f, l) } catch (_: Exception) {}
        unlockPill?.visibility = View.VISIBLE
        unlockPill?.animate()?.alpha(1f)?.setDuration(200)?.start()
        main.postDelayed(relockRunnable, RELLOCK_DELAY_MS)
        Log.i(TAG, "awake: 解除锁定，背光恢复")
    }

    /** 重新锁定：背光再次关闭 */
    private fun sleep() {
        if (!awake) return
        awake = false
        main.removeCallbacks(relockRunnable)
        val f = frame ?: return
        val l = lp ?: return
        l.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        l.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        try { wm.updateViewLayout(f, l) } catch (_: Exception) {}
        unlockPill?.animate()?.alpha(0f)?.setDuration(200)
            ?.withEndAction { unlockPill?.visibility = View.INVISIBLE }?.start()
        Log.i(TAG, "sleep: 重新锁定")
    }

    private fun unlockPillBg(context: Context) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = 28f * context.resources.displayMetrics.density
        setColor(0xE61E8E5A.toInt())
    }
}
