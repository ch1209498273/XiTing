package com.lujinyu.xiting

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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
 * 黑屏遮罩：全屏纯黑窗口，吞掉所有触摸防误触，双击任意处恢复。
 *
 * @param windowType TYPE_APPLICATION_OVERLAY（普通悬浮窗）或
 *                   TYPE_ACCESSIBILITY_OVERLAY（无障碍服务层，系统栏之上，全屏彻底盖黑）
 *
 * 关键技术：窗口级背光覆盖 screenBrightness=BRIGHTNESS_OVERRIDE_OFF——
 * 系统取"最上层可见窗口"的亮度覆盖值，我们的黑幕就是最上层窗口，
 * 因此黑幕期间**背光被物理关闭**（真息屏级别的黑），绕过OEM的亮度下限
 * （ColorOS会把系统亮度钳制在最低档，所以改系统设置压不到真黑）。
 * 黑幕消失，覆盖即失效，无需保存/还原任何系统设置。
 */
class BlackOverlay(private val context: Context, private val windowType: Int) {

    companion object {
        private const val TAG = "XiTing"
    }

    var isShowing = false
        private set

    private var frame: FrameLayout? = null

    fun show(onDismiss: () -> Unit) {
        if (isShowing) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val f = FrameLayout(context)
        f.setBackgroundColor(Color.BLACK)

        val hint = TextView(context).apply {
            text = "息屏听剧中 · 双击任意位置恢复画面"
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

        val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.d(TAG, "overlay onDoubleTap!")
                hide()
                onDismiss()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Log.d(TAG, "overlay onSingleTapConfirmed (单击，忽略)")
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
            PixelFormat.OPAQUE // 必须OPAQUE：背光覆盖只认"全屏不透明窗口"，TRANSLUCENT不生效
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // 不为状态栏/导航栏预留inset，并把遮罩扩展进刘海区
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            lp.setFitInsetsTypes(0)
        }
        // 核心：黑幕是最上层可见窗口，系统采用它的背光覆盖值 → 背光物理关闭
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        lp.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF

        try {
            wm.addView(f, lp)
            frame = f
            isShowing = true
            Log.d(TAG, "black overlay added, type=$windowType, backlight override OFF")
            // ColorOS把状态栏/导航栏画在一切第三方窗口之上，z序盖不住；
            // 但黑幕是焦点窗口，有权通过WindowInsetsController主动申请隐藏系统栏
            f.post { hideSystemBars(f) }
            f.post {
                hint.animate().alpha(0f).setStartDelay(2500).setDuration(800).start()
            }
        } catch (e: Exception) {
            Log.d(TAG, "black overlay add FAILED: $e")
        }
    }

    private fun hideSystemBars(f: FrameLayout) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val c = f.windowInsetsController ?: return
                c.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.systemBars())
                Log.d(TAG, "insets: requested system bars hide")
            } else {
                @Suppress("DEPRECATION")
                f.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                Log.d(TAG, "legacy immersive requested")
            }
        } catch (e: Exception) {
            Log.d(TAG, "hide system bars failed: $e")
        }
    }

    fun hide() {
        frame?.let { f ->
            try {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(f)
            } catch (_: Exception) {
            }
        }
        frame = null
        isShowing = false
    }
}

