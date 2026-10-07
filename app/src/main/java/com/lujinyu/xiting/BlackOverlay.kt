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
import android.view.KeyEvent
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
    private var mediaRow: LinearLayout? = null
    private var playPauseBtn: android.widget.ImageView? = null
    private val am by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }
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
            if (level >= 0 && scale > 0) {
                batteryText?.text = context.getString(R.string.battery_fmt, level)
            }
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
        // 覆写 performClick（lint ClickableViewAccessibility）：黑幕全屏铺满，它本身
        // 确实可点（轻点解锁/切夜钟），所以无障碍服务应当能读到这个点击，
        // 不能因为点击逻辑写在 GestureDetector 里就让 TalkBack 把它当成死区。
        val f = object : FrameLayout(context) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }
        f.setBackgroundColor(Color.BLACK)

        val hint = TextView(context).apply {
            text = context.getString(R.string.black_hint)
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
            text = context.getString(R.string.black_return)
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

        // 媒体控制行（唤醒态显示）：黑幕下切集/暂停——通知栏被黑幕遮住，
        // 这里是媒体键唯一可达的位置。默认关闭（防误触），可在设置中选择开启
        val showMedia = context.prefs()
            .getBoolean(Prefs.BLACK_MEDIA_CONTROLS, false)
        val media = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            alpha = 0f
        }
        val mkBtn = { resId: Int, code: Int, desc: String ->
            android.widget.ImageView(context).apply {
                setImageResource(resId)
                setColorFilter(Color.WHITE)
                contentDescription = desc
                val size = (52 * density).toInt()
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x66000000)
                }
                background = bg
                setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = (14 * density).toInt()
                    marginEnd = (14 * density).toInt()
                }
                setOnClickListener { sendMediaKey(code) }
            }
        }
        media.addView(mkBtn(R.drawable.ic_media_prev, KeyEvent.KEYCODE_MEDIA_PREVIOUS, context.getString(R.string.cd_prev_episode)))
        val ppBtn = mkBtn(R.drawable.ic_media_pause, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, context.getString(R.string.cd_play_pause))
        playPauseBtn = ppBtn
        media.addView(ppBtn)
        media.addView(mkBtn(R.drawable.ic_media_next, KeyEvent.KEYCODE_MEDIA_NEXT, context.getString(R.string.cd_next_episode)))
        if (showMedia) {
            f.addView(
                media,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                ).apply { bottomMargin = (185 * density).toInt() }
            )
        }

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

        val prefs = context.prefs()
        showInfo = prefs.getBoolean(Prefs.BLACK_INFO_SHOW, false) // 时钟/电量默认关：纯黑偏好

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
        directUnlock = prefs.getBoolean(Prefs.DIRECT_UNLOCK, false)
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
        f.setOnTouchListener { v, e ->
            gd.onTouchEvent(e)
            // 走标准点击入口，让无障碍服务能识别到这次交互（lint ClickableViewAccessibility）
            if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
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

        // 隐藏非系统悬浮窗（ColorOS 智能侧边栏小白条等 OEM 边缘组件是普通App uid 的
        // 悬浮层，图层高于第三方窗口，黑幕盖不住）。该系统标志在黑幕显示期间会让
        // WindowService 临时隐藏它们；API 为 @hide，反射注入，缺字段的老 ROM 静默跳过。
        try {
            val lpCls = WindowManager.LayoutParams::class.java
            val pfField = lpCls.getField("privateFlags")
            val hideFlag = lpCls.getField("SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS").getInt(null)
            pfField.setInt(lp, pfField.getInt(lp) or hideFlag)
        } catch (_: Exception) {
        }

        try {
            wm.addView(f, lp)
            frame = f
            this.lp = lp
            unlockPill = pill
            mediaRow = media
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
            SessionLog.add(context, ListenSession(sessionStart, now, dur, SessionLog.MODE_BLACK))
            // 稀有能量掉落：≥5 分钟的会话 10% 概率刷出雷暴能量（每日一次），惊喜钩子
            try {
                if (dur >= 300_000) {
                    val sp = context.prefs()
                    // 用 epochDayOf 而不是 SimpleDateFormat("yyyyMMdd")：
                    // 后者受 locale 影响（th-TH 佛历、阿拉伯语非 ASCII 数字），
                    // 用户换语言/换系统区域后可能算出不同的「今天」，
                    // 于是“每日一次”的去重失效，同一天能反复掉能量。
                    val today = Streaks.epochDayOf(now)
                    if (sp.getLong(Prefs.LAST_RARE_DAY, -1L) != today &&
                        java.util.Random().nextFloat() < 0.10f
                    ) {
                        sp.edit().putLong(Prefs.LAST_RARE_DAY, today).apply()
                        val bonus = (dur / 60_000).toInt().coerceAtLeast(15)
                        EnergyStore.add(context, bonus)
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.toast_rare_energy, bonus),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (_: Exception) {}
            // 听剧产生能量球：每满 1 分钟 1 点能量（含跨会话余数累积，不浪费零头）
            try {
                val sp = context.prefs()
                val carry = sp.getLong(Prefs.ENERGY_CARRY_MS, 0) + dur
                val energy = (carry / 60000).toInt()
                sp.edit().putLong(Prefs.ENERGY_CARRY_MS, carry % 60000).apply()
                if (energy > 0) EnergyStore.add(context, energy)
            } catch (_: Exception) {
            }
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
        mediaRow = null
        playPauseBtn = null
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
        mediaRow?.visibility = View.VISIBLE
        mediaRow?.animate()?.alpha(1f)?.setDuration(200)?.start()
        updatePlayIcon()
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
        mediaRow?.animate()?.alpha(0f)?.setDuration(200)
            ?.withEndAction { mediaRow?.visibility = View.INVISIBLE }?.start()
        Log.i(TAG, "sleep: 重新锁定")
    }

    /** 黑幕内媒体键：注入后重置重锁计时（连续操作不被打断） */
    private fun sendMediaKey(code: Int) {
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            Log.i(TAG, "black overlay media key: $code")
        } catch (e: Exception) {
            Log.w(TAG, "media key failed: $e")
        }
        if (awake) {
            main.removeCallbacks(relockRunnable)
            main.postDelayed(relockRunnable, RELLOCK_DELAY_MS)
        }
        // 媒体键生效后刷新播放/暂停图标（音频状态更新有延迟）
        main.postDelayed({ updatePlayIcon() }, 400)
    }

    /** 播放状态→显示暂停键；暂停状态→显示播放键 */
    private fun updatePlayIcon() {
        val btn = playPauseBtn ?: return
        val playing = try { am.isMusicActive } catch (_: Exception) { false }
        btn.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
    }

    private fun unlockPillBg(context: Context) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = 28f * context.resources.displayMetrics.density
        setColor(0xE61E8E5A.toInt())
    }
}
