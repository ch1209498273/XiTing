package com.lujinyu.xiting

import android.media.session.MediaController
import android.media.session.PlaybackState

/** Retains the original session token via its controller. Never reselects another app at expiry. */
internal class TimerPlaybackTarget(private val controller:MediaController):AutoCloseable {
    private var destroyed=false
    private var watching=false
    private val lifetime=object:MediaController.Callback() { override fun onSessionDestroyed() { destroyed=true } }
    fun watchLifetime() {
        if(watching)return
        try { controller.registerCallback(lifetime,android.os.Handler(android.os.Looper.getMainLooper()));watching=true }
        catch(_:RuntimeException) { destroyed=true }
    }
    override fun close() {
        if(watching)try { controller.unregisterCallback(lifetime) }catch(_:RuntimeException){ }
        watching=false
    }
    /** Observe only the original target, for a bounded result-confirmation window. */
    fun observePause(handler:android.os.Handler,onResult:(PauseReport)->Unit):()->Unit {
        var settled=false
        lateinit var callback:MediaController.Callback
        lateinit var timeout:Runnable
        fun settle(report:PauseReport) {
            if(settled)return
            settled=true
            handler.removeCallbacks(timeout)
            try { controller.unregisterCallback(callback) } catch(_:RuntimeException) {}
            onResult(report)
        }
        callback=object:MediaController.Callback() {
            override fun onPlaybackStateChanged(state:PlaybackState?) {
                if(state?.state==PlaybackState.STATE_PAUSED || state?.state==PlaybackState.STATE_STOPPED)settle(PauseReport.CONFIRMED)
            }
            override fun onSessionDestroyed() { settle(PauseReport.UNAVAILABLE) }
        }
        timeout=Runnable { settle(PauseReport.UNCONFIRMED) }
        try {
            controller.registerCallback(callback,handler)
            handler.postDelayed(timeout,5_000)
            val state=controller.playbackState
            callback.onPlaybackStateChanged(state)
        } catch(_:RuntimeException) { settle(PauseReport.UNAVAILABLE) }
        return { settle(PauseReport.UNCONFIRMED) }
    }
    fun canArm():Boolean = try {
        val state=controller.playbackState
        !destroyed && state!=null && playbackControlsReliable(state.state) && state.actions and PlaybackState.ACTION_PAUSE != 0L
    } catch(_:RuntimeException) { false }

    fun pause():PauseDecision = try {
        val state=controller.playbackState
        val decision=SleepTimerPolicy.pauseDecision(!destroyed && state!=null,
            state?.state ?: PlaybackState.STATE_NONE,
            state!=null && state.actions and PlaybackState.ACTION_PAUSE != 0L)
        if(decision==PauseDecision.REQUEST) controller.transportControls.pause()
        decision
    } catch(_:RuntimeException) { PauseDecision.UNAVAILABLE }
}
