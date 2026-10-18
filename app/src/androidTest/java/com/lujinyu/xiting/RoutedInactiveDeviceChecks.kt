package com.lujinyu.xiting

import android.app.Instrumentation
import android.media.MediaPlayer
import android.media.AudioManager
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState

/** Recreates OPPO's observed routing hint with a real inactive token, not a claim about emulator routing policy. */
internal class RoutedInactiveDeviceChecks(private val i:Instrumentation) {
    fun run() {
        val ctx=i.targetContext
        val target=MediaSession(ctx,"inactive-routed-source")
        val decoy=MediaSession(ctx,"old-paused-source")
        var audio:MediaPlayer?=null
        try {
            target.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_NONE,0,1f).build())
            target.isActive=false
            decoy.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,100,0f).build())
            val old=MediaController(ctx,decoy.sessionToken)
            i.runOnMainSync { audio=MediaPlayer.create(ctx,R.raw.guide_sample_zh).apply {isLooping=true;start()} }
            val audioManager=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val until=android.os.SystemClock.elapsedRealtime()+5000
            while(!audioManager.isMusicActive && android.os.SystemClock.elapsedRealtime()<until)android.os.SystemClock.sleep(40)
            val sounding=audioManager.isMusicActive
            check(sounding) {"Audio fixture not playing"}
            val sources=ctx.includeRoutedPlaybackSource(listOf(old),target.sessionToken,sounding)
            check(sources.any {it.sessionToken==target.sessionToken}) {"Current routed inactive session missing from discovered sources"}
            val chosen=PlaybackSelection.resolve(sources,target.sessionToken,sounding)
            check(chosen?.sessionToken==target.sessionToken) {"Old paused source selected instead of current routed source"}
            check(!TimerPlaybackTarget(chosen!!).canArm()) {"Inactive source exposed pause timer"}
            check(!playbackControlsReliable(chosen.playbackState?.state ?: PlaybackState.STATE_NONE))
            check(ctx.includeRoutedPlaybackSource(emptyList(),target.sessionToken,false).isEmpty()) {"Silent routed history promoted"}
            check(ctx.includeRoutedPlaybackSource(emptyList(),decoy.sessionToken,true).isEmpty()) {"Paused history promoted by unrelated audio"}
            check(ctx.includeRoutedPlaybackSource(sources,target.sessionToken,true).count {it.sessionToken==target.sessionToken}==1) {"Routed token duplicated"}
        } finally {
            i.runOnMainSync {audio?.release()}
            target.release();decoy.release();PlaybackSelection.clear()
        }
    }
}
