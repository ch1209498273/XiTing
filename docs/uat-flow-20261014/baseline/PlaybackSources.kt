package com.lujinyu.xiting

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.content.Intent

/** Explicit selection is session-scoped; armed timers retain their own original token. */
internal object PlaybackSelection { var token:MediaSession.Token?=null }

internal fun Context.hasMediaAccess():Boolean = try {
    val actual=ComponentName(this,NotificationListener::class.java)
    Settings.Secure.getString(contentResolver,"enabled_notification_listeners")
        ?.split(':')?.any { ComponentName.unflattenFromString(it)==actual }==true
} catch(_:RuntimeException) { false }

internal fun Context.playbackSources():List<MediaController> = try {
    (getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
        ?.getActiveSessions(ComponentName(this,NotificationListener::class.java)).orEmpty()
        .filter { it.playbackState!=null }
        .sortedWith(compareByDescending<MediaController> { it.playbackState?.state==PlaybackState.STATE_PLAYING }
            .thenByDescending { it.playbackState?.lastPositionUpdateTime ?: 0 })
} catch(_:RuntimeException) { emptyList() }

internal fun Context.playerLabel(c:MediaController):String = playerLabel(c.packageName)

internal fun MainActivity.showPlaybackSourcePicker():AlertDialog {
    val sources=playbackSources()
    if(sources.isEmpty()) {
        if(hasMediaAccess()) {
            return AlertDialog.Builder(this).setTitle(R.string.ui_playback_source).setMessage(R.string.ui_no_session)
                .setPositiveButton(R.string.dlg_ok,null).show()
        }
        return AlertDialog.Builder(this).setTitle(R.string.ui_playback_source).setMessage(R.string.ui_media_permission)
            .setPositiveButton(R.string.locale_dlg_go) { _,_ ->
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch(_:RuntimeException) {}
            }.setNegativeButton(R.string.dlg_cancel,null).show()
    }
    return AlertDialog.Builder(this).setTitle(R.string.ui_choose_player)
        .setAdapter(PlayerRows.listAdapter(this,sources)) { d,index ->
            PlaybackSelection.token=sources[index].sessionToken;d.dismiss();postRefresh()
        }.setNegativeButton(R.string.dlg_cancel,null).show()
}
