// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 息屏听剧核心服务：
 * 1. 常驻一个可拖动的悬浮球（点击 = 黑屏/恢复切换）
 * 2. 黑屏遮罩：开了无障碍就用 TYPE_ACCESSIBILITY_OVERLAY（连手势条一起盖黑），
 *    否则退回 TYPE_APPLICATION_OVERLAY（可能被系统手势条压住一条）
 * 3. 真息屏续播：电源键真熄屏后自动注入"播放"媒体键，让视频App后台恢复出声
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "XiTing"
        private const val CHANNEL_ID = "xiiting_service"
        private const val NOTIF_ID = 1
        private const val NOTIF_EXIT_ID = 2
        const val ACTION_TOGGLE = "com.lujinyu.xiting.TOGGLE"
        const val ACTION_BUBBLE = "com.lujinyu.xiting.BUBBLE_TOGGLE"
        const val ACTION_EXIT = "com.lujinyu.xiting.EXIT"
        const val ACTION_SET_TIMER = "com.lujinyu.xiting.SET_TIMER"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_END_AT = "end_at"        // 听完这集：以绝对时刻（epoch ms）定时
        const val ACTION_CALIB_START = "com.lujinyu.xiting.CALIB_START"
        const val EXTRA_CALIB_ON_UA = "calib_on_ua"
        private const val ACTION_TEST_TOGGLE = "com.lujinyu.xiting.TEST_TOGGLE"
        private const val ACTION_TEST_RUN_MODE = "com.lujinyu.xiting.TEST_RUN_MODE"
        const val ACTION_TEST_UNLOCK_SKINS = "com.lujinyu.xiting.TEST_UNLOCK_SKINS"
        const val ACTION_TEST_WEAR = "com.lujinyu.xiting.TEST_WEAR"

        var instance: OverlayService? = null
            private set
        val isRunning: Boolean get() = instance != null
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())

    /** 来电检测：响铃/通话时音频模式会切换（系统标准回调，无需权限）。
     *  黑幕期间来电 → 自动解除黑幕；通话期间 → 挂起所有自动注入动作。 */
    // OnModeChangedListener 仅存在于 API 31+：字段若直接引用该类型，
    // 低版本设备加载本类即 ClassNotFoundException（矩阵回归 api26/29 实测崩溃）。
    // 改为 Any 持有 + 独立方法内创建，只在 31+ 分支调用时才解析类型。
    private var modeListener: Any? = null

    private fun registerModeListener() {
        val l = AudioManager.OnModeChangedListener { mode ->
            Log.i(TAG, "audio mode -> $mode")
            when (mode) {
                AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL -> main.post {
                    Log.i(TAG, "来电/通话中：黑幕解除 + 挂起自动动作")
                    hideAllBlack()   // 内部已停掉 powerTick（会话功耗采样）
                    // 只停「会被通话状态打断」的两条链：
                    //  · calibTick —— 校准向导需要重新采一段干净样本
                    //  · 悬浮球长按 —— 松手时机被通话打断，不该再弹出退出确认条
                    //
                    // 绝不能改成 removeCallbacksAndMessages(null)：那会连带干掉
                    // timerTick。timerTick 的职责是在未到期时把自己重新 post 一次，
                    // 一旦被整体清空就没有任何人再挂它，睡眠定时器从此静默失效
                    // （timerEndAt 仍留着值，看起来一切正常，实际永远不会触发）。
                    main.removeCallbacks(calibTick)
                    cancelLongPress()
                    refreshNotification()
                    try {
                        Toast.makeText(this, getString(R.string.toast_call), Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                }
            }
        }
        modeListener = l
        audioManager.addOnModeChangedListener(main::post, l)
    }

    /** 保活监控计数：YouTube等App会在片尾/中途把后台播放掐掉，息屏后3分钟内自动再救 */

    private var bubble: View? = null
    private var black: BlackOverlay? = null

    /** 悬浮球 400ms 长按判定（提为字段，便于来电/重建时主动撤销） */
    private var longPressCheck: Runnable? = null

    /** 撤销待触发的长按判定：来电打断松手、或悬浮球重建时不应再弹退出确认条 */
    private fun cancelLongPress() {
        longPressCheck?.let { main.removeCallbacks(it) }
        longPressCheck = null
    }

    /** 黑幕是否在显示，供磁贴等外部判断 */
    fun isAnyBlackShowing(): Boolean = black?.isShowing == true

    private fun hideAllBlack() {
        black?.hide()
        black = null
        main.removeCallbacks(powerTick) // 停止会话功耗采样
        setBubbleAnimating(true)
    }

    /**
     * 黑幕是不透明全屏窗，把悬浮球整个盖住。此时精灵悬浮球若继续按帧重绘，
     * 就是在完全看不见的地方空转——一次两小时听剧等于十几万帧无效渲染。
     * 文字样式的悬浮球不是 BubblePetView，无需处理。
     */
    private fun setBubbleAnimating(on: Boolean) {
        (bubble as? BubblePetView)?.setAnimating(on)
    }

    // ---------- 会话功耗采样（省电实测） ----------

    private val powerTick: Runnable = Runnable {
        if (black?.isShowing == true) {
            PowerCalib.sampleNow(this)?.let { PowerCalib.recordSample(this, it) }
            main.postDelayed(powerTick, 60_000)
        }
    }

    // ---------- 耳机拔出联动 ----------

    /** 有线拔出/蓝牙断开时自动撤黑幕返回视频（个性化开关，默认开） */
    private val headsetCb = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
            val headsetGone = removedDevices.any {
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (headsetGone && prefs.getBoolean(Prefs.SWITCH_HEADSET, true) && black?.isShowing == true) {
                hideAllBlack()
                refreshNotification()
                Toast.makeText(this@OverlayService, getString(R.string.toast_headset), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 省电校准（5分钟向导·黑屏段） ----------

    private var calibOnUa = 0L
    private var calibSamples = mutableListOf<Long>()
    private var calibTicks = 0

    private fun startCalibrationBlack(onUa: Long) {
        calibOnUa = onUa
        if (black?.isShowing != true) {
            black = BlackOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            black?.show { refreshNotification() }
            setBubbleAnimating(false)
        }
        calibSamples = mutableListOf()
        calibTicks = 0
        Toast.makeText(this, getString(R.string.toast_calib_step2), Toast.LENGTH_LONG).show()
        main.postDelayed(calibTick, 30_000)
    }

    private val calibTick: Runnable = Runnable {
        calibTicks++
        PowerCalib.sampleNow(this)?.let { calibSamples.add(it) }
        if (calibTicks < 6) {
            main.postDelayed(calibTick, 30_000)
        } else {
            hideAllBlack()
            refreshNotification()
            val avgOff = if (calibSamples.size >= 4) calibSamples.average().toLong() else 0L
            if (avgOff > 0 && calibOnUa > avgOff) {
                PowerCalib.storeCalibration(this, calibOnUa, avgOff)
                Toast.makeText(this, getString(R.string.toast_calib_ok, calibSamples.size), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_calib_invalid), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 真息屏续播：按电源键真熄屏后，视频App会自己暂停；
     * 向系统注入"播放"媒体键，视频App的MediaSession收到后在息屏+后台状态恢复出声。
     * 不能用"熄屏瞬间isMusicActive"判断——广播到达时App往往已暂停；
     * 改用"熄屏前15秒内有声音在播"判定，避免平时锁屏误触发。
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_TEST_TOGGLE -> {
                Log.i(TAG, "TEST_TOGGLE broadcast received")
                toggleOverlay()
            }

                Intent.ACTION_SCREEN_OFF -> {
                    // 电源键/自动息屏时若黑幕还开着：撤掉遮罩，唤醒后直接回到视频画面
                    if (isAnyBlackShowing()) {
                        hideAllBlack()
                        refreshNotification()
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // 常驻部分唤醒锁：服务运行期间保持CPU唤醒、防止ColorOS冻结进程
        // （「熄屏挂机」类工具的标准做法；退出助手即释放，不白白耗电）
        // 部分ROM会剥离WAKE_LOCK权限：拿不到时降级运行，绝不能拖垮服务
        wakeLock = try {
            (getSystemService(POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "XiTing:service")
                .apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) {
            Log.w(TAG, "唤醒锁获取失败，降级为无锁运行: $e")
            null
        }
        if (Build.VERSION.SDK_INT >= 31) {
            registerModeListener()
            Log.i(TAG, "来电监听已注册")
        }
        audioManager.registerAudioDeviceCallback(headsetCb, null)
        prefs = prefs()
        prefs.edit().putBoolean(Prefs.ASSISTANT_WANTED, true).apply()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        // 上次退出时留的「撤销」通知：助手已重启，清掉
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_EXIT_ID)
        showBubble()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            // UAT 测试钩子只在 debug 包注册。release 包必须收不到：
            // 否则同机任意 App 一条广播就能强制开关用户的黑幕
            // （ACTION_SCREEN_ON / UNLOCK_SKINS / WEAR / RUN_MODE 这几条在
            //   screenOffReceiver 里其实已无处理分支，属「真息屏续播」移除后的残留，
            //   一并收进 debug 门禁，不改变任何对外行为）
            if (BuildConfig.DEBUG) {
                addAction(ACTION_TEST_TOGGLE)
                addAction(ACTION_TEST_RUN_MODE)
                addAction(ACTION_TEST_UNLOCK_SKINS)
                addAction(ACTION_TEST_WEAR)
            }
        }
        // RECEIVER_NOT_EXPORTED：只收本进程 + 系统广播。
        // ACTION_SCREEN_OFF 是 protected 系统广播，非导出模式下依然收得到；
        // 同机其他 App 的一律拦掉。
        // Context.RECEIVER_NOT_EXPORTED 是编译期内联的 int，5 参 registerReceiver
        // 自 API 26 起就存在，故 minSdk 26 无需按版本分支。
        registerReceiver(screenOffReceiver, filter, null, null, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleOverlay()
            ACTION_BUBBLE -> setBubbleVisible(!prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false))
            ACTION_SET_TIMER -> handleSetTimer(
                intent?.getLongExtra(EXTRA_MINUTES, 0) ?: 0,
                intent?.getLongExtra(EXTRA_END_AT, 0) ?: 0
            )
            ACTION_CALIB_START -> startCalibrationBlack(
                intent?.getLongExtra(EXTRA_CALIB_ON_UA, 0) ?: 0
            )
            ACTION_TEST_TOGGLE -> toggleOverlay() // 测试广播
            ACTION_TEST_UNLOCK_SKINS -> PetSkins.unlockAllForDebug(this)
            ACTION_TEST_WEAR -> {
                val skinId = intent?.getStringExtra("skin") ?: "star"
                PetSkins.wear(this, PetSkins.ALL.first { it.id == skinId })
                refreshNotification()
            }
            ACTION_EXIT -> {
                prefs.edit().putBoolean(Prefs.ASSISTANT_WANTED, false).apply()
                hideAllBlack()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 31) {
            (modeListener as? AudioManager.OnModeChangedListener)?.let {
                try { audioManager.removeOnModeChangedListener(it) } catch (_: Exception) {}
            }
            modeListener = null
        }
        try { audioManager.unregisterAudioDeviceCallback(headsetCb) } catch (_: Exception) {}
        main.removeCallbacksAndMessages(null)
        hideAllBlack()
        bubble?.let { b -> try { wm.removeView(b) } catch (_: Exception) {} }
        bubble = null
        // 必须释放：PARTIAL_WAKE_LOCK 跨 onDestroy 存活会让 CPU 永不休眠。
        // 本进程还挂着 NotificationListener + 3 个小组件，ColorOS 下进程不会立刻死，
        // 漏释放等于「退出助手后仍在耗电」——对一款卖省电的 App 是最严重的反噬。
        releaseWakeLock()
        super.onDestroy()
    }

    /** 释放唤醒锁；服务已持有时调用幂等 */
    private fun releaseWakeLock() {
        val wl = wakeLock ?: return
        try {
            if (wl.isHeld) wl.release()
        } catch (e: Exception) {
            Log.w(TAG, "唤醒锁释放失败: $e")
        } finally {
            wakeLock = null
        }
    }

    // ---------- 悬浮球 ----------

    private fun showBubble() {
        if (bubble != null) return
        if (prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false)) return // 用户已隐藏悬浮球：尊重设置
        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()

        // 悬浮球样式：默认「息屏」文字，可换成精灵形象（形态与配色跟随图鉴里的选择）
        val usePet = PetForm.bubbleUsesPet(this)
        val tv: View = if (usePet) {
            val st = PetForm.selected(this)
            val skin = PetSkins.active(this)
            BubblePetView(this).apply {
                stage = st
                skinHue = skin.hue
                layoutParams = android.view.ViewGroup.LayoutParams(size, size)
            }
        } else {
            TextView(this).apply {
                text = "息屏"
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = size
                minHeight = size
                val bg = ShapeDrawable(OvalShape())
                bg.paint.color = 0xB3000000.toInt()
                background = bg
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        // 悬浮球位置记忆：恢复上次拖动后的位置
        lp.x = prefs.getInt(Prefs.BUBBLE_X, resources.displayMetrics.widthPixels - size - (8 * density).toInt())
        lp.y = prefs.getInt(Prefs.BUBBLE_Y, (180 * density).toInt())
        bubbleLp = lp   // 确认条定位用（球的窗口坐标在这里，View.getX 恒为 0）

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downAt = 0L
        var longPressFired = false

        cancelLongPress()
        longPressCheck = Runnable {
            longPressCheck = null
            if (!moved) {
                longPressFired = true
                vibrateShort()
                showExitConfirm()
            }
        }

        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPressFired = false
                    downAt = SystemClock.elapsedRealtime()
                    longPressCheck?.let { main.postDelayed(it, 400) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        moved = true
                        cancelLongPress()
                    }
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cancelLongPress()
                    // 短按（<250ms）即使有轻微位移也按点击处理：手持抖动超过
                    // 触摸容差很常见，不能让点击被当成拖动吞掉
                    val quickTap = SystemClock.elapsedRealtime() - downAt < 250
                    Log.i(TAG, "bubble ACTION_UP, moved=$moved, quickTap=$quickTap, longPress=$longPressFired")
                    when {
                        longPressFired -> Unit                 // 退出确认条已弹出，UP 不再触发
                        moved && !quickTap -> {
                            // 贴边吸附：吸到最近的左右边缘，避免悬浮球悬在半空挡内容
                            val margin = (8 * density).toInt()
                            val w = resources.displayMetrics.widthPixels
                            lp.x = if (lp.x + lp.width / 2 < w / 2) margin else w - tv.width - margin
                            wm.updateViewLayout(tv, lp)
                            prefs.edit().putInt(Prefs.BUBBLE_X, lp.x).putInt(Prefs.BUBBLE_Y, lp.y).apply()
                        }
                        else -> toggleOverlay()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(tv, lp)
            bubble = tv
            prefs.edit().putBoolean(Prefs.BUBBLE_PERM_ERROR, false).apply()
            Log.e(TAG, "bubble added at ${lp.x},${lp.y}")
        } catch (e: Exception) {
            Log.e(TAG, "bubble add failed: $e")
            // 权限表征与内核态不一致（重装后 ColorOS 常见）：标记供主界面引导修复
            prefs.edit().putBoolean(Prefs.BUBBLE_PERM_ERROR, true).apply()
        }
    }

    /** 悬浮球是否存在（供自愈检测：服务在跑但球丢了就重建） */
    fun isBubbleVisible(): Boolean = bubble != null

    /** 悬浮球显隐（通知按钮/设置开关/长按退出共用）：状态持久化，重启尊重 */
    fun setBubbleVisible(visible: Boolean) {
        prefs.edit().putBoolean(Prefs.BUBBLE_HIDDEN, !visible).apply()
        if (visible) showBubble() else {
            bubble?.let { b -> try { wm.removeView(b) } catch (_: Exception) {} }
            bubble = null
        }
        refreshNotification()
    }

    /** 重建悬浮球（样式切换后立即生效） */
    fun rebuildBubble() {
        bubble?.let { b -> try { wm.removeView(b) } catch (_: Exception) {} }
        bubble = null
        showBubble()
    }

    // ---------- 悬浮球长按退出 ----------

    private var exitConfirm: View? = null
    private var bubbleLp: WindowManager.LayoutParams? = null

    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager)
                    .defaultVibrator.vibrate(40)
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(40)
            }
        } catch (_: Exception) {}
    }

    /** 球旁弹出退出确认条（4 秒无操作自动消失；点外部也消失） */
    private fun showExitConfirm() {
        if (exitConfirm != null) return
        val d = resources.displayMetrics.density
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.exit_pill_bg)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
        }
        pill.addView(TextView(this).apply {
            text = getString(R.string.exit_confirm_title)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(0, 0, (14 * d).toInt(), 0)
        })
        pill.addView(TextView(this).apply {
            text = getString(R.string.exit_confirm_yes)
            setTextColor(android.graphics.Color.rgb(255, 120, 110))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            setOnClickListener { hideExitConfirm(); exitAssistant() }
        })
        pill.addView(TextView(this).apply {
            text = getString(R.string.dlg_cancel)
            setTextColor(android.graphics.Color.parseColor("#9FB0D0"))
            textSize = 13f
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setOnClickListener { hideExitConfirm() }
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        // 定位到悬浮球旁：球的窗口坐标存在 bubbleLp（View.getX 在独立窗口里恒为 0，
        // 之前误用它导致确认条总在固定位置）。球在上半屏弹下方，下半屏弹上方。
        pill.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pillW = pill.measuredWidth
        val pillH = pill.measuredHeight
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        bubbleLp?.let { blp ->
            val bw = bubble?.width ?: 96
            val bh = bubble?.height ?: 96
            val bubbleCx = blp.x + bw / 2
            val below = blp.y < screenH / 2
            lp.x = (bubbleCx - pillW / 2).coerceIn(12, maxOf(12, screenW - pillW - 12))
            lp.y = if (below) blp.y + bh + 14
                   else (blp.y - pillH - 14).coerceAtLeast(12)
        } ?: run { lp.x = 200; lp.y = 600 }
        try {
            wm.addView(pill, lp)
            exitConfirm = pill
            pill.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { hideExitConfirm(); true } else false
            }
            main.postDelayed({ hideExitConfirm() }, 4000)
        } catch (e: Exception) {
            Log.w(TAG, "退出确认条显示失败: $e")
        }
    }

    private fun hideExitConfirm() {
        exitConfirm?.let { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        exitConfirm = null
    }

    /** 彻底退出助手（长按确认/通知/磁贴共用）：球与黑幕全撤，留一条可撤销通知 */
    private fun exitAssistant() {
        prefs.edit().putBoolean(Prefs.ASSISTANT_WANTED, false).apply()
        hideAllBlack()
        hideExitConfirm()
        bubble?.let { b -> try { wm.removeView(b) } catch (_: Exception) {} }
        bubble = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        showUndoNotification()
    }

    private fun showUndoNotification() {
        val undoPi = PendingIntent.getForegroundService(
            this, 12, Intent(this, OverlayService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.exit_notif_title))
            .setContentText(getString(R.string.exit_notif_text))
            .setContentIntent(undoPi)
            .addAction(0, getString(R.string.exit_notif_undo), undoPi)
            .setAutoCancel(true)
            .build()
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_EXIT_ID, n)
        } catch (_: Exception) {}
    }

    // ---------- 黑屏遮罩 ----------

    // 睡眠定时器：到期自动关闭黑幕（0=未设置）
    @Volatile
    private var timerEndAt = 0L

    fun timerRemainingMs(): Long =
        if (timerEndAt > 0) (timerEndAt - System.currentTimeMillis()).coerceAtLeast(0) else 0

    private val timerTick: Runnable = Runnable {
        if (timerEndAt <= 0) return@Runnable
        if (System.currentTimeMillis() >= timerEndAt) {
            timerEndAt = 0
            if (isAnyBlackShowing()) {
                hideAllBlack()
                Toast.makeText(this, getString(R.string.toast_timer_done), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_timer_expired), Toast.LENGTH_SHORT).show()
            }
            refreshNotification()
        } else {
            refreshNotification()
            main.postDelayed(timerTick, 30_000)
        }
    }

    private fun handleSetTimer(minutes: Long, endAtMs: Long = 0L) {
        timerEndAt = when {
            endAtMs > 0 -> endAtMs
            minutes > 0 -> System.currentTimeMillis() + minutes * 60_000
            else -> 0
        }
        main.removeCallbacks(timerTick)
        if (timerEndAt > 0) {
            main.postDelayed(timerTick, 15_000)
            if (endAtMs > 0) {
                Toast.makeText(this, getString(R.string.toast_timer_episode), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_timer_set, minutes), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_timer_cancelled), Toast.LENGTH_SHORT).show()
        }
        refreshNotification()
    }

    /** 黑幕显示中时重挂（设置变更即时生效；同帧先拆后挂，视觉无闪动） */
    fun reapplyBlack() {
        if (!isAnyBlackShowing()) return
        hideAllBlack()
        black = BlackOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        black?.show { refreshNotification() }
        setBubbleAnimating(false)
    }

    /** 黑幕/恢复 切换（悬浮球、通知、快捷磁贴共用） */
    fun toggleOverlay() {
        Log.i(TAG, "toggleOverlay via app overlay")
        if (black?.isShowing == true) {
            hideAllBlack()
        } else {
            black = BlackOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            black?.show { refreshNotification() }
            setBubbleAnimating(false)
            // 会话功耗采样：黑屏期间每 60 秒记录一次电池电流
            main.removeCallbacks(powerTick)
            main.postDelayed(powerTick, 60_000)
        }
        refreshNotification()
    }

    // ---------- 通知 ----------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val togglePi = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitPi = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val bubblePi = PendingIntent.getService(
            this, 3, Intent(this, OverlayService::class.java).setAction(ACTION_BUBBLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val remainMin = timerRemainingMs() / 60000
        val timerText = if (timerEndAt > 0) " · 定时${remainMin + 1}分钟后关闭" else ""
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, timerText))
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.notif_action_stop), exitPi)
            .addAction(0, getString(if (isAnyBlackShowing()) R.string.notif_action_restore else R.string.notif_action_listen), togglePi)
            .addAction(0, getString(if (prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false)) R.string.notif_action_bubble_show else R.string.notif_action_bubble_hide), bubblePi)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        WidgetData.refreshAll(this) // 小部件状态同步
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}
