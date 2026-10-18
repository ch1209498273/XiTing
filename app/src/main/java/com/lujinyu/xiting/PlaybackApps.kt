package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri

internal data class PlaybackApp(val packageName:String,val label:String,val controller:MediaController?) {
    val canPause:Boolean get()=controller?.let { TimerPlaybackTarget(it).canArm() }==true
    fun description(ctx:Context):String {
        if(!ctx.hasMediaAccess())return ctx.getString(R.string.player_media_access_missing)
        val c=controller ?: return ctx.getString(R.string.player_control_unavailable)
        val state=when(c.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> R.string.player_state_playing
            PlaybackState.STATE_PAUSED -> R.string.player_state_paused
            else -> R.string.player_state_available
        }
        return ctx.getString(state)+" · "+ctx.getString(if(canPause)R.string.player_control_pause else R.string.player_control_no_pause)
    }
}

