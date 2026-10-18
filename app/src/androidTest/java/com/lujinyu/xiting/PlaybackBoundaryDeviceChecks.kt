package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Emulator fixtures: a timer accepted while PLAYING must not invent success after state loss. */
internal class PlaybackBoundaryDeviceChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(block:()->Unit) {var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(message:String,test:()->Boolean) {val end=SystemClock.elapsedRealtime()+8000;while(!test()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(test()){"$message; actual=${SessionRuntime.snapshot()}; outcomes=${SessionOutcomeStore.list(ctx).map{it.pause}}"}}
    fun run() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"));check(ctx.hasMediaAccess())
        val baseline=BackupManager.buildBackupJson(ctx)
        val sessions=mutableListOf<MediaSession>();val otherPauses=AtomicInteger();val passed=mutableListOf<String>()
        fun setState(session:MediaSession,state:Int)=session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY)
            .setState(state,1000,if(state==3)1f else 0f).build())
        fun request(value:TimerRequest):TimerAcceptance {
            val done=CountDownLatch(1);var result:TimerAcceptance?=null
            main{TimerRequests.submit(ctx,value){result=it;done.countDown()}}
            check(done.await(6,TimeUnit.SECONDS));return result!!
        }
        try {
            main {ctx.startForegroundService(Intent(ctx,OverlayService::class.java))}
            await("Service did not start"){OverlayService.instance!=null}
            main {
                PlaybackSelection.clear()
                val other=MediaSession(ctx,"boundary-other").apply {
                    setState(this,3);isActive=true
                    setCallback(object:MediaSession.Callback(){override fun onPause(){otherPauses.incrementAndGet()}},Handler(Looper.getMainLooper()))
                };sessions.add(other)
            }
            lateinit var deadlineTarget:MediaSession
            main{deadlineTarget=MediaSession(ctx,"deadline-target").apply{setState(this,3);isActive=true};sessions.add(deadlineTarget)}
            check(request(TimerRequest(5,token=deadlineTarget.sessionToken,pauseKeepBlack=true)).accepted)
            val deadlineBefore=SystemClock.elapsedRealtime()+OverlayService.instance!!.timerRemainingMs()
            val past=request(TimerRequest(1,System.currentTimeMillis()-1,deadlineTarget.sessionToken,pauseKeepBlack=true))
            check(!past.accepted && past.errorResource==R.string.flow_request_expired) {"Expired absolute deadline was accepted and cancelled the existing timer"}
            check(kotlin.math.abs(SystemClock.elapsedRealtime()+OverlayService.instance!!.timerRemainingMs()-deadlineBefore)<250)
            passed.add("expired absolute deadline rejected without cancelling original timer")
            main {deadlineTarget.release()}
            for(drift in listOf(0,1,7,2,3)) {
                lateinit var original:MediaSession;val pauses=AtomicInteger()
                val done=CountDownLatch(1);var accepted:TimerAcceptance?=null
                main {
                    original=MediaSession(ctx,"boundary-$drift").apply {
                        setState(this,3);isActive=true
                        setCallback(object:MediaSession.Callback(){override fun onPause(){pauses.incrementAndGet();setState(this@apply,2)}},Handler(Looper.getMainLooper()))
                    };sessions.add(original)
                    TimerRequests.submit(ctx,TimerRequest(1,System.currentTimeMillis()+2500,original.sessionToken,pauseKeepBlack=true)){accepted=it;done.countDown()}
                }
                check(done.await(6,TimeUnit.SECONDS) && accepted?.accepted==true) {"Timer was not accepted for drift=$drift"}
                main {setState(original,drift)}
                await("Timer did not expire"){OverlayService.instance?.timerRemainingMs()==0L}
                val expected=when(drift){2->PauseReport.ALREADY_PAUSED;3->PauseReport.CONFIRMED;else->PauseReport.UNAVAILABLE}
                await("drift=$drift falsely reported ${SessionRuntime.snapshot().pause}, expected=$expected"){SessionRuntime.snapshot().pause==expected}
                check(pauses.get()==if(drift==3)1 else 0) {"Wrong pause request count"}
                check(otherPauses.get()==0) {"Timer controlled the replacement source"}
                passed.add("accepted PLAYING then state=$drift => $expected; other source untouched")
                main {original.release()}
            }
            main {
                val stopped=PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED,0,0f).build()
                check(SessionRuntime.playback(stopped)!=PlaybackReport.PAUSED) {"STOPPED was exposed as PAUSED in the session snapshot"}
            }
            java.io.File(ctx.cacheDir,"v52-boundaries/results.json").apply{parentFile!!.mkdirs()}.writeText(org.json.JSONArray(passed).toString())
        } finally {
            main {sessions.forEach{runCatching{it.release()}};PlaybackSelection.clear();ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))}
            await("Service cleanup failed"){!OverlayService.isRunning};check(BackupManager.restore(ctx,baseline))
        }
    }
}
