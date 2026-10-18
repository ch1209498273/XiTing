package com.lujinyu.xiting

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal data class EpisodeReading(val valid:Boolean, val advancing:Boolean, val remainingMs:Long)

/** Follows one original episode. Pauses/buffering suspend the deadline; next episode cancels it. */
internal class EpisodeTimerTarget(private val controller:MediaController) : AutoCloseable {
    private var destroyed=false
    private var lastSpeed=controller.playbackState?.playbackSpeed?.takeIf { it.isFinite() && it>0f } ?: 1f
    private val original=identity(controller.metadata)
    private var callback:MediaController.Callback?=null

    private fun identity(md:MediaMetadata?):String? {
        if(md==null) return null
        return md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }
            ?.let { "id:$it" }
            ?: md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?.let { "title:$it:${md.getLong(MediaMetadata.METADATA_KEY_DURATION)}" }
            // Duration alone cannot distinguish equally long episodes: fail closed.
    }

    fun read():EpisodeReading = try {
        val md=controller.metadata
        val st=controller.playbackState
        if(destroyed || original==null || identity(md)!=original || st==null ||
            st.position<0 || st.state==PlaybackState.STATE_NONE || st.state==PlaybackState.STATE_ERROR) {
            EpisodeReading(false,false,0)
        } else {
            val advancing=st.state==PlaybackState.STATE_PLAYING && st.playbackSpeed.isFinite() && st.playbackSpeed>0f
            if(advancing) lastSpeed=st.playbackSpeed
            val duration=md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position=PlaybackMath.position(st.position,st.lastPositionUpdateTime,
                SystemClock.elapsedRealtime(),st.playbackSpeed,advancing,duration)
            val remaining=PlaybackMath.remainingMs(duration,position,lastSpeed)
            val completed=remaining==0L && st.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_STOPPED)
            val valid=remaining!=null && (st.state!=PlaybackState.STATE_STOPPED || completed)
            EpisodeReading(valid,advancing || completed,remaining ?: 0)
        }
    } catch(_:RuntimeException) { EpisodeReading(false,false,0) }

    fun observe(onChange:()->Unit) {
        closeCallback()
        callback=object:MediaController.Callback() {
            override fun onPlaybackStateChanged(state:PlaybackState?)=onChange()
            override fun onMetadataChanged(metadata:MediaMetadata?)=onChange()
            override fun onSessionDestroyed() { destroyed=true;onChange() }
        }
        controller.registerCallback(callback!!,Handler(Looper.getMainLooper()))
    }
    private fun closeCallback() {
        callback?.let { try { controller.unregisterCallback(it) } catch(_:RuntimeException) {} }
        callback=null
    }
    override fun close() { closeCallback();destroyed=true }
}
