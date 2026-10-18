package com.lujinyu.xiting

import android.app.Instrumentation
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Actual overlay interaction and clipping checks; fixtures never run on a user's phone. */
internal class BlackLayoutDeviceChecks(private val i:Instrumentation) {
    private var localized:android.content.Context?=null
    private val ctx get()=localized ?: i.targetContext
    private fun main(block:()->Unit) {var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun field(overlay:BlackOverlay,name:String)=BlackOverlay::class.java.getDeclaredField(name).apply{isAccessible=true}.get(overlay)
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
                java.io.File(ctx.cacheDir,"black-layout/$language.json").writeText("{\"unsupportedControlsHidden\":true,\"timerEditorAccessible\":true,\"longTitleControlsFit\":true,\"returnReachable\":true}")
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
}
