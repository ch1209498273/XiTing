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
        private const val NOTIF_ID = 1
        private const val ACTION_TOGGLE = "com.lujinyu.xiting.TOGGLE"
        const val ACTION_EXIT = "com.lujinyu.xiting.EXIT"
        const val ACTION_SET_TIMER = "com.lujinyu.xiting.SET_TIMER"
        const val EXTRA_MINUTES = "minutes"
        private const val ACTION_MEDIA_PREV = "com.lujinyu.xiting.MEDIA_PREV"
        private const val ACTION_MEDIA_PLAYPAUSE = "com.lujinyu.xiting.MEDIA_PLAYPAUSE"
        private const val ACTION_MEDIA_NEXT = "com.lujinyu.xiting.MEDIA_NEXT"
        private const val PREFS = "xiiting_prefs"

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

    private var bubble: TextView? = null
    private var black: BlackOverlay? = null

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
            audioManager.addOnModeChangedListener(main::post, modeListener)
            Log.i(TAG, "来电监听已注册")
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean("assistant_wanted", true).apply()
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
            ACTION_SET_TIMER -> handleSetTimer(intent?.getLongExtra(EXTRA_MINUTES, 0) ?: 0)
            ACTION_MEDIA_PREV, ACTION_MEDIA_PLAYPAUSE, ACTION_MEDIA_NEXT ->
                intent?.action?.let { dispatchMediaKey(it) }
            ACTION_EXIT -> {
                prefs.edit().putBoolean("assistant_wanted", false).apply()
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
            try { audioManager.removeOnModeChangedListener(modeListener) } catch (_: Exception) {}
        }
        main.removeCallbacksAndMessages(null)
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
        var downAt = 0L

        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    downAt = SystemClock.elapsedRealtime()
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
                    // 短按（<250ms）即使有轻微位移也按点击处理：手持抖动超过
                    // 触摸容差很常见，不能让点击被当成拖动吞掉
                    val quickTap = SystemClock.elapsedRealtime() - downAt < 250
                    Log.i(TAG, "bubble ACTION_UP, moved=$moved, quickTap=$quickTap")
                    if (moved && !quickTap) {
                        // 贴边吸附：吸到最近的左右边缘，避免悬浮球悬在半空挡内容
                        val margin = (8 * density).toInt()
                        val w = resources.displayMetrics.widthPixels
                        lp.x = if (lp.x + lp.width / 2 < w / 2) margin else w - tv.width - margin
                        wm.updateViewLayout(tv, lp)
                        prefs.edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply()
                    } else {
                        toggleOverlay()
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
                Toast.makeText(this, "定时结束，黑幕已关闭", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "定时已到期", Toast.LENGTH_SHORT).show()
            }
            refreshNotification()
        } else {
            refreshNotification()
            main.postDelayed(timerTick, 30_000)
        }
    }

    private fun handleSetTimer(minutes: Long) {
        timerEndAt = if (minutes <= 0) 0 else System.currentTimeMillis() + minutes * 60_000
        main.removeCallbacks(timerTick)
        if (timerEndAt > 0) {
            main.postDelayed(timerTick, 30_000)
            Toast.makeText(this, "定时关闭：${minutes}分钟后", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "定时关闭已取消", Toast.LENGTH_SHORT).show()
        }
        refreshNotification()
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

    private fun dispatchMediaKey(action: String) {
        try {
            val code = when (action) {
                ACTION_MEDIA_PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                ACTION_MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    // ---------- 通知 ----------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "息屏听剧助手", NotificationManager.IMPORTANCE_LOW)
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
        val mediaPi: (String, Int) -> PendingIntent = { action, rc ->
            PendingIntent.getService(
                this, rc, Intent(this, OverlayService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val exitPi = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_EXIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val remainMin = timerRemainingMs() / 60000
        val timerText = if (timerEndAt > 0) " · 定时${remainMin + 1}分钟后关闭" else ""
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("息屏听剧助手运行中")
            .setContentText("点悬浮球黑屏听剧，声音继续$timerText")
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_media_prev, "上一集", mediaPi(ACTION_MEDIA_PREV, 10))
            .addAction(R.drawable.ic_media_pause, "播放/暂停", mediaPi(ACTION_MEDIA_PLAYPAUSE, 11))
            .addAction(R.drawable.ic_media_next, "下一集", mediaPi(ACTION_MEDIA_NEXT, 12))
            .addAction(0, if (isAnyBlackShowing()) "恢复画面" else "息屏听剧", togglePi)
            .addAction(0, "退出助手", exitPi)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}
