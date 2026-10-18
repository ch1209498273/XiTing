package com.lujinyu.xiting.listening

import android.content.Context
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import com.lujinyu.xiting.*
import java.util.UUID
import java.util.concurrent.Executors

internal object ListeningWork {
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    fun <T> submit(work:()->T,done:(Result<T>)->Unit={}) {
        executor.execute {val result=runCatching(work);main.post {done(result)}}
    }
}
internal object ListeningRecorder {
    private val main=Handler(Looper.getMainLooper())
    private var running=false
    private var generation=0
    // Accessed only on ListeningWork's serial executor.
    private var entryId:String?=null
    private var segmentId:String?=null
    private var last:ContentSnapshot?=null
    private var lastWrite=0L
    private var timerId:String?=null
    fun capture(ctx:Context):ContentSnapshot?=try {
        ctx.queryNowPlaying()?.let {np->
            if(np.controlsReliable)captureController(ctx,np.controller)
            else ContentSnapshot(np.pkg,np.appLabel.orEmpty(),null,null,null,null)
        }
    }catch(_:RuntimeException){null}
    fun captureController(ctx:Context,controller:android.media.session.MediaController?):ContentSnapshot?=try {
        controller?.let {c->
            val ps=c.playbackState;val md=c.metadata
            if(ps==null || !playbackControlsReliable(ps.state))null else {
                val title=md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()}
                val duration=md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf{it>0}
                val identity=md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf{title!=null && it.isNotBlank()}?.let {it+"\u0000${c.sessionToken.hashCode()}"}
                val position=PlaybackMath.position(ps.position,ps.lastPositionUpdateTime,android.os.SystemClock.elapsedRealtime(),ps.playbackSpeed,ps.state==android.media.session.PlaybackState.STATE_PLAYING,duration ?: 0L).takeIf{it>=0}
                ContentSnapshot(c.packageName,ctx.playerLabel(c.packageName),title,identity,position,duration)
            }
        }
    }catch(_:RuntimeException){null}
    private fun finish(ctx:Context) {
        val s=last;val id=segmentId
        if(s!=null && id!=null)ListeningStore.updateSegment(ctx,id,s,true)
        entryId=null;segmentId=null;last=null;lastWrite=0
    }
    private fun observe(ctx:Context,s:ContentSnapshot?,enabled:Boolean):String? {
        if(!enabled || s?.title==null || s.identity==null){finish(ctx);return null}
        val previous=last
        if(previous==null || entryId?.let{ListeningStore.get(ctx,it)}==null || !ContentIdentityPolicy.same(s.pkg,s.identity,previous.pkg,previous.identity)) {
            finish(ctx);entryId=ListeningStore.create(ctx,s);segmentId=ListeningStore.segment(ctx,entryId!!,s)
        }
        last=s
        if(s.at-lastWrite>=15_000) {segmentId?.let{ListeningStore.updateSegment(ctx,it,s)};lastWrite=s.at}
        return entryId
    }
    fun start(ctx:Context) {
        if(running)return
        running=true;val g=++generation;val app=ctx.applicationContext
        val tick=object:Runnable {
            override fun run() {
                if(!running || g!=generation)return
                val snapshot=capture(app);val enabled=app.prefs().getBoolean(ListeningStore.AUTO,true)
                ListeningWork.submit({observe(app,snapshot,enabled)})
                main.postDelayed(this,3000)
            }
        };main.post(tick)
    }
    fun stop(ctx:Context) {running=false;generation++;ListeningWork.submit({finish(ctx.applicationContext)})}
    fun mark(ctx:Context,done:(Boolean)->Unit) {
        val app=ctx.applicationContext;val s=capture(app) ?: ContentSnapshot("","",null,null,null,null)
        val enabled=app.prefs().getBoolean(ListeningStore.AUTO,true)
        ListeningWork.submit({
            val id=observe(app,s,enabled) ?: ListeningStore.create(app,s)
            ListeningStore.mark(app,id,s);true
        },{done(it.getOrDefault(false))})
    }
    fun acceptedTimer(ctx:Context,s:ContentSnapshot?):String {
        val app=ctx.applicationContext;val id=UUID.randomUUID().toString()
        ListeningWork.submit({
            timerId?.let{ListeningStore.timerStatus(app,it,"replaced")};timerId=id
            if(s?.title!=null && app.prefs().getBoolean(ListeningStore.AUTO,true)) {
                val previous=last
                val entry=if(previous!=null && ContentIdentityPolicy.same(s.pkg,s.identity,previous.pkg,previous.identity))entryId else null
                val target=entry ?: ListeningStore.create(app,s)
                ListeningStore.mark(app,target,s,"timer",id)
            }
        })
        return id
    }
    fun timerEnded(ctx:Context,status:String,id:String?=null) {
        val app=ctx.applicationContext
        ListeningWork.submit({val target=id ?: timerId;target?.let{ListeningStore.timerStatus(app,it,status)};if(timerId==target)timerId=null})
    }
}
