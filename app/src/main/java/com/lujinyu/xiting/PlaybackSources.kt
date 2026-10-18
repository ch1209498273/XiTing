package com.lujinyu.xiting

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.media.AudioManager
import android.os.Build

internal enum class PlaybackAvailability { AVAILABLE, NO_ACCESS, EMPTY, SELECTED_UNAVAILABLE, SELECTED_NO_SESSION, ERROR }
internal data class PlaybackLookup(val sources:List<MediaController>,val availability:PlaybackAvailability,
    val systemToken:MediaSession.Token?=null,val audioActive:Boolean=false)

internal fun Context.hasMediaAccess():Boolean = try {
    val actual=ComponentName(this,NotificationListener::class.java)
    Settings.Secure.getString(contentResolver,"enabled_notification_listeners")
        ?.split(':')?.any { ComponentName.unflattenFromString(it)==actual }==true
} catch(_:RuntimeException) { false }

internal fun Context.playbackLookup():PlaybackLookup {
    if(!hasMediaAccess()) return PlaybackLookup(emptyList(),PlaybackAvailability.NO_ACCESS)
    return try {
    val manager=getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    val audioActive=(getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive==true
    // API 33+: this public API is available with the existing notification-listener grant.
    // It is a routing hint, not permission to attribute arbitrary audio UIDs or revive old paused players.
    val systemToken=if(Build.VERSION.SDK_INT>=33 && audioActive)try { manager?.mediaKeyEventSession } catch(_:RuntimeException) { null } else null
    val queried=manager?.getActiveSessions(ComponentName(this,NotificationListener::class.java)).orEmpty()
        .filter { it.playbackState!=null }
        .sortedWith(compareByDescending<MediaController> { it.playbackState?.state==PlaybackState.STATE_PLAYING }
            .thenByDescending { it.playbackState?.lastPositionUpdateTime ?: 0 })
    val active=includeRoutedPlaybackSource(queried,systemToken,audioActive)
    val cached=PlaybackSelection.cached()
    val sources=if(cached!=null && active.none { it.sessionToken==cached.sessionToken })listOf(cached)+active else active
    val selected=PlaybackSelection.resolve(active,systemToken,audioActive)
    PlaybackLookup(sources,when {
        PlaybackSelection.hasSelection && selected==null -> if(PlaybackSelection.token!=null)PlaybackAvailability.SELECTED_UNAVAILABLE else PlaybackAvailability.SELECTED_NO_SESSION
        sources.isEmpty() -> PlaybackAvailability.EMPTY
        else -> PlaybackAvailability.AVAILABLE
    },systemToken,audioActive)
    } catch(_:RuntimeException) { PlaybackLookup(emptyList(),PlaybackAvailability.ERROR) }
}
internal fun Context.includeRoutedPlaybackSource(active:List<MediaController>,
    systemToken:MediaSession.Token?,audioActive:Boolean):List<MediaController> {
    if(!audioActive || systemToken==null || active.any {it.sessionToken==systemToken})return active
    // Some players remain the system's media-key target after becoming inactive.
    // Discover that exact token without promoting unrelated paused history.
    return try {
        val routed=MediaController(this,systemToken)
        if(routed.packageName.isNotBlank() && routed.playbackState?.state in listOf(
                PlaybackState.STATE_NONE,PlaybackState.STATE_STOPPED,PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING))active+routed else active
    } catch(_:RuntimeException) {active}
}
internal fun Context.playbackSources():List<MediaController> = playbackLookup().sources

internal fun Context.playerLabel(c:MediaController):String = playerLabel(c.packageName)

