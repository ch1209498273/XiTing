package com.lujinyu.xiting

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock

internal data class TimerRequest(
    val minutes:Long=30, val endAt:Long=0, val token:MediaSession.Token?=null,
    val episode:Boolean=false, val pauseKeepBlack:Boolean=false, val mediaSignature:String?=null,val appPackage:String?=null
)
internal data class TimerAcceptance(val accepted:Boolean,val errorResource:Int=0)

/** One service command/result contract for Activity and curtain. No persistent callbacks or tokens. */
internal object TimerRequests {
    const val RESULT="timer_result"
    const val PAUSE="timer_pause_keep_black"
    const val SIGNATURE="timer_media_signature"
    const val SENT_AT="timer_sent_elapsed"
    const val APP_PACKAGE="timer_app_package"
    const val SELECTION_REVISION="timer_selection_revision"
    private const val ID="timer_command_id"
    const val TIMEOUT_MS=5_000L
    private class TargetWatch(val controller:MediaController?,val requiresTarget:Boolean) {
        var destroyed=false
        val callback=object:MediaController.Callback() { override fun onSessionDestroyed() { destroyed=true } }
        fun open() { try { controller?.registerCallback(callback,Handler(Looper.getMainLooper())) }catch(_:RuntimeException){destroyed=true} }
        fun close() { try { controller?.unregisterCallback(callback) }catch(_:RuntimeException){ } }
    }
    private val pending=mutableMapOf<String,TargetWatch>()
    fun validationError(intent:Intent):Int {
        val id=intent.getStringExtra(ID) ?: return 0
        val watch=pending[id] ?: return R.string.flow_request_expired
        if(watch.destroyed && watch.requiresTarget)return R.string.timer_no_target
        return 0
    }
    fun isGuarded(intent:Intent)=intent.hasExtra(ID)
    fun controller(intent:Intent):MediaController?=pending[intent.getStringExtra(ID)]?.let { if(it.destroyed)null else it.controller }
    fun signature(c:MediaController):String? = try {
        val m=c.metadata
        listOf(m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),m?.getString(MediaMetadata.METADATA_KEY_TITLE),
            m?.getLong(MediaMetadata.METADATA_KEY_DURATION)).joinToString("\n")
    } catch(_:RuntimeException) { null }

    fun submit(ctx:Context,request:TimerRequest,onResult:(TimerAcceptance)->Unit):()->Unit {
        val handler=Handler(Looper.getMainLooper())
        val id=java.util.UUID.randomUUID().toString()
        var settled=false
        lateinit var timeout:Runnable
        fun finish(result:TimerAcceptance) {
            if(settled)return
            settled=true;handler.removeCallbacks(timeout);pending.remove(id)?.close();onResult(result)
        }
        timeout=Runnable { finish(TimerAcceptance(false,R.string.flow_timer_unconfirmed)) }
        val receiver=object:ResultReceiver(handler) {
            override fun onReceiveResult(resultCode:Int,resultData:Bundle?) {
                finish(TimerAcceptance(resultCode==0,resultData?.getInt("error") ?: 0))
            }
        }
        handler.postDelayed(timeout,TIMEOUT_MS)
        if(!request.episode && request.minutes !in 1..600)finish(TimerAcceptance(false,R.string.timer_custom_hint))
        else try {
            val watch=TargetWatch(request.token?.let { MediaController(ctx.applicationContext,it) },request.pauseKeepBlack || request.episode)
            pending[id]=watch;watch.open()
            val intent=Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_SET_TIMER)
                .putExtra(OverlayService.EXTRA_MINUTES,request.minutes).putExtra(OverlayService.EXTRA_END_AT,request.endAt)
                .putExtra(OverlayService.EXTRA_TIMER_SESSION,request.token).putExtra(OverlayService.EXTRA_TIMER_EPISODE,request.episode)
                .putExtra(PAUSE,request.pauseKeepBlack).putExtra(SIGNATURE,request.mediaSignature)
                .putExtra(APP_PACKAGE,request.appPackage)
                .putExtra(SELECTION_REVISION,PlaybackSelection.revision)
                .putExtra(SENT_AT,SystemClock.elapsedRealtime()).putExtra(ID,id).putExtra(RESULT,receiver)
            if(OverlayService.isRunning)ctx.startService(intent) else ctx.startForegroundService(intent)
        } catch(_:RuntimeException) { finish(TimerAcceptance(false,R.string.flow_timer_failed)) }
        return { settled=true;handler.removeCallbacks(timeout);pending.remove(id)?.close() }
    }
    @Suppress("DEPRECATION")
    fun reply(intent:Intent?,result:TimerAcceptance) {
        try { intent?.getParcelableExtra<ResultReceiver>(RESULT)?.send(if(result.accepted)0 else 1,
            Bundle().apply { putInt("error",result.errorResource) }) } catch(_:RuntimeException) { }
    }
}
