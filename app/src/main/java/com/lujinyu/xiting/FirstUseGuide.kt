package com.lujinyu.xiting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings

internal fun MainActivity.showFirstUseGuide() { GuideExperience.show(this) }
internal fun MainActivity.showDemoFeedback() { GuideExperience.consumeFeedback(this) }

/** Every automatic failure stays in plain-language help; diagnostics are explicit only. */
internal object GuideExperience {
 fun show(activity:Activity):AlertDialog=AlertDialog.Builder(activity).setTitle(R.string.guide_title)
  .setMessage(R.string.guide_intro).setPositiveButton(R.string.guide_start){_,_->start(activity)}
  .setNegativeButton(R.string.dlg_cancel,null).show()
 fun soundHelp(activity:Activity):AlertDialog=AlertDialog.Builder(activity).setTitle(R.string.guide_sound_help_title)
  .setMessage(R.string.guide_sound_help).setPositiveButton(R.string.guide_retry_audio){_,_->show(activity)}
  .setNegativeButton(R.string.dlg_cancel,null).show()
 fun returnHelp(activity:Activity):AlertDialog=AlertDialog.Builder(activity).setTitle(R.string.guide_return_help_title)
  .setMessage(R.string.guide_return_help).setPositiveButton(R.string.guide_retry_audio){_,_->show(activity)}
  .setNegativeButton(R.string.dlg_cancel,null).show()
 private fun start(activity:Activity) {
  val service=OverlayService.instance
  val audio=activity.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
  val decision=demoStartDecision(Settings.canDrawOverlays(activity),service?.isAnyBlackShowing()==true || service?.isDemoInProgress()==true || (service?.timerRemainingMs() ?: 0)>0,
   audio.getStreamVolume(AudioManager.STREAM_MUSIC),audio.isMusicActive)
  if(decision==DemoStartDecision.MUTED){soundHelp(activity);return}
  if(decision!=DemoStartDecision.START) {
   AlertDialog.Builder(activity).setTitle(R.string.guide_title).setMessage(when(decision){
    DemoStartDecision.PERMISSION->R.string.guide_permission
    DemoStartDecision.PLAYBACK->R.string.guide_pause_first
    else->R.string.guide_busy
   }).setPositiveButton(if(decision==DemoStartDecision.PERMISSION)R.string.locale_dlg_go else R.string.dlg_got_it){_,_->
    if(decision==DemoStartDecision.PERMISSION)try{activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${activity.packageName}")))}catch(_:RuntimeException){failure(activity)}
   }.setNegativeButton(R.string.dlg_cancel,null).show();return
  }
  try {
   activity.startForegroundService(Intent(activity,OverlayService::class.java).setAction(OverlayService.ACTION_START_DEMO))
   val feedbackUntil=android.os.SystemClock.elapsedRealtime()+35_000
   fun watch() {
    if(activity.isFinishing || activity.isDestroyed)return
    if(OverlayService.instance?.isDemoInProgress()==true)activity.window.decorView.postDelayed({watch()},500)
    else {
     consumeFeedback(activity)
     if(activity.prefs().getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false) && android.os.SystemClock.elapsedRealtime()<feedbackUntil)
      activity.window.decorView.postDelayed({watch()},500)
    }
   }
   activity.window.decorView.postDelayed({watch()},500)
  } catch(_:RuntimeException) {failure(activity)}
 }
 fun failure(activity:Activity):AlertDialog=AlertDialog.Builder(activity).setTitle(R.string.guide_title)
  .setMessage(R.string.guide_audio_failed).setPositiveButton(R.string.guide_retry_audio){_,_->show(activity)}
  .setNegativeButton(R.string.dlg_cancel,null).show()
 fun consumeFeedback(activity:Activity) {
  if(activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus() || OverlayService.instance?.isAnyBlackShowing()==true ||
   !activity.prefs().getBoolean(Prefs.DEMO_FEEDBACK_PENDING,false))return
  activity.prefs().edit().putBoolean(Prefs.DEMO_FEEDBACK_PENDING,false).apply()
  FirstSuccessFlow.confirm(activity)
 }
}
