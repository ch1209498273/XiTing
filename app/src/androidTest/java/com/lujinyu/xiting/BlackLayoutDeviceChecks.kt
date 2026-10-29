package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Actual overlay interaction and clipping checks; fixtures never run on a user's phone. */
internal class BlackLayoutDeviceChecks(private val i:Instrumentation) {
    private var localized:android.content.Context?=null
    private val ctx get()=localized ?: i.targetContext
    private fun main(block:()->Unit) {var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun field(overlay:BlackOverlay,name:String)=BlackOverlay::class.java.getDeclaredField(name).apply{isAccessible=true}.get(overlay)
    /** 断言只能证明「可见」，证明不了没被遮挡、字够不够大；缺能力与补能力各留一张实际画面 */
    private fun capture(root:View,name:String) {
        val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(ctx.cacheDir,"black-layout/$name.png").apply{parentFile!!.mkdirs()}
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    fun runActiveTimer() {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val baseline=BackupManager.buildBackupJson(i.targetContext)
        var overlay:BlackOverlay?=null
        try {
            for(language in listOf("zh","en","de","fr","es","ja","ko","ru")) {
                val config=android.content.res.Configuration(i.targetContext.resources.configuration).apply {setLocale(java.util.Locale.forLanguageTag(language))}
                localized=android.view.ContextThemeWrapper(i.targetContext.createConfigurationContext(config),R.style.Theme_XiTing)
                val done=java.util.concurrent.CountDownLatch(1);var result:TimerAcceptance?=null
                main {TimerRequests.submit(ctx,TimerRequest(3,pauseKeepBlack=false)){result=it;done.countDown()}}
                check(done.await(6,java.util.concurrent.TimeUnit.SECONDS) && result?.accepted==true) {"Timer was not accepted"}
                main {
                    overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false).also{it.show{}}
                    BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
                }
                SystemClock.sleep(200);i.waitForIdleSync()
                main {
                    val root=field(overlay!!,"frame") as View
                    val status=field(overlay!!,"timerStatus") as TextView
                    check(status.text.contains(":")) {"$language: active countdown missing"}
                    val extend=root.findViewById<View>(R.id.black_timer_extend)
                    val cancel=root.findViewById<View>(R.id.black_timer_cancel)
                    for(control in listOf(extend,cancel)) {
                        control.requestRectangleOnScreen(android.graphics.Rect(0,0,control.width,control.height),true)
                        val rect=android.graphics.Rect()
                        check(control.getGlobalVisibleRect(rect) && rect.height()==control.height && control.isEnabled) {"$language: timer action unreachable"}
                    }
                    val before=OverlayService.instance!!.timerRemainingMs()
                    extend.performClick()
                    check(OverlayService.instance!!.timerRemainingMs()>before+890000) {"Timer extension failed"}
                    cancel.performClick()
                    check(OverlayService.instance!!.timerRemainingMs()==0L && !extend.isShown && !cancel.isShown) {"Timer cancellation failed"}
                    overlay!!.hide();overlay=null
                }
            }
        } finally {
            main {overlay?.hide();ctx.startService(android.content.Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))}
            val until=SystemClock.elapsedRealtime()+5000
            while(OverlayService.isRunning && SystemClock.elapsedRealtime()<until)SystemClock.sleep(30)
            check(!OverlayService.isRunning);check(BackupManager.restore(i.targetContext,baseline))
        }
    }
    fun run() {
        for(language in listOf("zh","en","de","fr","es","ja","ko","ru")) {
            val config=android.content.res.Configuration(i.targetContext.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(language)) }
            localized=android.view.ContextThemeWrapper(i.targetContext.createConfigurationContext(config),R.style.Theme_XiTing)
            runVariant(language)
        }
    }
    private fun runVariant(language:String) {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val baseline=BackupManager.buildBackupJson(ctx)
        var overlay:BlackOverlay?=null;var session:MediaSession?=null
        try {
            main {
                PlaybackSelection.clear();ctx.prefs().edit().putBoolean(Prefs.BLACK_MEDIA_CONTROLS,true).commit()
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
                overlay!!.show{}
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
            }
            i.waitForIdleSync()
            main {
                val row=field(overlay!!,"mediaRow") as LinearLayout
                check(!row.isShown) {"Unusable media controls occupy the black panel"}
                val root=field(overlay!!,"frame") as View
                val back=root.findViewById<TextView>(R.id.black_return_button)
                check(back.isShown && back.isEnabled && !back.text.contains("🔒"))
                root.findViewWithTag<View>("flow_timer_setup").performClick()
            }
            i.waitForIdleSync()
            main {
                val root=field(overlay!!,"frame") as View
                check(root.findViewWithTag<View>("flow_timer_editor").isShown)
                check(root.findViewById<View>(R.id.black_return_button).isShown)
                val close=root.findViewWithTag<View>("flow_timer_close")
                close.requestRectangleOnScreen(android.graphics.Rect(0,0,close.width,close.height),true)
                val closeRect=android.graphics.Rect()
                check(close.getGlobalVisibleRect(closeRect) && closeRect.height()==close.height) {"$language: timer close is clipped"}
                close.performClick()
                check(root.findViewWithTag<View>("flow_timer_editor")==null)
                overlay!!.hide();overlay=null
                session=MediaSession(ctx,"layout-long-title").apply {
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"很长的视频标题用于检查文字换行和控制面板布局 ".repeat(10))
                        .putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build())
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .setState(PlaybackState.STATE_PLAYING,10000,1f).build());isActive=true
                }
            }
            SystemClock.sleep(250)
            main {
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false).also{it.show{}}
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
            }
            SystemClock.sleep(150)
            i.waitForIdleSync()
            main {
                val root=field(overlay!!,"frame") as View
                val media=field(overlay!!,"mediaRow") as LinearLayout
                check(media.isShown)
                for(index in 0 until media.childCount) {
                    val child=media.getChildAt(index)
                    if(child.visibility==View.VISIBLE) {
                        check(child.left>=0 && child.right<=media.width) {"$language: playback control clipped horizontally"}
                        child.requestRectangleOnScreen(android.graphics.Rect(0,0,child.width,child.height),true)
                        val rect=android.graphics.Rect()
                        check(child.getGlobalVisibleRect(rect) && rect.height()==child.height && rect.width()==child.width) {"$language: playback control unreachable"}
                    }
                }
                val back=root.findViewById<View>(R.id.black_return_button)
                val panel=field(overlay!!,"wakePanel") as android.widget.ScrollView
                if(ctx.resources.configuration.fontScale<=1f)check(panel.getChildAt(0).height<=panel.height) {"$language: normal-size panel unnecessarily scrolls"}
                check(back.isShown && back.height>=48*ctx.resources.displayMetrics.density)
                back.requestRectangleOnScreen(android.graphics.Rect(0,0,back.width,back.height),true)
                val backRect=android.graphics.Rect()
                check(back.getGlobalVisibleRect(backRect) && backRect.height()==back.height) {"$language: return button unreachable"}
                val backPosition=IntArray(2);val mediaPosition=IntArray(2)
                back.getLocationOnScreen(backPosition);media.getLocationOnScreen(mediaPosition)
                check(backPosition[1]>=mediaPosition[1]+media.height) {"$language: controls overlap return button"}
                val bitmap=android.graphics.Bitmap.createBitmap(root.width,root.height,android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                java.io.File(ctx.cacheDir,"black-layout/$language.png").apply{parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                java.io.File(ctx.cacheDir,"black-layout/$language.json").writeText("{\"unsupportedControlsHidden\":true,\"skipCapabilityHinted\":true,\"timerEditorAccessible\":true,\"longTitleControlsFit\":true,\"returnReachable\":true}")
                BlackOverlay::class.java.getDeclaredMethod("sleep").apply{isAccessible=true}.invoke(overlay)
            }
            SystemClock.sleep(250)
            main { BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay) }
            SystemClock.sleep(250)
            main {
                check((field(overlay!!,"playPauseBtn") as View).isShown && (field(overlay!!,"unlockPill") as View).isShown) {"$language: controls disappear after relock"}
                session!!.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SKIP_TO_NEXT)
                    .setState(PlaybackState.STATE_PLAYING,10000,1f).build())
            }
            SystemClock.sleep(150)
            main {
                BlackOverlay::class.java.getDeclaredMethod("refreshNowPlaying").apply{isAccessible=true}.invoke(overlay)
                check(!(field(overlay!!,"playPauseBtn") as View).isShown) {"$language: unsupported pause key still occupies the panel"}
                // 未声明切集：B 档下键不再消失，而是亮着等一次注入验证（抖音就是这一类）
                session!!.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
                    or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO)
                    .setState(PlaybackState.STATE_PLAYING,10000,1f).build())
            }
            SystemClock.sleep(150)
            main {
                BlackOverlay::class.java.getDeclaredMethod("refreshNowPlaying").apply{isAccessible=true}.invoke(overlay)
            }
            // 同一帧里刚设的显隐还没测量，宽与可见矩形都是 0：先让布局走完再量
            SystemClock.sleep(200);i.waitForIdleSync()
            main {
                val prev=field(overlay!!,"previousBtn") as View
                val next=field(overlay!!,"nextBtn") as View
                check((field(overlay!!,"mediaRow") as View).isShown && prev.isShown && prev.isEnabled && next.isShown && next.isEnabled)
                    {"$language: skip keys vanished again for an undeclared player"}
                val hint=field(overlay!!,"skipHintText") as View
                check(!hint.isShown) {"$language: explanation shown before any probe failed"}
                capture(field(overlay!!,"frame") as View,"$language-always-shown")
                // 只差一个要素：同一会话补声明切集，两个键回来、提示收起
                session!!.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
                    or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO
                    or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT)
                    .setState(PlaybackState.STATE_PLAYING,10000,1f).build())
            }
            SystemClock.sleep(150)
            main {
                BlackOverlay::class.java.getDeclaredMethod("refreshNowPlaying").apply{isAccessible=true}.invoke(overlay)
            }
            // 刚改完显隐就 draw() 会拍到子 View 的上一帧位置（切集键画到播放键上）：等布局走完
            SystemClock.sleep(200);i.waitForIdleSync()
            main {
                check((field(overlay!!,"previousBtn") as View).isShown && (field(overlay!!,"nextBtn") as View).isShown)
                    {"$language: declared capability still hides the skip keys"}
                check(!(field(overlay!!,"skipHintText") as View).isShown) {"$language: explanation outlives the capability"}
                val prev=field(overlay!!,"previousBtn") as View
                val next=field(overlay!!,"nextBtn") as View
                val pp=field(overlay!!,"playPauseBtn") as View
                for(control in listOf(prev,next,pp)) {
                    val rect=android.graphics.Rect()
                    check(control.getGlobalVisibleRect(rect) && rect.width()>0) {"$language: skip key has no room after relayout"}
                }
                // 拍到过切集键压在播放键上（布局未走完就 draw 留的旧位置）：按坐标钉死
                val prevAt=IntArray(2);val nextAt=IntArray(2);val ppAt=IntArray(2)
                prev.getLocationOnScreen(prevAt);next.getLocationOnScreen(nextAt);pp.getLocationOnScreen(ppAt)
                check(prevAt[0]+prev.width<=ppAt[0] && nextAt[0]>=ppAt[0]+pp.width) {"$language: skip keys overlap the play key"}
                capture(field(overlay!!,"frame") as View,"$language-skip")
            }
            // 探针三档与复活：行为与语言无关，只在中文跑一遍，别把 8 语言轮次拖成 8 倍时长。
            // 必须在 main{} 外调用——probeSkip 自己会切主线程，套两层直接抛异常
            if(language=="zh") probeSkip(overlay!!,session!!)
            main {
                overlay!!.hide();overlay=null;session!!.release();session=null
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false,demo=true).also{it.show{}}
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
                BlackOverlay::class.java.getDeclaredMethod("sleep").apply{isAccessible=true}.invoke(overlay)
            }
            SystemClock.sleep(250)
            main {
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(overlay)
                val title=field(overlay!!,"titleText") as TextView
                check(title.isShown && title.alpha>.99f) {"$language: guide hint disappears after relock and wake"}
            }
        } finally {
            main {overlay?.hide();session?.release();PlaybackSelection.clear()}
            check(BackupManager.restore(ctx,baseline))
        }
    }

    /**
     * 切集探针三档（B 档的成立条件，必须实测而不是讲道理）：
     * ② 会话没声明 `ACTION_SKIP_TO_*`，但在 `onMediaButtonEvent` 里响应并换内容
     *    （抖音这类）→ 注入算成功，键必须保持可用；
     * ③ 既不声明也不换内容 → 判死：转灰、不可点；只死一个方向不出说明，两向都死才出；
     * ④ 换一条内容 → 判死作废，键回来（上一条不响不代表这一条不响）。
     */
    private fun probeSkip(overlay:BlackOverlay,session:MediaSession) {
        val prev=field(overlay,"previousBtn") as View
        val next=field(overlay,"nextBtn") as View
        val hint=field(overlay,"skipHintText") as View
        val handled=java.util.concurrent.atomic.AtomicInteger(0)
        // 探针只在「该方向未声明」时才有意义：先把能力位收回到只声明播放与拖动，否则走的是 transport
        main { session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
            or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO)
            .setState(PlaybackState.STATE_PLAYING,10000,1f).build()) }
        SystemClock.sleep(200)
        main { BlackOverlay::class.java.getDeclaredMethod("refreshNowPlaying").apply{isAccessible=true}.invoke(overlay) }
        SystemClock.sleep(200);i.waitForIdleSync()
        // setCallback 会建 CallbackMessageHandler(Looper.myLooper())，只能在有 Looper 的主线程上调
        main { session.setCallback(object:MediaSession.Callback() {
            override fun onMediaButtonEvent(intent:Intent):Boolean {
                @Suppress("DEPRECATION")
                val code=intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)?.keyCode ?: return false
                if(code!=KeyEvent.KEYCODE_MEDIA_PREVIOUS && code!=KeyEvent.KEYCODE_MEDIA_NEXT) return false
                val n=handled.incrementAndGet()
                session.setMetadata(MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"探针·已响应$n")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build())
                return true
            }
        }) }
        main { prev.performClick() }
        SystemClock.sleep(1400)   // 探针窗口 800ms + 元数据回传余量
        main {
            check(handled.get()>0) {"注入的媒体键没有送达会话，夹具先失效"}
            check(prev.isEnabled && next.isEnabled) {"注入生效的方向被误判死"}
            check(!hint.isShown) {"探针成功却弹出说明"}
        }
        // ③ 收了键但不换内容
        main { session.setCallback(object:MediaSession.Callback() {}) }
        main { prev.performClick() }
        SystemClock.sleep(1400)
        main {
            check(!prev.isEnabled && prev.alpha<0.5f) {"按了没响的方向没有判死转灰"}
            check(next.isEnabled) {"只试了一个方向就把两个键一起判死"}
            check(!hint.isShown) {"只死一个方向就弹说明"}
            next.performClick()
        }
        SystemClock.sleep(1400)
        main {
            check(!next.isEnabled) {"第二个方向没判死"}
            check(hint.isShown) {"两向都死后仍不说明"}
            capture(field(overlay,"frame") as View,"skip-dead")
            session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"探针·换了一条")
                .putLong(MediaMetadata.METADATA_KEY_DURATION,300000).build())
        }
        SystemClock.sleep(300)
        main {
            BlackOverlay::class.java.getDeclaredMethod("refreshNowPlaying").apply{isAccessible=true}.invoke(overlay)
        }
        SystemClock.sleep(250);i.waitForIdleSync()
        main {
            check(prev.isEnabled && next.isEnabled) {"换内容后判死没复活"}
            check(!hint.isShown) {"复活后说明行没收起"}
            capture(field(overlay,"frame") as View,"skip-revived")
        }
    }
}
