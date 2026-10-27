package com.lujinyu.xiting.listening

import android.content.Context
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private const val TAG="XiTing"
    private const val RESUME_WINDOW_MS=60_000L
    private val main=Handler(Looper.getMainLooper())
    private var running=false
    private var generation=0
    private var lastSkip=""
    private var closedEntry:String?=null
    private var closedKey:String?=null
    private var closedAt=0L
    // Accessed only on ListeningWork's serial executor.
    private var entryId:String?=null
    private var segmentId:String?=null
    private var last:ContentSnapshot?=null
    private var lastWrite=0L
    private var timerId:String?=null
    fun capture(ctx:Context):ContentSnapshot?=try {
        ctx.queryNowPlaying()?.let{np->captureController(ctx,np.controller,np.controlsReliable)?.let{it.copy(playing=np.playing)}}
    }catch(_:RuntimeException){null}
    /**
     * 状态不可信（播放器谎报 STOPPED/NONE 却仍在出声）时标题照样可用，位置不可用：
     * 记标题、不记续播点，免得把可疑读数写成「听到第 0 秒」。
     */
    fun captureController(ctx:Context,controller:android.media.session.MediaController?,reliable:Boolean=true):ContentSnapshot?=try {
        controller?.let {c->
            val ps=c.playbackState;val md=c.metadata
            if(ps==null)null else {
                val title=md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()}
                val duration=md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf{it>0}
                val identity=md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf{title!=null && it.isNotBlank()}?.let {it+"\u0000${c.sessionToken.hashCode()}"}
                val position=if(reliable)PlaybackMath.position(ps.position,ps.lastPositionUpdateTime,android.os.SystemClock.elapsedRealtime(),ps.playbackSpeed,ps.state==android.media.session.PlaybackState.STATE_PLAYING,duration ?: 0L).takeIf{it>=0}else null
                ContentSnapshot(c.packageName,ctx.playerLabel(c.packageName),title,identity,position,duration)
            }
        }
    }catch(_:RuntimeException){null}
    private fun finish(ctx:Context) {
        val s=last;val id=segmentId
        if(s!=null && id!=null)ListeningStore.updateSegment(ctx,id,s,true)
        // 记住刚结束的是哪条内容：广告、缓冲、切后台都会让会话闪断一下，
        // 60 秒内同一条内容回来就续写原条目，否则用户看到「同一集记成两行」。
        val key=s?.let{ListeningCapturePolicy.resolve(it)?.first}
        if(s!=null && key!=null && entryId!=null){closedEntry=entryId;closedKey=key;closedAt=s.at}
        else{closedEntry=null;closedKey=null;closedAt=0L}
        entryId=null;segmentId=null;last=null;lastWrite=0
    }
    private fun observe(ctx:Context,s:ContentSnapshot?,enabled:Boolean):String? {
        val decided=if(enabled)s?.let{ListeningCapturePolicy.resolve(it)} else null
        if(s==null || decided==null) {
            // 真机上唯一能分辨「读不到会话」与「没标题又没在播」的线索；按原因变化才打，避免刷屏。
            if(enabled) {
                val why=if(s==null)"no usable media session" else "idle without title (${s.pkg})"
                if(why!=lastSkip){lastSkip=why;Log.i(TAG,"listening skip: $why")}
            }
            finish(ctx);return null
        }
        lastSkip=""
        val (key,out)=decided
        val previous=last
        val previousKey=previous?.let{ListeningCapturePolicy.resolve(it)?.first}
        if(previous==null || previousKey==null || entryId?.let{ListeningStore.get(ctx,it)}==null || !ContentIdentityPolicy.same(out.pkg,key,previous.pkg,previousKey)) {
            val resume=closedEntry?.takeIf{closedKey==key && out.at-closedAt<=RESUME_WINDOW_MS && ListeningStore.get(ctx,it)!=null}
            finish(ctx)
            entryId=resume ?: ListeningStore.create(ctx,out)
            segmentId=ListeningStore.segment(ctx,entryId!!,out)
        }
        last=out
        if(out.at-lastWrite>=15_000) {segmentId?.let{ListeningStore.updateSegment(ctx,it,out)};lastWrite=out.at}
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
            if(s!=null && app.prefs().getBoolean(ListeningStore.AUTO,true)) {
                val decided=ListeningCapturePolicy.resolve(s)
                if(decided!=null) {
                    val (key,out)=decided
                    val previous=last
                    val previousKey=previous?.let{ListeningCapturePolicy.resolve(it)?.first}
                    val entry=if(previousKey!=null && ContentIdentityPolicy.same(out.pkg,key,checkNotNull(previous).pkg,previousKey))entryId else null
                    ListeningStore.mark(app,entry ?: ListeningStore.create(app,out),out,"timer",id)
                }
            }
        })
        return id
    }
    fun timerEnded(ctx:Context,status:String,id:String?=null) {
        val app=ctx.applicationContext
        ListeningWork.submit({val target=id ?: timerId;target?.let{ListeningStore.timerStatus(app,it,status)};if(timerId==target)timerId=null})
    }
}
