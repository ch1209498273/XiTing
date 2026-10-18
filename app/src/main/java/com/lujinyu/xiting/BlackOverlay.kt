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
import android.widget.Toast
import android.widget.TextView

/**
 * 黑屏遮罩：全屏纯黑窗口，吞掉所有触摸防误触。
 *
 * 交互（两段式解锁）：
 *   锁定态：全黑 + 背光物理关闭（screenBrightness=OFF）
 *   轻点屏幕 → 解除锁定：背光恢复到用户亮度，浮现紧凑播放面板
 *   点返回播放器 → 返回原应用；5秒无操作 → 自动重新锁定
 *
 * 系统栏隐藏（实测重要）：insets隐藏只做一次、绝不周期性重复调用——
 * ColorOS 16上反复调用hide()反而会让系统栏重新显示（真机A/B实测结论）。
 *
 * @param windowType TYPE_APPLICATION_OVERLAY（普通悬浮窗）或
 *                   TYPE_ACCESSIBILITY_OVERLAY（无障碍服务层）
 */
class BlackOverlay(private val context: Context, private val windowType: Int,
    private val recordSession:Boolean=true,private val demo:Boolean=false) {

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
    internal val isAwake:Boolean get()=awake
    private var mediaRow: LinearLayout? = null
    private var playPauseBtn: android.widget.ImageView? = null
    private var previousBtn:android.widget.ImageView?=null
    private var nextBtn:android.widget.ImageView?=null
    private var lastSeekSession:android.media.session.MediaSession.Token?=null
    private var sourceText:TextView?=null
    private var touchHint:TextView?=null
    private var titleText: TextView? = null        // 唤醒态剧名（需通知使用权，无则不显示）
    private var wakePanel: android.widget.ScrollView? = null
    private var timerStatus:TextView?=null
    private var timerArea:View?=null
    private var closeTimerEditor:(()->Unit)?=null
    private var timerEditor:CurtainTimerPanel?=null
    private var captionText: TextView? = null      // 黑幕字幕（实验性，无障碍采集，居中大字）
    private var seekBackBtn: TextView? = null      // ±30s（会话声明 ACTION_SEEK_TO 才显示）
    private var seekFwdBtn: TextView? = null
    /** ±30s 连点防抖基准：-1 = 窗口外（下一次点按从真实位置起算） */
    private var lastSeekTarget = -1L
    private val seekReset = Runnable { lastSeekTarget = -1 }
    private val nowPlayingTick: Runnable = Runnable {   // 唤醒态轻轮询（2s；锁定态不跑）
        refreshNowPlaying()
        main.postDelayed(nowPlayingTick, 2_000)
    }
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
    /** 会话开始时的剩余电荷（µAh）；null = 读不到（不支持或当时在充电） */
    private var sessionStartUwh: Long? = null
    private var sessionStartElapsed = 0L   // 单调时钟：用于时长计算，不受时间跳变/跨天影响
    private val relockRunnable = Runnable { if(timerEditor==null)sleep() }

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
        PlaybackSelection.beginAutomaticCurtain()
        try { context.queryNowPlaying() } catch(_:RuntimeException) { }

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
        touchHint=hint
        // v4.0：hint 移到顶部居中——底部已被 剧名/媒体行/返回按钮 占满（剧名两行后更挤）；
        // 顶部空旷，锁定/唤醒两态都可读，与 8 秒淡出逻辑兼容
        f.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP
            ).apply { topMargin = (60 * density).toInt() }
        )

        // 黑幕字幕（v4.1 实验性）：无障碍采集的字幕文本镜像到屏幕中央，
        // 大字可读——「闭眼听 + 睁眼读字」的阅读模式。仅唤醒态显示。
        val caption = TextView(context).apply {
            textSize = 20f
            setTextColor(0xE6FFFFFF.toInt())
            gravity = Gravity.CENTER
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(6f * density, 1f)
            visibility = View.INVISIBLE
            alpha = 0f
        }
        f.addView(
            caption,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { marginStart = (48 * density).toInt(); marginEnd = (48 * density).toInt() }
        )
        captionText = caption

        fun dp(value:Int)=(value*density+.5f).toInt()
        fun surface(color:Int,radius:Int)=android.graphics.drawable.GradientDrawable().apply {
            setColor(color);cornerRadius=radius*density
        }
        val pill = TextView(context).apply {
            id=R.id.black_return_button
            text=context.getString(if(demo)R.string.black_demo_return else R.string.black_return)
            textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            minHeight=dp(48);setPadding(dp(12),dp(10),dp(12),dp(10))
            background=surface(0xff252b28.toInt(),14)
            visibility=View.INVISIBLE;alpha=0f
            setOnClickListener { hide();onDismiss() }
        }
        val showMedia=context.prefs().getBoolean(Prefs.BLACK_MEDIA_CONTROLS,false)
        val media=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER
            visibility=View.GONE;alpha=0f
        }
        fun mediaButton(resId:Int,description:String,action:()->Unit)=android.widget.ImageView(context).apply {
            setImageResource(resId);setColorFilter(Color.WHITE);contentDescription=description
            scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12),dp(12),dp(12),dp(12));minimumHeight=dp(48)
            layoutParams=LinearLayout.LayoutParams(0,dp(48),1f)
            setOnClickListener { action() }
        }
        fun seekButton(label:String,description:String,delta:Long)=TextView(context).apply {
            text=label;textSize=13f;setTextColor(0xffc6d1ca.toInt());gravity=Gravity.CENTER
            contentDescription=description;minHeight=dp(48)
            layoutParams=LinearLayout.LayoutParams(0,dp(48),1f)
            setOnClickListener { nudgeSeek(delta) };visibility=View.GONE
        }
        val previous=mediaButton(R.drawable.ic_media_prev,context.getString(R.string.cd_prev_episode)) { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        previousBtn=previous;media.addView(previous)
        val seekBack=seekButton("−30",context.getString(R.string.cd_seek_back),-30_000)
        seekBackBtn=seekBack;media.addView(seekBack)
        val ppBtn=mediaButton(R.drawable.ic_media_pause,context.getString(R.string.cd_play_pause)) { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }.apply {
            id=R.id.black_play_pause
            layoutParams=LinearLayout.LayoutParams(dp(64),dp(64))
            background=surface(0xff246448.toInt(),32)
            setPadding(dp(18),dp(18),dp(18),dp(18))
        }
        playPauseBtn=ppBtn;media.addView(ppBtn)
        val seekFwd=seekButton("+30",context.getString(R.string.cd_seek_forward),30_000)
        seekFwdBtn=seekFwd;media.addView(seekFwd)
        val next=mediaButton(R.drawable.ic_media_next,context.getString(R.string.cd_next_episode)) { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        nextBtn=next;media.addView(next)

        val source=TextView(context).apply {
            textSize=12f;setTextColor(0xffa7b6ad.toInt());maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END;setLineSpacing(2*density,1f)
        }
        sourceText=source
        val title=TextView(context).apply {
            textSize=18f;setTextColor(0xfff1f5f2.toInt());maxLines=2
            setTypeface(typeface,android.graphics.Typeface.BOLD)
            ellipsize=android.text.TextUtils.TruncateAt.END;setLineSpacing(2*density,1f)
            visibility=View.GONE
        }
        titleText=title
        val information=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        information.addView(source,LinearLayout.LayoutParams(-1,-2))
        information.addView(title,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        if(showMedia)information.addView(media,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16) })

        val timerArea=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            visibility=if(!demo && context.prefs().getBoolean(Prefs.BLACK_TIMER_SHOW,false))View.VISIBLE else View.GONE
        }
        this.timerArea=timerArea
        val timing=TextView(context).apply {
            textSize=13f;setTextColor(0xffa7b6ad.toInt());gravity=Gravity.END or Gravity.CENTER_VERTICAL
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        timerStatus=timing
        val setup=LinearLayout(context).apply {
            tag="flow_timer_setup";orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            minimumHeight=dp(56);setPadding(dp(12),dp(8),dp(8),dp(8))
            background=surface(0xff1d2420.toInt(),12)
            isFocusable=true
        }
        setup.addView(TextView(context).apply {
            text=context.getString(R.string.timer_title);textSize=14f;setTextColor(0xffe1e9e3.toInt())
            maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        },LinearLayout.LayoutParams(0,-2,1f))
        setup.addView(timing,LinearLayout.LayoutParams(0,-2,1f))
        setup.addView(TextView(context).apply {
            text="›";textSize=22f;setTextColor(0xffa7b6ad.toInt());gravity=Gravity.CENTER
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(20),-2))
        timerArea.addView(setup,LinearLayout.LayoutParams(-1,-2))
        val timingActions=LinearLayout(context).apply { gravity=Gravity.CENTER }
        for((label,action) in listOf(R.string.ui_timer_extend to { OverlayService.instance?.extendTimer() },
            R.string.timer_cancel to { OverlayService.instance?.cancelTimer() })) {
            timingActions.addView(TextView(context).apply {
                id=if(label==R.string.ui_timer_extend)R.id.black_timer_extend else R.id.black_timer_cancel
                text=context.getString(label);textSize=13f;setTextColor(0xffb7d8c5.toInt());gravity=Gravity.CENTER
                minHeight=dp(48);setPadding(dp(6),dp(4),dp(6),dp(4));maxLines=2
                setOnClickListener { action();refreshTimer();main.removeCallbacks(relockRunnable);main.postDelayed(relockRunnable,RELLOCK_DELAY_MS) }
            },LinearLayout.LayoutParams(0,-2,1f))
        }
        timerArea.addView(timingActions);timing.tag=timingActions
        val content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        content.addView(information,LinearLayout.LayoutParams(-1,-2))
        content.addView(timerArea,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
        val stack=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(20),dp(16),dp(16))
            background=surface(0xff111613.toInt(),24).apply { setStroke(dp(1),0xff2b3630.toInt()) }
        }
        stack.addView(content,LinearLayout.LayoutParams(-1,-2))
        if(!demo && recordSession && context.prefs().getBoolean(com.lujinyu.xiting.listening.ListeningStore.MARK_SHOW,false)) {
            stack.addView(TextView(context).apply {
                tag="listening_mark";text=context.getString(R.string.listen_mark);textSize=14f
                setTextColor(0xffb7d8c5.toInt());gravity=Gravity.CENTER;minHeight=dp(48)
                setOnClickListener {
                    if(!awake || !isShowing || !isEnabled)return@setOnClickListener
                    isEnabled=false
                    com.lujinyu.xiting.listening.ListeningRecorder.mark(context) {ok->
                        isEnabled=true
                        Toast.makeText(context,if(ok)R.string.listen_marked else R.string.listen_storage_error,Toast.LENGTH_SHORT).show()
                    }
                    main.removeCallbacks(relockRunnable);main.postDelayed(relockRunnable,RELLOCK_DELAY_MS)
                }
            },LinearLayout.LayoutParams(-1,-2))
        }
        stack.addView(pill,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        setup.setOnClickListener { openTimerEditor(stack,pill) }
        var landscape=false
        fun arrange(wide:Boolean) {
            landscape=wide;content.orientation=if(wide)LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            title.maxLines=if(wide)1 else 2
            information.layoutParams=if(wide)LinearLayout.LayoutParams(0,-2,1.15f) else LinearLayout.LayoutParams(-1,-2)
            timerArea.layoutParams=if(wide)LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(18) }
                else LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) }
        }
        arrange(context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        val scroll=object:android.widget.ScrollView(context) {
            override fun onMeasure(widthSpec:Int,heightSpec:Int) {
                val availableWidth=View.MeasureSpec.getSize(widthSpec)
                val availableHeight=View.MeasureSpec.getSize(heightSpec)
                val width=minOf(availableWidth,dp(if(landscape)680 else 420))
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((availableHeight*if(landscape).94f else .78f).toInt(),View.MeasureSpec.AT_MOST))
            }
        }.apply {
            addView(stack);visibility=View.INVISIBLE;isFillViewport=false;isVerticalScrollBarEnabled=false
            clipToPadding=false
            setOnTouchListener { v,event ->
                if(event.actionMasked==MotionEvent.ACTION_UP)v.performClick()
                main.removeCallbacks(relockRunnable);main.postDelayed(relockRunnable,RELLOCK_DELAY_MS);false
            }
        }
        wakePanel=scroll
        f.addView(scroll,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin=dp(16);rightMargin=dp(16);bottomMargin=dp(24)
        })
        f.addOnLayoutChangeListener { _,left,top,right,bottom,_,_,_,_ ->
            val wide=right-left>bottom-top
            if(wide!=landscape) { arrange(wide);scroll.requestLayout() }
        }
        f.setOnApplyWindowInsetsListener { _,insets ->
            val safeBottom=if(Build.VERSION.SDK_INT>=30)insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).bottom else @Suppress("DEPRECATION") insets.stableInsetBottom
            val params=scroll.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin=safeBottom+dp(16)
            @Suppress("DEPRECATION")
            val sideInsets=if(Build.VERSION.SDK_INT>=30) {
                val edges=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                edges.left to edges.right
            } else insets.stableInsetLeft to insets.stableInsetRight
            params.leftMargin=dp(16)+sideInsets.first;params.rightMargin=dp(16)+sideInsets.second
            scroll.layoutParams=params
            insets
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
                    // 用格式串而不是字符串模板：lint SetTextI18n，也与其它文案一致
                    badge.text = context.getString(R.string.notif_badge_fmt, n)
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
                if(timerEditor!=null)return true
                if (directUnlock) {
                    val onReturn=onDismissCallback
                    hide()
                    onReturn?.invoke()
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
            if(demo)pill.text=context.getString(R.string.guide_exit)
            unlockPill = pill
            mediaRow = media
            isShowing = true
            if(recordSession && !demo)com.lujinyu.xiting.listening.ListeningRecorder.start(context)
            sessionStart = if(recordSession)System.currentTimeMillis() else 0L
            sessionStartElapsed = SystemClock.elapsedRealtime()
            // 省电实测：记下会话开始时的能量计数器读数，作为差值法的起点
            sessionStartUwh = if(recordSession)PowerCalib.energyCounter(context) else null
            Log.i(TAG, "black overlay added, type=$windowType, backlight override OFF")
            hideSystemBars(f)
            f.post {
                hint.animate().alpha(0f).setStartDelay(8000).setDuration(1000).start()
            }
        } catch (e: Exception) {
            PlaybackSelection.endAutomaticCurtain()
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
        if(recordSession && !demo && isShowing)com.lujinyu.xiting.listening.ListeningRecorder.stop(context)
        PlaybackSelection.endAutomaticCurtain()
        main.removeCallbacks(relockRunnable)
        awake = false
        if (sessionStart > 0) {
            val now = System.currentTimeMillis()
            // 时长用单调时钟：就算系统时间被改/跨天，记录的也是真实经过的时间。
            // 只有设备中途重启（elapsed回绕为负）才退回墙钟差。
            val elapsed = SystemClock.elapsedRealtime() - sessionStartElapsed
            val dur = if (elapsed >= 0) elapsed else now - sessionStart
            SessionLog.add(context, ListenSession(sessionStart, now, dur, SessionLog.MODE_BLACK))
            // 省电实测：用能量计数器的**差值**记录本次黑屏段真实能耗。
            // 零打扰 —— 用户什么都没做，一次会话就是一个精确样本。
            val startUwh = sessionStartUwh
            if (startUwh != null && !PowerCalib.isCharging(context)) {
                PowerCalib.energyCounter(context)?.let { endUwh ->
                    PowerCalib.recordSegment(context, startUwh, endUwh, dur, black = true)
                }
            }
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
            sessionStartUwh = null
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
        main.removeCallbacks(nowPlayingTick)
        main.removeCallbacks(seekReset)
        captionListener?.let { CaptionBus.unregister(it) }
        captionListener = null
        captionText = null
        infoCol = null
        frame?.let { f -> try { wm.removeView(f) } catch (_: Exception) {} }
        frame = null
        lp = null
        unlockPill = null
        mediaRow = null
        playPauseBtn = null
        previousBtn=null;nextBtn=null;lastSeekSession=null
        titleText = null;sourceText=null;touchHint=null
        timerEditor?.dispose();timerEditor=null;closeTimerEditor=null
        wakePanel=null;timerStatus=null;timerArea=null
        seekBackBtn = null
        seekFwdBtn = null
        lastSeekTarget = -1
        isShowing = false
    }

    /** 解除锁定：背光恢复到用户亮度，浮现返回按钮 */
    private fun wake() {
        if (awake) return
        awake = true
        titleText?.animate()?.withEndAction(null)?.cancel()
        mediaRow?.animate()?.withEndAction(null)?.cancel()
        unlockPill?.animate()?.withEndAction(null)?.cancel()
        touchHint?.text=context.getString(R.string.black_wake_hint)
        val f = frame ?: return
        val l = lp ?: return
        l.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        try { wm.updateViewLayout(f, l) } catch (_: Exception) {}
        wakePanel?.visibility=View.VISIBLE
        refreshTimer()
        unlockPill?.visibility = View.VISIBLE
        unlockPill?.animate()?.alpha(1f)?.setDuration(200)?.start()
        mediaRow?.visibility = View.VISIBLE
        mediaRow?.animate()?.alpha(1f)?.setDuration(200)?.start()
        refreshNowPlaying()
        main.removeCallbacks(nowPlayingTick)
        main.postDelayed(nowPlayingTick, 2_000)
        // 字幕总线：唤醒期间镜像无障碍采集的文本；字幕到达即显示
        captionListener = { text -> main.post { showCaption(text) } }
        CaptionBus.register(captionListener!!)
        main.postDelayed(relockRunnable, RELLOCK_DELAY_MS)
        Log.i(TAG, "awake: 解除锁定，背光恢复")
        OverlayService.instance?.onCurtainStateChanged()
    }

    /** 重新锁定：开时钟→背光压到最暗（暗态时钟持续可见）；未开→彻底关闭纯黑 */
    private fun sleep() {
        if(timerEditor!=null)return
        if (!awake) return
        awake = false
        touchHint?.text=context.getString(R.string.black_hint)
        main.removeCallbacks(relockRunnable)
        wakePanel?.visibility=View.INVISIBLE
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
        titleText?.animate()?.alpha(0f)?.setDuration(200)
            ?.withEndAction { titleText?.visibility = View.INVISIBLE }?.start()
        hideCaption()
        captionListener?.let { CaptionBus.unregister(it) }
        captionListener = null
        main.removeCallbacks(nowPlayingTick)
        Log.i(TAG, "sleep: 重新锁定")
        OverlayService.instance?.onCurtainStateChanged()
    }

    internal fun refreshTimerDisplay() {
        if(!context.prefs().getBoolean(Prefs.BLACK_TIMER_SHOW,false))closeTimerEditor?.invoke()
        refreshTimer()
    }
    private fun refreshTimer() {
        timerArea?.visibility=if(!demo && context.prefs().getBoolean(Prefs.BLACK_TIMER_SHOW,false))View.VISIBLE else View.GONE
        val service=OverlayService.instance
        val remain=service?.timerRemainingMs() ?: 0
        timerStatus?.text=if(remain<=0 && SessionRuntime.snapshot().pause!=PauseReport.NONE)SessionConsoleUi.pause(context,SessionRuntime.snapshot().pause)
            else if(service?.timerWaitsForPlayback()==true)context.getString(R.string.timer_episode_waiting)
            else if(remain>0) {
                val seconds=(remain+999)/1000
                val time=String.format(java.util.Locale.getDefault(),"%d:%02d",seconds/60,seconds%60)
                context.getString(if(service?.timerKeepsBlack()==true)R.string.black_timer_pause_fmt else R.string.black_timer_return_fmt,time)
            } else context.getString(R.string.black_timer_off)
        frame?.findViewWithTag<View>("flow_timer_setup")?.contentDescription=
            context.getString(R.string.timer_title)+", "+timerStatus?.text?.toString().orEmpty()
        (timerStatus?.tag as? View)?.visibility=if(remain>0)View.VISIBLE else View.GONE
    }
    private fun openTimerEditor(stack:LinearLayout,pill:View) {
        if(timerEditor!=null || !awake || demo || !context.prefs().getBoolean(Prefs.BLACK_TIMER_SHOW,false))return
        main.removeCallbacks(relockRunnable);main.removeCallbacks(nowPlayingTick)
        val visibility=(0 until stack.childCount).associate { stack.getChildAt(it) to stack.getChildAt(it).visibility }
        fun close() {
            val editor=timerEditor ?: return
            editor.dispose();stack.removeView(editor);timerEditor=null;closeTimerEditor=null
            visibility.forEach { (view,state)->view.visibility=state }
            refreshTimer();refreshNowPlaying()
            main.removeCallbacks(nowPlayingTick);main.postDelayed(nowPlayingTick,2_000)
            main.removeCallbacks(relockRunnable);main.postDelayed(relockRunnable,RELLOCK_DELAY_MS)
        }
        visibility.keys.filter { it!==pill }.forEach { it.visibility=View.GONE }
        if(demo)return
        timerEditor=CurtainTimerPanel(context,{ close() },{ close();refreshTimer() })
        closeTimerEditor={close()}
        stack.addView(timerEditor,stack.indexOfChild(pill),LinearLayout.LayoutParams(-1,-2))
    }

    /** 黑幕内媒体键：注入后重置重锁计时（连续操作不被打断） */
    private fun sendMediaKey(code: Int) {
        try {
            val np=context.queryNowPlaying()
            if(np!=null && np.controlsReliable) {
                val controls=np.controller.transportControls
                val actions=np.controller.playbackState?.actions ?: 0L
                fun supports(action:Long)=actions and action!=0L
                when(code) {
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if(supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS))controls.skipToPrevious()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> if(supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT))controls.skipToNext()
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if(np.playing) {
                        if(supports(android.media.session.PlaybackState.ACTION_PAUSE) || supports(android.media.session.PlaybackState.ACTION_PLAY_PAUSE))controls.pause()
                    } else if(supports(android.media.session.PlaybackState.ACTION_PLAY) || supports(android.media.session.PlaybackState.ACTION_PLAY_PAUSE))controls.play()
                }
            }
            Log.i(TAG, "black overlay media key: $code")
        } catch (e: Exception) {
            Log.w(TAG, "media key failed: $e")
        }
        if (awake) {
            main.removeCallbacks(relockRunnable)
            main.postDelayed(relockRunnable, RELLOCK_DELAY_MS)
        }
        // 媒体键生效后刷新播放/暂停图标（音频状态更新有延迟）
        main.postDelayed({ refreshNowPlaying() }, 400)
    }

    private var captionListener: ((String) -> Unit)? = null

    /** 字幕到达：唤醒态显示（空文本 = 字幕间隙，隐藏）；锁定态绝不显示 */
    private fun showCaption(text: String) {
        val c = captionText ?: return
        if (!awake) return
        if (text.isBlank()) {
            hideCaption()
            return
        }
        c.text = text
        if (c.visibility != View.VISIBLE) {
            c.visibility = View.VISIBLE
            c.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun hideCaption() {
        val c = captionText ?: return
        c.animate().alpha(0f).setDuration(150)
            .withEndAction { c.visibility = View.INVISIBLE }?.start()
    }

    /**
     * 播放键图标 + 剧名 + ±30s 可见性：三个刷新点收拢在这一个入口
     * （图鉴大图不跟随选择的教训：刷新散落在各回调里必漏一处）。
     * 来源识别与可控能力分开：状态不可信时显示来源，禁用媒体控制。
     */
    private fun refreshNowPlaying() {
        refreshTimer()
        if(demo) {
            sourceText?.visibility=View.GONE
            mediaRow?.visibility=View.GONE
            timerStatus?.visibility=View.GONE
            frame?.findViewWithTag<View>("flow_timer_setup")?.visibility=View.GONE
            frame?.findViewById<View>(R.id.black_timer_extend)?.visibility=View.GONE
            frame?.findViewById<View>(R.id.black_timer_cancel)?.visibility=View.GONE
            titleText?.apply{animate().cancel();alpha=1f;text=context.getString(R.string.guide_black_hint);visibility=if(awake)View.VISIBLE else View.INVISIBLE}
            return
        }
        val np = context.queryNowPlaying()
        val targetUnavailable=np==null || !np.controlsReliable
        val btn = playPauseBtn
        val actions=np?.controller?.playbackState?.actions ?: 0L
        fun supports(action:Long)=actions and action!=0L
        previousBtn?.visibility=if(np==null || supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS))View.VISIBLE else View.GONE
        nextBtn?.visibility=if(np==null || supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT))View.VISIBLE else View.GONE
        for(control in listOf(previousBtn,nextBtn))control?.apply {
            isEnabled=!targetUnavailable;alpha=if(isEnabled)1f else .35f
        }
        if (btn != null) {
            val playing = np?.playing ?: try { am.isMusicActive } catch (_: Exception) { false }
            val capability=if(playing)android.media.session.PlaybackState.ACTION_PAUSE else android.media.session.PlaybackState.ACTION_PLAY
            btn.isEnabled=!targetUnavailable && (np==null || supports(capability) || supports(android.media.session.PlaybackState.ACTION_PLAY_PAUSE))
            btn.visibility=if(btn.isEnabled)View.VISIBLE else View.GONE
            btn.alpha=if(btn.isEnabled)1f else .35f
            btn.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        }
        val title=np?.title?.takeIf { np.controlsReliable && it!=np.appLabel }
        sourceText?.apply {
            text=when {
                np==null -> context.getString(R.string.black_control_unavailable)
                !np.controlsReliable -> context.getString(R.string.playback_source_control_in_app,np.appLabel.orEmpty())
                else -> np.appLabel.orEmpty()
            }
            visibility=if(np!=null && np.controlsReliable && title==null)View.GONE else View.VISIBLE
        }
        titleText?.apply {
            text=title ?: np?.appLabel.orEmpty()
            visibility=if(awake && np?.controlsReliable==true)View.VISIBLE else View.GONE
            alpha=1f
        }
        val on = np?.canSeek == true
        seekBackBtn?.visibility = if (on) View.VISIBLE else View.GONE
        seekFwdBtn?.visibility = if (on) View.VISIBLE else View.GONE
        mediaRow?.visibility=if(!targetUnavailable && listOf(previousBtn,nextBtn,playPauseBtn,seekBackBtn,seekFwdBtn)
            .any { it?.visibility==View.VISIBLE && it.isEnabled })View.VISIBLE else View.GONE
    }

    /**
     * ±30s。800ms 窗口内连点在「已应用的目标」上累加，而不是每次读实时位置——
     * playbackState.position 异步更新，读实时位置会让第 2、3 次点按算重复步。
     * 窗口过后冷启动，重新从真实位置起算。一次点按=用户醒着：
     * 重置重锁计时（与媒体键一致），并按媒体键同款 400ms 节奏刷状态。
     */
    private fun nudgeSeek(deltaMs: Long) {
        val np = context.queryNowPlaying()
        Log.i(TAG, "nudgeSeek delta=$deltaMs np=${np != null} pos=${np?.positionMs} canSeek=${np?.canSeek}")
        if (np == null) return
        if(!np.canSeek) return
        if(lastSeekSession!=np.controller.sessionToken)lastSeekTarget=-1L
        lastSeekSession=np.controller.sessionToken
        val base = if (lastSeekTarget < 0) np.positionMs else lastSeekTarget
        val target = PlaybackMath.offset(base,deltaMs,np.durationMs)
        try {
            np.seekTo(target)
            Log.i(TAG, "nudgeSeek -> seekTo($target) ok")
        } catch (e: Exception) {
            Log.w(TAG, "nudgeSeek seekTo($target) fail: $e")
            return
        }
        lastSeekTarget = target
        main.removeCallbacks(seekReset)
        main.postDelayed(seekReset, 800)
        main.removeCallbacks(relockRunnable)
        main.postDelayed(relockRunnable, RELLOCK_DELAY_MS)
        main.postDelayed({ refreshNowPlaying() }, 400)
    }

    private fun unlockPillBg(context: Context) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = 28f * context.resources.displayMetrics.density
        setColor(0xE61E8E5A.toInt())
    }
}
