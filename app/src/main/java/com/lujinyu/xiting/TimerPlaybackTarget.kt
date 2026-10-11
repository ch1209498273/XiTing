package com.lujinyu.xiting

import android.media.session.MediaController
import android.media.session.PlaybackState

/** Retains the original session token via its controller. Never reselects another app at expiry. */
internal class TimerPlaybackTarget(private val controller:MediaController) {
    fun canArm():Boolean = try {
        val state=controller.playbackState
        state!=null && state.actions and PlaybackState.ACTION_PAUSE != 0L
    } catch(_:RuntimeException) { false }

    fun pause():PauseDecision = try {
        val state=controller.playbackState
        val decision=SleepTimerPolicy.pauseDecision(state!=null,
            state?.state==PlaybackState.STATE_PLAYING || state?.state==PlaybackState.STATE_BUFFERING ||
                state?.state==PlaybackState.STATE_CONNECTING,
            state!=null && state.actions and PlaybackState.ACTION_PAUSE != 0L)
        if(decision==PauseDecision.REQUEST) controller.transportControls.pause()
        decision
    } catch(_:RuntimeException) { PauseDecision.UNAVAILABLE }
}
