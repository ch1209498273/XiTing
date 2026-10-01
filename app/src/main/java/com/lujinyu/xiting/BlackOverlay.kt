// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/trace.json
package com.lujinyu.xiting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 黑屏遮罩：全屏纯黑窗口，吞掉所有触摸防误触。
 *
 * 交互（两段式解锁）：
 *   锁定态：全黑 + 背光物理关闭（screenBrightness=OFF）
 *   轻点屏幕 → 解除锁定：背光恢复到用户亮度，浮现「点击返回视频」按钮
 *   点按钮 → 返回视频；5秒无操作 → 自动重新锁定（开时钟时压至最暗背光，暗态夜钟持续可见）
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
    private var badgeListener: ((Int) -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null
    private var directUnlock = false          // 轻点直接解锁（跳过两段式）
    private var showInfo = false              // 黑幕时钟/电量显示（默认关，首页可开）
    private var clockText: TextView? = null
    private var batteryText: TextView? = null
    private var awake = false
    private var sessionStart = 0L          // 墙钟：用于记录起始时间与按天归属
    private var sessionStartElapsed = 0L   // 单调时钟：用于时长计算，不受时间跳变/跨天影响
    private val relockRunnable = Runnable { sleep() }

    // 黑幕时钟：每15秒刷新（分钟级精度足够）
    private val clockTick: Runnable = Runnable {
        updateClock()
        main.postDelayed(clockTick, 15_000)
    }

    // OLED防烧屏：黑幕上的时钟/电量是长时间静止的亮区，同一片像素持续工作
    // 有烧屏风险。每45秒把内容微移±2px（不可感知），让像素轮换休息。
    private var infoCol: LinearLayout? = null
    private var burnPhase = 0
    private val burnInTick: Runnable = object : Runnable {
        override fun run() {
            burnPhase = (burnPhase + 1) % 4
            val dx = floatArrayOf(0f, 2f, -2f, 1f)[burnPhase]
            val dy = floatArrayOf(0f, -1f, 1f, 2f)[burnPhase]
            infoCol?.translationX = dx
            infoCol?.translationY = dy
            main.postDelayed(this, 45_000)
        }
    }

    // 电量：注册即收到系统粘性广播，锁屏期间插拔充电线也能实时更新
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, i: Intent?) {
            val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) batteryText?.text = "电量 $level%"
        }
    }

    private fun updateClock() {
        val cal = java.util.Calendar.getInstance()
        clockText?.text = String.format(
            java.util.Locale.getDefault(), "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)
        )
    }

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
            ).apply { topMargin = (150 * density).toInt() } // 下移避让时钟
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

        // 未读通知角标（需用户授予「通知使用权」，未授权时不显示）
        val badge = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = unlockPillBg(context)
            (background as android.graphics.drawable.GradientDrawable).setColor(0x66000000)
            visibility = View.GONE
        }
        f.addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = (36 * density).toInt()
                marginEnd = (16 * density).toInt()
            }
        )
        badgeListener = { n ->
            main.post {
                if (n > 0) {
                    badge.text = "🔔 $n"
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
        NotificationBadge.register(badgeListener!!)

        val prefs = context.getSharedPreferences("xiiting_prefs", Context.MODE_PRIVATE)
        showInfo = prefs.getBoolean("black_info_show", false) // 时钟/电量默认关：纯黑偏好

        // 黑幕信息：时间 + 电量（暗色显示，夜间看时间/电量不用亮屏）
        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        clockText = TextView(context).apply {
            textSize = 88f
            setTextColor(0x30FFFFFF.toInt())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        batteryText = TextView(context).apply {
            textSize = 20f
            setTextColor(0x28FFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        infoCol.addView(clockText)
        infoCol.addView(batteryText)
        if (showInfo) {
            this.infoCol = infoCol
            f.addView(
                infoCol,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            updateClock()
            main.postDelayed(clockTick, 15_000)
            main.postDelayed(burnInTick, 45_000)
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        // 解锁方式：轻点直接解锁（跳过两段式确认）默认关
        directUnlock = prefs.getBoolean("direct_unlock", false)
        onDismissCallback = onDismiss

        val gd = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (directUnlock) {
                    hide()
                    onDismissCallback?.invoke()
                } else if (awake) sleep() else wake()
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
            sessionStart = System.currentTimeMillis()
            sessionStartElapsed = SystemClock.elapsedRealtime()
            Log.i(TAG, "black overlay added, type=$windowType, backlight override OFF")
            hideSystemBars(f)
            f.post {
                hint.animate().alpha(0f).setStartDelay(8000).setDuration(1000).start()
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
        if (sessionStart > 0) {
            val now = System.currentTimeMillis()
            // 时长用单调时钟：就算系统时间被改/跨天，记录的也是真实经过的时间。
            // 只有设备中途重启（elapsed回绕为负）才退回墙钟差。
            val elapsed = SystemClock.elapsedRealtime() - sessionStartElapsed
            val dur = if (elapsed >= 0) elapsed else now - sessionStart
            Stats.addDelta(context, dur)
            SessionLog.add(context, ListenSession(sessionStart, now, dur, SessionLog.MODE_BLACK))
            sessionStart = 0
        }
        badgeListener?.let { NotificationBadge.unregister(it) }
        badgeListener = null
        onDismissCallback = null
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        main.removeCallbacks(clockTick)
        main.removeCallbacks(burnInTick)
        infoCol = null
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

    /** 重新锁定：开时钟→背光压到最暗（暗态时钟持续可见）；未开→彻底关闭纯黑 */
    private fun sleep() {
        if (!awake) return
        awake = false
        main.removeCallbacks(relockRunnable)
        val f = frame ?: return
        val l = lp ?: return
        l.screenBrightness =
            if (showInfo) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
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
