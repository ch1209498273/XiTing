package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.TextView

/** Emulator-only real audio + deliberately incorrect session state; no external apps or user data. */
internal class SystemPlaybackDeviceChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(block:()->Unit) {var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(message:String,test:()->Boolean) {val end=SystemClock.elapsedRealtime()+6000;while(!test()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);check(test()){message}}
    fun run() {
        check(Build.HARDWARE in listOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=33)
        check(ctx.hasMediaAccess())
        val baseline=BackupManager.buildBackupJson(ctx)
        val prefs=ctx.prefs();val mediaSetting=prefs.getBoolean(Prefs.BLACK_MEDIA_CONTROLS,false)
        val manager=ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        var session:MediaSession?=null;var audio:MediaPlayer?=null;var curtain:BlackOverlay?=null
        try {
            main {
                PlaybackSelection.clear()
                session=MediaSession(ctx,"incorrect-state-audio").apply {
                    setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO)
                        .setState(PlaybackState.STATE_STOPPED,0,1f).build());isActive=true
                }
                audio=MediaPlayer.create(ctx,R.raw.guide_sample_zh).apply {isLooping=true;start()}
            }
            await("System did not route audio to the fixture session"){manager.mediaKeyEventSession==session!!.sessionToken}
            await("STOPPED session with actual audio was not recognized"){ctx.queryNowPlaying()?.controller?.sessionToken==session!!.sessionToken}
            main {
                val np=ctx.queryNowPlaying()!!
                check(np.playing && !np.controlsReliable && !np.canSeek)
                check(!TimerPlaybackTarget(np.controller).canArm()) {"Unreliable STOPPED state offered a pause timer"}
                prefs.edit().putBoolean(Prefs.BLACK_MEDIA_CONTROLS,true).commit()
                curtain=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false).also{it.show{}}
                check(curtain!!.isShowing)
                BlackOverlay::class.java.getDeclaredMethod("wake").apply{isAccessible=true}.invoke(curtain)
                val title=BlackOverlay::class.java.getDeclaredField("sourceText").apply{isAccessible=true}.get(curtain) as TextView
                check(title.visibility==View.VISIBLE && title.text.toString()==ctx.getString(R.string.playback_source_control_in_app,np.appLabel))
                val pause=BlackOverlay::class.java.getDeclaredField("playPauseBtn").apply{isAccessible=true}.get(curtain) as View
                check(!pause.isEnabled)
                curtain!!.hide();curtain=null
                audio!!.stop();audio!!.release();audio=null
            }
            await("Silent STOPPED history was still selected"){ctx.queryNowPlaying()==null}
            val after=BackupManager.buildBackupJson(ctx)
            for(key in listOf("gp","sessions","session_outcomes","pet_form","pet_skin","skins_unlocked","ach_unlocked","ach_history","pending_energy"))
                check(after.get(key).toString()==baseline.get(key).toString()) {"Source test changed user records: $key"}
            java.io.File(ctx.cacheDir,"system-playback/results.json").apply{parentFile!!.mkdirs()}.writeText(
                "{\"recognizedStoppedAudio\":true,\"blackTitleVisible\":true,\"unreliableControlsDisabled\":true,\"pauseTimerDisabled\":true,\"silentHistoryRejected\":true}")
        } finally {
            main {curtain?.hide();audio?.release();session?.release();PlaybackSelection.clear();prefs.edit().putBoolean(Prefs.BLACK_MEDIA_CONTROLS,mediaSetting).commit()}
            check(BackupManager.restore(ctx,baseline)) {"Source test settings cleanup failed"}
        }
    }
}
