package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real framework checks using isolated files, not external players. */
internal class V50SessionDeviceChecks(private val i:Instrumentation) {
    private fun main(block:()->Unit) {
        var error:Throwable?=null
        i.runOnMainSync { try { block() } catch(t:Throwable) { error=t } }
        error?.let { throw it }
    }
    fun run() {
        main { i.targetContext.startService(Intent(i.targetContext,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT)) }
        i.waitForIdleSync()
        repeat(50) { if(OverlayService.isRunning)android.os.SystemClock.sleep(50) }
        check(!OverlayService.isRunning)
        SessionOutcomeDeviceChecks.run(i.targetContext)
        checkpointAndIdempotence()
        pauseConfirmation()
    }
    private fun checkpointAndIdempotence() {
        val base=i.targetContext;val id="runtime-probe-${System.nanoTime()}"
        val dir=File(base.cacheDir,id).apply { check(mkdirs()) }
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir()=dir
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences(id,mode)
        }
        try {
            SessionRuntime.selectScene(ctx,"sleep");SessionRuntime.started(ctx)
            check(SessionRuntime.snapshot().id.isEmpty())
            check(SessionRuntime.snapshot().playback==PlaybackReport.UNKNOWN)
            SessionRuntime.begin(ctx);val first=SessionRuntime.snapshot().id
            SessionRuntime.begin(ctx);check(SessionRuntime.snapshot().id==first)
            SessionRuntime.pause(ctx,PauseReport.REQUESTED)
            SessionRuntime.finish(ctx,SessionEndReason.TIMER)
            SessionRuntime.begin(ctx)
            SessionRuntime.pause(ctx,PauseReport.CONFIRMED,first)
            check(SessionRuntime.snapshot().pause==PauseReport.NONE) { "Old callback polluted new session" }
            SessionRuntime.finish(ctx,SessionEndReason.USER)
            check(SessionOutcomeStore.list(ctx).first { it.id==first }.let { it.reason=="timer" && it.pause=="confirmed" })
            SessionRuntime.begin(ctx);check(SessionRuntime.snapshot().id!=first)
            val interruptedId=SessionRuntime.snapshot().id
            SessionRuntime.started(ctx)
            val interrupted=SessionOutcomeStore.list(ctx).first { it.id==interruptedId }
            check(interrupted.reason=="interrupted" && interrupted.endedAt==null)
            check(SessionRuntime.snapshot().id.isEmpty() && SessionRuntime.snapshot().timer==TimerPhase.NONE)
            check(HistoryStore.totalCount(ctx)==0L && EnergyStore.collectedTotal(ctx)==0)
        } finally {
            ctx.prefs().edit().clear().commit();dir.deleteRecursively();SessionRuntime.started(base)
            SessionRuntime.publish(base,SessionRuntime.snapshot().copy(assistant=AssistantPhase.IDLE))
        }
    }
    private fun pauseConfirmation() {
        lateinit var session:MediaSession
        lateinit var target:TimerPlaybackTarget
        var stop:(()->Unit)?=null
        val confirmed=CountDownLatch(1);var confirmedReport:PauseReport?=null
        main {
            session=MediaSession(i.targetContext,"v50-confirmation")
            session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PLAYING,100,1f).build());session.isActive=true
            target=TimerPlaybackTarget(android.media.session.MediaController(i.targetContext,session.sessionToken))
            stop=target.observePause(Handler(Looper.getMainLooper())) { confirmedReport=it;confirmed.countDown() }
            session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PAUSED,100,0f).build())
        }
        try {
            check(confirmed.await(3,TimeUnit.SECONDS));check(confirmedReport==PauseReport.CONFIRMED)
            val unknown=CountDownLatch(1);var unknownReport:PauseReport?=null
            main {
                stop?.invoke()
                session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING,100,1f).build())
                stop=target.observePause(Handler(Looper.getMainLooper())) { unknownReport=it;unknown.countDown() }
            }
            check(unknown.await(7,TimeUnit.SECONDS));check(unknownReport==PauseReport.UNCONFIRMED)
        } finally { main { stop?.invoke();session.release() } }
    }
}
