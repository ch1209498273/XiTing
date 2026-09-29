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
        private const val CHANNEL_ALERT = "xiiting_alert"
        private const val NOTIF_ID = 1
        private const val NOTIF_ID_ALERT = 2
        private const val ACTION_TOGGLE = "com.lujinyu.xiting.TOGGLE"
        const val ACTION_EXIT = "com.lujinyu.xiting.EXIT"
        private const val ACTION_RESUME_TOGGLE = "com.lujinyu.xiting.RESUME_TOGGLE"
        const val ACTION_START_BLACK = "com.lujinyu.xiting.START_BLACK"
        const val ACTION_RUN_MODE = "com.lujinyu.xiting.RUN_MODE"
        private const val PREFS = "xiiting_prefs"
        private const val KEY_AUTO_RESUME = "auto_resume_on_screen_off"
        const val KEY_LISTEN_MODE = "listen_mode"

        var instance: OverlayService? = null
            private set
        val isRunning: Boolean get() = instance != null
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val pollHandler = Handler(Looper.getMainLooper())

    /** 来电检测：响铃/通话时音频模式会切换（系统标准回调，无需权限）。
     *  黑幕期间来电 → 自动解除黑幕；通话期间 → 挂起所有自动注入动作。 */
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        Log.i(TAG, "audio mode -> $mode")
        when (mode) {
            AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL -> main.post {
                Log.i(TAG, "来电/通话中：黑幕解除 + 挂起自动动作")
                hideAllBlack()
                main.removeCallbacksAndMessages(null)
                refreshNotification()
                try {
                    Toast.makeText(this, "来电，黑幕已自动解除", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }
    }

    /** 保活监控计数：YouTube等App会在片尾/中途把后台播放掐掉，息屏后3分钟内自动再救 */
    private var keepAliveTicks = 0
    private var screenSessionStart = 0L           // 墙钟：记录起始
    private var screenSessionStartElapsed = 0L    // 单调时钟：算时长用
    private var keepAliveDispatches = 0

    private var bubble: TextView? = null
    private var black: BlackOverlay? = null

    /** 最近一次有App在出声的时刻（elapsedRealtime）：轮询+回调双保险。
     *  必须轮询——持续播放期间AudioPlaybackCallback不会再来回调，单靠回调时间戳会陈旧 */
    private var lastAudioActiveAt = 0L

    private val audioPollRunnable = object : Runnable {
        override fun run() {
            try {
                if (audioManager.isMusicActive) lastAudioActiveAt = SystemClock.elapsedRealtime()
            } catch (_: Exception) {
            }
            pollHandler.postDelayed(this, 2000)
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            if (configs.isNotEmpty()) lastAudioActiveAt = SystemClock.elapsedRealtime()
        }
    }

    /** 黑幕是否在显示，供磁贴等外部判断 */
    fun isAnyBlackShowing(): Boolean = black?.isShowing == true

    private fun hideAllBlack() {
        black?.hide()
        black = null
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
            Intent.ACTION_SCREEN_ON -> {
                    // 用户亮屏了：取消所有待执行的续播/保活，别干扰正常操作
                    Log.i(TAG, "SCREEN_ON: cancel pending resume/keepalive")
                    main.removeCallbacksAndMessages(null)
                    if (screenSessionStart > 0) {
                        val now = System.currentTimeMillis()
                        val elapsed = SystemClock.elapsedRealtime() - screenSessionStartElapsed
                        val dur = if (elapsed >= 0) elapsed else now - screenSessionStart
                        Stats.addDelta(applicationContext, dur)
                        SessionLog.add(applicationContext, ListenSession(screenSessionStart, now, dur, SessionLog.MODE_SCREEN_OFF))
                        screenSessionStart = 0
                    }
                }

                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "SCREEN_OFF received, overlayOn=${isAnyBlackShowing()}")

                    // 遮罩还开着说明屏幕是被电源键强制熄灭的：撤掉遮罩，唤醒后直接回到视频画面
                    if (isAnyBlackShowing()) {
                        hideAllBlack()
                        refreshNotification()
                    }

                    if (!prefs.getBoolean(KEY_AUTO_RESUME, true)) {
                        Log.i(TAG, "auto resume disabled by user, skip")
                        return
                    }
                    val agoMs = SystemClock.elapsedRealtime() - lastAudioActiveAt
                    if (agoMs > 4000) {
                        Log.i(TAG, "skip resume: no audio in last ${agoMs}ms")
                        return
                    }

                    // 关键：持有部分唤醒锁，防止ColorOS在息屏瞬间冻结本App，
                    // 否则后续的媒体键注入回调永远不会执行（真机实测踩坑）
                    val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
                    wakeLock?.release()
                    wakeLock = pm.newWakeLock(
                        android.os.PowerManager.PARTIAL_WAKE_LOCK, "XiTing:resume"
                    ).apply {
                        setReferenceCounted(false)
                        acquire(35 * 1000L) // 35秒后自动释放，覆盖续播+保活窗口
                    }

                    // 视频App暂停有先后、各家响应的键也不同，多试几轮；
                    // KEYCODE_MEDIA_PLAY 对已在播的会话是no-op，不会误暂停
                    keepAliveTicks = 0
                    keepAliveDispatches = 0
                    main.postDelayed({ tryResume(false) }, 1000)
                    main.postDelayed({ tryResume(false) }, 2500)
                    main.postDelayed({ tryResume(false) }, 4500)
                    main.postDelayed({ tryResume(true) }, 7000) // 最后一搏：换PLAY_PAUSE键
                    main.postDelayed({ reportResumeResult() }, 9500)
                    // 保活监控：12秒后开始，每8秒巡检一次，共约3分钟。
                    // YouTube等App会在片尾把后台播放掐断，这里自动再注入PLAY救回
                    main.postDelayed(keepAliveTick, 12000)
                    screenSessionStart = System.currentTimeMillis()
                    screenSessionStartElapsed = SystemClock.elapsedRealtime()
                    Log.i(TAG, "resume attempts scheduled (lastAudioActiveAgo=${agoMs}ms)")
                }
            }
        }
    }

    private val keepAliveTick = object : Runnable {
        override fun run() {
            keepAliveTicks++
            if (keepAliveTicks > 25) {
                Log.i(TAG, "keepalive: 3min window over, stop monitoring")
                return
            }
            if (audioManager.isMusicActive) {
                Log.i(TAG, "keepalive tick $keepAliveTicks: playing, ok")
            } else if (keepAliveDispatches < 5) {
                keepAliveDispatches++
                Log.i(TAG, "keepalive tick $keepAliveTicks: silent, re-dispatch PLAY #$keepAliveDispatches")
                tryResume(false)
            } else {
                Log.i(TAG, "keepalive: still silent after $keepAliveDispatches tries, give up")
                reportResumeResult()
                return
            }
            main.postDelayed(this, 8000)
        }
    }

    /** 向系统注入一次媒体键（DOWN+UP），由视频App的MediaSession接收 */
    private fun tryResume(usePlayPause: Boolean) {
        if (isAnyBlackShowing()) return // 遮罩模式下屏幕没真息，无需恢复
        if (audioManager.isMusicActive) {
            Log.i(TAG, "already playing, skip dispatch")
            return
        }
        try {
            val code =
                if (usePlayPause) KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            Log.i(TAG, "dispatched media key: ${if (usePlayPause) "PLAY_PAUSE" else "PLAY"}")
        } catch (e: Exception) {
            Log.i(TAG, "dispatch failed: $e")
        }
    }

    /** 多轮注入后仍没声音：发条提醒，告诉用户该App不支持这条路 */
    private fun reportResumeResult() {
        val active = audioManager.isMusicActive
        Log.i(TAG, "resume check: isMusicActive=$active")
        if (active) return
        try {
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("自动续播未生效")
                .setContentText("这个App可能不支持媒体键唤醒：改用悬浮球黑屏，或开它自带的「后台播放」")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID_ALERT, n)
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        audioPollRunnable.run()
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
            audioManager.addOnModeChangedListener(main::post, modeListener)
            Log.i(TAG, "来电监听已注册")
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        showBubble()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleOverlay()
            ACTION_START_BLACK ->
                // 主界面「一键息屏听剧」：onStartCommand在onCreate之后主线程执行，直接执行所选模式
                runSelectedMode()

            ACTION_RUN_MODE -> runSelectedMode()
            ACTION_RESUME_TOGGLE -> {
                val cur = prefs.getBoolean(KEY_AUTO_RESUME, true)
                prefs.edit().putBoolean(KEY_AUTO_RESUME, !cur).apply()
                refreshNotification()
            }
            ACTION_EXIT -> {
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
        try { audioManager.unregisterAudioPlaybackCallback(playbackCallback) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 31) {
            try { audioManager.removeOnModeChangedListener(modeListener) } catch (_: Exception) {}
        }
        main.removeCallbacksAndMessages(null)
        pollHandler.removeCallbacksAndMessages(null)
        hideAllBlack()
        bubble?.let { b -> try { wm.removeView(b) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    // ---------- 悬浮球 ----------

    private fun showBubble() {
        if (bubble != null) return
        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()

        val tv = TextView(this).apply {
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
        lp.x = prefs.getInt("bubble_x", resources.displayMetrics.widthPixels - size - (8 * density).toInt())
        lp.y = prefs.getInt("bubble_y", (180 * density).toInt())

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Log.i(TAG, "bubble ACTION_UP, moved=$moved")
                    if (moved) {
                        // 位置记忆
                        prefs.edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply()
                    } else {
                        runSelectedMode()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(tv, lp)
            bubble = tv
            Log.i(TAG, "bubble added at $lp.x,$lp.y")
        } catch (e: Exception) {
            Log.i(TAG, "bubble add failed: $e")
            // 无悬浮窗权限时会到这里；主界面有引导
        }
    }

    // ---------- 黑屏遮罩 ----------

    /** 当前所选听剧模式：0=黑幕 1=真息屏 */
    private val listenMode: Int get() = prefs.getInt(KEY_LISTEN_MODE, 0)

    /**
     * 执行所选模式（悬浮球、通知、磁贴、主界面大按钮共用）。
     * 黑幕模式=全屏黑幕；真息屏模式=锁屏（设备管理员force-lock，激活一次后可用）。
     */
    fun runSelectedMode() {
        if (listenMode == 1) {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(this, LockReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow() // 真息屏：SCREEN_OFF广播接自动续播链路
            } else {
                // 首次使用：引导激活一键锁屏（标准设备管理员流程，可随时在系统设置里停用）
                startActivity(
                    android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "用于悬浮球触发真息屏（锁屏听剧）。激活后点悬浮球即锁屏，声音自动恢复。"
                        )
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        } else {
            toggleOverlay()
        }
    }

    /** 黑幕/恢复 切换（悬浮球、通知、快捷磁贴共用） */
    fun toggleOverlay() {
        Log.i(TAG, "toggleOverlay via app overlay")
        if (black?.isShowing == true) {
            black?.hide()
            black = null
        } else {
            black = BlackOverlay(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            black?.show { refreshNotification() }
        }
        refreshNotification()
    }

    // ---------- 通知 ----------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "息屏听剧助手", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "续播提醒", NotificationManager.IMPORTANCE_DEFAULT)
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
        val resumePi = PendingIntent.getService(
            this, 3, Intent(this, OverlayService::class.java).setAction(ACTION_RESUME_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val autoResume = prefs.getBoolean(KEY_AUTO_RESUME, true)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("息屏听剧助手运行中")
            .setContentText("点悬浮球黑屏听 · 或按电源键真息屏自动续播")
            .setContentIntent(openPi)
            .addAction(0, if (isAnyBlackShowing()) "恢复画面" else "息屏听剧", togglePi)
            .addAction(0, if (autoResume) "真息屏续播：开" else "真息屏续播：关", resumePi)
            .addAction(0, "退出助手", exitPi)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}
