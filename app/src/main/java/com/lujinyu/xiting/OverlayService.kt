// XiTing · (c) 2026 ch1209498273 · 非商业许可（见LICENSE）· 溯源ID见应用页脚与assets/.trace
package com.lujinyu.xiting

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
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
        /**
         * 唤醒锁兜底超时：10 小时。
         *
         * 正常情况下锁会在用户「退出助手」时立即释放，所以这个值只在服务挂死时生效。
         * 取 10 小时是因为它要足够大 —— 比任何一次正常的连续听剧（连着一整夜也就
         * 8 小时上下）都长，所以绝不会误杀；又足够小，能在服务真的挂死时把手机救回来。
         */
        const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 60L * 1000L

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
        const val ACTION_CALIB_START = "com.lujinyu.xiting.CALIB_START"   // 保留：外部 adb 调试用
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

    /**
     * 注册音频模式监听（来电/通话检测）。
     *
     * 仅 31+ 调用（见下方两个 `SDK_INT >= 31` 分支）。消掉 NewApi 告警即可 ——
     * 它们是**设计内的**：上方注释记着当初「矩阵回归 api26/29 实测崩溃」，
     * 所以 `OnModeChangedListener` 这个类型必须隔离在方法体内，
     * 只在 31+ 真正调用时才被解析。
     */
    @SuppressLint("NewApi")
    private fun registerModeListener() {
        val l = AudioManager.OnModeChangedListener { mode ->
            Log.i(TAG, "audio mode -> $mode")
            when (mode) {
                AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL -> main.post {
                    Log.i(TAG, "来电/通话中：黑幕解除 + 挂起自动动作")
                    hideAllBlack()   // 内部已停掉 powerTick（会话功耗采样）
                    // 只停「会被通话状态打断」的两条链：
                    //                    //  · 悬浮球长按 —— 松手时机被通话打断，不该再弹出退出确认条
                    //
                    // 绝不能改成 removeCallbacksAndMessages(null)：那会连带干掉
                    // timerTick。timerTick 的职责是在未到期时把自己重新 post 一次，
                    // 一旦被整体清空就没有任何人再挂它，睡眠定时器从此静默失效
                    // （timerEndAt 仍留着值，看起来一切正常，实际永远不会触发）。
                    cancelLongPress()
                    refreshNotification()
                    try {
                        Toast.makeText(this, lstr(R.string.toast_call), Toast.LENGTH_LONG).show()
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

    /** 悬浮球是否存在（供自愈检测：服务在跑但球丢了就重建） */
    fun isBubbleVisible(): Boolean = bubble != null

    private fun hideAllBlack() {
        val wasShowing = black?.isShowing == true
        black?.hide()
        black = null
        main.removeCallbacks(powerTick) // 停止会话功耗采样
        setBubbleAnimating(true)
        // 小组件必须在这里刷，而不是依赖 BlackOverlay 的 onDismissCallback：
        // 那个回调**只在轻点解锁那条路径上被调用**（BlackOverlay.hide() 结尾会把
        // 它置空），而耳机拔出、来电、退出助手、磁贴都走本方法。
        // 结果就是：这些路径结束了一次听剧（有新记录、能量可能入账），
        // 小组件却一直显示旧数据，直到 30 分钟轮询才纠正。
        if (wasShowing) refreshNotification()
    }

    /**
     * 黑幕是不透明全屏窗，把悬浮球整个盖住。此时精灵悬浮球若继续按帧重绘，
     * 就是在完全看不见的地方空转——一次两小时听剧等于十几万帧无效渲染。
     * 文字样式的悬浮球不是 BubblePetView，无需处理。
     */
    private fun setBubbleAnimating(on: Boolean) {
        (bubble as? BubblePetView)?.setAnimating(on)
    }

    // ---------- 省电实测：亮屏段被动采样（零打扰） ----------

    /**
     * 亮屏段采样。
     *
     * 这是**零打扰方案的关键一半**：用户不需要做任何事，App 就在后台
     * 周期读能量计数器，累积「正常使用时的能耗速率」作为分母。
     *
     * 只有满足以下条件才计一个段：
     * - 屏幕亮着（熄屏/锁屏时不算，否则混进待机功耗）
     * - 没在充电（插电时计数器回升，差值无意义）
     * - 黑幕没开（开了就是黑屏段，不能重复计）
     */
    private val powerTick: Runnable = Runnable {
        val now = System.currentTimeMillis()
        if (black?.isShowing == true) {
            // 黑幕显示中 —— 这段归 BlackOverlay 自己记，这里不动
        } else if (isScreenOn() && !PowerCalib.isCharging(this)) {
            val start = onSegStartUwh
            if (start != null) {
                val dur = now - onSegStartAt
                if (dur >= PowerCalib.MIN_SAMPLE_MS) {
                    PowerCalib.energyCounter(this)?.let { end ->
                        PowerCalib.recordSegment(this, start, end, dur, black = false)
                    }
                }
                onSegStartUwh = null      // 本段结束，下次开屏重新起段
            } else {
                onSegStartUwh = PowerCalib.energyCounter(this)
                onSegStartAt = now
            }
        } else {
            onSegStartUwh = null          // 亮屏段中断
        }
        main.postDelayed(powerTick, 60_000)
    }

    private var onSegStartUwh: Long? = null
    private var onSegStartAt = 0L

    /** 屏幕是否亮着（读 PowerManager.isInteractive） */
    private fun isScreenOn(): Boolean {
        return try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            pm.isInteractive
        } catch (_: Exception) {
            false   // 读不到就不计，宁可少测
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
                // hideAllBlack() 内部已经 refreshNotification()（修小组件漏刷新那次加的），
                // 不要再调一次
                hideAllBlack()
                Toast.makeText(this@OverlayService, lstr(R.string.toast_headset), Toast.LENGTH_SHORT).show()
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
                        hideAllBlack()   // 内部已 refreshNotification()
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    // ---------- 语言随切随新 ----------
    //
    // 服务是长生命周期组件：attachBaseContext 只在进程创建时跑一次。用户之后切
    // 语言（per-app locale 变化）时，已经建好的球/通知不会自己更新 —— 文案在
    // 创建那一刻 getString 出来就定死了。对策两件套：
    // 1. 面向用户的文案一律走 lstr()：调用点当场 wrap 一次，永远取当前语言；
    // 2. 语言切换会回调 onConfigurationChanged，就地重建球/通知/渠道名。
    // （AppLocales.wrap 在 API 33+ 直接读系统 LocaleManager、低版本读 pref，
    //   两种路径都能拿到最新值。）

    /** 最近一次见到的语言列表；只认语言变化，不因深色模式/字体缩放重建 UI */
    private var lastLocales: String? = null

    /** 取文案（实时语言）。服务内面向用户的字符串都从这里走。 */
    private fun lstr(id: Int, vararg args: Any?): String =
        AppLocales.wrap(this).getString(id, *args)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = newConfig.locales.toLanguageTags()
        if (lastLocales != null && lastLocales != now) {
            refreshLocale()
        }
        lastLocales = now
    }

    /** 语言切换后调用：把常驻的系统级文案（球/通知/渠道名）换成当前语言 */
    fun refreshLocale() {
        createChannel()
        rebuildBubble()
        refreshNotification()
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
        //
        // 必须带超时（lint WakelockTimeout）：如果服务没走到 onDestroy（被系统强杀、
        // 进程崩溃、异常退出），无超时的 PARTIAL_WAKE_LOCK 会让 CPU 一直醒着，
        // 手机发烫掉电而用户完全无感。WAKE_TIMEOUT 是兜底：远大于任何一次正常
        // 听剧时长，又能保证挂死时自动释放。
        wakeLock = try {
            (getSystemService(POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "XiTing:service")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
        } catch (e: Exception) {
            Log.w(TAG, "唤醒锁获取失败，降级为无锁运行: $e")
            null
        }
        if (Build.VERSION.SDK_INT >= 31) {
            registerModeListener()
            Log.i(TAG, "来电监听已注册")
        }
        audioManager.registerAudioDeviceCallback(headsetCb, null)
    // 省电实测：亮屏段被动采样的常驻循环。
    // 不启动它的话，用户从不点悬浮球、只开 App 看统计，就完全不会有分母样本。
    main.removeCallbacks(powerTick)
    main.postDelayed(powerTick, 60_000)
        prefs = prefs()
        lastLocales = resources.configuration.locales.toLanguageTags()
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
            // 通知里的「显示/隐藏悬浮球」：把当前状态取反。
            // ⚠️ 这里曾经写成 `setBubbleVisible(!BUBBLE_HIDDEN)` —— 而 BUBBLE_HIDDEN=false
            // 时按钮文案正是「隐藏」，却去调 setBubbleVisible(true)，即「去显示一个已经显示着的球」。
            // 两种状态都是空操作，所以这个按钮从来就没生效过。
            // 正确写法：BUBBLE_HIDDEN 同时也是切换后的目标可见性（隐藏中→显示，未隐藏→隐藏）。
            ACTION_BUBBLE -> setBubbleVisible(prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false))
            ACTION_SET_TIMER -> handleSetTimer(
                intent?.getLongExtra(EXTRA_MINUTES, 0) ?: 0,
                intent?.getLongExtra(EXTRA_END_AT, 0) ?: 0
            )
            // ⚠ 原来的 ACTION_CALIB_START 分支已删：省电实测改成**被动测量**，
            // 不再需要「先亮屏采 3 分钟、再黑屏采 3 分钟」的向导。
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
                applySkin(skin)
                layoutParams = android.view.ViewGroup.LayoutParams(size, size)
            }
        } else {
            TextView(this).apply {
                text = lstr(R.string.bubble_label_plain)
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                // 形状由窗口的固定正方尺寸保证（见下方 lp 的创建处）：
                // 窗口 48dp 见方 + OvalShape 背景 = 正圆，不随译文长度变形。
                // 这里只负责单行显示，超长译文截断而不是把圆撑成椭圆。
                // 配套约束：各语言的 bubble_label_plain 必须短（≤ 3 个全角字 / 6 个半角字符）。
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                val bg = ShapeDrawable(OvalShape())
                bg.paint.color = 0xB3000000.toInt()
                background = bg
            }
        }

        val lp = WindowManager.LayoutParams(
            // ⚠️ 窗口必须是**固定正方（size×size）**，不能用 WRAP_CONTENT：
            // 背景是 OvalShape（椭圆填满视图边界），WRAP_CONTENT 下窗口尺寸会随译文长度变化，
            // 实测俄语「Выкл」得到 93x53px 扁椭圆，而非应有的 48dp 正方形（本机 144x144px）。
            // ⚠️ 固定尺寸必须设在**这里（窗口参数）**，不能设在 view.layoutParams ——
            // `wm.addView(tv, lp)` 会用 lp 覆盖掉 view 自己的 layoutParams，设了也是白设。
            // 精灵模式 BubblePetView.onMeasure 自测量到 48dp，与此值一致。
            size,
            size,
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
                    // ⚠️ 必须在每次 DOWN **重建** longPressCheck，不能只在建 View 时建一次。
                    // 原实现在这外面建好 Runnable，而 cancelLongPress() 会把它置为 null；
                    // 于是第一次短按（正常点球切黑幕）后 longPressCheck 就永远是 null，
                    // 后续 `?.let` 全部落空 —— 长按退出从那时起永久失效，
                    // 要等悬浮球重建才恢复（用户反馈「用一段时间后长按退出失灵」）。
                    longPressCheck = Runnable {
                        longPressCheck = null
                        if (!moved) {
                            longPressFired = true
                            vibrateShort()
                            showExitConfirm()
                        }
                    }
                    main.postDelayed(longPressCheck!!, 400)
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
                        else -> {
                            // 走标准点击入口，让无障碍服务能识别悬浮球是可点的
                            // （lint ClickableViewAccessibility）
                            v.performClick()
                            toggleOverlay()
                        }
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

    /**
     * 同步悬浮球的**外观**（已于 2026-10-07 删除）。
     *
     * 起因是一个**误诊**：当时报「悬浮球没跟着形态变」，我去查了 logcat、导出备份、
     * 写了对账逻辑，结果真机证据表明悬浮球显示的**一直是正确**的，
     * 过期的是**设置页「悬浮球样式」那一行**（`switchTab` 里只给统计页加了重绘）。
     *
     * 保留这段说明是为了不让后人重蹈：一个「看起来像缓存没刷新」的现象，
     * 先分清是**哪一块 UI 陈旧**再动手 —— 我当时把锅扣在了错的组件上。
     */

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
        val pill = object : LinearLayout(this) {
            /**
             * 覆写标准点击入口（lint ClickableViewAccessibility）。
             * 容器本身不处理点击（只处理 ACTION_OUTSIDE），但因为它装了
             * setOnTouchListener，无障碍服务需要能读到 performClick 已被处理，
             * 否则 TalkBack 会把它当成一个读不出名字的可点击控件。
             */
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.exit_pill_bg)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
        }
        pill.addView(TextView(this).apply {
            text = lstr(R.string.exit_confirm_title)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding(0, 0, (14 * d).toInt(), 0)
        })
        pill.addView(TextView(this).apply {
            text = lstr(R.string.exit_confirm_yes)
            setTextColor(android.graphics.Color.rgb(255, 120, 110))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
            setOnClickListener { hideExitConfirm(); exitAssistant() }
        })
        pill.addView(TextView(this).apply {
            text = lstr(R.string.dlg_cancel)
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
            // pill 本身的点击已由子 View 的 OnClickListener 处理，这里只负责「点外面就收起」。
            // ACTION_OUTSIDE 不是点击，不该调 performClick()——调了会对无障碍服务
            // 谎报一次点击（@SuppressLint 写在下面那句 lambda 上）。
            @SuppressLint("ClickableViewAccessibility")
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
            .setContentTitle(lstr(R.string.exit_notif_title))
            .setContentText(lstr(R.string.exit_notif_text))
            .setContentIntent(undoPi)
            .addAction(0, lstr(R.string.exit_notif_undo), undoPi)
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
                Toast.makeText(this, lstr(R.string.toast_timer_done), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, lstr(R.string.toast_timer_expired), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, lstr(R.string.toast_timer_episode), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, lstr(R.string.toast_timer_set, minutes), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, lstr(R.string.toast_timer_cancelled), Toast.LENGTH_SHORT).show()
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
            // 省电实测：重启常驻循环。黑幕打开时它会自动让位给 BlackOverlay 记黑屏段。
            // 先 remove 再 post，避免反复开关黑幕时排出一串重复任务。
            main.removeCallbacks(powerTick)
            main.postDelayed(powerTick, 60_000)
        }
        refreshNotification()
    }

    // ---------- 通知 ----------

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, lstr(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
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
        val timerText = if (timerEndAt > 0) lstr(R.string.notif_timer_fmt, remainMin + 1) else ""
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(lstr(R.string.notif_title))
            .setContentText(lstr(R.string.notif_text, timerText))
            .setContentIntent(openPi)
            .addAction(0, lstr(R.string.notif_action_stop), exitPi)
            .addAction(0, lstr(if (isAnyBlackShowing()) R.string.notif_action_restore else R.string.notif_action_listen), togglePi)
            .addAction(0, lstr(if (prefs.getBoolean(Prefs.BUBBLE_HIDDEN, false)) R.string.notif_action_bubble_show else R.string.notif_action_bubble_hide), bubblePi)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        WidgetData.refreshAll(this) // 小部件状态同步
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }
}
