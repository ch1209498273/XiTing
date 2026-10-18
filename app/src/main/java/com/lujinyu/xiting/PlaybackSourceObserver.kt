package com.lujinyu.xiting

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper

/** Foreground observation only; no polling/background process or persisted media metadata. */
internal class PlaybackSourceObserver(private val ctx:Context,private val changed:()->Unit):AutoCloseable {
    private val manager=ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val handler=Handler(Looper.getMainLooper())
    private val watched=mutableListOf<Pair<MediaController,MediaController.Callback>>()
    private val sessions=MediaSessionManager.OnActiveSessionsChangedListener { bind(it.orEmpty());changed() }
    init {
        try {
            manager.addOnActiveSessionsChangedListener(sessions,ComponentName(ctx,NotificationListener::class.java),handler)
            bind(ctx.playbackSources())
        } catch(_:RuntimeException) {}
    }
    private fun bind(controllers:List<MediaController>) {
        clear()
        for(controller in controllers) {
            val cb=object:MediaController.Callback() {
                override fun onPlaybackStateChanged(state:android.media.session.PlaybackState?)=changed()
                override fun onMetadataChanged(metadata:android.media.MediaMetadata?)=changed()
                override fun onSessionDestroyed()=changed()
            }
            try { controller.registerCallback(cb,handler);watched.add(controller to cb) } catch(_:RuntimeException) {}
        }
    }
    private fun clear() { watched.forEach { (c,cb)->try { c.unregisterCallback(cb) } catch(_:RuntimeException) {} };watched.clear() }
    override fun close() { try { manager.removeOnActiveSessionsChangedListener(sessions) } catch(_:RuntimeException) {};clear() }
}
