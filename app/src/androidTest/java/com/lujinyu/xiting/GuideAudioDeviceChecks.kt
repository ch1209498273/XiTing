package com.lujinyu.xiting

import android.app.Instrumentation
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioManager
import android.os.*
import android.view.View
import android.widget.TextView

internal class GuideAudioDeviceChecks(private val i:Instrumentation) {
 private val ctx get()=i.targetContext
 private fun main(block:()->Unit){var failure:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
 private fun await(message:String,limit:Long=6000,test:()->Boolean){val end=SystemClock.elapsedRealtime()+limit;while(!test()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30);check(test()){message}}
 private fun shell(command:String){ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
 private fun currentText():String {
  val root=i.uiAutomation.rootInActiveWindow ?: return ""
  fun scan(node:android.view.accessibility.AccessibilityNodeInfo):List<String> {
   val result=mutableListOf(node.text?.toString().orEmpty())
   for(index in 0 until node.childCount)node.getChild(index)?.let { result.addAll(scan(it)) }
   return result
  }
  return scan(root).joinToString("\n")
 }
 fun run(){
  check(Build.HARDWARE in listOf("ranchu","goldfish"))
  val baseline=BackupManager.buildBackupJson(ctx);val audio=ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager;val volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
  val passed=mutableListOf<String>();var activity:MainActivity?=null
  try {
   main{PlaybackSelection.clear();ServiceRecovery.stopped(ctx,false);ctx.prefs().edit().putBoolean(Prefs.ASSISTANT_WANTED,false).putString(Prefs.UPDATE_SEEN_VERSION,BuildConfig.VERSION_NAME).remove(FirstSuccessFlow.COMPLETE).putBoolean(Prefs.DEMO_FEEDBACK_PENDING,false).commit()}
   activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
   shell("cmd media_session volume --stream 3 --set 0")
   lateinit var guide:AlertDialog
   main{guide=GuideExperience.show(activity!!);guide.getButton(AlertDialog.BUTTON_POSITIVE).performClick()}
   await("Muted guide did not show volume help"){currentText().contains(ctx.getString(R.string.guide_sound_help_title))}
   check(OverlayService.instance?.isAnyBlackShowing()!=true && OverlayService.instance?.isDemoAudioPlaying()!=true)
   check(!ctx.prefs().getBoolean(FirstSuccessFlow.COMPLETE,false))
   i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
   passed.add("zero media volume shows friendly help before any black curtain")
   shell("cmd media_session volume --stream 3 --set 5")
   main{guide=GuideExperience.show(activity!!);guide.getButton(AlertDialog.BUTTON_POSITIVE).performClick()}
   await("Built-in audio did not start",12000){OverlayService.instance?.isDemoAudioPlaying()==true && OverlayService.instance?.isAnyBlackShowing()==true}
   await("Audio stream did not become active",2000){audio.isMusicActive}
   passed.add("built-in sample produces active media playback without external player")
   await("Sample completion failed to close curtain",13000){OverlayService.instance?.isAnyBlackShowing()!=true && OverlayService.instance?.isDemoAudioPlaying()!=true}
   try {await("Feedback was not displayed",8000){currentText().contains(activity!!.getString(R.string.support_sound_question))}}catch(t:Throwable){
    throw IllegalStateException("Feedback missing: pending="+ctx.prefs().getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false)+" focus="+activity!!.hasWindowFocus()+" text="+currentText(),t)
   }
   val no=i.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("android:id/button2").first()
   check(no.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
   await("No-audio answer did not show friendly help"){currentText().contains(ctx.getString(R.string.guide_sound_help_title))}
   check(!currentText().contains(ctx.getString(R.string.support_title)) && !currentText().contains(ctx.getString(R.string.support_session)))
   check(!ctx.prefs().getBoolean(FirstSuccessFlow.COMPLETE,false))
   i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
   passed.add("sample completion releases audio and curtain; no-audio feedback never opens diagnostics or grants success")
   main {
    val service=OverlayService.instance!!
    OverlayService::class.java.getDeclaredMethod("startFirstUseDemo").apply{isAccessible=true}.invoke(service)
    check(service.isDemoInProgress() && !service.isAnyBlackShowing())
    service.toggleOverlay()
    check(!service.isDemoInProgress() && !service.isAnyBlackShowing())
   }
   SystemClock.sleep(500)
   check(OverlayService.instance?.isAnyBlackShowing()!=true && OverlayService.instance?.isDemoAudioPlaying()!=true)
   passed.add("toggle during async preparation cancels it; late prepared callbacks never create an orphan curtain")
   main {
    val service=OverlayService.instance!!
    OverlayService::class.java.getDeclaredMethod("startFirstUseDemo").apply{isAccessible=true}.invoke(service)
    check(service.isDemoInProgress())
    val receiver=OverlayService::class.java.getDeclaredField("screenOffReceiver").apply{isAccessible=true}.get(service) as android.content.BroadcastReceiver
    receiver.onReceive(ctx,Intent(Intent.ACTION_SCREEN_OFF))
    check(!service.isDemoInProgress())
   }
   SystemClock.sleep(500)
   check(OverlayService.instance?.isAnyBlackShowing()!=true && OverlayService.instance?.isDemoAudioPlaying()!=true)
   passed.add("screen-off handler cancels preparation without delayed playback or orphan curtain")
   main{guide=GuideExperience.show(activity!!);guide.getButton(AlertDialog.BUTTON_POSITIVE).performClick()}
   await("Retry did not play",12000){OverlayService.instance?.isDemoAudioPlaying()==true}
   main{ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT))}
   await("Service exit leaked sample"){OverlayService.instance==null}
   check(!ctx.prefs().getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false))
   passed.add("early service exit releases test audio without false completion")
   main{GuideExperience.returnHelp(activity!!)}
   check(!currentText().contains(ctx.getString(R.string.support_title)))
   passed.add("return failure remains plain-language help")
   i.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
   val about=i.startActivitySync(Intent(ctx,AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AboutActivity
   check(!currentText().contains(ctx.getString(R.string.about_diag)) && !currentText().contains(ctx.getString(R.string.about_crash)))
   main{about.finish()}
   passed.add("about screen contains no diagnostics or crash-log actions")
   java.io.File(ctx.cacheDir,"guide-audio/results.json").apply{parentFile!!.mkdirs()}.writeText(org.json.JSONObject().put("passed",org.json.JSONArray(passed)).put("externalPlayerRequired",false).toString())
  }finally{
   shell("cmd media_session volume --stream 3 --set $volume")
   main{ctx.startService(Intent(ctx,OverlayService::class.java).setAction(OverlayService.ACTION_EXIT));activity?.finish();PlaybackSelection.clear()}
   await("Service cleanup incomplete"){!OverlayService.isRunning};check(BackupManager.restore(ctx,baseline))
  }
 }
}
