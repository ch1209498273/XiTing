package com.lujinyu.xiting

import android.app.Instrumentation
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic sessions are emulator-only; real phone acceptance is separate. */
internal class PlaybackAppDeviceChecks(private val i:Instrumentation) {
    private val ctx get()=i.targetContext
    private fun main(block:()->Unit) {var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(message:String,limit:Long=6000,test:()->Boolean) {val end=SystemClock.elapsedRealtime()+limit;while(!test()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(40);check(test()){message}}
    private fun views(v:View):List<View> = listOf(v)+(if(v is android.view.ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    fun run() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"));check(ctx.hasMediaAccess())
        val baseline=BackupManager.buildBackupJson(ctx);val defaults=ctx.prefs().getString(PlayerTimerDefaults.KEY,null)
        val sessions=mutableListOf<MediaSession>();val aCount=AtomicInteger();val bCount=AtomicInteger();val passed=mutableListOf<String>()
        var activity:MainActivity?=null
        fun state(s:MediaSession,value:Int) {s.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).setState(value,100,if(value==3)1f else 0f).build())}
        fun session(name:String,count:AtomicInteger)=MediaSession(ctx,name).apply {state(this,2);setCallback(object:MediaSession.Callback(){override fun onPause(){count.incrementAndGet();state(this@apply,2)}},Handler(Looper.getMainLooper()));isActive=true;sessions.add(this)}
        try {
            ctx.prefs().edit().putString(Prefs.UPDATE_SEEN_VERSION,BuildConfig.VERSION_NAME).commit()
            activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            lateinit var a:MediaSession;lateinit var b:MediaSession
            main {PlaybackSelection.clear();a=session("auto-a",aCount);b=session("auto-b",bCount)}
            await("Sessions not exposed"){ctx.playbackSources().size>=2}
            check(ctx.queryNowPlaying()==null) {"Paused old source was treated as current"}
            lateinit var dialog:android.app.AlertDialog
            main {activity!!.refreshUpgradeUi();check(activity!!.findViewById<View>(R.id.playback_source_card).visibility==View.GONE);dialog=activity!!.showTimerOptions()}
            SystemClock.sleep(350);i.waitForIdleSync()
            main {
                val nodes=views(dialog.window!!.decorView)
                check(nodes.filterIsInstance<android.widget.Spinner>().none{it.isShown && it.adapter is android.widget.ArrayAdapter<*> && it.adapter.count>5})
                check(!nodes.filterIsInstance<RadioButton>().first{it.text.toString()==ctx.getString(R.string.timer_pause_keep_black)}.isEnabled)
                check(nodes.filterIsInstance<TextView>().none{it.isShown && it.text.toString()==ctx.getString(R.string.ui_choose_player)})
                dialog.dismiss()
            }
            passed.add("no player selection in home or timer; paused old sessions never masquerade as current")
            main {state(a,3)};await("Did not follow A"){ctx.queryNowPlaying()?.controller?.sessionToken==a.sessionToken}
            main {state(b,3)};await("Multiple playing sessions did not disable control"){ctx.queryNowPlaying()==null}
            main {state(a,2)};await("Did not follow B"){ctx.queryNowPlaying()?.controller?.sessionToken==b.sessionToken}
            check(!PlaybackSelection.hasSelection)
            passed.add("automatically follows the unique playing session; conflicts disable control")
            lateinit var curtain:BlackOverlay
            main {curtain=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,recordSession=false);curtain.show{};check(curtain.isShowing);state(b,2)}
            await("Locked curtain did not capture its paused resume target"){ctx.queryNowPlaying()?.controller?.sessionToken==b.sessionToken}
            main {curtain.hide()};check(ctx.queryNowPlaying()==null)
            main {
                state(b,3)
                val failed=BlackOverlay(ctx,android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,recordSession=false)
                failed.show{}
                check(!failed.isShowing)
                state(b,2)
            }
            check(ctx.queryNowPlaying()==null) {"Failed curtain leaked a paused playback anchor"}
            main {state(a,3)};await("A missing"){ctx.queryNowPlaying()?.controller?.sessionToken==a.sessionToken}
            val latch=CountDownLatch(1);var result:TimerAcceptance?=null
            main {TimerRequests.submit(ctx,TimerRequest(1,System.currentTimeMillis()+10000,a.sessionToken,pauseKeepBlack=true,appPackage=ctx.packageName)){result=it;latch.countDown()}}
            check(latch.await(6,TimeUnit.SECONDS)&&result?.accepted==true);check(!PlaybackSelection.hasSelection)
            main {state(b,3)}
            await("Original timer did not pause A",15000){aCount.get()==1}
            check(bCount.get()==0)
            await("Playback did not follow B after original timer paused A"){ctx.queryNowPlaying()?.controller?.sessionToken==b.sessionToken}
            passed.add("accepted timer remains bound to A and never pins global playback; B is not paused")
            lateinit var stale:android.media.session.MediaController
            main {PlaybackSelection.beginAutomaticCurtain();stale=ctx.queryNowPlaying()!!.controller;b.release()}
            await("Destroyed current session retained"){ctx.queryNowPlaying()==null}
            check(PlaybackSelection.resolve(listOf(stale))==null) {"Delayed active-session list resurrected a destroyed anchor"}
            passed.add("paused curtain target resumes only within that curtain; destroyed source is not controlled")
            java.io.File(ctx.cacheDir,"auto-playback/results.json").apply{parentFile!!.mkdirs()}.writeText(org.json.JSONObject().put("passed",org.json.JSONArray(passed)).put("aPauseCount",aCount.get()).put("bPauseCount",bCount.get()).toString())
        } finally {
            main {sessions.forEach{runCatching{it.release()}};PlaybackSelection.clear();ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT));activity?.finish()}
            await("Cleanup failed"){!OverlayService.isRunning};check(BackupManager.restore(ctx,baseline));ctx.prefs().edit().apply{if(defaults==null)remove(PlayerTimerDefaults.KEY)else putString(PlayerTimerDefaults.KEY,defaults)}.commit()
        }
    }
}
