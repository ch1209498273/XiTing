package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/** Emulator fixtures only. No external player or user records are modified. */
internal class ListeningFlowDeviceChecks(private val i: Instrumentation) {
    private val ctx get() = i.targetContext
    private fun main(block: () -> Unit) {
        var failure: Throwable? = null
        i.runOnMainSync { try { block() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
    }
    private fun await(message: String, test: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (!test() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(40)
        check(test()) { message }
    }
    private fun tap(root:android.view.View) {
        val time=SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(time,time+20,action,100f,100f,0)
            root.dispatchTouchEvent(event);event.recycle()
        }
    }
    /** 防误触后只有双击能解锁：两次点击相隔 60ms，落在系统双击窗口内。 */
    private fun doubleTap(root:android.view.View) {
        val start=SystemClock.uptimeMillis()
        for(offset in listOf(0L,100L)) {
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(start+offset,start+offset+40,action,100f,100f,0)
                root.dispatchTouchEvent(event);event.recycle()
            }
        }
    }
    private fun withTimerVisible(action:()->Unit) {
        val p=ctx.prefs();val existed=p.contains(Prefs.BLACK_TIMER_SHOW);val previous=p.getBoolean(Prefs.BLACK_TIMER_SHOW,false)
        p.edit().putBoolean(Prefs.BLACK_TIMER_SHOW,true).commit()
        try {action()} finally {p.edit().apply {if(existed)putBoolean(Prefs.BLACK_TIMER_SHOW,previous) else remove(Prefs.BLACK_TIMER_SHOW)}.commit()}
    }
    private fun withMistouchGuard(action:()->Unit) {
        val p=ctx.prefs();val existed=p.contains(Prefs.MISTOUCH_GUARD);val previous=p.getBoolean(Prefs.MISTOUCH_GUARD,false)
        p.edit().putBoolean(Prefs.MISTOUCH_GUARD,true).commit()
        try {action()} finally {p.edit().apply {if(existed)putBoolean(Prefs.MISTOUCH_GUARD,previous) else remove(Prefs.MISTOUCH_GUARD)}.commit()}
    }
    fun run(part:String?=null) {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish")) { "Listening flow fixtures are emulator-only" }
        if(part?.startsWith("library_")==true) { ListeningLibraryDeviceChecks(i).run(part);return }
        if(part=="content_return_probe") { ContentReturnProbeChecks.run();return }
        if(part=="content_return_metadata") { ContentMetadataProbe.run(i);return }
        if(part=="routed_inactive") { RoutedInactiveDeviceChecks(i).run();return }
        if(part=="black_timer_visibility") { BlackTimerVisibilityChecks(i).run();return }
        if(part=="system_playback") { SystemPlaybackDeviceChecks(i).run();return }
        if(part=="black_layout") { withTimerVisible { BlackLayoutDeviceChecks(i).run() };return }
        if(part=="black_timer_layout") { withTimerVisible { BlackLayoutDeviceChecks(i).runActiveTimer() };return }
        if(part=="playback_boundaries") { PlaybackBoundaryDeviceChecks(i).run();return }
        if(part=="lost_controls") { withMistouchGuard { selectedPlayerLossMustNotSwitch() };return }
        if(part=="guide_audio") { GuideAudioDeviceChecks(i).run();return }
        if(part=="app_choices") { PlaybackAppDeviceChecks(i).run();return }
        if(part=="session_text") { SessionUiDeviceChecks(i).run();return }
        ctx.prefs().edit().putString(Prefs.UPDATE_SEEN_VERSION,BuildConfig.VERSION_NAME).commit()
        val activity = i.startActivitySync(Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            i.waitForIdleSync()
            when(part) {
                "lost_controls" -> withMistouchGuard { selectedPlayerLossMustNotSwitch() }
                "onboard" -> firstSuccessIsRemembered(activity)
                "panels" -> withMistouchGuard { withTimerVisible { ListeningPanelChecks(i).run() } }
                "contracts" -> TimerContractDeviceChecks(i).run(activity)
                "expiry" -> withMistouchGuard { withTimerVisible { actualOneMinuteFlow(activity) } }
                "episode" -> withMistouchGuard { withTimerVisible { curtainEntryIsAvailable() } }
                "guard_default" -> guardDefaultsToLegacy()
                else -> withMistouchGuard {
                    selectedPlayerLossMustNotSwitch();inactivePlayerIsRetained();rejectedRequestHasReceipt()
                    withTimerVisible { curtainEntryIsAvailable() };firstSuccessIsRemembered(activity)
                    withTimerVisible { actualOneMinuteFlow(activity) }
                }
            }
        }
        finally { main { activity.finish() } }
    }
    private fun selectedPlayerLossMustNotSwitch() {
        val previous = PlaybackSelection.token
        lateinit var selected: MediaSession
        lateinit var other: MediaSession
        main {
            fun fixture(name: String) = MediaSession(ctx, name).apply {
                setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 100, 1f).build())
                isActive = true
            }
            selected = fixture("flow-selected")
            other = fixture("flow-other")
            PlaybackSelection.token = selected.sessionToken
        }
        try {
            await("Synthetic sessions were not exposed") { ctx.playbackSources().any { it.sessionToken == other.sessionToken } }
            main { selected.release() }
            await("Released target still active") { ctx.playbackSources().none { it.sessionToken == selected.sessionToken } }
            lateinit var overlay:BlackOverlay
            main {
                check(ctx.queryNowPlaying() == null) { "Explicit player loss silently switched to a different session" }
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
                overlay.show { }
            }
            try {
                SystemClock.sleep(350)
                val root=BlackOverlay::class.java.getDeclaredField("frame").apply { isAccessible=true }.get(overlay) as android.view.View
                main {
                    check(root.width>0 && root.height>0) { "Curtain fixture was not laid out" }
                    tap(root)
                }
                // 单击要等双击窗口关闭才判定，确认它只给提示、不恢复背光。
                SystemClock.sleep(400)
                main { check(!overlay.isAwake) { "Single tap woke the curtain" };doubleTap(root) }
                await("Curtain fixture did not wake after double tap") { overlay.isAwake }
                main {
                    for(field in listOf("playPauseBtn","previousBtn","nextBtn")) {
                        val button=BlackOverlay::class.java.getDeclaredField(field).apply { isAccessible=true }.get(overlay) as android.view.View
                        check(!button.isEnabled) { "Lost target leaves $field falsely enabled" }
                    }
                    val source=BlackOverlay::class.java.getDeclaredField("sourceText").apply { isAccessible=true }.get(overlay) as android.widget.TextView
                    check(source.text.toString()==ctx.getString(R.string.black_control_unavailable)) { "Lost target has no actionable curtain feedback" }
                    check(source.isShown) { "Lost target feedback is hidden" }
                }
            } finally { main { overlay.hide() } }
        } finally { main { selected.release(); other.release(); PlaybackSelection.token = previous } }
    }
    private fun inactivePlayerIsRetained() {
        lateinit var session:MediaSession
        main {
            session=MediaSession(ctx,"flow-inactive").apply {
                setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING,100,1f).build());isActive=true
            }
            PlaybackSelection.select(android.media.session.MediaController(ctx,session.sessionToken))
            session.isActive=false
        }
        try {
            SystemClock.sleep(300)
            main { check(ctx.queryNowPlaying()?.controller?.sessionToken==session.sessionToken) { "Inactive valid target was discarded" } }
        } finally { main { session.release();PlaybackSelection.clear() } }
    }
    private fun rejectedRequestHasReceipt() {
        val p=ctx.prefs();val old=p.getBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false)
        lateinit var dead:MediaSession
        val receipt=java.util.concurrent.CountDownLatch(1)
        var receivedCode:Int?=null
        val receiver=object:android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode:Int,resultData:android.os.Bundle?) { receivedCode=resultCode;receipt.countDown() }
        }
        try {
            main {
                p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,false).commit()
                ctx.startForegroundService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_SET_TIMER)
                    .putExtra(OverlayService.EXTRA_MINUTES,30L))
            }
            await("Original timer did not arm") { (OverlayService.instance?.timerRemainingMs() ?: 0)>0 }
            val deadline=SystemClock.elapsedRealtime()+OverlayService.instance!!.timerRemainingMs()
            main {
                dead=MediaSession(ctx,"flow-dead");val token=dead.sessionToken;dead.release()
                p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,true).commit()
                ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_SET_TIMER)
                    .putExtra(OverlayService.EXTRA_MINUTES,15L).putExtra(OverlayService.EXTRA_TIMER_SESSION,token)
                    .putExtra("timer_pause_keep_black",true).putExtra("timer_result",receiver))
            }
            check(receipt.await(4,java.util.concurrent.TimeUnit.SECONDS)) { "Service rejection had no acknowledgement" }
            check(receivedCode==1) { "Rejected request was reported accepted" }
            check(kotlin.math.abs(SystemClock.elapsedRealtime()+OverlayService.instance!!.timerRemainingMs()-deadline)<200)
        } finally {
            main {
                OverlayService.instance?.cancelTimer()
                ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
                p.edit().putBoolean(Prefs.TIMER_PAUSE_KEEP_BLACK,old).commit();PlaybackSelection.clear()
            }
        }
    }
    private fun curtainEntryIsAvailable() {
        lateinit var overlay:BlackOverlay
        lateinit var root:android.view.View
        main {
            overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
            overlay.show { }
            root=BlackOverlay::class.java.getDeclaredField("frame").apply { isAccessible=true }.get(overlay) as android.view.View
            tap(root)
        }
        try {
            SystemClock.sleep(400)
            main { check(!overlay.isAwake) { "Single tap woke the curtain" };doubleTap(root) }
            await("Curtain did not wake after double tap") { overlay.isAwake }
            main {
                val button=root.findViewWithTag<android.view.View>("flow_timer_setup")
                check(button!=null && button.visibility==android.view.View.VISIBLE) { "No timer setup entry inside curtain" }
                button.performClick()
                check(root.findViewWithTag<android.view.View>("flow_timer_editor")?.visibility==android.view.View.VISIBLE)
                if(ctx.queryNowPlaying()==null)check(!root.findViewWithTag<android.view.View>("flow_preset_episode").isEnabled) {
                    "Episode action offered without a usable player timeline"
                }
            }
        } finally { main { overlay.hide() } }
    }
    /** 默认（不配置）= 旧行为：轻点解锁、单击返回、熄屏撤幕。 */
    private fun guardDefaultsToLegacy() {
        val p=ctx.prefs();val existed=p.contains(Prefs.MISTOUCH_GUARD);val previous=p.getBoolean(Prefs.MISTOUCH_GUARD,false)
        p.edit().remove(Prefs.MISTOUCH_GUARD).commit()
        try {
            lateinit var overlay:BlackOverlay
            lateinit var root:android.view.View
            main {
                overlay=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false)
                overlay.show { }
                root=BlackOverlay::class.java.getDeclaredField("frame").apply { isAccessible=true }.get(overlay) as android.view.View
                val hint=BlackOverlay::class.java.getDeclaredField("touchHint").apply { isAccessible=true }.get(overlay) as android.widget.TextView
                check(hint.text.toString()==ctx.getString(R.string.black_hint_tap)) { "Default mode hint still describes double-tap" }
                val back=root.findViewById<android.widget.TextView>(R.id.black_return_button)
                check(back.text.toString()==ctx.getString(R.string.black_return_tap)) { "Default mode return copy still asks for long press" }
                tap(root)
            }
            await("Default mode single tap did not wake") { overlay.isAwake }
            main { root.findViewById<android.widget.TextView>(R.id.black_return_button).performClick() }
            await("Default mode single click did not return") { !overlay.isShowing }

            if(OverlayService.isRunning) {
                main { ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT)) }
                await("Previous service still stopping") { !OverlayService.isRunning }
            }
            main { ctx.startForegroundService(Intent(ctx,OverlayService::class.java)) }
            await("Service did not start for default-mode check") { OverlayService.instance!=null }
            SystemClock.sleep(300)
            main { OverlayService.instance?.toggleOverlay() }
            await("Default mode curtain did not open") { OverlayService.instance?.isAnyBlackShowing()==true }
            val receiver=OverlayService::class.java.getDeclaredField("screenOffReceiver")
                .apply { isAccessible=true }.get(OverlayService.instance) as android.content.BroadcastReceiver
            main { receiver.onReceive(ctx,Intent(Intent.ACTION_SCREEN_OFF)) }
            await("Default screen-off kept the curtain") { OverlayService.instance?.isAnyBlackShowing()!=true }
            main { ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT)) }
            await("Service cleanup failed") { !OverlayService.isRunning }
        } finally {
            p.edit().apply {if(existed)putBoolean(Prefs.MISTOUCH_GUARD,previous) else remove(Prefs.MISTOUCH_GUARD)}.commit()
        }
    }
    private fun firstSuccessIsRemembered(activity:MainActivity) {
        val key="first_success_confirmed_v1";val old=ctx.prefs().getBoolean(key,false)
        try {
            main {
                ctx.prefs().edit().remove(key).commit()
                val dialog=FirstSuccessFlow.confirm(activity)
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            }
            await("Return confirmation was not shown") {
                i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(activity.getString(R.string.support_return_question))?.isNotEmpty()==true
            }
            val positive=i.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("android:id/button1").first()
            check(positive.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await("Confirmed first success was not persisted") { ctx.prefs().getBoolean(key,false) }
            main { activity.postRefresh() }
            await("Completed first-use prompt still visible") { activity.findViewById<android.view.View>(R.id.btn_first_use).visibility==android.view.View.GONE }
        } finally {
            i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            ctx.prefs().edit().putBoolean(key,old).commit()
        }
    }
    private fun actualOneMinuteFlow(activity:MainActivity) {
        val baseline=BackupManager.buildBackupJson(ctx)
        val previousDefaults=ctx.prefs().getString(PlayerTimerDefaults.KEY,null)
        lateinit var selected:MediaSession
        lateinit var other:MediaSession
        var selectedPauses=0;var otherPauses=0
        lateinit var root:android.view.View
        fun state(kind:Int)=PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO)
            .setState(kind,1_000,if(kind==PlaybackState.STATE_PLAYING)1f else 0f,SystemClock.elapsedRealtime()).build()
        fun wake(overlay:BlackOverlay) {
            main { doubleTap(root) }
            await("Real service curtain failed to wake") { overlay.isAwake }
        }
        try {
            await("Previous service still stopping") { !OverlayService.isRunning }
            main {
                selected=MediaSession(ctx,"flow-uat-primary").apply {
                    setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID,"synthetic-primary")
                        .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Synthetic episode A")
                        .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,600_000).build())
                    setPlaybackState(state(PlaybackState.STATE_PLAYING));isActive=true
                    setCallback(object:MediaSession.Callback() { override fun onPause() { selectedPauses++;setPlaybackState(state(PlaybackState.STATE_PAUSED)) } },android.os.Handler(android.os.Looper.getMainLooper()))
                }
                other=MediaSession(ctx,"flow-uat-secondary").apply {
                    setPlaybackState(state(PlaybackState.STATE_PLAYING));isActive=true
                    setCallback(object:MediaSession.Callback() { override fun onPause() { otherPauses++ } },android.os.Handler(android.os.Looper.getMainLooper()))
                }
                PlaybackSelection.select(android.media.session.MediaController(ctx,selected.sessionToken))
                activity.findViewById<android.view.View>(R.id.card_black).performClick()
            }
            await("Home button failed to start assistant/bubble") { OverlayService.instance?.isBubbleVisible()==true }
            main {
                val bubble=OverlayService::class.java.getDeclaredField("bubble").apply { isAccessible=true }.get(OverlayService.instance) as android.view.View
                val time=SystemClock.uptimeMillis()
                for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                    val e=android.view.MotionEvent.obtain(time,time+20,action,bubble.width/2f,bubble.height/2f,0);bubble.dispatchTouchEvent(e);e.recycle()
                }
            }
            await("Bubble did not open curtain") { OverlayService.instance?.isAnyBlackShowing()==true }
            val shownAt=SystemClock.elapsedRealtime()
            val overlay=OverlayService::class.java.getDeclaredField("black").apply { isAccessible=true }.get(OverlayService.instance) as BlackOverlay
            root=BlackOverlay::class.java.getDeclaredField("frame").apply { isAccessible=true }.get(overlay) as android.view.View
            main { selected.isActive=false }
            wake(overlay)
            clickTag(root,"flow_timer_setup")
            clickTag(root,"flow_preset_15");clickTag(root,"flow_preset_custom")
            repeat(2) { clickTag(root,"flow_step_-5") }
            repeat(4) { clickTag(root,"flow_step_-1") }
            main { check((root.findViewWithTag<android.widget.TextView>("flow_timer_duration").text).contains("1")) }
            clickTag(root,"flow_action_pause")
            SystemClock.sleep(5_400)
            main { check(overlay.isAwake && root.findViewWithTag<android.view.View>("flow_timer_editor")!=null) { "Editor was relocked while configuring" };save(root,"timer-editor") }
            clickTag(root,"flow_timer_confirm")
            await("One minute timer not accepted") { OverlayService.instance?.timerRemainingMs() in 55_000L..60_000L && root.findViewWithTag<android.view.View>("flow_timer_editor")==null }
            check(selectedPauses==0 && otherPauses==0)
            // 电源键锁屏：黑幕必须保留、界面收回锁定态；锁屏时长不计入听剧。
            val screenReceiver=OverlayService::class.java.getDeclaredField("screenOffReceiver")
                .apply { isAccessible=true }.get(OverlayService.instance) as android.content.BroadcastReceiver
            val offAt=SystemClock.elapsedRealtime()
            main { screenReceiver.onReceive(ctx,Intent(Intent.ACTION_SCREEN_OFF)) }
            main {
                check(overlay.isShowing) { "Screen off removed the curtain" }
                check(!overlay.isAwake) { "Screen off left the curtain awake" }
            }
            SystemClock.sleep(8_000)
            main {
                screenReceiver.onReceive(ctx,Intent(Intent.ACTION_SCREEN_ON))
                screenReceiver.onReceive(ctx,Intent(Intent.ACTION_USER_PRESENT))
            }
            main { check(overlay.isShowing && !overlay.isAwake) { "Screen on lost the curtain/lock state" } }
            val offMs=SystemClock.elapsedRealtime()-offAt
            val until=SystemClock.elapsedRealtime()+66_000
            while(selectedPauses==0 && SystemClock.elapsedRealtime()<until)SystemClock.sleep(100)
            check(selectedPauses==1 && otherPauses==0) { "Expiry did not pause exactly the original target" }
            await("Pause confirmation not recorded") { SessionRuntime.snapshot().pause==PauseReport.CONFIRMED }
            check(overlay.isShowing && OverlayService.instance!!.timerRemainingMs()==0L)
            // 起记线 60 秒：注入的锁屏时长会吃掉富余，先在锁定态多留 12 秒，别让夹具卡在门槛上抖。
            SystemClock.sleep(12_000)
            wake(overlay)
            SystemClock.sleep(350)
            main {
                save(root,"timer-completed")
                val back=root.findViewById<android.view.View>(R.id.black_return_button)
                back.requestRectangleOnScreen(android.graphics.Rect(0,0,back.width,back.height),true)
            }
            i.waitForIdleSync()
            main {
                val back=root.findViewById<android.view.View>(R.id.black_return_button);val rect=android.graphics.Rect()
                check(back.getGlobalVisibleRect(rect) && rect.height()==back.height && back.alpha>.9f) { "Return video exit is clipped/invisible" }
                back.performClick()
            }
            SystemClock.sleep(300)
            main { check(OverlayService.instance!!.isAnyBlackShowing()) { "Single click returned despite anti-mistouch" } }
            main { root.findViewById<android.view.View>(R.id.black_return_button).performLongClick() }
            await("Long press return did not remove curtain") { !OverlayService.instance!!.isAnyBlackShowing() }
            val recorded=SessionLog.sessions(ctx).firstOrNull { it.mode==SessionLog.MODE_BLACK }
            val heldMs=SystemClock.elapsedRealtime()-shownAt
            check(recorded!=null) { "Curtain session was not recorded (held=${heldMs}ms screenOff=${offMs}ms → 有效=${heldMs-offMs}ms，起记线 60000ms)" }
            val curtainWall=recorded!!.end-recorded.start
            check(kotlin.math.abs(curtainWall-recorded.durationMs-offMs)<=2_000) {
                "Screen-off time leaked into listening duration: wall=${curtainWall}ms recorded=${recorded.durationMs}ms off=${offMs}ms"
            }
            main { activity.findViewById<android.view.View>(R.id.card_black).performClick() }
            await("Home stop did not stop assistant") { !OverlayService.isRunning }
            java.io.File(ctx.cacheDir,"flow-uat/expiry.json").apply { parentFile!!.mkdirs() }.writeText(
                org.json.JSONObject().put("uiStarted",true).put("bubbleOpened",true).put("inactiveTargetRetained",true)
                    .put("editorHeldBeyondFiveSeconds",true).put("acceptedOneMinute",true).put("originalPauseCount",selectedPauses)
                    .put("otherPauseCount",otherPauses).put("pauseConfirmed",true).put("curtainKept",true).put("returned",true).put("stopped",true)
                    .put("screenOffKeptCurtain",true).put("singleClickKeptCurtain",true)
                    .put("screenOffExcludedMs",curtainWall-recorded.durationMs).toString())
        } finally {
            main {
                runCatching { selected.release() };runCatching { other.release() };PlaybackSelection.clear()
                ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
            }
            await("UAT service cleanup failed") { !OverlayService.isRunning }
            check(BackupManager.restore(ctx,baseline)) { "Synthetic UAT baseline restore failed" }
            ctx.prefs().edit().apply { if(previousDefaults==null)remove(PlayerTimerDefaults.KEY) else putString(PlayerTimerDefaults.KEY,previousDefaults) }.commit()
        }
    }
    private fun save(view:android.view.View,name:String) {
        val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file=java.io.File(ctx.cacheDir,"flow-uat/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun clickTag(root:android.view.View,tag:String) {
        main { root.findViewWithTag<android.view.View>(tag).apply { requestRectangleOnScreen(android.graphics.Rect(0,0,width,height),true) } }
        i.waitForIdleSync()
        main {
            val view=root.findViewWithTag<android.view.View>(tag)
            val rect=android.graphics.Rect()
            check(view.isShown && view.getGlobalVisibleRect(rect) && rect.height()>0 && view.isEnabled) { "$tag is not visible/enabled" }
            view.performClick()
        }
        i.waitForIdleSync()
    }
}
