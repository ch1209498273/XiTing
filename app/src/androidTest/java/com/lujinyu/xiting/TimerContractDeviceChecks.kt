package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.provider.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class TimerContractDeviceChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(action:()->Unit) { var failure:Throwable?=null;i.runOnMainSync { try { action() }catch(t:Throwable){failure=t} };failure?.let { throw it } }
    private fun await(message:String,test:()->Boolean) { val until=SystemClock.elapsedRealtime()+6_000;while(!test() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(40);check(test()){message} }
    private fun request(value:TimerRequest):TimerAcceptance {
        val done=CountDownLatch(1);var result:TimerAcceptance?=null
        main { TimerRequests.submit(ctx,value) { result=it;done.countDown() } }
        check(done.await(6,TimeUnit.SECONDS)) { "Command did not settle" };return result!!
    }
    private fun deadline()=SystemClock.elapsedRealtime()+(OverlayService.instance?.timerRemainingMs() ?: 0)
    private fun awaitExpiry(message:String) {
        val until=SystemClock.elapsedRealtime()+15_000
        while(OverlayService.instance!!.timerRemainingMs()>0 && SystemClock.elapsedRealtime()<until)SystemClock.sleep(40)
        check(OverlayService.instance!!.timerRemainingMs()==0L){message}
    }
    private fun shell(value:String) {
        val fd=i.uiAutomation.executeShellCommand(value)
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
    fun run(activity:MainActivity) {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val p=ctx.prefs();val before=p.getString(PlayerTimerDefaults.KEY,null)
        val baseline=BackupManager.buildBackupJson(ctx)
        val listeners=Settings.Secure.getString(ctx.contentResolver,"enabled_notification_listeners").orEmpty()
        val mine="${ctx.packageName}/${NotificationListener::class.java.name}"
        lateinit var selected:MediaSession
        lateinit var other:MediaSession
        val otherPauses=AtomicInteger()
        val passed=mutableListOf<String>()
        try {
            check(ctx.hasMediaAccess()) { "Synthetic media access must be granted" }
            main {
                selected=MediaSession(ctx,"contract-primary").apply {
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"contract-a")
                        .putString(MediaMetadata.METADATA_KEY_TITLE,"Synthetic contract A").putLong(MediaMetadata.METADATA_KEY_DURATION,600_000).build())
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING,1_000,1f).build());isActive=true
                }
                other=MediaSession(ctx,"contract-other").apply {
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING,1_000,1f).build());isActive=true
                    setCallback(object:MediaSession.Callback(){ override fun onPause() { otherPauses.incrementAndGet() } },Handler(Looper.getMainLooper()))
                }
                PlaybackSelection.select(android.media.session.MediaController(ctx,selected.sessionToken))
            }
            check(request(TimerRequest(15,token=selected.sessionToken)).accepted)
            val initial=deadline()
            check(PlayerTimerDefaults.load(ctx,ctx.packageName)==ListeningPreset(15,false))
            passed.add("accepted command persists only validated defaults")
            check(!request(TimerRequest(601,token=selected.sessionToken)).accepted)
            check(kotlin.math.abs(deadline()-initial)<200)
            passed.add("invalid minutes preserve original timer")
            val changed=request(TimerRequest(30,token=selected.sessionToken,pauseKeepBlack=true,mediaSignature="outdated"))
            check(!changed.accepted && changed.errorResource==R.string.v50_target_changed)
            check(kotlin.math.abs(deadline()-initial)<200 && PlayerTimerDefaults.load(ctx,ctx.packageName)==ListeningPreset(15,false))
            passed.add("changed media rejected without overwriting timer or defaults")
            val done=CountDownLatch(1);var expiredCode:Int?=null;var expiredError:Int?=null
            val receiver=object:ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(code:Int,data:Bundle?) { expiredCode=code;expiredError=data?.getInt("error");done.countDown() }
            }
            main { ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_SET_TIMER)
                .putExtra(OverlayService.EXTRA_MINUTES,1L).putExtra(TimerRequests.PAUSE,false)
                .putExtra(TimerRequests.SENT_AT,SystemClock.elapsedRealtime()-6_000).putExtra(TimerRequests.RESULT,receiver)) }
            check(done.await(3,TimeUnit.SECONDS) && expiredCode==1 && expiredError==R.string.flow_request_expired && kotlin.math.abs(deadline()-initial)<200)
            passed.add("expired command cannot replace timer")
            lateinit var dialog:android.app.AlertDialog
            main { dialog=activity.showTimerOptions(ListeningPreset(30,true)) }
            i.waitForIdleSync()
            main {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
                check(!dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled)
                selected.release()
            }
            await("Rejected panel did not become editable") { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled }
            main {
                check(dialog.isShowing && kotlin.math.abs(deadline()-initial)<200) { "Service rejection closed UI or replaced old timer: showing=${dialog.isShowing} deadlineDelta=${deadline()-initial}" }
                check(dialog.findViewById<android.widget.EditText>(R.id.timer_minutes_input).text.toString()=="30")
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            passed.add("UI validation-service race keeps panel input and original timer")
            check(request(TimerRequest(15,token=selected.sessionToken)).accepted) { "Fixed curtain removal incorrectly requires a live media target" }
            passed.add("fixed curtain removal remains usable after target destruction")
            val beforeExtend=deadline()
            main { OverlayService.instance!!.extendTimer() }
            check(deadline()-beforeExtend in 899_800..900_200)
            main { OverlayService.instance!!.cancelTimer() }
            check(OverlayService.instance!!.timerRemainingMs()==0L && SessionRuntime.snapshot().timer==TimerPhase.NONE)
            passed.add("extend and cancel publish matching service state")
            main {
                selected=MediaSession(ctx,"contract-destroy").apply {
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING,1_000,1f).build());isActive=true
                }
                PlaybackSelection.select(android.media.session.MediaController(ctx,selected.sessionToken))
            }
            check(request(TimerRequest(1,System.currentTimeMillis()+10_000,selected.sessionToken,pauseKeepBlack=true)).accepted)
            main { check(OverlayService.instance!!.timerRemainingMs()>0) { "Fixture expired before target destruction" };selected.release() }
            awaitExpiry("Destroyed target expiry did not settle")
            await("Destroyed target pause report did not settle") { SessionRuntime.snapshot().pause==PauseReport.UNAVAILABLE }
            check(otherPauses.get()==0)
            passed.add("destroyed target never pauses other session")
            main {
                selected=MediaSession(ctx,"contract-permission").apply {
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING,1_000,1f).build());isActive=true
                }
                PlaybackSelection.select(android.media.session.MediaController(ctx,selected.sessionToken))
            }
            check(request(TimerRequest(1,System.currentTimeMillis()+10_000,selected.sessionToken,pauseKeepBlack=true)).accepted)
            val component=android.content.ComponentName(ctx,NotificationListener::class.java)
            val removed=listeners.split(':').filter { android.content.ComponentName.unflattenFromString(it)!=component }.joinToString(":")
            if(Build.VERSION.SDK_INT>=27)shell("cmd notification disallow_listener $mine")
            else shell("settings put secure enabled_notification_listeners ${removed.ifEmpty { "null" }}")
            await("Media grant was not revoked") { !ctx.hasMediaAccess() }
            check(OverlayService.instance!!.timerRemainingMs()>0) { "Fixture expired before grant revocation" }
            check(ctx.queryNowPlaying()==null && ctx.playbackLookup().availability==PlaybackAvailability.NO_ACCESS)
            awaitExpiry("Revoked grant expiry did not settle")
            await("Revoked grant pause report did not settle") { SessionRuntime.snapshot().pause==PauseReport.UNAVAILABLE }
            check(otherPauses.get()==0)
            passed.add("revoked grant blocks cached source and delayed pause")
            java.io.File(ctx.cacheDir,"flow-uat/contracts.json").apply { parentFile!!.mkdirs() }
                .writeText(org.json.JSONObject().put("passed",org.json.JSONArray(passed)).put("otherPauseCount",otherPauses.get()).toString())
        } finally {
            if(Build.VERSION.SDK_INT>=27) {
                val wasGranted=listeners.split(':').any { android.content.ComponentName.unflattenFromString(it)==android.content.ComponentName(ctx,NotificationListener::class.java) }
                shell("cmd notification ${if(wasGranted)"allow_listener" else "disallow_listener"} $mine")
            } else shell("settings put secure enabled_notification_listeners ${listeners.ifEmpty { "null" }}")
            main {
                runCatching { selected.release() };runCatching { other.release() };PlaybackSelection.clear()
                ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))
            }
            await("Contract cleanup failed") { !OverlayService.isRunning }
            check(BackupManager.restore(ctx,baseline))
            p.edit().apply { if(before==null)remove(PlayerTimerDefaults.KEY) else putString(PlayerTimerDefaults.KEY,before) }.commit()
        }
    }
}
